package jsd.toiyeujava.mini_leetcode.console.service;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleDisabledException;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleRateLimitExceededException;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SourcePathValidator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Kiểm tra một lần Run của console live trước khi API mở kết nối tới judge worker.
 * Mã nguồn học viên KHÔNG bao giờ chạy ở API — chỉ trong Docker sandbox của judge.
 */
@Service
public class ConsoleService {

    /** Ngôn ngữ duy nhất console hỗ trợ hiện tại. */
    public static final String SUPPORTED_LANGUAGE = "JAVA";

    public record SourceFile(String path, String content) {
    }

    private final ProblemRepository problemRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final StringRedisTemplate redis;
    private final ConsoleGatewayProperties properties;

    public ConsoleService(ProblemRepository problemRepository,
                          ContestProblemRepository contestProblemRepository,
                          StringRedisTemplate redis,
                          ConsoleGatewayProperties properties) {
        this.problemRepository = problemRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * Kiểm tra quyền + payload, trừ một lượt trong hạn mức, và trả về danh sách file đã chuẩn hoá.
     * Lượt chỉ bị trừ khi mọi kiểm tra khác đã qua.
     */
    @Transactional(readOnly = true)
    public List<SourceFile> prepareRun(Long userId, Long problemId, String language, List<SourceFile> files) {
        if (!SUPPORTED_LANGUAGE.equals(language)) {
            throw new IllegalArgumentException("Language " + language + " cannot run in the console (Java only)");
        }
        if (problemId == null || !problemRepository.existsById(problemId)) {
            throw new ResourceNotFoundException("Problem not found");
        }
        for (Contest contest : contestProblemRepository.findContestsByProblemId(problemId)) {
            if (contest != null && contest.resolveStatus() == ContestStatus.RUNNING) {
                throw new ConsoleDisabledException("Console is disabled during the exam");
            }
        }
        List<SourceFile> validated = validateFiles(files);
        consumeRunQuota(userId);
        return validated;
    }

    /** Hạn mức cửa sổ cố định: INCR + TTL khi tạo key. */
    private void consumeRunQuota(Long userId) {
        String key = "console:runs:" + userId;
        Long used = redis.opsForValue().increment(key);
        if (used != null && used == 1L) {
            redis.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }
        if (used != null && used > properties.getRunsPerWindow()) {
            throw new ConsoleRateLimitExceededException("Console run limit reached, try again later");
        }
    }

    private List<SourceFile> validateFiles(List<SourceFile> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("No Java source files provided");
        }
        if (raw.size() > properties.getMaxFiles()) {
            throw new PayloadTooLargeException("Too many files (max " + properties.getMaxFiles() + ")");
        }
        long totalBytes = 0;
        boolean hasJava = false;
        List<SourceFile> files = new ArrayList<>(raw.size());
        for (SourceFile file : raw) {
            String path = SourcePathValidator.sanitizeAndValidatePath(file.path());
            for (char c : path.toCharArray()) {
                if (";&|<>`$".indexOf(c) >= 0) {
                    throw new InvalidSubmissionPathException("Invalid character '" + c + "' in path: " + file.path());
                }
            }
            String content = file.content() == null ? "" : file.content();
            totalBytes += content.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > properties.getMaxSourceBytes()) {
                throw new PayloadTooLargeException("Source code exceeds the console limit ("
                        + properties.getMaxSourceBytes() / 1024 + " KB)");
            }
            hasJava |= path.toLowerCase().endsWith(".java");
            files.add(new SourceFile(path, content));
        }
        if (!hasJava) {
            throw new IllegalArgumentException("No Java source files provided");
        }
        return files;
    }
}
