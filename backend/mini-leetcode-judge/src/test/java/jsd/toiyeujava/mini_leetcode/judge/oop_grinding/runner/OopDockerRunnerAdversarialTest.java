package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector.OopGrindingInspector;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive Adversarial Stress Test Suite for OopDockerRunner.
 * Covers security boundary isolation flags, path escaping/spaces/backslashes,
 * timeout enforcement (TLE), OOM kill (MLE / 137), container crashes (RE),
 * corrupted/truncated execution reports, and stream exhaustion attacks.
 */
@ExtendWith(MockitoExtension.class)
public class OopDockerRunnerAdversarialTest {

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

    // =========================================================================
    // Category 1: 13 Required Docker Security Flags (PROJECT.md § 2.1)
    // =========================================================================

    @Test
    @DisplayName("Flag Audit: Verifies all 13 security/isolation flags and exact argument bindings")
    void buildDockerCommand_VerifiesAll13SecurityFlagsStrictly(@TempDir Path workDir) {
        Long submissionId = 5555L;
        String image = "mini-leetcode-judge-runner:java17";
        int memMb = 256;

        List<String> cmd = runner.buildDockerCommand(submissionId, workDir, image, memMb);

        // 1. Binary invocation
        assertThat(cmd.get(0)).isEqualTo("docker");
        assertThat(cmd.get(1)).isEqualTo("run");

        // 2. Ephemeral container flag (--rm)
        assertThat(cmd).contains("--rm");

        // 3. Unique deterministic container name
        int nameIdx = cmd.indexOf("--name");
        assertThat(nameIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(nameIdx + 1)).isEqualTo("judge-oop-5555");

        // 4. Strict network isolation (--network none)
        int netIdx = cmd.indexOf("--network");
        assertThat(netIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(netIdx + 1)).isEqualTo("none");

        // 5. Memory ceiling (--memory 256m)
        int memIdx = cmd.indexOf("--memory");
        assertThat(memIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(memIdx + 1)).isEqualTo("256m");

        // 6. CPU allocation ceiling (--cpus 1.0)
        int cpusIdx = cmd.indexOf("--cpus");
        assertThat(cpusIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(cpusIdx + 1)).isEqualTo("1.0");

        // 7. Process limit against fork-bombs (--pids-limit 64)
        int pidsIdx = cmd.indexOf("--pids-limit");
        assertThat(pidsIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(pidsIdx + 1)).isEqualTo("64");

        // 8. Graceful stop timeout before kill (--stop-timeout 5)
        int stopIdx = cmd.indexOf("--stop-timeout");
        assertThat(stopIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(stopIdx + 1)).isEqualTo("5");

        // 9. Read-only root filesystem (--read-only)
        assertThat(cmd).contains("--read-only");
        int roIdx = cmd.indexOf("--read-only");
        // Must be a standalone boolean flag, not consuming subsequent token
        assertThat(cmd.get(roIdx + 1)).startsWith("-");

        // 10. Tmpfs mount for ephemeral /tmp with noexec,nosuid (--tmpfs /tmp:rw,noexec,nosuid,size=64m)
        int tmpfsIdx = cmd.indexOf("--tmpfs");
        assertThat(tmpfsIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(tmpfsIdx + 1)).isEqualTo("/tmp:rw,noexec,nosuid,size=64m");

        // 11. Non-root user execution (--user 1001:1001)
        int userIdx = cmd.indexOf("--user");
        assertThat(userIdx).isGreaterThanOrEqualTo(0);
        assertThat(cmd.get(userIdx + 1)).isEqualTo("1001:1001");

        // 12. Workspace volume mount (-v <hostWorkDir>:/workspace:rw)
        int vIdx = cmd.indexOf("-v");
        assertThat(vIdx).isGreaterThanOrEqualTo(0);
        String mountArg = cmd.get(vIdx + 1);
        assertThat(mountArg).endsWith(":/workspace:rw");
        assertThat(mountArg).contains(runner.resolveHostPath(workDir));

        // 13. Target image & harness entrypoint
        int imgIdx = cmd.indexOf(image);
        assertThat(imgIdx).isGreaterThan(vIdx + 1);
        List<String> entrypoint = cmd.subList(imgIdx + 1, cmd.size());
        assertThat(entrypoint).containsExactly("java", "-jar", "/opt/oop-harness.jar", "/workspace/harness-config.json");
    }

