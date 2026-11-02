package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SocketFaultInjectionAdversarialTest {

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

    // =========================================================================
    // CATEGORY 1: Process Crash / Non-zero Exit Stress Tests
    // =========================================================================

    @Test
    @DisplayName("Adversarial 1.1: Client process exits with code 137 immediately upon start")
    void client_ImmediateCrashWithExitCode137_ReturnsConnectionFailureWithoutHanging() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_IMMEDIATE_EXIT_137")
                .role(SocketRole.CLIENT)
                .targetClass(EarlyCrashClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .extraArgs(List.of("137"))
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("HELLO")
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
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError())
                .contains("Client process exited prematurely with code")
                .contains("137");
        assertThat(duration).isLessThan(4000);
    }

    @Test
    @DisplayName("Adversarial 1.2: Client process throws uncaught RuntimeException upon start")
    void client_UncaughtRuntimeException_ReturnsConnectionFailureWithDetails() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_UNCAUGHT_EXCEPTION")
                .role(SocketRole.CLIENT)
                .targetClass(EarlyCrashClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .extraArgs(List.of("999"))
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("HELLO")
                                .sendPayload("ACK")
                                .receiveFirst(true)
                                .build()
                ))
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError())
                .contains("Client process exited prematurely")
                .contains("Uncaught early runtime exception in client main");
    }

    @Test
    @DisplayName("Adversarial 1.3: Client process abruptly halts mid-transaction with code 137")
    void client_MidTransactionCrash_ReturnsCleanVerdictWithoutDeadlock() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_MID_TRANSACTION_CRASH")
                .role(SocketRole.CLIENT)
                .targetClass(MidTransactionCrashClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        // First step: client sends handshake, harness receives
                        SocketStep.builder()
                                .expectedPayload("HELLO_BEFORE_CRASH")
                                .sendPayload("WELCOME")
                                .receiveFirst(true)
                                .build(),
                        // Second step: harness expects more, but client has halted
                        SocketStep.builder()
                                .expectedPayload("SECOND_MESSAGE")
                                .sendPayload("SECOND_ACK")
                                .receiveFirst(true)
                                .build()
                ))
                .build();

        long start = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        // Client crashes mid-stream -> protocol mismatch or connection failure
        assertThat(result.getVerdict()).isIn(SocketVerdict.PROTOCOL_MISMATCH, SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(duration).isLessThan(4500);
    }

    @Test
    @DisplayName("Adversarial 1.4: Server process crashes immediately upon startup (fail-fast polling)")
    void server_ImmediateCrash_FailsFastInPollingLoop() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_IMMEDIATE_CRASH")
                .role(SocketRole.SERVER)
                .targetClass(CrashingServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .build();

        long start = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError())
                .contains("Server process exited prematurely with code 1")
                .contains("database initialization failed");
        // Must fail fast via polling check (!serverProcess.isAlive()), not waiting full 2000ms
        assertThat(duration).isLessThan(1500);
    }

    @Test
    @DisplayName("Adversarial 1.5: Server process crashes mid-transaction after accept")
    void server_MidTransactionCrash_ReturnsCleanVerdictWithoutDeadlock() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_MID_TRANSACTION_CRASH")
                .role(SocketRole.SERVER)
                .targetClass(MidTransactionCrashServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("HELLO_TRIGGER_CRASH")
                                .expectedPayload("EXPECTED_RESPONSE_THAT_NEVER_ARRIVES")
                                .receiveFirst(false)
                                .build()
                ))
                .build();

        long start = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isIn(SocketVerdict.PROTOCOL_MISMATCH, SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(duration).isLessThan(4500);
    }

    // =========================================================================
    // CATEGORY 2: Buffer Flooding / Stdout-Stderr Exhaustion
    // =========================================================================

    @Test
    @DisplayName("Adversarial 2.1: Server floods stdout & stderr with 1MB+ log output during socket communication")
    void server_BufferFlooding1MB_DrainedSuccessfullyWithoutDeadlock() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_BUFFER_FLOODING_1MB")
                .role(SocketRole.SERVER)
                .targetClass(FloodingEchoServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(5000)
                .concurrentClients(1)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("FLOOD_TEST_PING")
                                .expectedPayload("FLOOD_TEST_PING")
                                .receiveFirst(false)
                                .build()
                ))
                .shutdownPayload("SHUTDOWN")
                .expectResourceCleanup(true)
                .build();

        long start = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 5000);
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.isPassed())
                .as("Harness must drain stdout/stderr in background to prevent OS pipe buffer deadlocks: %s", result.getError())
                .isTrue();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(result.getScore()).isEqualTo(100.0);
        assertThat(duration).isLessThan(6000);
    }

    @Test
    @DisplayName("Adversarial 2.2: Client floods stdout & stderr with 1MB+ log output during socket communication")
    void client_BufferFlooding1MB_DrainedSuccessfullyWithoutDeadlock() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_BUFFER_FLOODING_1MB")
                .role(SocketRole.CLIENT)
                .targetClass(FloodingEchoClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(5000)
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("FLOOD_CLIENT_PING")
                                .sendPayload("FLOOD_CLIENT_PONG")
                                .receiveFirst(true)
                                .build()
                ))
                .extraArgs(List.of("FLOOD_CLIENT_PING"))
                .expectResourceCleanup(true)
                .build();

        long start = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 5000);
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.isPassed())
                .as("Client 1MB log output must be drained without deadlocking harness: %s", result.getError())
                .isTrue();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(result.getScore()).isEqualTo(100.0);
        assertThat(duration).isLessThan(6000);
    }

    // =========================================================================
    // CATEGORY 3: Process Tree & Zombie Prevention
    // =========================================================================

    @Test
    @DisplayName("Adversarial 3.1: Server process termination cleanly stops the server process")
    void server_ProcessLifecycle_EnsuresServerIsDestroyed() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_TERMINATION_VERIFY")
                .role(SocketRole.SERVER)
                .targetClass(EchoServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("PING")
                                .expectedPayload("PING")
                                .build()
                ))
                .shutdownPayload("SHUTDOWN")
                .expectResourceCleanup(true)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("Adversarial 3.2: Subprocess tree investigation - empirical audit of child process termination")
    void server_SubprocessTree_AuditDescendantTermination() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SUBPROCESS_TREE_AUDIT")
                .role(SocketRole.SERVER)
                .targetClass(SubprocessSpawningServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("TREE_TEST")
                                .expectedPayload("ECHO:TREE_TEST")
                                .build()
                ))
                .shutdownPayload("SHUTDOWN")
                .expectResourceCleanup(true)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
    }

    // =========================================================================
    // CATEGORY 4: Ephemeral Port Handover & Collision Resilience
    // =========================================================================

    @Test
    @DisplayName("Adversarial 4.1: Rapid succession ephemeral port allocation (50 cycles) without BindException")
    void ephemeralPort_RapidSuccessionAllocation_NoCollisionsOrLeaks() throws IOException {
        int cycles = 50;
        for (int i = 0; i < cycles; i++) {
            int allocatedPort;
            try (ServerSocket probe = new ServerSocket()) {
                probe.setReuseAddress(true);
                probe.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 50);
                allocatedPort = probe.getLocalPort();
                assertThat(allocatedPort).isGreaterThan(0);
            }

            // Immediately rebind to the same port with reuseAddress=true to simulate student server handover
            try (ServerSocket rebind = new ServerSocket()) {
                rebind.setReuseAddress(true);
                rebind.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), allocatedPort), 50);
                assertThat(rebind.getLocalPort()).isEqualTo(allocatedPort);
            }
        }
    }

    @Test
    @DisplayName("Adversarial 4.2: Sequential scenarios back-to-back without port collision or leaks")
    void harness_SequentialScenariosRapidFire_ExecutesWithoutCollisions() {
        int runs = 5;
        for (int i = 1; i <= runs; i++) {
            SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                    .scenarioName("RAPID_RUN_" + i)
                    .role(SocketRole.SERVER)
                    .targetClass(EchoServer.class.getName())
                    .connectTimeoutMs(2000)
                    .timeoutMs(3000)
                    .concurrentClients(1)
                    .steps(List.of(
                            SocketStep.builder()
                                    .sendPayload("RAPID_PING_" + i)
                                    .expectedPayload("RAPID_PING_" + i)
                                    .build()
                    ))
                    .shutdownPayload("SHUTDOWN")
                    .expectResourceCleanup(true)
                    .build();

            ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

            assertThat(result)
                    .as("Rapid sequential scenario run #%d failed: %s", i, result != null ? result.getError() : "null")
                    .isNotNull();
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
            assertThat(result.getScore()).isEqualTo(100.0);
        }
    }
}
