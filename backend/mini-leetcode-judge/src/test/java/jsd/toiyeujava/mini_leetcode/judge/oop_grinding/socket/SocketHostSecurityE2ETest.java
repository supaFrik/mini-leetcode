package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Socket Host Security & Hardening E2E Test Suite")
public class SocketHostSecurityE2ETest {

    private SocketIntegrationHarness harness;
    private Path classesDir;
    private final List<ProcessHandle> processesToClean = new ArrayList<>();

    @BeforeEach
    void setUp() {
        harness = new SocketIntegrationHarness();
        classesDir = resolveClassesDir();
    }

    @AfterEach
    void tearDown() {
        for (ProcessHandle handle : processesToClean) {
            try {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            } catch (Throwable ignored) {
            }
        }
        processesToClean.clear();
        try {
            harness.shutdown();
        } catch (Throwable ignored) {
        }
    }

    private Path resolveClassesDir() {
        Path p = Path.of("target", "test-classes");
        if (Files.exists(p)) {
            return p;
        }
        Path alt = Path.of("backend", "mini-leetcode-judge", "target", "test-classes");
        if (Files.exists(alt)) {
            return alt;
        }
        return Path.of(".").toAbsolutePath();
    }

    private Set<Long> getRunningWorkerPids() {
        return ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(p -> {
                    String cmd = p.info().commandLine().orElse("");
                    return cmd.contains(LongLivingWorker.class.getSimpleName());
                })
                .map(ProcessHandle::pid)
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // TIER 1: HOST SOCKET SECURITY (Loopback, Port Allocation, Process Tree, Stream Drain, Deadline)
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Socket Host Security Requirements")
    class Tier1SocketHostSecurity {

        @Test
        @DisplayName("T1.1: Host binding is strictly restricted to loopback address 127.0.0.1")
        void loopbackAddress_EnforcedAcrossServerProbeAndBindings() {
            assertThat(SocketIntegrationHarness.LOOPBACK_HOST).isEqualTo("127.0.0.1");
            assertThat(InetAddress.getLoopbackAddress().getHostAddress()).isEqualTo("127.0.0.1");

            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("LOOPBACK_VERIFY_ECHO")
                    .role(SocketRole.SERVER)
                    .targetClass(EchoServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("LOOPBACK_PING")
                                    .expectedPayload("LOOPBACK_PING")
                                    .receiveFirst(false)
                                    .build()
                    ))
                    .shutdownPayload("SHUTDOWN")
                    .expectResourceCleanup(true)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("Loopback server failed: %s, %s", result.getVerdict(), result.getError())
                    .isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
            assertThat(result.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("T1.2: Dynamic ephemeral port allocation strictly enforces unprivileged range [1024, 65535]")
        void ephemeralPortAllocation_AlwaysWithinSafeUnprivilegedRange() throws IOException {
            for (int i = 0; i < 20; i++) {
                int allocatedPort = harness.allocateSafeEphemeralPort();
                assertThat(allocatedPort)
                        .as("Ephemeral port must be >= 1024 (unprivileged) and <= 65535 on cycle %d", i)
                        .isGreaterThanOrEqualTo(1024)
                        .isLessThanOrEqualTo(65535);
            }
        }

        @Test
        @DisplayName("T1.3: Reliable process tree termination captures descendants before destruction and kills all child zombies")
        void processTreeTermination_KillsDescendantsBeforeParentDestruction() {
            Set<Long> workersBefore = getRunningWorkerPids();

            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("PROCESS_TREE_ZOMBIE_KILL")
                    .role(SocketRole.SERVER)
                    .targetClass(SubprocessSpawningServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("KILL_ZOMBIE_TEST")
                                    .expectedPayload("ECHO:KILL_ZOMBIE_TEST")
                                    .receiveFirst(false)
                                    .build()
                    ))
                    .shutdownPayload("SHUTDOWN")
                    .expectResourceCleanup(false)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("Subprocess server should pass step. Verdict: %s, Error: %s", result.getVerdict(), result.getError())
                    .isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);

            // Audit process table: any worker spawned during the test must be destroyed
            Set<Long> workersAfter = getRunningWorkerPids();
            workersAfter.removeAll(workersBefore);

            // Register any leaked worker for failsafe cleanup in tearDown
            workersAfter.forEach(pid -> ProcessHandle.of(pid).ifPresent(processesToClean::add));

            assertThat(workersAfter)
                    .as("No LongLivingWorker child processes must survive harness process tree termination")
                    .isEmpty();
        }

        @Test
        @DisplayName("T1.4: Fast crashing server (<10ms exit) drains stderr without race condition, preserving diagnostic message")
        void fastCrashServer_StdErrStreamDraining_PreservesExitDiagnostic() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("FAST_CRASH_DRAIN_TEST")
                    .role(SocketRole.SERVER)
                    .targetClass(CrashingServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
            assertThat(result.getError())
                    .as("Premature exit error output must contain verbatim stderr diagnostic")
                    .contains("Server process exited prematurely with code 1")
                    .contains("database initialization failed");
        }

        @Test
        @DisplayName("T1.5: Wall-clock execution timeout budget strictly terminates hanging server within deadline")
        void wallClockTimeoutBudget_PreventsSlowDripHangs() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("WALLCLOCK_DEADLINE_TEST")
                    .role(SocketRole.SERVER)
                    .targetClass(HangingServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(2500)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("PING_STEP_1")
                                    .expectedPayload("RESPONSE_STEP_1")
                                    .build(),
                            SocketStep.builder()
                                    .sendPayload("PING_STEP_2")
                                    .expectedPayload("RESPONSE_STEP_2")
                                    .build()
                    ))
                    .build();

