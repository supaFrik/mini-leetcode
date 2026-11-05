package jsd.toiyeujava.mini_leetcode.console.service;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteRequest;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteResponse;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleTranscriptEntry;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleTranscriptResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp nghiệp vụ console OOP.
 *
 * <p>Kiểm soát bảo mật tập trung ở đây:
 * <ul>
 *   <li>chỉ bài {@code OOP_GRINDING} mới được dùng console;</li>
 *   <li>console bị khoá trong kỳ thi (bài đang nằm trong một kỳ thi đang chạy);</li>
 *   <li>đường dẫn file đi qua {@link SourcePathValidator} (chống Zip Slip / path traversal);</li>
 *   <li>giới hạn kích thước payload và số lệnh trong phiên;</li>
 *   <li>mã nguồn học viên <b>chỉ</b> được chạy trong Docker sandbox của judge worker.</li>
 * </ul>
 */
@Slf4j
@Service
public class ConsoleService {

    private final ProblemRepository problemRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final JudgeWorkerClient judgeWorkerClient;
    private final ConsoleTranscriptStore transcriptStore;
    private final ConsoleGatewayProperties properties;

    public ConsoleService(ProblemRepository problemRepository,
                          ContestProblemRepository contestProblemRepository,
                          JudgeWorkerClient judgeWorkerClient,
                          ConsoleTranscriptStore transcriptStore,
                          ConsoleGatewayProperties properties) {
        this.problemRepository = problemRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.judgeWorkerClient = judgeWorkerClient;
        this.transcriptStore = transcriptStore;
        this.properties = properties;
    }

    /**
     * Chạy một lệnh console.
     *
     * @param userId   chủ phiên console
     * @param problemId bài OOP
     * @param request  payload (files + input + expression)
     */
    @Transactional(readOnly = true)
    public ConsoleExecuteResponse execute(Long userId, Long problemId, ConsoleExecuteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Console request is required");
        }
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));

        if (problem.getProblemType() != ProblemType.OOP_GRINDING) {
            throw new IllegalArgumentException("Console is only available for OOP problems");
        }

        assertNotDuringContest(problemId);

        List<JudgeWorkerClient.JudgeWorkerFile> files = validateFiles(request);

        int used = transcriptStore.count(userId, problemId);
        if (used >= properties.getMaxCommandsPerSession()) {
            throw new ConsoleRateLimitExceededException(
                    "Console command limit reached for this session ("
                            + properties.getMaxCommandsPerSession() + " commands)");
        }

        JudgeWorkerClient.JudgeWorkerResponse workerResponse = judgeWorkerClient.execute(
                new JudgeWorkerClient.JudgeWorkerRequest(
                        userId,
                        problemId,
                        files,
                        request.input(),
                        request.expression(),
                        request.mode()));

        ConsoleExecuteResponse response = new ConsoleExecuteResponse(
                safe(workerResponse.stdout()),
                safe(workerResponse.stderr()),
                workerResponse.exitCode(),
                workerResponse.runtimeMs(),
                workerResponse.peakMemoryKb(),
                workerResponse.compileErrors(),
                workerResponse.truncated(),
                workerResponse.timedOut());

        String transcriptInput = (request.expression() != null && !request.expression().isBlank())
                ? request.expression()
                : request.input();
        transcriptStore.append(userId, problemId, ConsoleTranscriptStore.entryOf(
                transcriptInput,
                response.stdout(),
                response.stderr(),
                response.exitCode(),
                response.runtimeMs(),
                response.peakMemoryKb(),
                response.compileErrors()));

        log.info("Console command executed userId={} problemId={} runtimeMs={} peakMemoryKb={} exitCode={}",
                userId, problemId, response.runtimeMs(), response.peakMemoryKb(), response.exitCode());

        return response;
    }

    /** Đọc transcript của phiên console. */
    @Transactional(readOnly = true)
    public ConsoleTranscriptResponse getTranscript(Long userId, Long problemId) {
        List<ConsoleTranscriptEntry> entries = transcriptStore.read(userId, problemId);
        int limit = properties.getMaxCommandsPerSession();
        return new ConsoleTranscriptResponse(entries, limit, transcriptStore.count(userId, problemId));
    }

    /** Xóa transcript của phiên console. */
    @Transactional
    public void clearTranscript(Long userId, Long problemId) {
        transcriptStore.clear(userId, problemId);
    }

    /**
     * Console bị khoá khi bài đang nằm trong một kỳ thi đang diễn ra (exam mode).
     */
    private void assertNotDuringContest(Long problemId) {
        List<Contest> contests = contestProblemRepository.findContestsByProblemId(problemId);
        if (contests == null || contests.isEmpty()) {
            return;
        }
        for (Contest contest : contests) {
            if (contest != null && contest.resolveStatus() == ContestStatus.RUNNING) {
                throw new ConsoleDisabledException("Console is disabled during the exam");
            }
        }
    }

    /**
     * Kiểm tra & chuẩn hoá danh sách file gửi lên.
     */
    private List<JudgeWorkerClient.JudgeWorkerFile> validateFiles(ConsoleExecuteRequest request) {
        List<ConsoleExecuteRequest.ConsoleFileDto> raw = request.files();
        if (raw == null || raw.isEmpty()) {
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
            String sanitized = SourcePathValidator.sanitizeAndValidatePath(file.path());
            for (char c : sanitized.toCharArray()) {
                if (c == ';' || c == '&' || c == '|' || c == '<' || c == '>' || c == '`') {
                    throw new InvalidSubmissionPathException(
                            "Dangerous shell character '" + c + "' in path: " + file.path());
                }
            }
            String content = file.content() == null ? "" : file.content();
            long size = content.getBytes(StandardCharsets.UTF_8).length;
            totalBytes += size;
            if (totalBytes > properties.getMaxSourceBytes()) {
                throw new PayloadTooLargeException("Payload exceeds the allowed limit (20MB)");
            }
            if (sanitized.toLowerCase().endsWith(".java")) {
                hasJavaFile = true;
            }
            files.add(new JudgeWorkerClient.JudgeWorkerFile(sanitized, content));
        }

        if (!hasJavaFile) {
            throw new IllegalArgumentException("No Java source files provided");
        }
        return files;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
