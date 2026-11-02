package jsd.toiyeujava.mini_leetcode.judge.strategy;

import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.*;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlgorithmicJudgeStrategy implements JudgeStrategy {

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
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;
    private final DockerExecutor dockerExecutor;
    private final JudgeProperties judgeProperties;

    @Override
    public boolean supports(ProblemType problemType) {
        return problemType == null || problemType == ProblemType.ALGORITHMIC;
    }

    @Override
    public void executeJudge(SubmissionTaskMessage message, Problem problem, Path workDir) throws Exception {
        Long submissionId = message.submissionId();
        LanguageRuntime runtime = LanguageRuntime.from(message.language())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + message.language()));

        List<TestCase> testCases =
                testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId());
        if (testCases.isEmpty()) {
            submissionService.failSubmission(submissionId, "Problem has no test cases");
            return;
        }

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

    private String truncateHeadTail(String text) {
        if (text == null || text.length() <= MAX_ERROR_MESSAGE_CHARS) {
            return text;
        }
        String separator = "\n...\n";
        int headLength = 1000;
        int tailLength = MAX_ERROR_MESSAGE_CHARS - headLength - separator.length();
        return text.substring(0, headLength) + separator + text.substring(text.length() - tailLength);
    }

    private record JudgeOutcome(SubmissionVerdict verdict, int passed, int maxTimeMs, int maxMemoryKb, String errorMessage) {
    }

    private record CaseOutcome(SubmissionVerdict verdict, int runtimeMs, int peakMemoryKb, String stderr) {
    }
}
