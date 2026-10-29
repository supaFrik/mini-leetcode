package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    private static final Path TEMP_ROOT = Path.of("temp-submissions");
    private static final int MAX_ERROR_MESSAGE_CHARS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    private final SubmissionService submissionService;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;
    private final DockerExecutor dockerExecutor;

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
                    null);
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
        SubmissionVerdict finalVerdict = SubmissionVerdict.AC;
        int passed = 0;
        int maxTimeMs = 0;
        int maxMemoryKb = 0;
        int memoryLimitMb = problem.getMemoryLimitMb() != null ? problem.getMemoryLimitMb() : DEFAULT_MEMORY_LIMIT_MB;

        for (TestCase testCase : testCases) {
            ExecutionResult result = dockerExecutor.execute(
                    workDir, runtime,
                    readText(testCase.getInputFileUri()),
                    problem.getTimeLimitMs(),
                    memoryLimitMb);
            SubmissionVerdict verdict = VerdictMapper.map(
                    result, readText(testCase.getExpectedOutputUri()));

            maxTimeMs = Math.max(maxTimeMs, result.runtimeMs());
            maxMemoryKb = Math.max(maxMemoryKb, result.peakMemoryKb());

            if (verdict != SubmissionVerdict.AC) {
                finalVerdict = verdict;
                break;
            }
            passed++;
        }
        return new JudgeOutcome(finalVerdict, passed, maxTimeMs, maxMemoryKb);
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

    private record JudgeOutcome(SubmissionVerdict verdict, int passed, int maxTimeMs, int maxMemoryKb) {
    }
}