            long start = System.currentTimeMillis();
            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 2500);
            long duration = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getVerdict()).isIn(SocketVerdict.SCENARIO_TIMEOUT, SocketVerdict.CONNECTION_FAILURE, SocketVerdict.PROTOCOL_MISMATCH);
            assertThat(duration)
                    .as("Total execution time must respect wall-clock timeout budget without multiplying per step")
                    .isLessThan(4500);
        }
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (Option Injection, Bad Names, Rapid Exit)
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: Boundary & Corner Cases")
    class Tier2BoundaryAndCornerCases {

        @Test
        @DisplayName("B1: Option injection attack starting with '-' is blocked before process launch")
        void targetClassName_OptionInjectionAttack_BlockedImmediately() {
            List<String> maliciousTargetClasses = List.of(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "-Duser.home=/tmp",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "--illegal-access=permit"
            );

            for (String malicious : maliciousTargetClasses) {
                SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                        .scenarioName("OPTION_INJECTION_ATTACK")
                        .role(SocketRole.SERVER)
                        .targetClass(malicious)
                        .connectTimeoutMs(2000)
                        .timeoutMs(3000)
                        .build();

                assertThatThrownBy(() -> harness.runServerAssessment(classesDir, scenario, 3000))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Invalid target class name");
            }
        }

        @Test
        @DisplayName("B2: Target class name with command injection special characters is blocked")
        void targetClassName_CommandInjectionSpecialCharacters_Blocked() {
            List<String> injectionPayloads = List.of(
                    "EchoServer; touch /tmp/pwned",
                    "EchoServer & calc.exe",
                    "EchoServer | cat /etc/passwd",
                    "EchoServer`whoami`",
                    "Echo$erver<script>"
            );

            for (String malicious : injectionPayloads) {
                SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                        .scenarioName("CMD_INJECTION_ATTACK")
                        .role(SocketRole.SERVER)
                        .targetClass(malicious)
                        .connectTimeoutMs(2000)
                        .timeoutMs(3000)
                        .build();

                assertThatThrownBy(() -> harness.runServerAssessment(classesDir, scenario, 3000))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Invalid target class name");
            }
        }

        @Test
        @DisplayName("B3: Blank or whitespace target class name is rejected immediately")
        void targetClassName_BlankOrWhitespace_Rejected() {
            List<String> blankTargets = List.of("", "   ", "\t\n");

            for (String blank : blankTargets) {
                SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                        .scenarioName("BLANK_TARGET_TEST")
                        .role(SocketRole.SERVER)
                        .targetClass(blank)
                        .connectTimeoutMs(2000)
                        .timeoutMs(3000)
                        .build();

                assertThatThrownBy(() -> harness.runServerAssessment(classesDir, scenario, 3000))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("B4: Client immediate crash with non-zero exit code exits cleanly without hang")
        void rapidExitServer_ImmediateCrashClient_ExitsWithoutHang() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("CLIENT_RAPID_EXIT_TEST")
                    .role(SocketRole.CLIENT)
                    .targetClass(EarlyCrashClient.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .extraArgs(List.of("1"))
                    .steps(List.of(
                            SocketStep.builder()
                                    .expectedPayload("GREETING")
                                    .sendPayload("ACK")
                                    .receiveFirst(true)
                                    .build()
                    ))
                    .build();

            long start = System.currentTimeMillis();
            ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);
            long duration = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
            assertThat(duration).isLessThan(4000);
        }

        @Test
        @DisplayName("B5: Consecutive ephemeral port allocation (10 cycles) without BindException collisions")
        void portHandover_SequentialServerScenarios_NoPortCollision() {
            for (int i = 0; i < 10; i++) {
                SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                        .scenarioName("HANDOVER_CYCLE_" + i)
                        .role(SocketRole.SERVER)
                        .targetClass(EchoServer.class.getName())
                        .connectTimeoutMs(2000)
                        .timeoutMs(3000)
                        .steps(List.of(
                                SocketStep.builder()
                                        .sendPayload("CYCLE_" + i)
                                        .expectedPayload("CYCLE_" + i)
                                        .build()
                        ))
                        .shutdownPayload("SHUTDOWN")
                        .expectResourceCleanup(true)
                        .build();

                ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);
                assertThat(result).isNotNull();
                assertThat(result.isPassed()).as("Cycle %d must succeed without port collision. Verdict: %s, Error: %s", i, result.getVerdict(), result.getError()).isTrue();
            }
        }
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================
    @Nested
    @DisplayName("Tier 3: Cross-Feature Combinations")
    class Tier3CrossFeatureCombinations {

        @Test
        @DisplayName("C1: Burst concurrent clients (5 clients) under security loopback binding")
        void crossFeature_ConcurrentClients_UnderSecurityLoopback() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("CONCURRENT_5_CLIENTS_SECURITY")
                    .role(SocketRole.SERVER)
                    .targetClass(EchoServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(4000)
                    .concurrentClients(5)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("BURST_{clientId}")
                                    .expectedPayload("BURST_{clientId}")
                                    .build()
                    ))
                    .shutdownPayload("SHUTDOWN")
                    .expectResourceCleanup(true)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 4000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("Concurrent 5 clients should succeed. Verdict: %s, Error: %s", result.getVerdict(), result.getError())
                    .isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
            assertThat(result.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("C2: Process tree termination when server times out on receive")
        void crossFeature_ProcessTreeTerminationWithHangingParent() {
            Set<Long> workersBefore = getRunningWorkerPids();

            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("HANGING_SERVER_TREE_CLEANUP")
                    .role(SocketRole.SERVER)
                    .targetClass(HangingServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(2000)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("TRIGGER_HANG")
                                    .expectedPayload("UNEXPECTED_RESP")
                                    .build()
                    ))
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 2000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed()).isFalse();

            Set<Long> workersAfter = getRunningWorkerPids();
            workersAfter.removeAll(workersBefore);
            workersAfter.forEach(pid -> ProcessHandle.of(pid).ifPresent(processesToClean::add));

            assertThat(workersAfter).isEmpty();
        }

        @Test
        @DisplayName("C3: Client assessment with resource cleanup check does not produce false leak on Windows teardown")
        void crossFeature_ClientAssessment_ResourceCleanupVerification_NoFalseLeak() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("ECHO_CLIENT_CLEANUP_CHECK")
                    .role(SocketRole.CLIENT)
                    .targetClass(EchoClient.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .steps(List.of(
                            SocketStep.builder()
                                    .expectedPayload("HELLO")
                                    .sendPayload("HELLO_ACK")
                                    .receiveFirst(true)
                                    .build()
                    ))
                    .extraArgs(List.of("HELLO"))
                    .expectResourceCleanup(true)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("Loopback server failed: %s, %s", result.getVerdict(), result.getError())
                    .isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
            assertThat(result.getScore()).isEqualTo(100.0);
        }
    }

    // =========================================================================
    // TIER 4: REAL-WORLD WORKLOAD SCENARIOS
    // =========================================================================
    @Nested
    @DisplayName("Tier 4: Real-World Workload Scenarios")
    class Tier4RealWorldWorkloads {

        @Test
        @DisplayName("S1: Full EchoServer lifecycle under loopback with graceful shutdown and resource verification")
        void realWorld_FullEchoServerLifecycle_WithPortAndProcessCleanup() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("REALWORLD_SERVER_LIFECYCLE")
                    .role(SocketRole.SERVER)
                    .targetClass(EchoServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .concurrentClients(1)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("MSG_1_LOGIN")
                                    .expectedPayload("MSG_1_LOGIN")
                                    .receiveFirst(false)
                                    .build(),
                            SocketStep.builder()
                                    .sendPayload("MSG_2_DATA_FETCH")
                                    .expectedPayload("MSG_2_DATA_FETCH")
                                    .receiveFirst(false)
                                    .build()
                    ))
                    .shutdownPayload("SHUTDOWN")
                    .expectResourceCleanup(true)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("EchoServer lifecycle should succeed. Verdict: %s, Error: %s", result.getVerdict(), result.getError())
                    .isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
            assertThat(result.getScore()).isEqualTo(100.0);
            assertThat(result.getLatencyMs()).isNotNull().isGreaterThan(0L);
        }

        @Test
        @DisplayName("S2: Full EchoClient lifecycle with loopback probe and multi-argument passing")
        void realWorld_FullEchoClientLifecycle_WithLoopbackProbe() {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("REALWORLD_CLIENT_LIFECYCLE")
                    .role(SocketRole.CLIENT)
                    .targetClass(EchoClient.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .steps(List.of(
                            SocketStep.builder()
                                    .expectedPayload("REAL_TEST_GREETING")
                                    .sendPayload("REAL_TEST_ACK")
                                    .receiveFirst(true)
                                    .build()
                    ))
                    .extraArgs(List.of("REAL_TEST_GREETING"))
                    .expectResourceCleanup(true)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("Loopback server failed: %s, %s", result.getVerdict(), result.getError())
                    .isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
            assertThat(result.getScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("S3: Adversarial student attempts background zombie subprocess fork — verified 0 surviving zombies")
        void realWorld_AdversarialSubprocessForking_ZeroZombiesLeft() {
            Set<Long> workersBefore = getRunningWorkerPids();

            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("ADVERSARIAL_SUBPROCESS_FORK_AUDIT")
                    .role(SocketRole.SERVER)
                    .targetClass(SubprocessSpawningServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("ADVERSARIAL_PING")
                                    .expectedPayload("ECHO:ADVERSARIAL_PING")
                                    .receiveFirst(false)
                                    .build()
                    ))
                    .shutdownPayload("SHUTDOWN")
                    .expectResourceCleanup(false)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

            assertThat(result).isNotNull();
            assertThat(result.isPassed())
                    .as("Adversarial fork assessment should succeed. Verdict: %s, Error: %s", result.getVerdict(), result.getError())
                    .isTrue();

            Set<Long> workersAfter = getRunningWorkerPids();
            workersAfter.removeAll(workersBefore);
            workersAfter.forEach(pid -> ProcessHandle.of(pid).ifPresent(processesToClean::add));

            assertThat(workersAfter)
                    .as("Adversarial fork test must leave exactly 0 orphaned zombie processes on host")
                    .isEmpty();
        }
    }
}
