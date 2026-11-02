package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.judge.strategy.AlgorithmicJudgeStrategy;
import jsd.toiyeujava.mini_leetcode.judge.strategy.JudgeStrategy;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final Path TEMP_ROOT = Path.of("temp-submissions");

    private final SubmissionService submissionService;
    private final ProblemRepository problemRepository;
    private final List<JudgeStrategy> strategies;

    @Autowired
    public JudgeServiceImpl(SubmissionService submissionService,
                            ProblemRepository problemRepository,
                            List<JudgeStrategy> strategies) {
        this.submissionService = submissionService;
        this.problemRepository = problemRepository;
        this.strategies = strategies;
    }

    /**
     * Backward-compatible constructor for existing tests.
     */
    public JudgeServiceImpl(SubmissionService submissionService,
                            ProblemRepository problemRepository,
                            TestCaseRepository testCaseRepository,
                            StorageService storageService,
                            DockerExecutor dockerExecutor,
                            JudgeProperties judgeProperties) {
        this(submissionService, problemRepository, List.of(
                new AlgorithmicJudgeStrategy(submissionService, testCaseRepository, storageService, dockerExecutor, judgeProperties)
        ));
    }

    @Override
    public void judge(SubmissionTaskMessage message) {
        Long submissionId = message.submissionId();
        Path workDir = TEMP_ROOT.resolve(String.valueOf(submissionId));
        try {
            Optional<LanguageRuntime> supported = LanguageRuntime.from(message.language());
            if (supported.isEmpty()) {
                submissionService.failSubmission(
                        submissionId, "Unsupported language: " + message.language());
                return;
            }

            // Chống chấm lại: một message có thể được giao lại (XCLAIM) nếu worker
            // chết giữa chừng. Nếu bài nộp đã có verdict cuối cùng thì bỏ qua,
            // tránh chạy Docker lại và ghi đè kết quả đúng.
            if (submissionService.isTerminal(submissionId)) {
                log.info("Submission {} already has a final verdict, skipping re-judge", submissionId);
                return;
            }
            // Đếm số lần bài nộp bị chấm lại để giảng viên / hệ thống quan sát được.
            // Chỉ tăng khi bài nộp đã từng được chấm (JUDGING/COMPLETED/FAILED),
            // tức là lần chấm này là lần chấm lại do worker bị gián đoạn.
            if (submissionService.hasBeenJudged(submissionId)) {
                submissionService.markRetried(submissionId);
            }

            submissionService.updateSubmissionVerdict(
                    submissionId, SubmissionStatus.JUDGING, null, null, null, 0, 0, null);

            Problem problem = problemRepository.findById(message.problemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Problem not found with id: " + message.problemId()));

            JudgeStrategy strategy = strategies.stream()
                    .filter(s -> s.supports(problem.getProblemType()))
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException(
                            "No judge strategy for problem type: " + problem.getProblemType()));

            Files.createDirectories(workDir);

            strategy.executeJudge(message, problem, workDir);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            submissionService.failSubmission(submissionId, "Judging was interrupted");
        } catch (Exception e) {
            log.error("Judging failed for submission {}", submissionId, e);
            submissionService.failSubmission(submissionId, e.getMessage());
        } finally {
            deleteQuietly(workDir);
        }
    }

    private void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("Could not delete temp dir {}", dir, e);
        }
    }
}
