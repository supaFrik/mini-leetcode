package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.fixtures.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocketIntegrationHarnessTest {

    private SocketIntegrationHarness harness;
    private Path classesDir;

    @BeforeEach
    void setUp() {
        harness = new SocketIntegrationHarness();
        classesDir = resolveClassesDir();
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

    @Test
    @DisplayName("Test 1: Client assessment happy path (SUCCESS 100%) with EchoClient")
    void runClientAssessment_EchoClient_Success() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CLIENT_ECHO_TEST")
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
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(result.getScore()).isEqualTo(100.0);
        assertThat(result.getLatencyMs()).isNotNull();
        assertThat(result.getError()).isNull();
    }

    @Test
    @DisplayName("Test 2: Server assessment happy path (SUCCESS 100%) with EchoServer")
    void runServerAssessment_EchoServer_Success() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("SERVER_ECHO_TEST")
                .role(SocketRole.SERVER)
                .targetClass(EchoServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .concurrentClients(1)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("PING")
                                .expectedPayload("PING")
                                .receiveFirst(false)
                                .build()
                ))
                .shutdownPayload("SHUTDOWN")
                .expectResourceCleanup(true)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(result.getScore()).isEqualTo(100.0);
        assertThat(result.getLatencyMs()).isNotNull();
        assertThat(result.getError()).isNull();
    }

    @Test
    @DisplayName("Test 3: Concurrent mock clients (5 clients burst test) with EchoServer")
    void runServerAssessment_Concurrent5Clients_Success() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CONCURRENT_5_CLIENTS")
                .role(SocketRole.SERVER)
                .targetClass(EchoServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
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

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(result.getScore()).isEqualTo(100.0);
        assertThat(result.getLatencyMs()).isNotNull();
        assertThat(result.getError()).isNull();
    }

    @Test
    @DisplayName("Test 4: CONNECTION_FAILURE when student server crashes upon startup")
    void runServerAssessment_CrashingServer_ReturnsConnectionFailure() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("CRASHING_SERVER_TEST")
                .role(SocketRole.SERVER)
                .targetClass(CrashingServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.CONNECTION_FAILURE);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError()).contains("Server process exited prematurely");
    }

    @Test
    @DisplayName("Test 5: PROTOCOL_MISMATCH when student server responds with corrupted payload")
    void runServerAssessment_MismatchServer_ReturnsProtocolMismatch() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("MISMATCH_SERVER_TEST")
                .role(SocketRole.SERVER)
                .targetClass(MismatchServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("PING")
                                .expectedPayload("PING")
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
    @DisplayName("Test 6: SCENARIO_TIMEOUT when server accepts connection but hangs")
    void runServerAssessment_HangingServer_ReturnsScenarioTimeout() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("HANGING_SERVER_TEST")
                .role(SocketRole.SERVER)
                .targetClass(HangingServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(1200) // Lower timeout for fast unit test
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("PING")
                                .expectedPayload("PING")
                                .build()
                ))
                .expectResourceCleanup(false)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 1200);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.SCENARIO_TIMEOUT);
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getError()).contains("timed out");
    }

    @Test
    @DisplayName("Test 7: RESOURCE_LEAK penalty (50.0 score) when server leaks socket connection")
    void runServerAssessment_LeakingServer_ReturnsResourceLeakPenalty() {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("LEAKING_SERVER_TEST")
                .role(SocketRole.SERVER)
                .targetClass(LeakingServer.class.getName())
                .connectTimeoutMs(2000)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder()
                                .sendPayload("PING")
                                .expectedPayload("PING")
                                .build()
                ))
                .expectResourceCleanup(true)
                .build();

        ExecutionReport.SocketScenarioResult result = harness.runServerAssessment(classesDir, scenario, 3000);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue(); // Partial pass
        assertThat(result.getVerdict()).isEqualTo(SocketVerdict.RESOURCE_LEAK);
        assertThat(result.getScore()).isEqualTo(50.0);
        assertThat(result.getError()).contains("Resource leak");
    }

    @Test
    @DisplayName("Test 8: runScenarios aggregates phase score and handles non-blocking evaluation")
    void runScenarios_AggregatesResultsNonBlocking() {
        SocketScenarioConfig scenarioSuccess = SocketScenarioConfig.builder()
                .scenarioName("TCP_ECHO_PASS")
                .role(SocketRole.SERVER)
                .targetClass(EchoServer.class.getName())
                .steps(List.of(SocketStep.builder().sendPayload("A").expectedPayload("A").build()))
                .shutdownPayload("SHUTDOWN")
                .build();

        SocketScenarioConfig scenarioMismatch = SocketScenarioConfig.builder()
                .scenarioName("TCP_MISMATCH_FAIL")
                .role(SocketRole.SERVER)
                .targetClass(MismatchServer.class.getName())
                .steps(List.of(SocketStep.builder().sendPayload("A").expectedPayload("A").build()))
                .expectResourceCleanup(false)
                .build();

        HarnessConfig.SocketIntegrationConfig config = HarnessConfig.SocketIntegrationConfig.builder()
                .enabled(true)
                .weight(0.30)
                .scenarios(List.of(scenarioSuccess, scenarioMismatch))
                .build();

        HarnessConfig.TimeoutBudget budget = HarnessConfig.TimeoutBudget.builder()
                .socketScenarioTimeoutSeconds(3)
                .build();

        ExecutionReport.SocketIntegrationPhase phase = harness.runScenarios(classesDir, config, budget);

        assertThat(phase).isNotNull();
        // 1 passed (100.0) + 1 failed (0.0) -> average = 50.0
        assertThat(phase.getScore()).isEqualTo(50.0);
        assertThat(phase.getWeight()).isEqualTo(0.30);
        assertThat(phase.getWeightedScore()).isEqualTo(15.0);
        assertThat(phase.getScenarios()).hasSize(2);
        assertThat(phase.getScenarios().get(0).getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(phase.getScenarios().get(1).getVerdict()).isEqualTo(SocketVerdict.PROTOCOL_MISMATCH);
    }
}
