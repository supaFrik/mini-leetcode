package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

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

    private JudgeServiceImpl judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeServiceImpl(
                submissionService, problemRepository, testCaseRepository, storageService, dockerExecutor);
    }

    private SubmissionTaskMessage message(SubmissionLanguage language) {
        return new SubmissionTaskMessage(1L, 10L, 5L, language);
    }

    private Problem problem() {
        return Problem.builder().id(10L).timeLimitMs(1000).memoryLimitMb(256).build();
    }

    private TestCase testCase(String inputUri, String expectedUri) {
        return TestCase.builder().inputFileUri(inputUri).expectedOutputUri(expectedUri).build();
    }

    // Mỗi lần gọi trả một stream MỚI, vì stream đọc xong là hết.
    private void stubFile(String uri, String content) {
        when(storageService.downloadFileByUri(uri))
                .thenAnswer(invocation -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void stubProblemAndTestCases(List<TestCase> testCases) {
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem()));
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L)).thenReturn(testCases);
    }

    @Test
    @DisplayName("Completes with AC when all Python test cases pass, without compiling")
    void judge_PythonAllTestCasesPass_CompletesWithAccepted() throws Exception {
        stubProblemAndTestCases(List.of(testCase("in1", "out1"), testCase("in2", "out2")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("print(42)");
        stubFile("in1", "1");
        stubFile("out1", "42");
        stubFile("in2", "2");
        stubFile("out2", "42");
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), anyLong(), anyInt()))
                .thenReturn(new ExecutionResult(0, false, false, "42\n", 120, 0));

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.AC),
                eq(120), eq(0), eq(2), eq(2), isNull());
        verify(dockerExecutor, never()).compile(any(Path.class), any(LanguageRuntime.class));
    }

    @Test
    @DisplayName("Stops at the first wrong test case (fail-fast) and returns WA")
    void judge_SecondTestCaseWrong_StopsAndReturnsWrongAnswer() throws Exception {
        stubProblemAndTestCases(List.of(
                testCase("in1", "out1"), testCase("in2", "out2"), testCase("in3", "out3")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("print(42)");
        stubFile("in1", "1");
        stubFile("out1", "42");
        stubFile("in2", "2");
        stubFile("out2", "42");
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), anyLong(), anyInt()))
                .thenReturn(
                        new ExecutionResult(0, false, false, "42", 100, 0),
                        new ExecutionResult(0, false, false, "41", 200, 0));

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        verify(dockerExecutor, times(2))
                .execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), anyLong(), anyInt());
        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.WA),
                eq(200), eq(0), eq(1), eq(3), isNull());
    }

    @Test
    @DisplayName("Fails without running Docker when the language is unsupported (CPP)")
    void judge_UnsupportedLanguage_FailsWithoutRunningDocker() {
        judgeService.judge(message(SubmissionLanguage.CPP));

        verify(submissionService).failSubmission(eq(1L), eq("Unsupported language: CPP"));
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Fails without reading the submission code when the problem has no test cases")
    void judge_NoTestCases_Fails() {
        stubProblemAndTestCases(List.of());

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        verify(submissionService).failSubmission(eq(1L), eq("Problem has no test cases"));
        verify(submissionService, never()).getSubmissionCode(anyLong());
    }

    @Test
    @DisplayName("Fails the submission with the error message when Docker throws IOException")
    void judge_DockerExecutorThrows_FailsSubmission() throws Exception {
        stubProblemAndTestCases(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("print(42)");
        stubFile("in1", "1");
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), anyLong(), anyInt()))
                .thenThrow(new IOException("docker unavailable"));

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        verify(submissionService).failSubmission(eq(1L), eq("docker unavailable"));
    }

    @Test
    @DisplayName("Completes with AC when Java compiles and passes the test case")
    void judge_JavaCompilesAndPasses_CompletesWithAccepted() throws Exception {
        stubProblemAndTestCases(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("public class Solution { }");
        stubFile("in1", "21");
        stubFile("out1", "42");
        when(dockerExecutor.compile(any(Path.class), eq(LanguageRuntime.JAVA)))
                .thenReturn(new CompileResult(true, ""));
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.JAVA), anyString(), anyLong(), anyInt()))
                .thenReturn(new ExecutionResult(0, false, false, "42\n", 300, 0));

        judgeService.judge(message(SubmissionLanguage.JAVA));

        verify(dockerExecutor).compile(any(Path.class), eq(LanguageRuntime.JAVA));
        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.AC),
                eq(300), eq(0), eq(1), eq(1), isNull());
    }

    @Test
    @DisplayName("Returns CE and skips running test cases when Java fails to compile")
    void judge_JavaCompileError_ReturnsCompileErrorWithoutRunning() throws Exception {
        stubProblemAndTestCases(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("public class Solution {");
        when(dockerExecutor.compile(any(Path.class), eq(LanguageRuntime.JAVA)))
                .thenReturn(new CompileResult(false, "Solution.java:1: error: ';' expected"));

        judgeService.judge(message(SubmissionLanguage.JAVA));

        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(1), eq("Solution.java:1: error: ';' expected"));
        verify(dockerExecutor, never())
                .execute(any(Path.class), any(LanguageRuntime.class), anyString(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("Passes the problem memory limit to DockerExecutor")
    void judge_ProblemWithCustomMemoryLimit_PassesMemoryLimitToExecutor() throws Exception {
        Problem customProblem = Problem.builder().id(10L).timeLimitMs(1000).memoryLimitMb(512).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(customProblem));
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(10L))
                .thenReturn(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("print(42)");
        stubFile("in1", "1");
        stubFile("out1", "42");
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), eq(1000L), eq(512)))
                .thenReturn(new ExecutionResult(0, false, false, "42\n", 50, 0));

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        verify(dockerExecutor).execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), eq(1000L), eq(512));
    }

    @Test
    @DisplayName("Records stderr as error message when execution results in Runtime Error")
    void judge_RuntimeErrorWithStderr_RecordsErrorMessage() throws Exception {
        stubProblemAndTestCases(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("1 / 0");
        stubFile("in1", "1");
        stubFile("out1", "42");
        String stderr = "Traceback (most recent call last):\n  ZeroDivisionError: division by zero\n";
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), anyLong(), anyInt()))
                .thenReturn(new ExecutionResult(1, false, false, "", 50, 0, stderr));

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.RE),
                eq(50), eq(0), eq(0), eq(1), eq(stderr));
    }

    @Test
    @DisplayName("Truncates long stderr preserving both head and tail within 2000 chars")
    void judge_RuntimeErrorWithLongStderr_TruncatesPreservingHeadAndTail() throws Exception {
        stubProblemAndTestCases(List.of(testCase("in1", "out1")));
        when(submissionService.getSubmissionCode(1L)).thenReturn("crash");
        stubFile("in1", "1");
        stubFile("out1", "42");
        String head = "START_OF_ERROR_HEADER_";
        String tail = "_END_OF_ERROR_TRACEBACK";
        String longStderr = head + "A".repeat(3000) + tail;
        when(dockerExecutor.execute(any(Path.class), eq(LanguageRuntime.PYTHON), anyString(), anyLong(), anyInt()))
                .thenReturn(new ExecutionResult(1, false, false, "", 50, 0, longStderr));

        judgeService.judge(message(SubmissionLanguage.PYTHON));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(submissionService).updateSubmissionVerdict(
                eq(1L), eq(SubmissionStatus.COMPLETED), eq(SubmissionVerdict.RE),
                eq(50), eq(0), eq(0), eq(1), captor.capture());

        String recordedError = captor.getValue();
        assertThat(recordedError).isNotNull();
        assertThat(recordedError.length()).isLessThanOrEqualTo(2000);
        assertThat(recordedError).startsWith(head);
        assertThat(recordedError).endsWith(tail);
        assertThat(recordedError).contains("\n...\n");
    }
}
