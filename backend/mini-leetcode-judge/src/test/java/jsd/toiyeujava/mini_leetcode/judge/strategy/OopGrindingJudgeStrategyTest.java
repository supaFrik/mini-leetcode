package jsd.toiyeujava.mini_leetcode.judge.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.DockerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopDockerRunner;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OopGrindingJudgeStrategyTest {

    @Mock
    private SubmissionService submissionService;

    @Mock
    private StorageService storageService;

    @Mock
    private MultiFileSourceExtractor sourceExtractor;

    @Mock
    private OopGrindingCompiler compiler;

    @Mock
    private DockerExecutor dockerExecutor;

    @Mock
    private OopDockerRunner oopDockerRunner;

    @Mock
    private SubmissionRepository submissionRepository;

    private ObjectMapper objectMapper;
    private OopGrindingJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        strategy = new OopGrindingJudgeStrategy(
                submissionService,
                storageService,
                sourceExtractor,
                compiler,
                objectMapper,
                dockerExecutor,
                oopDockerRunner,
                submissionRepository
        );
    }

    @Test
    @DisplayName("supports() returns true only for ProblemType.OOP_GRINDING")
    void supports_MatchesOnlyOopGrinding() {
        assertThat(strategy.supports(ProblemType.OOP_GRINDING)).isTrue();
        assertThat(strategy.supports(ProblemType.ALGORITHMIC)).isFalse();
        assertThat(strategy.supports(null)).isFalse();
    }

    @Test
    @DisplayName("Fail-Fast: When compilation fails, update verdict to CE, score 0.0, and never launch Docker")
    void executeJudge_CompilationFailure_UpdatesVerdictCeAndNeverInvokesDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 9999L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 1L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("public class Broken { int x = ; }");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        Path sourceFile = workDir.resolve("Broken.java");
        when(sourceExtractor.extract(anyString(), eq(workDir))).thenReturn(List.of(sourceFile));
        when(compiler.compile(eq(workDir), anyList())).thenReturn(
                new CompileResult(false, "Broken.java:1: error: illegal start of expression")
        );

        strategy.executeJudge(message, problem, workDir);

        // Verify verdict is updated to CE, status COMPLETED, errorMessage recorded
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                isNull(),
                isNull(),
                eq(0),
                eq(0),
                contains("error: illegal start of expression")
        );

        // Verify score = 0.0, executionPhase = COMPILATION_FAILED, scoreBreakdown JSON saved
        ArgumentCaptor<Submission> subCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(subCaptor.capture());
        Submission savedSub = subCaptor.getValue();
        assertThat(savedSub.getScore()).isEqualTo(0.0);
        assertThat(savedSub.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");
        assertThat(savedSub.getScoreBreakdown()).contains("\"passed\":false");

        // VERIFY ZERO DOCKER CONTAINER LAUNCHED
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Fail-Fast: When extraction fails (e.g. Zip Slip), update verdict to CE, score 0.0, and never launch Docker")
    void executeJudge_ExtractionFailure_UpdatesVerdictCeAndNeverInvokesDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 9998L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 1L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("corrupt content");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(sourceExtractor.extract(anyString(), eq(workDir)))
                .thenThrow(new SecurityException("Zip Slip detected! Entry [../evil.txt] resolves outside"));

        strategy.executeJudge(message, problem, workDir);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                isNull(),
                isNull(),
                eq(0),
                eq(0),
                contains("Zip Slip detected")
        );

        ArgumentCaptor<Submission> subCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(subCaptor.capture());
        Submission savedSub = subCaptor.getValue();
        assertThat(savedSub.getScore()).isEqualTo(0.0);
        assertThat(savedSub.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");

        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("When extraction yields no Java files, update verdict to CE, score 0.0, and never launch Docker")
    void executeJudge_NoJavaFilesFound_UpdatesVerdictCeAndNeverInvokesDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 9997L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 1L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("readme content");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(sourceExtractor.extract(anyString(), eq(workDir))).thenReturn(List.of(workDir.resolve("readme.txt")));

        strategy.executeJudge(message, problem, workDir);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                isNull(),
                isNull(),
                eq(0),
                eq(0),
                contains("No Java source files (.java) found")
        );

        ArgumentCaptor<Submission> subCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(subCaptor.capture());
        Submission savedSub = subCaptor.getValue();
        assertThat(savedSub.getScore()).isEqualTo(0.0);
        assertThat(savedSub.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");

        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("When submitted language is not Java, fail submission immediately")
    void executeJudge_NonJavaLanguage_FailsSubmission(@TempDir Path workDir) throws Exception {
        Long submissionId = 9996L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 1L, 1L, SubmissionLanguage.PYTHON);
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING).build();

        strategy.executeJudge(message, problem, workDir);

        verify(submissionService).failSubmission(eq(submissionId), contains("OOP Grinding only supports Java"));
        verifyNoInteractions(sourceExtractor);
        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("When compilation succeeds and OopDockerRunner reports 100.0, update verdict to AC with full score")
    void executeJudge_CompilationSuccess_RunsOopDockerRunnerAndUpdatesAcVerdict(@TempDir Path workDir) throws Exception {
        Long submissionId = 7777L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 1L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING)
                .gradingConfig("{\"rules\":[{\"targetClassName\":\"Shape\"}]}").build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("public class Shape {}");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        Path src = workDir.resolve("Shape.java");
        when(sourceExtractor.extract(anyString(), eq(workDir))).thenReturn(List.of(src));
        when(compiler.compile(eq(workDir), anyList())).thenReturn(new CompileResult(true, "Compilation succeeded"));

        ExecutionReport report = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .totalScore(100.0)
                .verdict("AC")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder().score(100.0).build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder()
                                .passedCount(5)
                                .totalCount(5)
                                .score(100.0)
                                .build())
                        .build())
                .build();
        when(oopDockerRunner.runInProcessHarness(eq(submissionId), eq(workDir), any())).thenReturn(report);

        strategy.executeJudge(message, problem, workDir);

        verify(oopDockerRunner).runInProcessHarness(eq(submissionId), eq(workDir), any());
        verify(oopDockerRunner, never()).runHarness(any(), any(), any(), anyLong());
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.AC),
                isNull(),
                isNull(),
                eq(5),
                eq(5),
                isNull()
        );

        ArgumentCaptor<Submission> subCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(subCaptor.capture());
        Submission savedSub = subCaptor.getValue();
        assertThat(savedSub.getScore()).isEqualTo(100.0);
        assertThat(savedSub.getExecutionPhase()).isEqualTo("COMPLETED");
        assertThat(savedSub.getScoreBreakdown()).contains("\"verdict\":\"AC\"");
    }

    @Test
    @DisplayName("Non-blocking pipeline: When OopDockerRunner reports partial failure (WA), update verdict to WA with partial score")
    void executeJudge_PartialFailure_RunsOopDockerRunnerAndUpdatesWaVerdict(@TempDir Path workDir) throws Exception {
        Long submissionId = 7778L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 1L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING)
                .gradingConfig("{\"rules\":[{\"targetClassName\":\"Shape\"}]}").build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("public class Shape {}");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        Path src = workDir.resolve("Shape.java");
        when(sourceExtractor.extract(anyString(), eq(workDir))).thenReturn(List.of(src));
        when(compiler.compile(eq(workDir), anyList())).thenReturn(new CompileResult(true, "Compilation succeeded"));

        ExecutionReport report = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .totalScore(76.0)
                .verdict("WA")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder().score(76.0).build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder()
                                .passedCount(4)
                                .totalCount(5)
                                .score(80.0)
                                .build())
                        .build())
                .build();
        when(oopDockerRunner.runInProcessHarness(eq(submissionId), eq(workDir), any())).thenReturn(report);

        strategy.executeJudge(message, problem, workDir);

        verify(oopDockerRunner).runInProcessHarness(eq(submissionId), eq(workDir), any());
        verify(oopDockerRunner, never()).runHarness(any(), any(), any(), anyLong());
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.WA),
                isNull(),
                isNull(),
                eq(4),
                eq(5),
                isNull()
        );

        ArgumentCaptor<Submission> subCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(subCaptor.capture());
        Submission savedSub = subCaptor.getValue();
        assertThat(savedSub.getScore()).isEqualTo(76.0);
        assertThat(savedSub.getExecutionPhase()).isEqualTo("COMPLETED");
        assertThat(savedSub.getScoreBreakdown()).contains("\"verdict\":\"WA\"");
    }
}
