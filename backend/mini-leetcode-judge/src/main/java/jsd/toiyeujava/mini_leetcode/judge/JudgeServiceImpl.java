package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    private static final Path TEMP_ROOT = Path.of("temp-submissions");
    private static final int MAX_ERROR_MESSAGE_CHARS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    /**
     * Thứ tự ưu tiên verdict khi chạy song song nhiều testcase.
     * Số nhỏ hơn => ưu tiên cao hơn.
     */
    private static final Map<SubmissionVerdict, Integer> VERDICT_PRIORITY = Map.of(
            SubmissionVerdict.TLE, 0,
            SubmissionVerdict.MLE, 1,
            SubmissionVerdict.RE, 2,
            SubmissionVerdict.WA, 3,
            SubmissionVerdict.CE, 4);

    private final SubmissionService submissionService;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;
    private final DockerExecutor dockerExecutor;
    private final JudgeProperties judgeProperties;

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
            LanguageRuntime runtime = supported.get();

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
            List<TestCase> testCases =
                    testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId());
            if (testCases.isEmpty()) {
                submissionService.failSubmission(submissionId, "Problem has no test cases");
                return;
            }

            Files.createDirectories(workDir);
            Files.writeString(
                    workDir.resolve(runtime.sourceFileName()),
                    submissionService.getSubmissionCode(submissionId));

            if (runtime.needsCompile()) {
                CompileResult compile = dockerExecutor.compile(workDir, runtime);
                if (!compile.success()) {
                    submissionService.updateSubmissionVerdict(
                            submissionId, SubmissionStatus.COMPLETED, SubmissionVerdict.CE,
                            0, 0, 0, testCases.size(), truncate(compile.output()));
                    return;
                }
            }

            JudgeOutcome outcome = runTestCases(workDir, runtime, problem, testCases);

            submissionService.updateSubmissionVerdict(
                    submissionId,
                    SubmissionStatus.COMPLETED,
                    outcome.verdict(),
                    outcome.maxTimeMs(),
                    outcome.maxMemoryKb(),
                    outcome.passed(),
                    testCases.size(),
                    outcome.errorMessage());
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

    private JudgeOutcome runTestCases(Path workDir, LanguageRuntime runtime, Problem problem,
                                      List<TestCase> testCases)
            throws IOException, InterruptedException {
        int memoryLimitMb = problem.getMemoryLimitMb() != null ? problem.getMemoryLimitMb() : DEFAULT_MEMORY_LIMIT_MB;
        if (judgeProperties.isParallelTestcases()) {
            return runTestCasesInParallel(workDir, runtime, problem, testCases, memoryLimitMb);
        }
        return runTestCasesSequentially(workDir, runtime, problem, testCases, memoryLimitMb);
    }

    /**
     * Chạy tuần tự, dừng ngay tại testcase lỗi đầu tiên (fail-fast).
     *
     * <p>Đây là hành vi mặc định, khớp với chính sách Fail-Fast của LeetCode
     * được mô tả trong ADR-004.
     */
    private JudgeOutcome runTestCasesSequentially(Path workDir, LanguageRuntime runtime, Problem problem,
                                                  List<TestCase> testCases, int memoryLimitMb)
            throws IOException, InterruptedException {
        SubmissionVerdict finalVerdict = SubmissionVerdict.AC;
        int passed = 0;
        int maxTimeMs = 0;
        int maxMemoryKb = 0;
        String errorMessage = null;

        for (TestCase testCase : testCases) {
            ExecutionResult result = dockerExecutor.execute(
                    workDir, runtime,
                    readText(testCase.getInputFileUri()),
                    problem.getTimeLimitMs(),
                    memoryLimitMb);
            SubmissionVerdict verdict = VerdictMapper.map(
                    result, readText(testCase.getExpectedOutputUri()), memoryLimitMb * 1024);

            maxTimeMs = Math.max(maxTimeMs, result.runtimeMs());
            maxMemoryKb = Math.max(maxMemoryKb, result.peakMemoryKb());

            if (verdict != SubmissionVerdict.AC) {
                finalVerdict = verdict;
                // Khi gặp Runtime Error, lưu trữ stderr (giữ cả đầu và đuôi) làm thông báo lỗi
                if (verdict == SubmissionVerdict.RE) {
                    errorMessage = truncateHeadTail(result.stderr());
                }
                break;
            }
            passed++;
        }
        return new JudgeOutcome(finalVerdict, passed, maxTimeMs, maxMemoryKb, errorMessage);
    }

    /**
     * Chạy song song các testcase (tùy chọn, mặc định tắt).
     *
     * <p>Khác với fail-fast, ở chế độ này mọi testcase đều được chạy để lấy đủ
     * số liệu. Verdict cuối cùng được chọn theo thứ tự ưu tiên
     * {@code TLE > MLE > RE > WA > CE} và {@code passTestCases} đếm số testcase
     * riêng lẻ cho kết quả AC.
     *
     * <p>Mỗi testcase chạy trong container riêng (tên UUID) và {@code /workspace}
     * được mount read-only, nên không có trạng thái chia sẻ giữa các testcase.
     */
    private JudgeOutcome runTestCasesInParallel(Path workDir, LanguageRuntime runtime, Problem problem,
                                                List<TestCase> testCases, int memoryLimitMb)
            throws IOException, InterruptedException {
        int parallelism = Math.max(1, Math.min(judgeProperties.getMaxParallelTestcases(), testCases.size()));
        ExecutorService pool = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "judge-testcase");
            t.setDaemon(true);
            return t;
        });

        List<Future<CaseOutcome>> futures = new ArrayList<>(testCases.size());
        try {
            for (TestCase testCase : testCases) {
                futures.add(pool.submit(() -> evaluateCase(workDir, runtime, problem, testCase, memoryLimitMb)));
            }

            List<CaseOutcome> outcomes = new ArrayList<>(futures.size());
            for (Future<CaseOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    log.error("Testcase evaluation failed in parallel mode", cause);
                    outcomes.add(new CaseOutcome(SubmissionVerdict.SV, 0, 0, cause.getMessage()));
                }
            }
            return aggregate(outcomes);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Chạy và chấm một testcase, trả về kết quả để gom lại. */
    private CaseOutcome evaluateCase(Path workDir, LanguageRuntime runtime, Problem problem,
                                     TestCase testCase, int memoryLimitMb) {
        try {
            ExecutionResult result = dockerExecutor.execute(
                    workDir, runtime,
                    readText(testCase.getInputFileUri()),
                    problem.getTimeLimitMs(),
                    memoryLimitMb);
            SubmissionVerdict verdict = VerdictMapper.map(
                    result, readText(testCase.getExpectedOutputUri()), memoryLimitMb * 1024);
            return new CaseOutcome(verdict, result.runtimeMs(), result.peakMemoryKb(), result.stderr());
        } catch (IOException e) {
            return new CaseOutcome(SubmissionVerdict.RE, 0, 0, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CaseOutcome(SubmissionVerdict.SV, 0, 0, "Judging was interrupted");
        } catch (Exception e) {
            return new CaseOutcome(SubmissionVerdict.RE, 0, 0, e.getMessage());
        }
    }

    /** Gom kết quả các testcase thành verdict cuối cùng. */
    private JudgeOutcome aggregate(List<CaseOutcome> outcomes) {
        SubmissionVerdict finalVerdict = SubmissionVerdict.AC;
        int passed = 0;
        int maxTimeMs = 0;
        int maxMemoryKb = 0;
        String errorMessage = null;

        for (CaseOutcome outcome : outcomes) {
            maxTimeMs = Math.max(maxTimeMs, outcome.runtimeMs());
            maxMemoryKb = Math.max(maxMemoryKb, outcome.peakMemoryKb());
            if (outcome.verdict() == SubmissionVerdict.AC) {
                passed++;
                continue;
            }
            if (VERDICT_PRIORITY.getOrDefault(outcome.verdict(), Integer.MAX_VALUE)
                    < VERDICT_PRIORITY.getOrDefault(finalVerdict, Integer.MAX_VALUE)) {
                finalVerdict = outcome.verdict();
            }
            if (finalVerdict == SubmissionVerdict.RE && errorMessage == null) {
                errorMessage = truncateHeadTail(outcome.stderr());
            }
        }
        return new JudgeOutcome(finalVerdict, passed, maxTimeMs, maxMemoryKb, errorMessage);
    }

    // TODO: đọc cả file testcase vào RAM, chỉ ổn với testcase nhỏ (repo yêu cầu streaming I/O).
    private String readText(String uri) throws IOException {
        try (InputStream in = storageService.downloadFileByUri(uri)) {
            if (in == null) {
                throw new IllegalStateException("Missing file in storage: " + uri);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_MESSAGE_CHARS) {
            return text;
        }
        return text.substring(0, MAX_ERROR_MESSAGE_CHARS);
    }

    /**
     * Cắt chuỗi lỗi giữ cả đầu và đuôi:
     * - Python traceback chứa lỗi cụ thể ở dòng cuối cùng
     * - Java traceback chứa exception name ở đầu
     */
    private String truncateHeadTail(String text) {
        if (text == null || text.length() <= MAX_ERROR_MESSAGE_CHARS) {
            return text;
        }
        String separator = "\n...\n";
        int headLength = 1000;
        int tailLength = MAX_ERROR_MESSAGE_CHARS - headLength - separator.length();
        return text.substring(0, headLength) + separator + text.substring(text.length() - tailLength);
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

    private record JudgeOutcome(SubmissionVerdict verdict, int passed, int maxTimeMs, int maxMemoryKb, String errorMessage) {
    }

    /** Kết quả của một testcase khi chạy song song. */
    private record CaseOutcome(SubmissionVerdict verdict, int runtimeMs, int peakMemoryKb, String stderr) {
    }
}
