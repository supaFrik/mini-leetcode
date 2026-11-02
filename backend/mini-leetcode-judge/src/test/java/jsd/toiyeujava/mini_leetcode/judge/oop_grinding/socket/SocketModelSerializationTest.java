package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocketModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Test 1: HarnessConfig serializes and deserializes SocketIntegrationConfig with typed scenarios")
    void harnessConfig_SocketIntegration_SerializesAndDeserializes() throws Exception {
        SocketScenarioConfig scenario = SocketScenarioConfig.builder()
                .scenarioName("TCP_ECHO_SERVER")
                .role(SocketRole.SERVER)
                .targetClass("EchoServer")
                .concurrentClients(5)
                .timeoutMs(3000)
                .steps(List.of(
                        SocketStep.builder().sendPayload("PING").expectedPayload("PING").build()
                ))
                .weight(1.0)
                .build();

        HarnessConfig config = HarnessConfig.builder()
                .submissionId(1284L)
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                        .enabled(true)
                        .weight(0.30)
                        .scenarios(List.of(scenario))
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(config);
        assertThat(json).contains("\"TCP_ECHO_SERVER\"");
        assertThat(json).contains("\"SERVER\"");
        assertThat(json).contains("\"concurrentClients\":5");

        HarnessConfig deserialized = objectMapper.readValue(json, HarnessConfig.class);
        assertThat(deserialized.getSocketIntegration().isEnabled()).isTrue();
        assertThat(deserialized.getSocketIntegration().getWeight()).isEqualTo(0.30);
        assertThat(deserialized.getSocketIntegration().getScenarios()).hasSize(1);
        SocketScenarioConfig parsedScenario = deserialized.getSocketIntegration().getScenarios().get(0);
        assertThat(parsedScenario.getScenarioName()).isEqualTo("TCP_ECHO_SERVER");
        assertThat(parsedScenario.getRole()).isEqualTo(SocketRole.SERVER);
        assertThat(parsedScenario.getConcurrentClients()).isEqualTo(5);
        assertThat(parsedScenario.getSteps()).hasSize(1);
        assertThat(parsedScenario.getSteps().get(0).getSendPayload()).isEqualTo("PING");
    }

    @Test
    @DisplayName("Test 2: ExecutionReport conforms to PROJECT.md § 4.4 JSON schema with verdict, score, latencyMs")
    void executionReport_SocketIntegrationPhase_MatchesProjectMdSchema() throws Exception {
        ExecutionReport report = ExecutionReport.builder()
                .submissionId(1284L)
                .executionPhase("COMPLETED")
                .totalScore(76.0)
                .verdict("AC")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .socketIntegration(ExecutionReport.SocketIntegrationPhase.builder()
                                .score(46.7)
                                .weight(0.30)
                                .weightedScore(14.0)
                                .scenarios(List.of(
                                        ExecutionReport.SocketScenarioResult.builder()
                                                .scenarioName("TCP_HANDSHAKE_ECHO")
                                                .passed(true)
                                                .verdict(SocketVerdict.SUCCESS)
                                                .score(100.0)
                                                .latencyMs(14L)
                                                .build(),
                                        ExecutionReport.SocketScenarioResult.builder()
                                                .scenarioName("CONCURRENT_5_CLIENTS")
                                                .passed(false)
                                                .verdict(SocketVerdict.CONNECTION_FAILURE)
                                                .score(0.0)
                                                .error("Client #4 Connection Reset")
                                                .build()
                                ))
                                .build())
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(report);
        assertThat(json).contains("\"TCP_HANDSHAKE_ECHO\"");
        assertThat(json).contains("\"passed\":true");
        assertThat(json).contains("\"verdict\":\"SUCCESS\"");
        assertThat(json).contains("\"latencyMs\":14");
        assertThat(json).contains("\"CONCURRENT_5_CLIENTS\"");
        assertThat(json).contains("\"error\":\"Client #4 Connection Reset\"");

        ExecutionReport parsed = objectMapper.readValue(json, ExecutionReport.class);
        ExecutionReport.SocketIntegrationPhase socketPhase = parsed.getPhases().getSocketIntegration();
        assertThat(socketPhase.getScore()).isEqualTo(46.7);
        assertThat(socketPhase.getWeight()).isEqualTo(0.30);
        assertThat(socketPhase.getWeightedScore()).isEqualTo(14.0);
        assertThat(socketPhase.getScenarios()).hasSize(2);
        assertThat(socketPhase.getScenarios().get(0).getVerdict()).isEqualTo(SocketVerdict.SUCCESS);
        assertThat(socketPhase.getScenarios().get(1).getError()).isEqualTo("Client #4 Connection Reset");
    }

    @Test
    @DisplayName("Test 3: SocketVerdict values adhere to PROJECT.md § 3.3 scoring specs")
    void socketVerdict_HasCorrectBaseScores() {
        assertThat(SocketVerdict.SUCCESS.getBaseScore()).isEqualTo(100.0);
        assertThat(SocketVerdict.RESOURCE_LEAK.getBaseScore()).isEqualTo(50.0);
        assertThat(SocketVerdict.CONNECTION_FAILURE.getBaseScore()).isEqualTo(0.0);
        assertThat(SocketVerdict.PROTOCOL_MISMATCH.getBaseScore()).isEqualTo(0.0);
        assertThat(SocketVerdict.SCENARIO_TIMEOUT.getBaseScore()).isEqualTo(0.0);
    }
}
