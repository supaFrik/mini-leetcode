package jsd.toiyeujava.mini_leetcode.console.service;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteRequest;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tầng nghiệp vụ console OOP: chỉ bài OOP, cấm kỳ thi, path traversal,
 * giới hạn payload và giới hạn số lần chạy.
 */
@ExtendWith(MockitoExtension.class)
class ConsoleServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private ContestProblemRepository contestProblemRepository;
    @Mock private JudgeWorkerClient judgeWorkerClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private ConsoleGatewayProperties properties;
    private ConsoleService consoleService;

    private static final Long USER_ID = 7L;
    private static final Long PROBLEM_ID = 42L;

    @BeforeEach
    void setUp() {
        properties = new ConsoleGatewayProperties();
        consoleService = new ConsoleService(
                problemRepository, contestProblemRepository, judgeWorkerClient, redisTemplate, properties);
    }

    private void mockOopProblem() {
        Problem problem = Problem.builder().id(PROBLEM_ID).problemType(ProblemType.OOP_GRINDING).build();
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findContestsByProblemId(PROBLEM_ID)).thenReturn(List.of());
    }

    /** Redis INCR trả về số lần chạy hiện tại (sau khi tăng). */
    private void runCountWillBe(long n) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("console:runs:" + USER_ID + ":" + PROBLEM_ID)).thenReturn(n);
    }

    private ConsoleExecuteRequest request(String... paths) {
        return new ConsoleExecuteRequest(Arrays.stream(paths)
                .map(p -> new ConsoleExecuteRequest.ConsoleFileDto(p, "public class X {}"))
                .toList(), "hello");
    }

    private static JudgeWorkerClient.JudgeWorkerResponse ok(String stdout) {
        return new JudgeWorkerClient.JudgeWorkerResponse(stdout, "", 0, 87, 24576, null, false, false, null);
    }

    @Test
    @DisplayName("Unknown problem → 404")
    void execute_ProblemNotFound_Throws() {
        when(problemRepository.findById(PROBLEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Non-OOP problem → rejected")
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
        Contest running = Contest.builder().id(101L).status(ContestStatus.RUNNING).build();
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
    }

    @Test
    @DisplayName("Path traversal → rejected")
    void execute_PathTraversal_Throws() {
        mockOopProblem();

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("../evil.java")))
                .isInstanceOf(InvalidSubmissionPathException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Too many files → 413")
    void execute_TooManyFiles_Throws() {
        mockOopProblem();
        properties.setMaxFiles(1);

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java", "B.java")))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    @DisplayName("Run #51 within the hour → 429, judge not called")
    void execute_RunLimitReached_Throws() {
        mockOopProblem();
        runCountWillBe(51);

        assertThatThrownBy(() -> consoleService.execute(USER_ID, PROBLEM_ID, request("A.java")))
                .isInstanceOf(ConsoleRateLimitExceededException.class);
        verify(judgeWorkerClient, never()).execute(any());
    }

    @Test
    @DisplayName("Happy path: stdin forwarded, stdout + metrics returned, window TTL set on first run")
    void execute_Success_ForwardsStdinAndReturnsOutput() {
        mockOopProblem();
        runCountWillBe(1);
        when(judgeWorkerClient.execute(any())).thenReturn(ok("HELLO\n"));

        ConsoleExecuteResponse response = consoleService.execute(USER_ID, PROBLEM_ID, request("A.java"));

        assertThat(response.stdout()).isEqualTo("HELLO\n");
        assertThat(response.exitCode()).isZero();
        assertThat(response.runtimeMs()).isEqualTo(87);
        assertThat(response.peakMemoryKb()).isEqualTo(24576);
        ArgumentCaptor<JudgeWorkerClient.JudgeWorkerRequest> captor =
                ArgumentCaptor.forClass(JudgeWorkerClient.JudgeWorkerRequest.class);
        verify(judgeWorkerClient).execute(captor.capture());
        assertThat(captor.getValue().input()).isEqualTo("hello");
        verify(redisTemplate).expire(any(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Compile errors are surfaced")
    void execute_CompileErrors_SurfacesCompileErrors() {
        mockOopProblem();
        runCountWillBe(2);
        when(judgeWorkerClient.execute(any())).thenReturn(new JudgeWorkerClient.JudgeWorkerResponse(
                "", "", null, null, null, "A.java:1: error: ';' expected", false, false, null));

        ConsoleExecuteResponse response = consoleService.execute(USER_ID, PROBLEM_ID, request("A.java"));

        assertThat(response.compileErrors()).contains("';' expected");
        assertThat(response.exitCode()).isNull();
    }
}
