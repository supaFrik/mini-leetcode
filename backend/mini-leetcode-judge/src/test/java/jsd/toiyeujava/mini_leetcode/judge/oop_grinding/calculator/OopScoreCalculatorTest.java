package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.calculator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.SocketScenarioConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OopScoreCalculatorTest {

    private ObjectMapper objectMapper;
    private OopScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        calculator = new OopScoreCalculator(objectMapper);
    }

    @Test
    @DisplayName("Formula: 30% OOP + 40% Unit + 30% Socket computes correctly with partial scores")
    void calculate_StandardThreePhase_CalculatesWeightedSum() {
        // Gherkin Scenario 1: 100% OOP + 75% Unit + 0% Socket = 30.0 + 30.0 + 0.0 = 60.0 (WA)
        OopScoreCalculator.ScoreResult scenario1 = calculator.calculate(100.0, 75.0, 0.0, true);
        assertThat(scenario1.getTotalScore()).isEqualTo(60.0);
        assertThat(scenario1.getVerdict()).isEqualTo("WA");
        assertThat(scenario1.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.WA);
        assertThat(scenario1.getOopWeight()).isEqualTo(0.30);
        assertThat(scenario1.getOopWeightedScore()).isEqualTo(30.0);
        assertThat(scenario1.getUnitWeight()).isEqualTo(0.40);
        assertThat(scenario1.getUnitWeightedScore()).isEqualTo(30.0);
        assertThat(scenario1.getSocketWeight()).isEqualTo(0.30);
        assertThat(scenario1.getSocketWeightedScore()).isEqualTo(0.0);

        // Full pass: 100% OOP + 100% Unit + 100% Socket = 100.0 (AC)
        OopScoreCalculator.ScoreResult fullPass = calculator.calculate(100.0, 100.0, 100.0, true);
        assertThat(fullPass.getTotalScore()).isEqualTo(100.0);
        assertThat(fullPass.getVerdict()).isEqualTo("AC");
        assertThat(fullPass.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.AC);

        // Mixed pass: 80% OOP + 50% Unit + 100% Socket = 24.0 + 20.0 + 30.0 = 74.0 (WA)
        OopScoreCalculator.ScoreResult mixed = calculator.calculate(80.0, 50.0, 100.0, true);
        assertThat(mixed.getTotalScore()).isEqualTo(74.0);
        assertThat(mixed.getVerdict()).isEqualTo("WA");
        assertThat(mixed.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.WA);
    }

    @Test
    @DisplayName("Dynamic Re-balancing: When Socket is absent, weights re-balance to 40% OOP + 60% Unit")
    void calculate_DynamicRebalancingWithoutSocket_Applies4060Weights() {
        // 100% OOP + 100% Unit = 40.0 + 60.0 = 100.0 (AC)
        OopScoreCalculator.ScoreResult perfect = calculator.calculate(100.0, 100.0, 0.0, false);
        assertThat(perfect.getTotalScore()).isEqualTo(100.0);
        assertThat(perfect.getVerdict()).isEqualTo("AC");
        assertThat(perfect.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.AC);
        assertThat(perfect.getOopWeight()).isEqualTo(0.40);
        assertThat(perfect.getOopWeightedScore()).isEqualTo(40.0);
        assertThat(perfect.getUnitWeight()).isEqualTo(0.60);
        assertThat(perfect.getUnitWeightedScore()).isEqualTo(60.0);
        assertThat(perfect.getSocketWeight()).isEqualTo(0.0);
        assertThat(perfect.getSocketWeightedScore()).isEqualTo(0.0);

        // Gherkin Scenario 4: 100% OOP + 50% Unit = 40.0 + 30.0 = 70.0 (WA)
        OopScoreCalculator.ScoreResult partial = calculator.calculate(100.0, 50.0, 0.0, false);
        assertThat(partial.getTotalScore()).isEqualTo(70.0);
        assertThat(partial.getVerdict()).isEqualTo("WA");
        assertThat(partial.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.WA);

        // 0% OOP + 100% Unit = 0.0 + 60.0 = 60.0 (WA)
        OopScoreCalculator.ScoreResult zeroOop = calculator.calculate(0.0, 100.0, 0.0, false);
        assertThat(zeroOop.getTotalScore()).isEqualTo(60.0);
        assertThat(zeroOop.getVerdict()).isEqualTo("WA");
    }

    @Test
    @DisplayName("Edge Cases: Single active phase re-balances to 100% (weight 1.00)")
    void calculate_SinglePhaseActive_RebalancesTo100Percent() {
        HarnessConfig onlyOop = HarnessConfig.builder()
                .oopInspection(HarnessConfig.OopInspectionConfig.builder().enabled(true).weight(1.0).build())
                .unitTest(HarnessConfig.UnitTestConfig.builder().enabled(false).weight(0.0).build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder().enabled(false).weight(0.0).build())
                .build();
        OopScoreCalculator.Weights weightsOop = calculator.resolveWeights(onlyOop);
        assertThat(weightsOop.oop()).isEqualTo(1.00);
        assertThat(weightsOop.unit()).isEqualTo(0.0);
        assertThat(weightsOop.socket()).isEqualTo(0.0);

        OopScoreCalculator.ScoreResult resOop = calculator.calculateWithWeights(85.0, 0.0, 0.0, weightsOop);
        assertThat(resOop.getTotalScore()).isEqualTo(85.0);
        assertThat(resOop.getVerdict()).isEqualTo("WA");

        HarnessConfig onlyUnit = HarnessConfig.builder()
                .oopInspection(HarnessConfig.OopInspectionConfig.builder().enabled(false).weight(0.0).build())
                .unitTest(HarnessConfig.UnitTestConfig.builder().enabled(true).weight(1.0).build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder().enabled(false).weight(0.0).build())
                .build();
        OopScoreCalculator.Weights weightsUnit = calculator.resolveWeights(onlyUnit);
        assertThat(weightsUnit.oop()).isEqualTo(0.0);
        assertThat(weightsUnit.unit()).isEqualTo(1.00);
        assertThat(weightsUnit.socket()).isEqualTo(0.0);

        OopScoreCalculator.ScoreResult resUnit = calculator.calculateWithWeights(0.0, 100.0, 0.0, weightsUnit);
        assertThat(resUnit.getTotalScore()).isEqualTo(100.0);
        assertThat(resUnit.getVerdict()).isEqualTo("AC");

        HarnessConfig onlySocket = HarnessConfig.builder()
                .oopInspection(HarnessConfig.OopInspectionConfig.builder().enabled(false).weight(0.0).build())
                .unitTest(HarnessConfig.UnitTestConfig.builder().enabled(false).weight(0.0).build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                        .enabled(true)
                        .weight(1.0)
                        .scenarios(List.of(new SocketScenarioConfig()))
                        .build())
                .build();
        OopScoreCalculator.Weights weightsSocket = calculator.resolveWeights(onlySocket);
        assertThat(weightsSocket.oop()).isEqualTo(0.0);
        assertThat(weightsSocket.unit()).isEqualTo(0.0);
        assertThat(weightsSocket.socket()).isEqualTo(1.00);
    }

    @Test
    @DisplayName("Precision: 1-decimal rounding (half-up) and clamping [0.0, 100.0]")
    void calculate_HalfUpRoundingToOneDecimalPlace() {
        // PROJECT.md § 4.4 Example: OOP 100 (30.0) + Unit 80 (32.0) + Socket 46.7 (14.01 -> 14.0) = 76.0
        OopScoreCalculator.ScoreResult res = calculator.calculate(100.0, 80.0, 46.7, true);
        assertThat(res.getOopWeightedScore()).isEqualTo(30.0);
        assertThat(res.getUnitWeightedScore()).isEqualTo(32.0);
        assertThat(res.getSocketWeightedScore()).isEqualTo(14.0);
        assertThat(res.getTotalScore()).isEqualTo(76.0);
        assertThat(res.getVerdict()).isEqualTo("WA");

        // Clamping upper bound
        OopScoreCalculator.ScoreResult over = calculator.calculate(150.0, 150.0, 150.0, true);
        assertThat(over.getTotalScore()).isEqualTo(100.0);

        // Clamping lower bound
        OopScoreCalculator.ScoreResult under = calculator.calculate(-50.0, -50.0, -50.0, true);
        assertThat(under.getTotalScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Verdict Resolution: Maps AC, WA, CE, RE, TLE, MLE accurately")
    void resolveVerdict_MapsPhaseAndScoresAccurately() {
        assertThat(calculator.resolveVerdict(100.0, "COMPLETED")).isEqualTo(SubmissionVerdict.AC);
        assertThat(calculator.resolveVerdict(99.9, "COMPLETED")).isEqualTo(SubmissionVerdict.WA);
        assertThat(calculator.resolveVerdict(60.0, "COMPLETED")).isEqualTo(SubmissionVerdict.WA);
        assertThat(calculator.resolveVerdict(0.0, "COMPLETED")).isEqualTo(SubmissionVerdict.WA);

        // Non-completed phases
        assertThat(calculator.resolveVerdict(0.0, "COMPILATION_FAILED")).isEqualTo(SubmissionVerdict.CE);
        assertThat(calculator.resolveVerdict(0.0, "TIMEOUT")).isEqualTo(SubmissionVerdict.TLE);
        assertThat(calculator.resolveVerdict(0.0, "OUT_OF_MEMORY")).isEqualTo(SubmissionVerdict.MLE);
        assertThat(calculator.resolveVerdict(0.0, "EXECUTION_FAILED")).isEqualTo(SubmissionVerdict.RE);

        // Null safety
        assertThat(calculator.resolveVerdict(null, null)).isEqualTo(SubmissionVerdict.WA);
        assertThat(calculator.resolveVerdict(100.0, null)).isEqualTo(SubmissionVerdict.AC);
    }

    @Test
    @DisplayName("Aggregate and Enrich: Enriches ExecutionReport with weights and normalized scores")
    void aggregateAndEnrich_EnrichesPhasesAndTotals() {
        ExecutionReport report = ExecutionReport.builder()
                .submissionId(1284L)
                .executionPhase("COMPLETED")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .compilation(ExecutionReport.CompilationPhase.builder().passed(true).build())
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder().score(100.0).build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder().score(80.0).passedCount(4).totalCount(5).build())
                        .socketIntegration(ExecutionReport.SocketIntegrationPhase.builder().score(46.7).build())
                        .build())
                .build();

        HarnessConfig config = HarnessConfig.builder()
                .oopInspection(HarnessConfig.OopInspectionConfig.builder().enabled(true).weight(0.30).build())
                .unitTest(HarnessConfig.UnitTestConfig.builder().enabled(true).weight(0.40).build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                        .enabled(true)
                        .weight(0.30)
                        .scenarios(List.of(new SocketScenarioConfig()))
                        .build())
                .build();

        ExecutionReport enriched = calculator.aggregateAndEnrich(report, config);

        assertThat(enriched.getTotalScore()).isEqualTo(76.0);
        assertThat(enriched.getVerdict()).isEqualTo("WA");
        assertThat(enriched.getExecutionPhase()).isEqualTo("COMPLETED");

        assertThat(enriched.getPhases().getOopInspection().getWeight()).isEqualTo(0.30);
        assertThat(enriched.getPhases().getOopInspection().getWeightedScore()).isEqualTo(30.0);
        assertThat(enriched.getPhases().getUnitTest().getWeight()).isEqualTo(0.40);
        assertThat(enriched.getPhases().getUnitTest().getWeightedScore()).isEqualTo(32.0);
        assertThat(enriched.getPhases().getSocketIntegration().getWeight()).isEqualTo(0.30);
        assertThat(enriched.getPhases().getSocketIntegration().getWeightedScore()).isEqualTo(14.0);
    }

    @Test
    @DisplayName("Aggregate and Enrich: Handles non-completed failure phases (TIMEOUT, OOM, RE)")
    void aggregateAndEnrich_FailurePhases_SetsZeroScoreAndProperVerdict() {
        ExecutionReport timeoutReport = ExecutionReport.builder()
                .submissionId(1285L)
                .executionPhase("TIMEOUT")
                .build();
        calculator.aggregateAndEnrich(timeoutReport, (HarnessConfig) null);
        assertThat(timeoutReport.getTotalScore()).isEqualTo(0.0);
        assertThat(timeoutReport.getVerdict()).isEqualTo("TLE");

        ExecutionReport oomReport = ExecutionReport.builder()
                .submissionId(1286L)
                .executionPhase("OUT_OF_MEMORY")
                .build();
        calculator.aggregateAndEnrich(oomReport, (HarnessConfig) null);
        assertThat(oomReport.getTotalScore()).isEqualTo(0.0);
        assertThat(oomReport.getVerdict()).isEqualTo("MLE");

        ExecutionReport crashReport = ExecutionReport.builder()
                .submissionId(1287L)
                .executionPhase("EXECUTION_FAILED")
                .build();
        calculator.aggregateAndEnrich(crashReport, (HarnessConfig) null);
        assertThat(crashReport.getTotalScore()).isEqualTo(0.0);
        assertThat(crashReport.getVerdict()).isEqualTo("RE");
    }

    @Test
    @DisplayName("JSON Serialization: formatScoreBreakdownJson produces valid JSON adhering to PROJECT.md § 4.4")
    void formatScoreBreakdownJson_ProducesValidJsonMatchingSpec() throws Exception {
        ExecutionReport report = ExecutionReport.builder()
                .submissionId(1284L)
                .executionPhase("COMPLETED")
                .totalScore(76.0)
                .verdict("WA")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .compilation(ExecutionReport.CompilationPhase.builder()
                                .passed(true)
                                .durationMs(1120L)
                                .errors(null)
                                .build())
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder()
                                .score(100.0)
                                .weight(0.30)
                                .weightedScore(30.0)
                                .build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder()
                                .score(80.0)
                                .weight(0.40)
                                .weightedScore(32.0)
                                .passedCount(4)
                                .totalCount(5)
                                .build())
                        .socketIntegration(ExecutionReport.SocketIntegrationPhase.builder()
                                .score(46.7)
                                .weight(0.30)
                                .weightedScore(14.0)
                                .build())
                        .build())
                .build();

        String json = calculator.formatScoreBreakdownJson(report);
        assertThat(json).isNotNull().isNotBlank();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("submissionId").asLong()).isEqualTo(1284L);
        assertThat(root.get("executionPhase").asText()).isEqualTo("COMPLETED");
        assertThat(root.get("totalScore").asDouble()).isEqualTo(76.0);
        assertThat(root.get("verdict").asText()).isEqualTo("WA");

        JsonNode phases = root.get("phases");
        assertThat(phases.get("compilation").get("passed").asBoolean()).isTrue();
        assertThat(phases.get("compilation").get("durationMs").asLong()).isEqualTo(1120L);
        assertThat(phases.get("oopInspection").get("score").asDouble()).isEqualTo(100.0);
        assertThat(phases.get("oopInspection").get("weight").asDouble()).isEqualTo(0.30);
        assertThat(phases.get("oopInspection").get("weightedScore").asDouble()).isEqualTo(30.0);
        assertThat(phases.get("unitTest").get("score").asDouble()).isEqualTo(80.0);
        assertThat(phases.get("unitTest").get("weight").asDouble()).isEqualTo(0.40);
        assertThat(phases.get("unitTest").get("weightedScore").asDouble()).isEqualTo(32.0);
        assertThat(phases.get("socketIntegration").get("score").asDouble()).isEqualTo(46.7);
        assertThat(phases.get("socketIntegration").get("weight").asDouble()).isEqualTo(0.30);
        assertThat(phases.get("socketIntegration").get("weightedScore").asDouble()).isEqualTo(14.0);
    }
}
