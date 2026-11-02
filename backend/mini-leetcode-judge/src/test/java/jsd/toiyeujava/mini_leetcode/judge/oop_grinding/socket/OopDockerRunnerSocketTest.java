package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector.OopGrindingInspector;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopDockerRunner;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopJUnit5TestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OopDockerRunnerSocketTest {

    @Mock
    private OopGrindingInspector inspector;

    @Mock
    private OopJUnit5TestRunner testRunner;

    @Mock
    private SocketIntegrationHarness socketHarness;

    private ObjectMapper objectMapper;
    private OopDockerRunner runner;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        runner = new OopDockerRunner(ProcessBuilder::start, objectMapper, inspector, testRunner, socketHarness);
    }

    @Test
    @DisplayName("Test: runInProcessHarness executes Phase 4 and combines weights: 30% OOP + 40% Unit + 30% Socket")
    void runInProcessHarness_ExecutesSocketPhaseAndCombinesScores(@TempDir Path workDir) {
        Long submissionId = 9999L;
        HarnessConfig config = HarnessConfig.builder()
                .submissionId(submissionId)
                .oopInspection(HarnessConfig.OopInspectionConfig.builder()
                        .enabled(true)
                        .weight(0.30)
                        .rules(Collections.emptyList())
                        .build())
                .unitTest(HarnessConfig.UnitTestConfig.builder()
                        .enabled(true)
                        .weight(0.40)
                        .testClassNames(List.of("SomeTest"))
                        .build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                        .enabled(true)
                        .weight(0.30)
                        .scenarios(List.of(SocketScenarioConfig.builder().scenarioName("S1").build()))
                        .build())
                .build();

        when(inspector.inspect(any(), anyList(), anyDouble())).thenReturn(
                OopInspectionReport.builder()
                        .passed(true)
                        .score(100.0)
                        .weight(0.30)
                        .weightedScore(30.0)
                        .summary("OOP passed")
                        .build()
        );

        when(testRunner.runTests(any(), any(), anyList(), anyInt(), anyDouble())).thenReturn(
                ExecutionReport.UnitTestPhase.builder()
                        .score(80.0)
                        .weight(0.40)
                        .weightedScore(32.0)
                        .passedCount(4)
                        .totalCount(5)
                        .build()
        );

        when(socketHarness.runScenarios(any(), any(), any())).thenReturn(
                ExecutionReport.SocketIntegrationPhase.builder()
                        .score(100.0)
                        .weight(0.30)
                        .weightedScore(30.0)
                        .summary("Socket passed")
                        .scenarios(List.of(
                                ExecutionReport.SocketScenarioResult.success("S1", 15L)
                        ))
                        .build()
        );

        ExecutionReport report = runner.runInProcessHarness(submissionId, workDir, config);

        assertThat(report).isNotNull();
        // 30.0 (OOP) + 32.0 (Unit) + 30.0 (Socket) = 92.0
        assertThat(report.getTotalScore()).isEqualTo(92.0);
        assertThat(report.getVerdict()).isEqualTo("WA"); // < 100.0
        assertThat(report.getPhases().getSocketIntegration()).isNotNull();
        assertThat(report.getPhases().getSocketIntegration().getScore()).isEqualTo(100.0);
        assertThat(report.getPhases().getSocketIntegration().getWeightedScore()).isEqualTo(30.0);
        assertThat(report.getPhases().getSocketIntegration().getScenarios()).hasSize(1);
        assertThat(report.getPhases().getSocketIntegration().getScenarios().get(0).getVerdict()).isEqualTo(SocketVerdict.SUCCESS);

        assertThat(Files.exists(workDir.resolve("execution-report.json"))).isTrue();
    }
}