    @Test
    @DisplayName("Configurable memory limits: applies positive integer + 'm'")
    void buildDockerCommand_ConfigurableMemoryLimits(@TempDir Path workDir) {
        List<String> cmd512 = runner.buildDockerCommand(100L, workDir, null, 512);
        int idx512 = cmd512.indexOf("--memory");
        assertThat(cmd512.get(idx512 + 1)).isEqualTo("512m");

        List<String> cmd1024 = runner.buildDockerCommand(100L, workDir, null, 1024);
        int idx1024 = cmd1024.indexOf("--memory");
        assertThat(cmd1024.get(idx1024 + 1)).isEqualTo("1024m");
    }

    @Test
    @DisplayName("Invalid memory limits: falls back to 256m default")
    void buildDockerCommand_InvalidMemoryFallsBackTo256m(@TempDir Path workDir) {
        List<String> cmd0 = runner.buildDockerCommand(100L, workDir, null, 0);
        int idx0 = cmd0.indexOf("--memory");
        assertThat(cmd0.get(idx0 + 1)).isEqualTo("256m");

        List<String> cmdNeg = runner.buildDockerCommand(100L, workDir, null, -500);
        int idxNeg = cmdNeg.indexOf("--memory");
        assertThat(cmdNeg.get(idxNeg + 1)).isEqualTo("256m");
    }

    @Test
    @DisplayName("Fallback to DEFAULT_RUNNER_IMAGE when runnerImage is blank or null")
    void buildDockerCommand_NullOrBlankImageFallback(@TempDir Path workDir) {
        List<String> cmd1 = runner.buildDockerCommand(100L, workDir, null, 256);
        List<String> cmd2 = runner.buildDockerCommand(100L, workDir, "   ", 256);

        assertThat(cmd1).contains(OopDockerRunner.DEFAULT_RUNNER_IMAGE);
        assertThat(cmd2).contains(OopDockerRunner.DEFAULT_RUNNER_IMAGE);
    }

    // =========================================================================
    // Category 2: Path Escaping, Spaces, Windows Backslashes & Symbols
    // =========================================================================

    @Test
    @DisplayName("Paths with Windows backslashes are normalized to forward slashes")
    void resolveHostPath_ReplacesAllBackslashesWithForwardSlashes(@TempDir Path workDir) {
        String resolved = runner.resolveHostPath(workDir);
        assertThat(resolved).doesNotContain("\\");
        assertThat(resolved).contains("/");
    }

    @Test
    @DisplayName("Paths containing spaces remain intact in volume mount argument without bash-splitting risk")
    void buildDockerCommand_PathWithSpaces_MountRemainsSingleToken(@TempDir Path tempBase) throws Exception {
        Path dirWithSpaces = tempBase.resolve("folder with spaces in name and (parens)");
        Files.createDirectories(dirWithSpaces);

        List<String> cmd = runner.buildDockerCommand(777L, dirWithSpaces, null, 256);

        int vIdx = cmd.indexOf("-v");
        assertThat(vIdx).isGreaterThanOrEqualTo(0);
        String mountArg = cmd.get(vIdx + 1);

        // Mount must be a single string argument passed to ProcessBuilder
        assertThat(mountArg).contains("folder with spaces in name and (parens)");
        assertThat(mountArg).endsWith(":/workspace:rw");
        assertThat(mountArg).doesNotContain("\\");
    }

    @Test
    @DisplayName("Paths with symbols and special characters are preserved accurately")
    void buildDockerCommand_PathWithSpecialSymbols(@TempDir Path tempBase) throws Exception {
        Path specialDir = tempBase.resolve("sub_dir-123.test#@!_plus+");
        Files.createDirectories(specialDir);

        List<String> cmd = runner.buildDockerCommand(888L, specialDir, null, 256);

        int vIdx = cmd.indexOf("-v");
        String mountArg = cmd.get(vIdx + 1);
        assertThat(mountArg).contains("sub_dir-123.test#@!_plus+");
        assertThat(mountArg).endsWith(":/workspace:rw");
    }

    // =========================================================================
    // Category 3: Container Timeout Enforcement (TLE) & Docker Kill
    // =========================================================================

    @Test
    @DisplayName("Container timeout: triggers destroyForcibly() and produces TLE verdict with score 0.0")
    void runHarness_Timeout_KillsContainerAndReturnsTle(@TempDir Path workDir) throws Exception {
        Long submissionId = 3001L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        // Timeout condition: process.waitFor returns false
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 10_000L);

        assertThat(report).isNotNull();
        assertThat(report.getSubmissionId()).isEqualTo(submissionId);
        assertThat(report.getVerdict()).isEqualTo("TLE");
        assertThat(report.getExecutionPhase()).isEqualTo("TIMEOUT");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("Container execution timed out");

