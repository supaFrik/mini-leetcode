package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.judge.strategy.AlgorithmicJudgeStrategy;
import jsd.toiyeujava.mini_leetcode.judge.strategy.JudgeStrategy;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JudgeServiceStrategyDispatchTest {

    private static final Path TEMP_ROOT = Path.of("temp-submissions");
    private static final Long SUBMISSION_ID = 88888L;
    private static final Long PROBLEM_ID = 777L;

    @Mock
    private SubmissionService submissionService;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private DockerExecutor dockerExecutor;

    private AlgorithmicJudgeStrategy realAlgorithmicStrategy;

    @BeforeEach
    void setUp() {
        realAlgorithmicStrategy = new AlgorithmicJudgeStrategy(
                submissionService, testCaseRepository, storageService, dockerExecutor, new JudgeProperties());
    }

    @AfterEach
    void tearDown() throws IOException {
        Path workDir = TEMP_ROOT.resolve(String.valueOf(SUBMISSION_ID));
        if (Files.exists(workDir)) {
            try (var paths = Files.walk(workDir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private SubmissionTaskMessage taskMessage() {
        return new SubmissionTaskMessage(SUBMISSION_ID, PROBLEM_ID, 100L, SubmissionLanguage.JAVA);
    }

    private Problem createProblem(ProblemType problemType) {
        return Problem.builder()
                .id(PROBLEM_ID)
                .timeLimitMs(1000)
                .memoryLimitMb(256)
                .problemType(problemType)
                .build();
    }

    // =========================================================================
    // 1. AlgorithmicJudgeStrategy.supports tests
    // =========================================================================

    @Test
    @DisplayName("AlgorithmicJudgeStrategy: supports(null) returns true defensively")
    void algorithmicStrategy_supportsNull_returnsTrue() {
        assertThat(realAlgorithmicStrategy.supports(null)).isTrue();
    }

    @Test
    @DisplayName("AlgorithmicJudgeStrategy: supports(ALGORITHMIC) returns true")
    void algorithmicStrategy_supportsAlgorithmic_returnsTrue() {
        assertThat(realAlgorithmicStrategy.supports(ProblemType.ALGORITHMIC)).isTrue();
    }

    @Test
    @DisplayName("AlgorithmicJudgeStrategy: supports(OOP_GRINDING) returns false")
    void algorithmicStrategy_supportsOopGrinding_returnsFalse() {
        assertThat(realAlgorithmicStrategy.supports(ProblemType.OOP_GRINDING)).isFalse();
    }

    // =========================================================================
    // 2. JudgeServiceImpl Strategy Dispatching tests
    // =========================================================================

    @Test
    @DisplayName("JudgeServiceImpl: OOP_GRINDING without OOP strategy calls failSubmission and does not crash worker thread")
    void judge_OopGrindingWithoutRegisteredStrategy_failsSubmissionCleanlyWithoutCrashing() {
        JudgeServiceImpl judgeService = new JudgeServiceImpl(
                submissionService, problemRepository, List.of(realAlgorithmicStrategy));

        Problem oopProblem = createProblem(ProblemType.OOP_GRINDING);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(oopProblem));

        // Must not throw an unhandled exception outside
        judgeService.judge(taskMessage());

        verify(submissionService).updateSubmissionVerdict(
                eq(SUBMISSION_ID), eq(SubmissionStatus.JUDGING), isNull(), isNull(), isNull(), eq(0), eq(0), isNull());
        verify(submissionService).failSubmission(
                eq(SUBMISSION_ID), eq("No judge strategy for problem type: OOP_GRINDING"));
    }

    @Test
    @DisplayName("JudgeServiceImpl: problem with problemType == null dispatches to AlgorithmicJudgeStrategy")
    void judge_ProblemTypeNull_dispatchesToAlgorithmicStrategy() throws Exception {
        JudgeStrategy mockStrategy = mock(JudgeStrategy.class);
        when(mockStrategy.supports(null)).thenReturn(true);

        JudgeServiceImpl judgeService = new JudgeServiceImpl(
                submissionService, problemRepository, List.of(mockStrategy));

        Problem nullTypeProblem = createProblem(null);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(nullTypeProblem));

        judgeService.judge(taskMessage());

        verify(mockStrategy).executeJudge(eq(taskMessage()), eq(nullTypeProblem), any(Path.class));
        verify(submissionService, never()).failSubmission(eq(SUBMISSION_ID), anyString());
    }

    @Test
    @DisplayName("JudgeServiceImpl: executeJudge throws unexpected exception -> cleans up workDir and calls failSubmission")
    void judge_ExecuteJudgeThrowsUnexpectedException_cleansUpWorkDirAndCallsFailSubmission() throws Exception {
        Path workDir = TEMP_ROOT.resolve(String.valueOf(SUBMISSION_ID));

        JudgeStrategy failingStrategy = mock(JudgeStrategy.class);
        when(failingStrategy.supports(ProblemType.ALGORITHMIC)).thenReturn(true);
        doAnswer(invocation -> {
            Path dir = invocation.getArgument(2);
            // Simulate workDir population with files & subdirectories
            Path nested = dir.resolve("nested-code");
            Files.createDirectories(nested);
            Files.writeString(nested.resolve("Solution.java"), "public class Solution {}");
            Files.writeString(dir.resolve("testcase.in"), "1 2 3");
            assertThat(Files.exists(nested.resolve("Solution.java"))).isTrue();

            throw new RuntimeException("Docker daemon connection timed out unexpectedly");
        }).when(failingStrategy).executeJudge(any(), any(), any());

        JudgeServiceImpl judgeService = new JudgeServiceImpl(
                submissionService, problemRepository, List.of(failingStrategy));

        Problem problem = createProblem(ProblemType.ALGORITHMIC);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));

        // Execute judge
        judgeService.judge(taskMessage());

        // Verify failSubmission called with exception message
        verify(submissionService).failSubmission(
                eq(SUBMISSION_ID), eq("Docker daemon connection timed out unexpectedly"));

        // Verify workDir and all nested contents were completely cleaned up
        assertThat(Files.exists(workDir))
                .as("workDir must be deleted by deleteQuietly in finally block")
                .isFalse();
    }

    @Test
    @DisplayName("JudgeServiceImpl: executeJudge throws InterruptedException -> restores interrupt flag and cleans up workDir")
    void judge_ExecuteJudgeThrowsInterruptedException_restoresInterruptAndCleansUpWorkDir() throws Exception {
        Path workDir = TEMP_ROOT.resolve(String.valueOf(SUBMISSION_ID));

        JudgeStrategy interruptStrategy = mock(JudgeStrategy.class);
        when(interruptStrategy.supports(ProblemType.ALGORITHMIC)).thenReturn(true);
        doAnswer(invocation -> {
            Path dir = invocation.getArgument(2);
            Files.writeString(dir.resolve("payload.bin"), "data");
            throw new InterruptedException("Worker thread interrupted");
        }).when(interruptStrategy).executeJudge(any(), any(), any());

        JudgeServiceImpl judgeService = new JudgeServiceImpl(
                submissionService, problemRepository, List.of(interruptStrategy));

        Problem problem = createProblem(ProblemType.ALGORITHMIC);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));

        // Clear interrupted flag before test
        Thread.interrupted();

        judgeService.judge(taskMessage());

        // Verify thread interrupt status is restored
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear flag for subsequent tests
        Thread.interrupted();

        verify(submissionService).failSubmission(eq(SUBMISSION_ID), eq("Judging was interrupted"));
        assertThat(Files.exists(workDir)).isFalse();
    }

    @Test
    @DisplayName("JudgeServiceImpl: multiple strategies dispatch to the correct matching strategy")
    void judge_MultipleStrategies_dispatchesToMatchingStrategy() throws Exception {
        JudgeStrategy algoStrategy = mock(JudgeStrategy.class);
        when(algoStrategy.supports(ProblemType.OOP_GRINDING)).thenReturn(false);

        JudgeStrategy oopStrategy = mock(JudgeStrategy.class);
        when(oopStrategy.supports(ProblemType.OOP_GRINDING)).thenReturn(true);

        JudgeServiceImpl judgeService = new JudgeServiceImpl(
                submissionService, problemRepository, List.of(algoStrategy, oopStrategy));

        Problem oopProblem = createProblem(ProblemType.OOP_GRINDING);
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(oopProblem));

        judgeService.judge(taskMessage());

        verify(oopStrategy).executeJudge(eq(taskMessage()), eq(oopProblem), any(Path.class));
        verify(algoStrategy, never()).executeJudge(any(), any(), any());
    }
}
