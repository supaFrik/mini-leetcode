package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.calculator.OopScoreCalculator;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector.OopGrindingInspector;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopInspectionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.SocketIntegrationHarness;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Component
public class OopDockerRunner {

    public static final String DEFAULT_RUNNER_IMAGE = "mini-leetcode-judge-runner:java17";
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;
    public static final long STARTUP_ALLOWANCE_MS = 5_000L;
    public static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private final ProcessLauncher processLauncher;
    private final ObjectMapper objectMapper;
    private final OopGrindingInspector inspector;
    private final OopJUnit5TestRunner testRunner;
    private final SocketIntegrationHarness socketHarness;
    private final OopScoreCalculator scoreCalculator;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "oop-docker-io");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public OopDockerRunner(ObjectMapper objectMapper,
                           OopGrindingInspector inspector,
                           OopJUnit5TestRunner testRunner,
                           @Autowired(required = false) SocketIntegrationHarness socketHarness,
                           @Autowired(required = false) OopScoreCalculator scoreCalculator) {
        this(ProcessBuilder::start, objectMapper, inspector, testRunner, socketHarness, scoreCalculator);
    }

    public OopDockerRunner(ObjectMapper objectMapper, OopGrindingInspector inspector, OopJUnit5TestRunner testRunner, SocketIntegrationHarness socketHarness) {
        this(ProcessBuilder::start, objectMapper, inspector, testRunner, socketHarness, null);
    }

    public OopDockerRunner(ObjectMapper objectMapper, OopGrindingInspector inspector, OopJUnit5TestRunner testRunner) {
        this(ProcessBuilder::start, objectMapper, inspector, testRunner, null, null);
    }

    public OopDockerRunner(ProcessLauncher processLauncher, ObjectMapper objectMapper, OopGrindingInspector inspector, OopJUnit5TestRunner testRunner) {
        this(processLauncher, objectMapper, inspector, testRunner, null, null);
    }

    public OopDockerRunner(ProcessLauncher processLauncher, ObjectMapper objectMapper, OopGrindingInspector inspector, OopJUnit5TestRunner testRunner, SocketIntegrationHarness socketHarness) {
        this(processLauncher, objectMapper, inspector, testRunner, socketHarness, null);
    }

    public OopDockerRunner(ProcessLauncher processLauncher,
                           ObjectMapper objectMapper,
                           OopGrindingInspector inspector,
                           OopJUnit5TestRunner testRunner,
                           SocketIntegrationHarness socketHarness,
                           OopScoreCalculator scoreCalculator) {
        this.processLauncher = processLauncher != null ? processLauncher : ProcessBuilder::start;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.inspector = inspector;
        this.testRunner = testRunner;
        this.socketHarness = socketHarness;
        this.scoreCalculator = scoreCalculator != null ? scoreCalculator : new OopScoreCalculator(this.objectMapper);
    }

    public List<String> buildDockerCommand(Long submissionId, Path workDir, String runnerImage, int memoryLimitMb) {
        String containerName = "judge-oop-" + submissionId;
        String hostWorkPath = resolveHostPath(workDir);
        String mount = hostWorkPath + ":/workspace:rw";
        String mem = (memoryLimitMb > 0 ? memoryLimitMb : 256) + "m";
        String image = (runnerImage != null && !runnerImage.isBlank()) ? runnerImage : DEFAULT_RUNNER_IMAGE;

        return List.of(
                "docker", "run", "--rm",
                "--name", containerName,
                "--network", "none",
                "--memory", mem,
                "--cpus", "1.0",
                "--pids-limit", "64",
                "--stop-timeout", "5",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--user", "1001:1001",
                "-v", mount,
                image,
                "java", "-jar", "/opt/oop-harness.jar", "/workspace/harness-config.json"
        );
    }

    public String resolveHostPath(Path workDir) {
        String hostEnv = System.getenv("HOST_WORK_DIR");
        if (hostEnv != null && !hostEnv.isBlank()) {
            String clean = hostEnv.replaceAll("[/\\\\]+$", "");
            return (clean + "/" + workDir.getFileName()).replace('\\', '/');
        }
        return workDir.toAbsolutePath().toString().replace('\\', '/');
    }

    public ExecutionReport runHarness(Long submissionId, Path workDir, HarnessConfig config, long timeoutMs) {
        // 1. Stage harness-config.json into workDir
        Path configPath = workDir.resolve("harness-config.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        } catch (IOException e) {
            log.error("Failed to write harness-config.json into {}", workDir, e);
            return buildFallbackReport(submissionId, "EXECUTION_FAILED", 0.0, "RE", "Failed to write harness config: " + e.getMessage());
        }

        // 2. Prepare workspace permissions for non-root user 1001
        prepareWorkspacePermissions(workDir);

        // 3. Execute via Docker container
        String containerName = "judge-oop-" + submissionId;
        cleanupContainerQuietly(containerName);

        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long totalWaitMs = effectiveTimeout + STARTUP_ALLOWANCE_MS;

        List<String> command = buildDockerCommand(submissionId, workDir, DEFAULT_RUNNER_IMAGE, 256);
        ProcessBuilder pb = new ProcessBuilder(command);

        try {
            Process process = processLauncher.launch(pb);

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()), ioExecutor);
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream()), ioExecutor);

            boolean finished = process.waitFor(totalWaitMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("Submission {} timed out after {} ms. Killing container {}", submissionId, totalWaitMs, containerName);
                runDockerQuietly("kill", containerName);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return buildFallbackReport(submissionId, "TIMEOUT", 0.0, "TLE", "Container execution timed out after " + (totalWaitMs / 1000) + " seconds");
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();

            // 4. Check exit code: 137 indicates OOM (Linux Out-Of-Memory Killer)
            if (exitCode == 137) {
                log.warn("Submission {} was killed with exit code 137 (OOM)", submissionId);
                return buildFallbackReport(submissionId, "OUT_OF_MEMORY", 0.0, "MLE", "Memory Limit Exceeded (Container killed by Linux OOM killer)");
            }

            // 5. Check report file
            Path reportPath = workDir.resolve("execution-report.json");
            if (Files.exists(reportPath)) {
                try {
                    ExecutionReport report = objectMapper.readValue(reportPath.toFile(), ExecutionReport.class);
                    if (report.getSubmissionId() == null) {
                        report.setSubmissionId(submissionId);
                    }
                    return scoreCalculator.aggregateAndEnrich(report, config);
                } catch (Exception e) {
                    log.error("Failed to parse execution-report.json for submission {}", submissionId, e);
                }
            }

            // 6. Non-zero exit code without valid report: runtime error
            if (exitCode != 0) {
                String errorSnippet = !stderr.isBlank() ? stderr : stdout;
                if (exitCode == 125 || errorSnippet.contains("Unable to find image") || errorSnippet.contains("repository does not exist") || errorSnippet.contains("Unable to access jarfile")) {
                    log.info("Docker runner image or harness jar not available. Falling back to in-process harness for submission {}", submissionId);
                    return runInProcessHarness(submissionId, workDir, config);
                }
                return buildFallbackReport(submissionId, "EXECUTION_FAILED", 0.0, "RE", "Container exited with code " + exitCode + ": " + errorSnippet);
            }

            // Fallback if exit 0 but no report found
            return buildFallbackReport(submissionId, "EXECUTION_FAILED", 0.0, "RE", "Container exited normally but execution-report.json was not generated");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runDockerQuietly("kill", containerName);
            return buildFallbackReport(submissionId, "EXECUTION_FAILED", 0.0, "RE", "Execution was interrupted: " + e.getMessage());
        } catch (IOException e) {
            // Only processLauncher.launch throws IOException here (e.g. docker binary missing).
            // OopGrindingJudgeStrategy matches this "Docker execution failed:" prefix and runs the in-process fallback itself.
            log.error("Failed to launch Docker runner for submission {}", submissionId, e);
            return buildFallbackReport(submissionId, "EXECUTION_FAILED", 0.0, "RE", "Docker execution failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error executing Docker runner for submission {}, falling back to in-process harness", submissionId, e);
            runDockerQuietly("kill", containerName);
            return runInProcessHarness(submissionId, workDir, config);
        } finally {
            cleanupContainerQuietly(containerName);
        }
    }

    public ExecutionReport runInProcessHarness(Long submissionId, Path workDir, HarnessConfig config) {
        Path studentClassesDir = workDir.resolve("classes");
        Path testClassesDir = studentClassesDir;

        // Phase 2: OOP Reflection Inspection
        OopInspectionReport oopReport;
        if (config.getOopInspection() != null && config.getOopInspection().isEnabled()) {
            double oopWeight = config.getOopInspection().getWeight();
            oopReport = inspector.inspect(studentClassesDir, config.getOopInspection().getRules(), oopWeight);
        } else {
            oopReport = OopInspectionReport.builder()
                    .passed(true)
                    .score(100.0)
                    .weight(0.0)
                    .weightedScore(0.0)
                    .summary("OOP inspection skipped")
                    .build();
        }
        if (oopReport == null) {
            oopReport = OopInspectionReport.builder()
                    .passed(false)
                    .score(0.0)
                    .weight(config.getOopInspection() != null ? config.getOopInspection().getWeight() : 0.40)
                    .weightedScore(0.0)
                    .summary("Inspection returned null report")
                    .build();
        }

        // Phase 3: Unit Tests
        ExecutionReport.UnitTestPhase unitPhase;
        if (config.getUnitTest() != null && config.getUnitTest().isEnabled()
                && config.getUnitTest().getTestClassNames() != null
                && !config.getUnitTest().getTestClassNames().isEmpty()) {
            double unitWeight = config.getUnitTest().getWeight();
            int timeoutSec = config.getUnitTest().getTimeoutSecondsPerTest();
            unitPhase = testRunner.runTests(studentClassesDir, testClassesDir, config.getUnitTest().getTestClassNames(), timeoutSec, unitWeight);
        } else {
            unitPhase = ExecutionReport.UnitTestPhase.builder()
                    .score(100.0)
                    .weight(0.0)
                    .weightedScore(0.0)
                    .passedCount(0)
                    .totalCount(0)
                    .failures(Collections.emptyList())
                    .build();
        }
        if (unitPhase == null) {
            unitPhase = ExecutionReport.UnitTestPhase.builder()
                    .score(0.0)
                    .weight(config.getUnitTest() != null ? config.getUnitTest().getWeight() : 0.60)
                    .weightedScore(0.0)
                    .passedCount(0)
                    .totalCount(0)
                    .failures(Collections.emptyList())
                    .build();
        }

        // Phase 4: Socket Integration
        ExecutionReport.SocketIntegrationPhase socketPhase;
        if (config.getSocketIntegration() != null && config.getSocketIntegration().isEnabled()
                && config.getSocketIntegration().getScenarios() != null
                && !config.getSocketIntegration().getScenarios().isEmpty()) {
            if (socketHarness != null) {
                socketPhase = socketHarness.runScenarios(studentClassesDir, config.getSocketIntegration(), config.getTimeoutBudget());
            } else {
                log.warn("Socket integration enabled but SocketIntegrationHarness is not configured");
                double socketWeight = config.getSocketIntegration().getWeight();
                socketPhase = ExecutionReport.SocketIntegrationPhase.builder()
                        .score(0.0)
                        .weight(socketWeight)
                        .weightedScore(0.0)
                        .summary("SocketIntegrationHarness not configured")
                        .scenarios(Collections.emptyList())
                        .build();
            }
        } else {
            double socketWeight = config.getSocketIntegration() != null ? config.getSocketIntegration().getWeight() : 0.0;
            socketPhase = ExecutionReport.SocketIntegrationPhase.builder()
                    .score(100.0)
                    .weight(socketWeight)
                    .weightedScore(0.0)
                    .summary("Socket integration skipped")
                    .scenarios(Collections.emptyList())
                    .build();
        }

        // Phase Breakdown
        ExecutionReport.PhaseBreakdown breakdown = ExecutionReport.PhaseBreakdown.builder()
                .compilation(ExecutionReport.CompilationPhase.builder()
                        .passed(true)
                        .durationMs(500L)
                        .errors(null)
                        .build())
                .oopInspection(ExecutionReport.OopInspectionPhase.builder()
                        .score(oopReport.getScore())
                        .weight(oopReport.getWeight())
                        .weightedScore(oopReport.getWeightedScore())
                        .rules(oopReport.getRules())
                        .summary(oopReport.getSummary())
                        .build())
                .unitTest(unitPhase)
                .socketIntegration(socketPhase)
                .build();

        ExecutionReport report = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .phases(breakdown)
                .build();

        report = scoreCalculator.aggregateAndEnrich(report, config);

        // Write execution-report.json to workspace
        try {
            Path reportPath = workDir.resolve("execution-report.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        } catch (IOException e) {
            log.warn("Failed to write in-process execution-report.json", e);
        }

        return report;
    }

    private void prepareWorkspacePermissions(Path workDir) {
        try {
            Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Safe to ignore on Windows NTFS
        }
    }

    private void cleanupContainerQuietly(String containerName) {
        runDockerQuietly("rm", "-f", containerName);
    }

    private void runDockerQuietly(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Quiet cleanup
        }
    }

    public boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String readLimited(InputStream in) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int n;
            while ((n = in.read(chunk)) != -1) {
                int room = MAX_OUTPUT_BYTES - buffer.size();
                if (room > 0) {
                    buffer.write(chunk, 0, Math.min(n, room));
                }
            }
        } catch (IOException ignored) {
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private ExecutionReport buildFallbackReport(Long submissionId, String phase, Double score, String verdict, String errorMessage) {
        return ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase(phase)
                .totalScore(score)
                .verdict(verdict)
                .errorMessage(errorMessage)
                .build();
    }
}
