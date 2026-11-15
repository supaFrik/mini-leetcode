package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector.OopGrindingInspector;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopInspectionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OopDockerRunnerTest {

    @Mock
    private ProcessLauncher processLauncher;

    @Mock
    private Process process;

    @Mock
    private OopGrindingInspector inspector;

    @Mock
    private OopJUnit5TestRunner testRunner;

    private ObjectMapper objectMapper;
    private OopDockerRunner runner;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        runner = new OopDockerRunner(processLauncher, objectMapper, inspector, testRunner);
    }

    @Test
    @DisplayName("Test 1: buildDockerCommand() generates all 13 security flags mandated by PROJECT.md § 2.1")
    void buildDockerCommand_AssertsAll13SecurityFlags(@TempDir Path workDir) {
        Long submissionId = 1284L;
        String runnerImage = "mini-leetcode-judge-runner:java17";
        int memoryLimitMb = 256;

        List<String> command = runner.buildDockerCommand(submissionId, workDir, runnerImage, memoryLimitMb);

        assertThat(command).containsSubsequence("docker", "run", "--rm");
        assertThat(command).contains("--name", "judge-oop-1284");
        assertThat(command).contains("--network", "none");
        assertThat(command).contains("--memory", "256m");
        assertThat(command).contains("--cpus", "1.0");
        assertThat(command).contains("--pids-limit", "64");
        assertThat(command).contains("--stop-timeout", "5");
        assertThat(command).contains("--read-only");
        assertThat(command).contains("--tmpfs", "/tmp:rw,noexec,nosuid,size=64m");
        assertThat(command).contains("--user", "1001:1001");

        String expectedMount = runner.resolveHostPath(workDir) + ":/workspace:rw";
        assertThat(command).contains("-v", expectedMount);

        assertThat(command).contains("mini-leetcode-judge-runner:java17");
        assertThat(command).containsSubsequence("java", "-jar", "/opt/oop-harness.jar", "/workspace/harness-config.json");
    }

    @Test
    @DisplayName("Test 2: resolveHostPath() translates Windows backslashes to Unix forward slashes")
    void resolveHostPath_ReplacesBackslashesWithForwardSlashes(@TempDir Path workDir) {
        String resolved = runner.resolveHostPath(workDir);
        assertThat(resolved).doesNotContain("\\");
        assertThat(resolved).contains("/");
    }

    @Test
    @DisplayName("Test 3: Normal container execution parses execution-report.json when exit code is 0")
    void runHarness_NormalExecutionExit0_ParsesExecutionReport(@TempDir Path workDir) throws Exception {
        Long submissionId = 2001L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        // Write sample execution-report.json into workDir
        ExecutionReport mockReport = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .totalScore(100.0)
                .verdict("AC")
                .build();
        Path reportPath = workDir.resolve("execution-report.json");
        objectMapper.writeValue(reportPath.toFile(), mockReport);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream("harness started\n".getBytes(StandardCharsets.UTF_8)));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getSubmissionId()).isEqualTo(submissionId);
        assertThat(report.getVerdict()).isEqualTo("AC");
        assertThat(report.getTotalScore()).isEqualTo(100.0);
        assertThat(report.getExecutionPhase()).isEqualTo("COMPLETED");

        verify(processLauncher).launch(any());
    }

    @Test
    @DisplayName("Test 4: Container timeout triggers docker kill, process destroy, and returns TLE report")
    void runHarness_ContainerTimeout_KillsContainerAndReturnsTle(@TempDir Path workDir) throws Exception {
        Long submissionId = 2002L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false); // Timed out!

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 10_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("TLE");
        assertThat(report.getExecutionPhase()).isEqualTo("TIMEOUT");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("timed out");

        verify(process).destroyForcibly();
    }

    @Test
    @DisplayName("Test 5: Exit code 137 maps directly to MLE (Memory Limit Exceeded)")
    void runHarness_ExitCode137_ReturnsMleReport(@TempDir Path workDir) throws Exception {
        Long submissionId = 2003L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("Killed\n".getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(137);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("MLE");
        assertThat(report.getExecutionPhase()).isEqualTo("OUT_OF_MEMORY");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("Memory Limit Exceeded");
    }

    @Test
    @DisplayName("Test 6: Non-zero exit code with missing report returns RE with stderr details")
    void runHarness_NonZeroExitMissingReport_ReturnsReWithStderr(@TempDir Path workDir) throws Exception {
        Long submissionId = 2004L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("Exception in thread main: java.lang.InternalError: Fatal JVM bug\n".getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("Fatal JVM bug");
    }

    @Test
    @DisplayName("Test 7: In-process fallback execution coordinates inspector and test runner cleanly")
    void runInProcessHarness_CoordinatesInspectorAndTestRunner(@TempDir Path workDir) {
        Long submissionId = 2005L;
        HarnessConfig config = HarnessConfig.builder()
                .submissionId(submissionId)
                .oopInspection(HarnessConfig.OopInspectionConfig.builder()
                        .enabled(true)
                        .weight(0.40)
                        .rules(Collections.emptyList())
                        .build())
                .unitTest(HarnessConfig.UnitTestConfig.builder()
                        .enabled(true)
                        .weight(0.60)
                        .testClassNames(List.of("CircleTest"))
                        .timeoutSecondsPerTest(2)
                        .build())
                .build();

        when(inspector.inspect(any(), anyList(), anyDouble())).thenReturn(
                OopInspectionReport.builder()
                        .passed(true)
                        .score(100.0)
                        .weight(0.40)
                        .weightedScore(40.0)
                        .summary("All rules passed")
                        .build()
        );

        when(testRunner.runTests(any(), any(), anyList(), anyInt(), anyDouble())).thenReturn(
                ExecutionReport.UnitTestPhase.builder()
                        .score(100.0)
                        .weight(0.60)
                        .weightedScore(60.0)
                        .passedCount(5)
                        .totalCount(5)
                        .failures(Collections.emptyList())
                        .build()
        );

        ExecutionReport report = runner.runInProcessHarness(submissionId, workDir, config);

        assertThat(report).isNotNull();
        assertThat(report.getTotalScore()).isEqualTo(100.0);
        assertThat(report.getVerdict()).isEqualTo("AC");
        assertThat(report.getPhases()).isNotNull();
        assertThat(report.getPhases().getOopInspection().getScore()).isEqualTo(100.0);
        assertThat(report.getPhases().getUnitTest().getScore()).isEqualTo(100.0);

        // Verify execution-report.json is written
        assertThat(Files.exists(workDir.resolve("execution-report.json"))).isTrue();
    }
}