        // Process must be forcibly killed
        verify(process).destroyForcibly();
    }

    @Test
    @DisplayName("Timeout calculation: includes STARTUP_ALLOWANCE_MS (5000ms) added to requested timeout")
    void runHarness_TimeoutCalculation_IncludesStartupAllowance(@TempDir Path workDir) throws Exception {
        Long submissionId = 3002L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);

        long customTimeout = 12_000L;
        runner.runHarness(submissionId, workDir, config, customTimeout);

        ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(process).waitFor(timeoutCaptor.capture(), eq(TimeUnit.MILLISECONDS));

        // 12000 + 5000 = 17000ms
        assertThat(timeoutCaptor.getValue()).isEqualTo(17_000L);
    }

    @Test
    @DisplayName("Zero or negative timeout: defaults to DEFAULT_TIMEOUT_MS (30000ms) + 5000ms")
    void runHarness_ZeroTimeout_DefaultsTo35000Ms(@TempDir Path workDir) throws Exception {
        Long submissionId = 3003L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);

        runner.runHarness(submissionId, workDir, config, -1L);

        ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(process).waitFor(timeoutCaptor.capture(), eq(TimeUnit.MILLISECONDS));

        // 30000 + 5000 = 35000ms
        assertThat(timeoutCaptor.getValue()).isEqualTo(35_000L);
    }

    // =========================================================================
    // Category 4: OOM Kill (Exit Code 137 -> MLE)
    // =========================================================================

    @Test
    @DisplayName("Exit code 137 maps directly to MLE verdict with OUT_OF_MEMORY phase and score 0.0")
    void runHarness_ExitCode137_ReturnsMleReport(@TempDir Path workDir) throws Exception {
        Long submissionId = 4001L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("Killed\n".getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(137);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getSubmissionId()).isEqualTo(submissionId);
        assertThat(report.getVerdict()).isEqualTo("MLE");
        assertThat(report.getExecutionPhase()).isEqualTo("OUT_OF_MEMORY");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("Memory Limit Exceeded");
    }

    @Test
    @DisplayName("Security Defense: Exit 137 supersedes any spoofed execution-report.json in workspace")
    void runHarness_ExitCode137_SupersedesSpoofedReportFile(@TempDir Path workDir) throws Exception {
        Long submissionId = 4002L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        // Adversarial attempt: a malicious submission creates a fake AC report before exhausting memory
        ExecutionReport spoofedReport = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .totalScore(100.0)
                .verdict("AC")
                .build();
        Path reportPath = workDir.resolve("execution-report.json");
        objectMapper.writeValue(reportPath.toFile(), spoofedReport);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("OOM Killed\n".getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(137);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        // Must NOT accept the spoofed AC report! Must strictly return MLE!
        assertThat(report.getVerdict()).isEqualTo("MLE");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getExecutionPhase()).isEqualTo("OUT_OF_MEMORY");
    }

    // =========================================================================
    // Category 5: Crashing Container (Exit Code != 0 -> RE)
    // =========================================================================

    @Test
    @DisplayName("Exit code 1 with stderr: captures error snippet and returns RE verdict")
    void runHarness_ExitCode1_CapturesStderrInReReport(@TempDir Path workDir) throws Exception {
        Long submissionId = 5001L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        String stderrMsg = "Exception in thread \"main\" java.lang.NoClassDefFoundError: org/junit/platform/launcher/Launcher";
        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(stderrMsg.getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("Container exited with code 1");
        assertThat(report.getErrorMessage()).contains(stderrMsg);
    }

    @Test
    @DisplayName("Exit code 127 with empty stderr: falls back to stdout message in RE report")
    void runHarness_ExitCode127_BlankStderr_FallsBackToStdout(@TempDir Path workDir) throws Exception {
        Long submissionId = 5002L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        String stdoutMsg = "/opt/oop-harness.jar: not found";
        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(stdoutMsg.getBytes(StandardCharsets.UTF_8)));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("   \n".getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(127);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getErrorMessage()).contains("Container exited with code 127");
        assertThat(report.getErrorMessage()).contains(stdoutMsg);
    }

    @Test
    @DisplayName("Exit code 2 with completely empty stdout/stderr: returns clean RE report without NPE")
    void runHarness_ExitCode2_EmptyStreams_ReturnsCleanRe(@TempDir Path workDir) throws Exception {
        Long submissionId = 5003L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(2);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getErrorMessage()).contains("Container exited with code 2");
    }

    // =========================================================================
    // Category 6: Corrupted or Truncated execution-report.json
    // =========================================================================

    @Test
    @DisplayName("Truncated JSON in execution-report.json does not throw, returns RE fallback")
    void runHarness_TruncatedJson_ReturnsReFallbackGracefully(@TempDir Path workDir) throws Exception {
        Long submissionId = 6001L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        // Write malformed/truncated JSON
        Path reportPath = workDir.resolve("execution-report.json");
        Files.writeString(reportPath, "{\"submissionId\": 6001, \"verdict\": \"AC\", \"totalScore\": 100.0, \"executionPhase\": \"CO", StandardCharsets.UTF_8);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
        assertThat(report.getErrorMessage()).contains("execution-report.json was not generated");
    }

    @Test
    @DisplayName("Garbage binary/non-JSON content in execution-report.json returns RE fallback")
    void runHarness_GarbageContent_ReturnsReFallback(@TempDir Path workDir) throws Exception {
        Long submissionId = 6002L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        Path reportPath = workDir.resolve("execution-report.json");
        Files.writeString(reportPath, "<<<JVM CRASH DUMP / SEGMENTATION FAULT 0x0000DEAD>>>", StandardCharsets.UTF_8);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Zero-byte empty execution-report.json returns RE fallback")
    void runHarness_ZeroBytesReport_ReturnsReFallback(@TempDir Path workDir) throws Exception {
        Long submissionId = 6003L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        Path reportPath = workDir.resolve("execution-report.json");
        Files.createFile(reportPath); // 0 bytes

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("JSON array instead of object schema in execution-report.json returns RE fallback")
    void runHarness_JsonArrayTypeMismatch_ReturnsReFallback(@TempDir Path workDir) throws Exception {
        Long submissionId = 6004L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        Path reportPath = workDir.resolve("execution-report.json");
        Files.writeString(reportPath, "[{\"unexpected\": \"array_shape\"}]", StandardCharsets.UTF_8);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getTotalScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Report with null submissionId automatically receives submissionId from method argument")
    void runHarness_NullSubmissionIdInReport_PopulatedFromCaller(@TempDir Path workDir) throws Exception {
        Long submissionId = 6005L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        ExecutionReport reportWithoutId = ExecutionReport.builder()
                .submissionId(null)
                .executionPhase("COMPLETED")
                .totalScore(80.0)
                .verdict("WA")
                .build();
        Path reportPath = workDir.resolve("execution-report.json");
        objectMapper.writeValue(reportPath.toFile(), reportWithoutId);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getSubmissionId()).isEqualTo(submissionId);
        assertThat(report.getVerdict()).isEqualTo("WA");
        assertThat(report.getTotalScore()).isEqualTo(80.0);
    }

    // =========================================================================
    // Category 7: Stream Exhaustion, Process Launch Exceptions & Interruption
    // =========================================================================

    @Test
    @DisplayName("ProcessLauncher throwing IOException (e.g. docker binary missing) returns RE cleanly")
    void runHarness_LaunchThrowsIoException_ReturnsReReport(@TempDir Path workDir) throws Exception {
        Long submissionId = 7001L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenThrow(new IOException("Cannot run program 'docker': File not found"));

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getErrorMessage()).contains("Docker execution failed");
        assertThat(report.getErrorMessage()).contains("File not found");
    }

    @Test
    @DisplayName("Thread interruption during process.waitFor returns RE report cleanly")
    void runHarness_Interrupted_ReturnsReReport(@TempDir Path workDir) throws Exception {
        Long submissionId = 7002L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("Worker thread interrupted"));

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report).isNotNull();
        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(report.getErrorMessage()).contains("Execution was interrupted");
    }

    @Test
    @DisplayName("Large stream output (>256KB) is capped at MAX_OUTPUT_BYTES without OOMing host worker")
    void runHarness_OutputBombExceeding256KB_TruncatedSafely(@TempDir Path workDir) throws Exception {
        Long submissionId = 7003L;
        HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

        // Create 512KB of repetitive log output (bomb attack)
        byte[] largeBomb = new byte[512 * 1024];
        Arrays.fill(largeBomb, (byte) 'A');

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(largeBomb));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(largeBomb));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        ExecutionReport report = runner.runHarness(submissionId, workDir, config, 30_000L);

        assertThat(report.getVerdict()).isEqualTo("RE");
        assertThat(report.getErrorMessage()).isNotNull();
        // Stderr snippet capped at MAX_OUTPUT_BYTES (256KB)
        assertThat(report.getErrorMessage().length()).isLessThanOrEqualTo(OopDockerRunner.MAX_OUTPUT_BYTES + 200);
    }
}
