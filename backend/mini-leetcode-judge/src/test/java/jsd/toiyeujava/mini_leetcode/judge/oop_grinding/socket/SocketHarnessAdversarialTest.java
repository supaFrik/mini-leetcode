package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Empirical Adversarial Stress Test Suite for SocketIntegrationHarness.
 *
 * Verifies the 5 mandatory stress categories:
 * 1. Burst Concurrency Stress (10 concurrent clients, simultaneous bursts, no deadlocks)
 * 2. Strict Timeout Enforcement (3000ms deadline, hanging client & server, no runner hangs)
 * 3. Resource Leak Detection (-50% penalty, unclosed connections & hanging processes)
 * 4. Protocol Mismatch & Corruption Defense (garbage binary, truncated data, wrong strings)
 * 5. Connection Failure Handling (unbound server polling 2000ms, unreachable port client)
 */
class SocketHarnessAdversarialTest {

    private SocketIntegrationHarness harness;
    private Path classesDir;
    private final List<ProcessHandle> processesToVerify = new ArrayList<>();

    @BeforeEach
    void setUp() {
        harness = new SocketIntegrationHarness();
        classesDir = resolveClassesDir();
    }

    @AfterEach
    void tearDown() {
        for (ProcessHandle handle : processesToVerify) {
            try {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            } catch (Throwable ignored) {
            }
        }
        processesToVerify.clear();
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
    // CATEGORY 1: Burst Concurrency Stress (10 Concurrent Clients)
    // =========================================================================

    @Test
    @DisplayName("Stress 1.1: 10 concurrent mock clients communicating with ConcurrentEchoServer in simultaneous bursts")
    void burstConcurrency_10ClientsSimultaneousBurst_SuccessWithoutDeadlock() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CONCURRENT_10_CLIENTS_BURST")
                .role(SocketRole.SERVER)
                .targetClass(ConcurrentEchoServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .concurrentClients(10)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("BURST_REQ_{clientId}")
                                .expectedPayload("BURST_REQ_{clientId}")
                                .build()
                ))
                .shutdownPayload("SHUTDOWN")
                .expectResourceCleanup(true)
                .build();

