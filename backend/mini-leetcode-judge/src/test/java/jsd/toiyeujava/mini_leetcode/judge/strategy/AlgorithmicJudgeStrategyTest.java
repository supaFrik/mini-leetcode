package jsd.toiyeujava.mini_leetcode.judge.strategy;

import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.DockerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.ExecutionResult;
import jsd.toiyeujava.mini_leetcode.judge.LanguageRuntime;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlgorithmicJudgeStrategyTest {

    @Mock
    private SubmissionService submissionService;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private DockerExecutor dockerExecutor;

    private AlgorithmicJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AlgorithmicJudgeStrategy(
                submissionService, testCaseRepository, storageService, dockerExecutor, new JudgeProperties());
    }

    private SubmissionTaskMessage message(SubmissionLanguage language) {
        return new SubmissionTaskMessage(1L, 10L, 5L, language);
    }

    private Problem problem() {
        return Problem.builder()
                .id(10L)
                .timeLimitMs(1000)
                .memoryLimitMb(256)
                .problemType(ProblemType.ALGORITHMIC)
                .build();
    }

    private TestCase testCase(String inUri, String outUri) {
        return TestCase.builder()
                .inputFileUri(inUri)
                .expectedOutputUri(outUri)
                .build();
    }

    private void stubFile(String uri, String content) {
        when(storageService.downloadFileByUri(uri))
                .thenAnswer(inv -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("supports returns true for ALGORITHMIC and null, false for OOP_GRINDING")
    void supports_ValidatesProblemType() {
        assertThat(strategy.supports(ProblemType.ALGORITHMIC)).isTrue();
        assertThat(strategy.supports(null)).isTrue();
        assertThat(strategy.supports(ProblemType.OOP_GRINDING)).isFalse();
    }

    @Test
    @DisplayName("Fails submission when problem has no test cases")
    void executeJudge_NoTestCases_FailsSubmission(@TempDir Path workDir) throws Exception {
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of());

        strategy.executeJudge(message(SubmissionLanguage.PYTHON), problem(), workDir);

        verify(submissionService).failSubmission(1L, "Problem has no test cases");
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Returns CE when compilation fails for compiled language")
    void executeJudge_CompilationFails_UpdatesVerdictToCE(@TempDir Path workDir) throws Exception {
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L))
                .thenReturn(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("public class Solution {");
        when(dockerExecutor.compile(any(Path.class), eq(LanguageRuntime.JAVA)))
                .thenReturn(new CompileResult(false, "syntax error"));

        strategy.executeJudge(message(SubmissionLanguage.JAVA), problem(), workDir);

        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(1), eq("syntax error"));
        verify(dockerExecutor, never()).execute(any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("Wrong answer on a public case reports the first differing line; normalized mode ignores layout")
    void executeJudge_WrongAnswer_ReportsLineAndHonoursOutputPolicy(@TempDir Path workDir) throws Exception {
        TestCase first = TestCase.builder().inputFileUri("in1").expectedOutputUri("out1").isHidden(false).label("spacing").build();
        TestCase second = TestCase.builder().inputFileUri("in2").expectedOutputUri("out2").isHidden(false).label("totals").build();
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(first, second));
        when(submissionService.getSubmissionCode(1L)).thenReturn("print(42)");
        stubFile("in1", "1");
        stubFile("out1", "Total:   42");
        stubFile("in2", "2");
        stubFile("out2", "Total: 84");
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), eq("1"), eq(1000L), eq(256)))
                .thenReturn(new ExecutionResult(0, false, false, "  Total: 42\n\n", 80, 1024));
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), eq("2"), eq(1000L), eq(256)))
                .thenReturn(new ExecutionResult(0, false, false, "Total: 85\n", 80, 1024));
        Problem problem = problem();
        problem.setGradingConfig("{\"output\": {\"mode\": \"normalized\"}}");

        strategy.executeJudge(message(SubmissionLanguage.PYTHON), problem, workDir);

        // Case 1 chỉ khác khoảng trắng ⇒ qua ở chế độ normalized; case 2 sai nội dung ⇒ WA kèm dòng lệch.
        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.WA), eq(80), eq(1024), eq(1), eq(2),
                eq("Wrong answer on test case #2 (totals) — compared ignoring extra spaces and blank lines\n"
                        + "Line 1: expected \"Total: 84\"\n        but got  \"Total: 85\""));
    }

    @Test
    @DisplayName("Executes test cases and updates verdict to AC when all match")
    void executeJudge_AllTestCasesPass_UpdatesVerdictToAC(@TempDir Path workDir) throws Exception {
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L))
                .thenReturn(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("print(42)");
        stubFile("in1", "1");
        stubFile("out1", "42");
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), eq(1000L), eq(256)))
                .thenReturn(new ExecutionResult(0, false, false, "42\n", 80, 1024));

        strategy.executeJudge(message(SubmissionLanguage.PYTHON), problem(), workDir);

        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.AC),
                eq(80), eq(1024), eq(1), eq(1), isNull());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException if language runtime is unknown")
    void executeJudge_UnsupportedLanguage_ThrowsException(@TempDir Path workDir) {
        assertThatThrownBy(() -> strategy.executeJudge(message(SubmissionLanguage.CPP), problem(), workDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported language");
    }
}
