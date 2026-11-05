package jsd.toiyeujava.mini_leetcode.console.service;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteRequest;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ConsoleMode;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleDisabledException;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleRateLimitExceededException;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử tầng nghiệp vụ của console OOP.
 *
 * <p>Trọng tâm: các quy tắc bảo mật (chỉ bài OOP, cấm kỳ thi, path traversal,
 * giới hạn payload) và việc ghi transcript.
 */
@ExtendWith(MockitoExtension.class)
class ConsoleServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ContestProblemRepository contestProblemRepository;

    @Mock
    private JudgeWorkerClient judgeWorkerClient;

    @Mock
    private ConsoleTranscriptStore transcriptStore;

    private ConsoleGatewayProperties properties;
    private ConsoleService consoleService;

    private static final Long USER_ID = 7L;
    private static final Long PROBLEM_ID = 42L;

    @BeforeEach
    void setUp() {
        properties = new ConsoleGatewayProperties();
        properties.setMaxCommandsPerSession(50);
        properties.setMaxFiles(100);
        properties.setMaxSourceBytes(20L * 1024 * 1024);
        consoleService = new ConsoleService(
                problemRepository,
                contestProblemRepository,
                judgeWorkerClient,
                transcriptStore,
                properties);
    }

    private void mockOopProblem() {
        Problem problem = Problem.builder()
                .id(PROBLEM_ID)
                .problemType(ProblemType.OOP_GRINDING)
                .build();
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findContestsByProblemId(PROBLEM_ID)).thenReturn(List.of());
    }

    private ConsoleExecuteRequest request(String... paths) {
        List<ConsoleExecuteRequest.ConsoleFileDto> files = java.util.Arrays.stream(paths)
                .map(p -> new ConsoleExecuteRequest.ConsoleFileDto(p, "public class X {}"))
                .toList();
        return new ConsoleExecuteRequest(files, "42", "42", null, ConsoleMode.EVALUATE);
    }

    private ConsoleExecuteRequest requestWithMode(ConsoleMode mode, String... paths) {
        List<ConsoleExecuteRequest.ConsoleFileDto> files = java.util.Arrays.stream(paths)
                .map(p -> new ConsoleExecuteRequest.ConsoleFileDto(p, "public class X {}"))
                .toList();
        return new ConsoleExecuteRequest(files, "42", "42", null, mode);
    }

    @Test
    @DisplayName("Unknown problem → 404 ResourceNotFoundException")
    void execute_ProblemNotFound_Throws() {
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Non-OOP problem → rejected, judge worker is never called")
    void execute_NonOopProblem_Throws() {
        Problem problem = Problem.builder().id(PROBLEM_ID).problemType(ProblemType.ALGORITHMIC).build();
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Running contest → console disabled (403)")
    void execute_DuringRunningContest_ThrowsConsoleDisabled() {
        mockOopProblem();
        Contest running = Contest.builder()
                .id(101L)
                .status(ContestStatus.RUNNING)
                .build();
        when(contestProblemRepository.findContestsByProblemId(PROBLEM_ID)).thenReturn(List.of(running));

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java")))
                .isInstanceOf(ConsoleDisabledException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("No Java files → rejected")
    void execute_NoJavaFiles_Throws() {
        mockOopProblem();

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("notes.txt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No Java source files");
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Path traversal in file path → rejected")
    void execute_PathTraversal_Throws() {
        mockOopProblem();

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("../evil.java")))
                .isInstanceOf(InvalidSubmissionPathException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Too many files → 413 PayloadTooLargeException")
    void execute_TooManyFiles_Throws() {
        mockOopProblem();
        properties.setMaxFiles(1);

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java", "B.java")))
                .isInstanceOf(PayloadTooLargeException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Session command limit exceeded → 429")
    void execute_CommandLimitReached_Throws() {
        mockOopProblem();
        when(transcriptStore.count(USER_ID, PROBLEM_ID)).thenReturn(50);

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java")))
                .isInstanceOf(ConsoleRateLimitExceededException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Happy path: metrics are forwarded and the transcript is appended")
    void execute_Success_ReturnsMetricsAndAppendsTranscript() {
        mockOopProblem();
        when(transcriptStore.count(USER_ID, PROBLEM_ID)).thenReturn(0);
        when(judgeWorkerClient.execute(any())).thenReturn(
                new JudgeWorkerClient.JudgeWorkerResponse(
                        "42\n", "", 0, 87, 24576, null, false, false, null));

        ConsoleExecuteResponse response = consoleService.execute(USER_ID, PROBLEM_ID, request("A.java"));

        assertThat(response.stdout()).isEqualTo("42\n");
        assertThat(response.exitCode()).isZero();
        assertThat(response.runtimeMs()).isEqualTo(87);
        assertThat(response.peakMemoryKb()).isEqualTo(24576);
        assertThat(response.compileErrors()).isNull();

        verify(transcriptStore).append(eq(USER_ID), eq(PROBLEM_ID), any());
    }

    @Test
    @DisplayName("Compile errors are surfaced without running the program")
    void execute_CompileErrors_SurfacesCompileErrors() {
        mockOopProblem();
        when(transcriptStore.count(USER_ID, PROBLEM_ID)).thenReturn(0);
        when(judgeWorkerClient.execute(any())).thenReturn(
                new JudgeWorkerClient.JudgeWorkerResponse(
                        "", "", null, null, null, "A.java:1: error: ';' expected", false, false, null));

        ConsoleExecuteResponse response = consoleService.execute(USER_ID, PROBLEM_ID, request("A.java"));

        assertThat(response.compileErrors()).contains("';' expected");
        assertThat(response.exitCode()).isNull();
    }

    @Test
    @DisplayName("Console mode is forwarded to the judge worker")
    void execute_ForwardsConsoleMode() {
        mockOopProblem();
        when(transcriptStore.count(USER_ID, PROBLEM_ID)).thenReturn(0);
        when(judgeWorkerClient.execute(any())).thenReturn(
                new JudgeWorkerClient.JudgeWorkerResponse("out", "", 0, 10, 100, null, false, false, null));

        consoleService.execute(USER_ID, PROBLEM_ID, requestWithMode(ConsoleMode.PROGRAM, "A.java"));

        ArgumentCaptor<JudgeWorkerClient.JudgeWorkerRequest> captor =
                ArgumentCaptor.forClass(JudgeWorkerClient.JudgeWorkerRequest.class);
        verify(judgeWorkerClient).execute(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(ConsoleMode.PROGRAM);
    }

    @Test
    @DisplayName("A request without mode defaults to EVALUATE")
    void execute_NullMode_DefaultsToEvaluate() {
        mockOopProblem();
        when(transcriptStore.count(USER_ID, PROBLEM_ID)).thenReturn(0);
        when(judgeWorkerClient.execute(any())).thenReturn(
                new JudgeWorkerClient.JudgeWorkerResponse("out", "", 0, 10, 100, null, false, false, null));

        List<ConsoleExecuteRequest.ConsoleFileDto> files = List.of(
                new ConsoleExecuteRequest.ConsoleFileDto("A.java", "public class X {}"));
        consoleService.execute(USER_ID, PROBLEM_ID, new ConsoleExecuteRequest(files, "1", "1", null, null));

        ArgumentCaptor<JudgeWorkerClient.JudgeWorkerRequest> captor =
                ArgumentCaptor.forClass(JudgeWorkerClient.JudgeWorkerRequest.class);
        verify(judgeWorkerClient).execute(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(ConsoleMode.EVALUATE);
    }
}