        long startTime = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result)
                .as("Scenario result must not be null")
                .isNotNull();
        assertThat(result.isPassed())
                .as("All 10 concurrent clients must complete without dropped connections or resets: error=%s", result.getError())
                .isTrue();
        assertThat(result.getVerdict())
                .as("Verdict must be SUCCESS for 100%% successful burst")
                .isEqualTo(SocketVerdict.SUCCESS);
        assertThat(result.getScore())
                .as("Score must be 100.0")
                .isEqualTo(100.0);
        assertThat(result.getLatencyMs())
                .as("Latency must be positive and within reasonable bounds")
                .isNotNull()
                .isGreaterThanOrEqualTo(0L)
                .isLessThan(3000L);
        assertThat(result.getError()).isNull();

        // Ensure harness completed without hanging
        assertThat(elapsedMs)
                .as("10 client burst must complete within 5000ms")
                .isLessThan(5000L);
    }

    // =========================================================================
    // CATEGORY 2: Strict Timeout Enforcement (3000ms Deadline)
    // =========================================================================

    @Test
    @DisplayName("Stress 2.1: Hanging Server that sleeps 60s terminates at ~3000ms returning SCENARIO_TIMEOUT (0.0 score)")
    void timeoutEnforcement_HangingServer_TerminatesWithinDeadlineWithTimeoutVerdict() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("HANGING_SERVER_STRICT_TIMEOUT")
                .role(SocketRole.SERVER)
                .targetClass(HangingServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("PING")
                                .expectedPayload("PING")
                                .receiveFirst(false)
                                .build()
                ))
                .expectResourceCleanup(false)
                .build();

        long startTime = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result).isNotNull();
        assertThat(result.isPassed())
                .as("Timed out scenario must be marked failed")
                .isFalse();
        assertThat(result.getVerdict())
                .as("Verdict must be SCENARIO_TIMEOUT")
                .isEqualTo(SocketVerdict.SCENARIO_TIMEOUT);
        assertThat(result.getScore())
                .as("Score must be 0.0")
                .isEqualTo(0.0);
        assertThat(result.getError())
                .as("Error must mention timeout")
                .contains("timed out");

        // Polling ready (~200ms) + 3000ms socket timeout + cleanup (~500ms) = ~3200-4500ms.
        // It must NEVER take anywhere near 60,000ms.
        assertThat(elapsedMs)
                .as("Must enforce timeout budget and terminate in under 5000ms")
                .isGreaterThanOrEqualTo(2900L)
                .isLessThan(5500L);
    }

    @Test
    @DisplayName("Stress 2.2: Hanging Client that sleeps 60s terminates at ~3000ms returning SCENARIO_TIMEOUT (0.0 score)")
    void timeoutEnforcement_HangingClient_TerminatesWithinDeadlineWithTimeoutVerdict() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("HANGING_CLIENT_STRICT_TIMEOUT")
                .role(SocketRole.CLIENT)
                .targetClass(HangingClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("CLIENT_HELLO")
                                .sendPayload("SERVER_ACK")
                                .receiveFirst(true)
                                .build()
                ))
                .expectResourceCleanup(false)
                .build();

        long startTime = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result).isNotNull();
        assertThat(result.isPassed())
                .as("Timed out client must be marked failed")
                .isFalse();
        assertThat(result.getVerdict())
                .as("Verdict must be SCENARIO_TIMEOUT")
                .isEqualTo(SocketVerdict.SCENARIO_TIMEOUT);
        assertThat(result.getScore())
                .as("Score must be 0.0")
                .isEqualTo(0.0);
        assertThat(result.getError())
                .as("Error must mention timeout")
                .contains("timed out");

        // Socket timeout 3000ms + process termination. Must terminate in under 5500ms.
        assertThat(elapsedMs)
                .as("Hanging client assessment must enforce timeout budget and terminate under 5500ms")
                .isGreaterThanOrEqualTo(2900L)
                .isLessThan(5500L);
    }

    // =========================================================================
    // CATEGORY 3: Resource Leak Detection (-50% Penalty)
    // =========================================================================

    @Test
    @DisplayName("Stress 3.1: Server echoes correctly but retains unclosed socket - detected as RESOURCE_LEAK (50.0 score)")
    void resourceLeak_ServerRetainingUnclosedSocket_ReturnsResourceLeakPenalty() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("LEAKING_SERVER_PENALTY")
                .role(SocketRole.SERVER)
                .targetClass(LeakingServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("LEAK_TEST_REQ")
                                .expectedPayload("LEAK_TEST_REQ")
                                .receiveFirst(false)
                                .build()
                ))
                .expectResourceCleanup(true)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed())
                .as("Partial pass applies when functionality passes but resource leak is detected")
                .isTrue();
        assertThat(result.getVerdict())
                .as("Verdict must be RESOURCE_LEAK")
                .isEqualTo(SocketVerdict.RESOURCE_LEAK);
        assertThat(result.getScore())
                .as("Score must be exactly 50.0 (-50%% penalty according to Section 3.3)")
                .isEqualTo(50.0);
        assertThat(result.getError())
                .as("Error must state that socket was not closed")
                .contains("Server socket was not closed");
    }

    @Test
    @DisplayName("Stress 3.2: Client echoes correctly but does not close socket or exit process - detected as RESOURCE_LEAK (50.0 score)")
    void resourceLeak_ClientRetainingUnclosedSocket_ReturnsResourceLeakPenalty() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("LEAKING_CLIENT_PENALTY")
                .role(SocketRole.CLIENT)
                .targetClass(LeakingClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("CLIENT_LEAK_MSG")
                                .sendPayload("CLIENT_LEAK_ACK")
                                .receiveFirst(true)
                                .build()
                ))
                .extraArgs(List.of("CLIENT_LEAK_MSG"))
                .expectResourceCleanup(true)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed())
                .as("Client partial pass with leak detection")
                .isTrue();
        assertThat(result.getVerdict())
                .as("Verdict must be RESOURCE_LEAK")
                .isEqualTo(SocketVerdict.RESOURCE_LEAK);
        assertThat(result.getScore())
                .as("Score must be exactly 50.0")
                .isEqualTo(50.0);
        assertThat(result.getError())
                .as("Error must indicate client failed to close socket or terminate cleanly")
                .contains("Client failed to close socket or hung");
    }

    // =========================================================================
    // CATEGORY 4: Protocol Mismatch & Corruption Defense
    // =========================================================================

    @Test
    @DisplayName("Stress 4.1: Server returns garbage binary data - caught as PROTOCOL_MISMATCH (0.0 score) without crash")
    void corruptionDefense_ServerGarbageBinaryData_ReturnsProtocolMismatchWithoutCrash() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_GARBAGE_BINARY_DEFENSE")
                .role(SocketRole.SERVER)
                .targetClass(CorruptedProtocolServer.class.getName())
                .serverArgs("GARBAGE_BINARY")
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("HELLO")
                                .expectedPayload("EXPECTED_VALID_TEXT_RESPONSE")
                                .receiveFirst(false)
                                .build()
                ))
                .expectResourceCleanup(false)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.PROTOCOL_MISMATCH);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError())
                .as("Error must document protocol mismatch cleanly")
                .contains("Protocol mismatch");
    }

    @Test
    @DisplayName("Stress 4.2: Server sends truncated data without newline and closes - caught as PROTOCOL_MISMATCH (0.0 score)")
    void corruptionDefense_ServerTruncatedData_ReturnsProtocolMismatch() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_TRUNCATED_DATA_DEFENSE")
                .role(SocketRole.SERVER)
                .targetClass(CorruptedProtocolServer.class.getName())
                .serverArgs("TRUNCATED")
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("HELLO")
                                .expectedPayload("TRUNCATED_COMPLETE_LINE")
                                .receiveFirst(false)
                                .build()
                ))
                .expectResourceCleanup(false)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.PROTOCOL_MISMATCH);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError()).contains("Protocol mismatch");
    }

    @Test
    @DisplayName("Stress 4.3: Client sends garbage binary data - caught as PROTOCOL_MISMATCH (0.0 score) without crash")
    void corruptionDefense_ClientGarbageBinaryData_ReturnsProtocolMismatchWithoutCrash() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_GARBAGE_BINARY_DEFENSE")
                .role(SocketRole.CLIENT)
                .targetClass(CorruptedProtocolClient.class.getName())
                .extraArgs(List.of("GARBAGE_BINARY"))
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("EXPECTED_CLEAN_TEXT_PAYLOAD")
                                .sendPayload("SERVER_ACK")
                                .receiveFirst(true)
                                .build()
                ))
                .expectResourceCleanup(false)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.PROTOCOL_MISMATCH);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError()).contains("Protocol mismatch");
    }

    // =========================================================================
    // CATEGORY 5: Connection Failure Handling
    // =========================================================================

    @Test
    @DisplayName("Stress 5.1: Student server remains alive but does not bind - polling loop exhausts after ~2000ms (CONNECTION_FAILURE, 0.0)")
    void connectionFailure_ServerSilentUnbound_PollingExhaustsWithConnectionFailure() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_SILENT_UNBOUND_TEST")
                .role(SocketRole.SERVER)
                .targetClass(SilentUnboundServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .build();

        long startTime = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError())
                .as("Error must specify failure to accept connections after polling duration")
                .contains("Server failed to accept connections")
                .contains("after 2000ms");

        // Polling loop is 10 attempts x (100ms connect timeout + 200ms sleep) = ~2000ms - 3500ms
        assertThat(elapsedMs)
                .as("Polling loop must terminate close to 2000ms without indefinite hanging")
                .isGreaterThanOrEqualTo(1900L)
                .isLessThan(4500L);
    }

    @Test
    @DisplayName("Stress 5.2: Client attempts connection to unreachable closed port - returns CONNECTION_FAILURE (0.0)")
    void connectionFailure_ClientConnectsToUnreachablePort_ReturnsConnectionFailure() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_UNREACHABLE_PORT_TEST")
                .role(SocketRole.CLIENT)
                .targetClass(UnreachableTargetClient.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .expectedPayload("DATA")
                                .sendPayload("ACK")
                                .receiveFirst(true)
                                .build()
                ))
                .build();

        long startTime = System.currentTimeMillis();
        ExecutionReport.SocketScenarioResult result = harness.runClientAssessment(classesDir, scenario, 3000);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError())
                .as("Error must record connection failure or premature exit")
                .contains("Client process exited prematurely");

        assertThat(elapsedMs).isLessThan(4000L);
    }

    // =========================================================================
    // CATEGORY 6: Full Scenarios Pipeline Aggregation with Mixed Adversarial Verdicts
    // =========================================================================

    @Test
    @DisplayName("Stress 6.1: Full runScenarios pipeline with mixed SUCCESS, LEAK, MISMATCH, TIMEOUT, CONNECTION_FAILURE")
    void fullPipeline_MixedAdversarialScenarios_CalculatesCorrectNormalizedScore() {
        HarnessConfig.SocketIntegrationConfig config = HarnessConfig.SocketIntegrationConfig.builder()
                .enabled(true)
                .weight(0.30)
                .scenarios(List.of(
                        // 1. Success (100.0)
                        SocketScenarioConfig.builder()
                                .scenarioName("S1_SUCCESS")
                                .role(SocketRole.SERVER)
                                .targetClass(EchoServer.class.getName())
                                .steps(List.of(SocketStep.builder().sendPayload("A").expectedPayload("A").build()))
                                .shutdownPayload("SHUTDOWN")
                                .build(),
                        // 2. Leak (50.0)
                        SocketScenarioConfig.builder()
                                .scenarioName("S2_LEAK")
                                .role(SocketRole.SERVER)
                                .targetClass(LeakingServer.class.getName())
                                .steps(List.of(SocketStep.builder().sendPayload("B").expectedPayload("B").build()))
                                .expectResourceCleanup(true)
                                .build(),
                        // 3. Mismatch (0.0)
                        SocketScenarioConfig.builder()
                                .scenarioName("S3_MISMATCH")
                                .role(SocketRole.SERVER)
                                .targetClass(CorruptedProtocolServer.class.getName())
                                .serverArgs("MISMATCH_STRING")
                                .steps(List.of(SocketStep.builder().sendPayload("C").expectedPayload("EXPECTED_C").build()))
                                .expectResourceCleanup(false)
                                .build(),
                        // 4. Connection Failure (0.0)
                        SocketScenarioConfig.builder()
                                .scenarioName("S4_CONN_FAIL")
                                .role(SocketRole.SERVER)
                                .targetClass(CrashingServer.class.getName())
                                .build()
                ))
                .build();

        ExecutionReport.SocketIntegrationPhase report = harness.runScenarios(classesDir, config, null);

        assertThat(report).isNotNull();
        assertThat(report.getScenarios()).hasSize(4);

        // Verification of individual scenario verdicts
        assertThat(report.getScenarios().get(0).getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(report.getScenarios().get(0).getScore()).isEqualTo(100.0);

        assertThat(report.getScenarios().get(1).getVerdict()).isEqualTo(SocketVerdict.RESOURCE_LEAK);
        assertThat(report.getScenarios().get(1).getScore()).isEqualTo(50.0);

        assertThat(report.getScenarios().get(2).getVerdict()).isEqualTo(SocketVerdict.PROTOCOL_MISMATCH);
        assertThat(report.getScenarios().get(2).getScore()).isEqualTo(0.0);

        assertThat(report.getScenarios().get(3).getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
        assertThat(report.getScenarios().get(3).getScore()).isEqualTo(0.0);

        // Average score: (100.0 + 50.0 + 0.0 + 0.0) / 4 = 37.5
        assertThat(report.getScore()).isEqualTo(37.5);
        assertThat(report.getWeight()).isEqualTo(0.30);
        // Weighted score: 37.5 * 0.30 = 11.25 -> rounded to 11.3
        assertThat(report.getWeightedScore()).isEqualTo(11.3);
    }
}
