package jsd.toiyeujava.mini_leetcode.console.service;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteRequest;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleDisabledException;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleRateLimitExceededException;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SourcePathValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Console OOP kiểu terminal IDE: gửi mã nguồn + stdin, nhận stdout/stderr.
 *
 * <p>Kiểm soát bảo mật tập trung ở đây: chỉ bài {@code OOP_GRINDING}; khoá khi bài
 * nằm trong kỳ thi đang chạy; path qua {@link SourcePathValidator}; giới hạn payload
 * và số lần chạy. Mã nguồn chỉ chạy trong Docker sandbox của judge worker.
 */
@Slf4j
@Service
public class ConsoleService {

    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final ProblemRepository problemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final JudgeWorkerClient judgeWorkerClient;
    private final StringRedisTemplate redisTemplate;
    private final ConsoleGatewayProperties properties;

    public ConsoleService(ProblemRepository problemRepository,
                          ContestParticipantRepository contestParticipantRepository,
                          JudgeWorkerClient judgeWorkerClient,
                          StringRedisTemplate redisTemplate,
                          ConsoleGatewayProperties properties) {
        this.problemRepository = problemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.judgeWorkerClient = judgeWorkerClient;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * KHÔNG {@code @Transactional}: lời gọi judge chặn tới {@code request-timeout-ms}; bọc trong
     * transaction sẽ giữ 1 kết nối DB suốt thời gian đó ⇒ vài người chạy cùng lúc là cạn pool.
     * Mỗi truy vấn repository tự chạy trong transaction ngắn của nó (open-in-view đã tắt).
     */
    public ConsoleExecuteResponse execute(Long userId, Long problemId, ConsoleExecuteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Console request is required");
        }
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));
        if (problem.getProblemType() != ProblemType.OOP_GRINDING) {
            throw new IllegalArgumentException("Console is only available for OOP problems");
        }
        assertNotInRunningContest(userId);
        List<JudgeWorkerClient.JudgeWorkerFile> files = validateFiles(request);
        consumeRun(userId);

        JudgeWorkerClient.JudgeWorkerResponse r = judgeWorkerClient.execute(
                new JudgeWorkerClient.JudgeWorkerRequest(files, request.input()));
        log.info("Console run userId={} problemId={} runtimeMs={} peakMemoryKb={} exitCode={}",
                userId, problemId, r.runtimeMs(), r.peakMemoryKb(), r.exitCode());
        return new ConsoleExecuteResponse(
                r.stdout() == null ? "" : r.stdout(),
                r.stderr() == null ? "" : r.stderr(),
                r.exitCode(), r.runtimeMs(), r.peakMemoryKb(), r.compileErrors(),
                r.truncated(), r.timedOut());
    }

    /**
     * Mỗi lần chạy = 1 container ⇒ giới hạn theo user, cửa sổ 1 giờ (INCR atomic).
     * Không gắn với problemId: code chạy là tuỳ ý, đổi bài không được thêm lượt.
     */
    private void consumeRun(Long userId) {
        String key = "console:runs:" + userId;
        Long runs = redisTemplate.opsForValue().increment(key);
        if (runs != null && runs == 1) {
            redisTemplate.expire(key, RATE_WINDOW);
        }
        if (runs != null && runs > properties.getMaxRunsPerHour()) {
            throw new ConsoleRateLimitExceededException(
                    "Console run limit reached (" + properties.getMaxRunsPerHour() + " runs per hour)");
        }
    }

    /**
     * Khoá console khi USER đang tham gia một kỳ thi đang diễn ra — ở MỌI bài, vì code gửi lên
     * là tuỳ ý (khoá theo bài thì chỉ cần gửi bài thi vào một problemId khác là lách được).
     * Người không thi (kể cả staff) vẫn dùng bình thường.
     */
    private void assertNotInRunningContest(Long userId) {
        for (Contest contest : contestParticipantRepository.findContestsByUserId(userId)) {
            if (contest != null && contest.resolveStatus() == ContestStatus.RUNNING) {
                throw new ConsoleDisabledException("Console is disabled during the exam");
            }
        }
    }

    private List<JudgeWorkerClient.JudgeWorkerFile> validateFiles(ConsoleExecuteRequest request) {
        List<ConsoleExecuteRequest.ConsoleFileDto> raw = request.files();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("No Java source files provided");
        }
        if (raw.size() > properties.getMaxFiles()) {
            throw new PayloadTooLargeException(
                    "Payload exceeds the allowed limit: too many files (max " + properties.getMaxFiles() + ")");
        }
        long totalBytes = 0;
        boolean hasJavaFile = false;
        List<JudgeWorkerClient.JudgeWorkerFile> files = new ArrayList<>(raw.size());
        for (ConsoleExecuteRequest.ConsoleFileDto file : raw) {
            if (file == null) {
                continue;
            }
            String path = SourcePathValidator.sanitizeAndValidatePath(file.path());
            for (char c : path.toCharArray()) {
                if (";&|<>`".indexOf(c) >= 0) {
                    throw new InvalidSubmissionPathException(
                            "Dangerous shell character '" + c + "' in path: " + file.path());
                }
            }
            totalBytes += file.content().getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > properties.getMaxSourceBytes()) {
                throw new PayloadTooLargeException("Payload exceeds the allowed limit");
            }
            hasJavaFile |= path.toLowerCase().endsWith(".java");
            files.add(new JudgeWorkerClient.JudgeWorkerFile(path, file.content()));
        }
        if (!hasJavaFile) {
            throw new IllegalArgumentException("No Java source files provided");
        }
        return files;
    }
}
