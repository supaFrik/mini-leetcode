package jsd.toiyeujava.mini_leetcode.judge.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.DockerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.calculator.OopScoreCalculator;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.inspector.OopGrindingInspector;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.*;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopDockerRunner;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopJUnit5TestRunner;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.ProcessLauncher;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.SocketIntegrationHarness;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Adversarial stress-testing suite for Slice 4: Partial Scoring, Mathematical Boundaries,
 * and Gherkin Acceptance Scenarios (PROJECT.md § 4.1, 4.2, 4.4, § 7 Scenarios 1 & 2).
 */
@ExtendWith(MockitoExtension.class)
public class OopGrindingAdversarialScoringTest {

    private ObjectMapper objectMapper;
    private OopScoreCalculator calculator;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private StorageService storageService;

    @Mock
    private DockerExecutor dockerExecutor;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ProcessLauncher processLauncher;

    @Mock
    private Process process;

    private MultiFileSourceExtractor sourceExtractor;
    private OopGrindingCompiler compiler;
    private OopGrindingInspector inspector;
    private OopJUnit5TestRunner testRunner;
    private SocketIntegrationHarness socketHarness;
    private OopDockerRunner dockerRunner;
    private OopGrindingJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        calculator = new OopScoreCalculator(objectMapper);
        sourceExtractor = new MultiFileSourceExtractor();
        compiler = new OopGrindingCompiler();
        inspector = new OopGrindingInspector(objectMapper);
        testRunner = new OopJUnit5TestRunner();
        socketHarness = new SocketIntegrationHarness(processLauncher);
        dockerRunner = new OopDockerRunner(processLauncher, objectMapper, inspector, testRunner, socketHarness, calculator);

        strategy = new OopGrindingJudgeStrategy(
                submissionService,
                storageService,
                sourceExtractor,
                compiler,
                objectMapper,
                dockerExecutor,
                dockerRunner,
                submissionRepository,
                calculator
        );
    }

    // =========================================================================
    // CATEGORY 1: GHERKIN SCENARIO 1 EMPIRICAL VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("Category 1: Gherkin Scenario 1 Empirical Verification")
    class GherkinScenario1Tests {

        @Test
        @DisplayName("Gherkin 1 Calculator: 100% OOP (30) + 75% Unit (30) + 0% Socket (0) = 60.0 WA, COMPLETED")
        void scenario1_ExactCalculationAndBreakdown() {
            // Given: 100% OOP, 75% Unit (3/4 passed), 0% Socket (Connection Failure)
            OopScoreCalculator.ScoreResult result = calculator.calculate(100.0, 75.0, 0.0, true);

            // Then: TotalScore = 60.0, verdict = WA
            assertThat(result.getTotalScore()).isEqualTo(60.0);
            assertThat(result.getVerdict()).isEqualTo("WA");
            assertThat(result.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.WA);

            // Phase weights and weighted scores
            assertThat(result.getOopWeight()).isEqualTo(0.30);
            assertThat(result.getOopWeightedScore()).isEqualTo(30.0);
            assertThat(result.getUnitWeight()).isEqualTo(0.40);
            assertThat(result.getUnitWeightedScore()).isEqualTo(30.0);
            assertThat(result.getSocketWeight()).isEqualTo(0.30);
            assertThat(result.getSocketWeightedScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Gherkin 1 Report Enrichment: Serialized JSON reflects exact weights and scores")
        void scenario1_ExecutionReportEnrichmentAndJsonPersistence() throws Exception {
            ExecutionReport report = ExecutionReport.builder()
                    .submissionId(9001L)
                    .executionPhase("COMPLETED")
                    .phases(ExecutionReport.PhaseBreakdown.builder()
                            .compilation(ExecutionReport.CompilationPhase.builder().passed(true).durationMs(800L).build())
                            .oopInspection(ExecutionReport.OopInspectionPhase.builder().score(100.0).build())
                            .unitTest(ExecutionReport.UnitTestPhase.builder().score(75.0).passedCount(3).totalCount(4).build())
                            .socketIntegration(ExecutionReport.SocketIntegrationPhase.builder().score(0.0).build())
                            .build())
                    .build();

            ExecutionReport enriched = calculator.aggregateAndEnrich(report, true);

            assertThat(enriched.getTotalScore()).isEqualTo(60.0);
            assertThat(enriched.getVerdict()).isEqualTo("WA");
            assertThat(enriched.getExecutionPhase()).isEqualTo("COMPLETED");

            String json = calculator.formatScoreBreakdownJson(enriched);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.get("submissionId").asLong()).isEqualTo(9001L);
            assertThat(root.get("executionPhase").asText()).isEqualTo("COMPLETED");
            assertThat(root.get("totalScore").asDouble()).isEqualTo(60.0);
            assertThat(root.get("verdict").asText()).isEqualTo("WA");

            JsonNode phases = root.get("phases");
            assertThat(phases.get("oopInspection").get("score").asDouble()).isEqualTo(100.0);
            assertThat(phases.get("oopInspection").get("weight").asDouble()).isEqualTo(0.30);
            assertThat(phases.get("oopInspection").get("weightedScore").asDouble()).isEqualTo(30.0);

            assertThat(phases.get("unitTest").get("score").asDouble()).isEqualTo(75.0);
            assertThat(phases.get("unitTest").get("weight").asDouble()).isEqualTo(0.40);
            assertThat(phases.get("unitTest").get("weightedScore").asDouble()).isEqualTo(30.0);
            assertThat(phases.get("unitTest").get("passedCount").asInt()).isEqualTo(3);
            assertThat(phases.get("unitTest").get("totalCount").asInt()).isEqualTo(4);

            assertThat(phases.get("socketIntegration").get("score").asDouble()).isEqualTo(0.0);
            assertThat(phases.get("socketIntegration").get("weight").asDouble()).isEqualTo(0.30);
            assertThat(phases.get("socketIntegration").get("weightedScore").asDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Gherkin 1 Strategy Pipeline: Full mock run records 60.0 WA, COMPLETED in repository")
        void scenario1_StrategyPipelineExecution(@TempDir Path workDir) throws Exception {
            Long submissionId = 9002L;
            SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 201L, 1L, SubmissionLanguage.JAVA);
            Problem problem = Problem.builder().id(201L).title("Chat Server Gherkin").problemType(ProblemType.OOP_GRINDING).build();
            Submission submission = Submission.builder().id(submissionId).problem(problem).build();

            String code = """
                    // === FILE: Server.java ===
                    public class Server {
                        private final int port;
                        public Server(int port) { this.port = port; }
                        public int getPort() { return port; }
                        public boolean check() { return false; }
                    }
                    // === FILE: ServerTest.java ===
                    import org.junit.jupiter.api.Test;
                    import static org.junit.jupiter.api.Assertions.*;
                    public class ServerTest {
                        @Test void t1() { assertEquals(1, 1); }
                        @Test void t2() { assertEquals(2, 2); }
                        @Test void t3() { assertEquals(3, 3); }
                        @Test void t4() { assertTrue(new Server(80).check()); } // Fails! 3/4 pass = 75%
                    }
                    """;

            OopConstraintRule rule = OopConstraintRule.builder()
                    .targetClassName("Server")
                    .expectedType(ExpectedClassType.CLASS)
                    .ruleName("SERVER_CHECK")
                    .build();
            Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(rule)));

            SocketScenarioConfig socketConfig = SocketScenarioConfig.builder()
                    .scenarioName("SOCKET_TEST")
                    .mode(SocketAssessmentMode.CLIENT_ASSESSMENT)
                    .targetClass("Server")
                    .build();
            Files.writeString(workDir.resolve("socket-scenarios.json"), objectMapper.writeValueAsString(List.of(socketConfig)));

            when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
            when(submissionService.getSubmissionCode(submissionId)).thenReturn(code);
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            strategy.executeJudge(message, problem, workDir);

            // Verify verdict WA and passed 3 / total 4
            verify(submissionService).updateSubmissionVerdict(
                    eq(submissionId),
                    eq(SubmissionStatus.COMPLETED),
                    eq(SubmissionVerdict.WA),
                    anyInt(),
                    anyInt(),
                    eq(3),
                    eq(4),
                    isNull()
            );

            ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
            verify(submissionRepository, atLeastOnce()).save(captor.capture());
            Submission saved = captor.getValue();

            assertThat(saved.getScore()).isEqualTo(60.0);
            assertThat(saved.getExecutionPhase()).isEqualTo("COMPLETED");

            JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
            assertThat(root.get("totalScore").asDouble()).isEqualTo(60.0);
            assertThat(root.get("verdict").asText()).isEqualTo("WA");
            assertThat(root.get("phases").get("oopInspection").get("weightedScore").asDouble()).isEqualTo(30.0);
            assertThat(root.get("phases").get("unitTest").get("weightedScore").asDouble()).isEqualTo(30.0);
            assertThat(root.get("phases").get("socketIntegration").get("weightedScore").asDouble()).isEqualTo(0.0);
        }
    }

    // =========================================================================
    // CATEGORY 2: GHERKIN SCENARIO 2 MALICIOUS CODE SIMULATION
    // =========================================================================
    @Nested
    @DisplayName("Category 2: Gherkin Scenario 2 Malicious Code Defense & Process Isolation")
    class GherkinScenario2MaliciousCodeTests {

        @Test
        @DisplayName("Scenario 2A: System.exit(0) stealth termination (exit 0 without report) -> RE, EXECUTION_FAILED, host survives")
        void maliciousCode_SystemExitZero_HostSurvivesAndRecordsRe(@TempDir Path workDir) throws Exception {
            Long submissionId = 8801L;
            HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

            // Simulate container process finishing with exit code 0, but no report file created
            when(processLauncher.launch(any())).thenReturn(process);
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream("System.exit(0) called by user code\n".getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(process.exitValue()).thenReturn(0);

            ExecutionReport report = dockerRunner.runHarness(submissionId, workDir, config, 1000L);

            // Verify host did NOT crash, score is 0.0, verdict is RE, phase is EXECUTION_FAILED
            assertThat(report).isNotNull();
            assertThat(report.getTotalScore()).isEqualTo(0.0);
            assertThat(report.getVerdict()).isEqualTo("RE");
            assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
            assertThat(report.getErrorMessage()).contains("execution-report.json was not generated");
        }

        @Test
        @DisplayName("Scenario 2B: Crash / Runtime.exec error (exit code 1) -> RE, EXECUTION_FAILED, host survives")
        void maliciousCode_CrashExitCode1_HostSurvivesAndRecordsRe(@TempDir Path workDir) throws Exception {
            Long submissionId = 8802L;
            HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

            when(processLauncher.launch(any())).thenReturn(process);
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("Fatal: java.lang.SecurityException or crash\n".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(process.exitValue()).thenReturn(1);

            ExecutionReport report = dockerRunner.runHarness(submissionId, workDir, config, 1000L);

            assertThat(report.getTotalScore()).isEqualTo(0.0);
            assertThat(report.getVerdict()).isEqualTo("RE");
            assertThat(report.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
            assertThat(report.getErrorMessage()).contains("Container exited with code 1");
        }

        @Test
        @DisplayName("Scenario 2C: Out of Memory attack (exit code 137) -> MLE, OUT_OF_MEMORY")
        void maliciousCode_OutOfMemoryExitCode137_RecordsMle(@TempDir Path workDir) throws Exception {
            Long submissionId = 8803L;
            HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

            when(processLauncher.launch(any())).thenReturn(process);
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("Killed\n".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(process.exitValue()).thenReturn(137);

            ExecutionReport report = dockerRunner.runHarness(submissionId, workDir, config, 1000L);

            assertThat(report.getTotalScore()).isEqualTo(0.0);
            assertThat(report.getVerdict()).isEqualTo("MLE");
            assertThat(report.getExecutionPhase()).isEqualTo("OUT_OF_MEMORY");
        }

        @Test
        @DisplayName("Scenario 2D: Infinite loop / hang timeout -> TLE, TIMEOUT, destroys process")
        void maliciousCode_InfiniteLoopTimeout_RecordsTle(@TempDir Path workDir) throws Exception {
            Long submissionId = 8804L;
            HarnessConfig config = HarnessConfig.builder().submissionId(submissionId).build();

            when(processLauncher.launch(any())).thenReturn(process);
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            // Timeout occurs: waitFor returns false
            when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);

            ExecutionReport report = dockerRunner.runHarness(submissionId, workDir, config, 500L);

            assertThat(report.getTotalScore()).isEqualTo(0.0);
            assertThat(report.getVerdict()).isEqualTo("TLE");
            assertThat(report.getExecutionPhase()).isEqualTo("TIMEOUT");

            verify(process).destroyForcibly();
        }
    }

    // =========================================================================
    // CATEGORY 3: FLOATING POINT ARITHMETIC & CLAMPING
    // =========================================================================
    @Nested
    @DisplayName("Category 3: Floating Point Precision, Rounding & Boundary Clamping")
    class FloatingPointAndClampingTests {

        @Test
        @DisplayName("Precision Rounding: 76.01 -> 76.0, 99.95 -> 100.0, 0.04 -> 0.0")
        void precisionRounding_ExactHalfUpSpecification() {
            // Task requirement: 76.01 -> 76.0
            assertThat(OopScoreCalculator.round1(76.01)).isEqualTo(76.0);

            // Task requirement: 99.95 -> 100.0
            assertThat(OopScoreCalculator.round1(99.95)).isEqualTo(100.0);

            // Task requirement: 0.04 -> 0.0
            assertThat(OopScoreCalculator.round1(0.04)).isEqualTo(0.0);

            // Additional half-up checks
            assertThat(OopScoreCalculator.round1(0.05)).isEqualTo(0.1);
            assertThat(OopScoreCalculator.round1(99.94)).isEqualTo(99.9);
            assertThat(OopScoreCalculator.round1(73.333333333)).isEqualTo(73.3);
            assertThat(OopScoreCalculator.round1(86.666666667)).isEqualTo(86.7);
        }

        @Test
        @DisplayName("Clamping: inputs < 0.0 clamp strictly to 0.0; inputs > 100.0 clamp strictly to 100.0")
        void boundaryClamping_StrictZeroToHundred() {
            // Negative inputs clamped to 0.0
            OopScoreCalculator.ScoreResult negativeUnderflow = calculator.calculate(-10.0, -50.0, -99.0, true);
            assertThat(negativeUnderflow.getTotalScore()).isEqualTo(0.0);
            assertThat(negativeUnderflow.getVerdict()).isEqualTo("WA");

            OopScoreCalculator.ScoreResult extremeNegative = calculator.calculate(-9999.9, -9999.9, -9999.9, false);
            assertThat(extremeNegative.getTotalScore()).isEqualTo(0.0);

            // Positive overflow clamped to 100.0
            OopScoreCalculator.ScoreResult positiveOverflow = calculator.calculate(150.0, 120.0, 110.0, true);
            assertThat(positiveOverflow.getTotalScore()).isEqualTo(100.0);
            assertThat(positiveOverflow.getVerdict()).isEqualTo("AC");

            OopScoreCalculator.ScoreResult extremePositive = calculator.calculate(9999.9, 9999.9, 9999.9, false);
            assertThat(extremePositive.getTotalScore()).isEqualTo(100.0);
            assertThat(extremePositive.getVerdict()).isEqualTo("AC");

            // Exact boundaries
            OopScoreCalculator.ScoreResult exactZero = calculator.calculate(0.0, 0.0, 0.0, true);
            assertThat(exactZero.getTotalScore()).isEqualTo(0.0);
            assertThat(exactZero.getVerdict()).isEqualTo("WA");

            OopScoreCalculator.ScoreResult exactHundred = calculator.calculate(100.0, 100.0, 100.0, true);
            assertThat(exactHundred.getTotalScore()).isEqualTo(100.0);
            assertThat(exactHundred.getVerdict()).isEqualTo("AC");
        }

        @Test
        @DisplayName("Verdict Threshold: 99.94 rounds to 99.9 (WA), 99.95 rounds to 100.0 (AC)")
        void verdictThreshold_PrecisionBoundary() {
            // 99.94 rounds to 99.9 -> WA
            OopScoreCalculator.ScoreResult justBelow = calculator.calculateWithWeights(
                    99.94, 0.0, 0.0, new OopScoreCalculator.Weights(1.0, 0.0, 0.0)
            );
            assertThat(justBelow.getTotalScore()).isEqualTo(99.9);
            assertThat(justBelow.getVerdict()).isEqualTo("WA");
            assertThat(justBelow.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.WA);

            // 99.95 rounds to 100.0 -> AC
            OopScoreCalculator.ScoreResult roundedToAc = calculator.calculateWithWeights(
                    99.95, 0.0, 0.0, new OopScoreCalculator.Weights(1.0, 0.0, 0.0)
            );
            assertThat(roundedToAc.getTotalScore()).isEqualTo(100.0);
            assertThat(roundedToAc.getVerdict()).isEqualTo("AC");
            assertThat(roundedToAc.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.AC);
        }
    }

    // =========================================================================
    // CATEGORY 4: MIXED PHASE PERMUTATIONS
    // =========================================================================
    @Nested
    @DisplayName("Category 4: Mixed Phase Permutations & Weight Rebalancing")
    class MixedPhasePermutationTests {

        @Test
        @DisplayName("Permutation: 0% OOP + 100% Unit + 100% Socket = 70.0 WA")
        void permutation_0Oop_100Unit_100Socket_Produces70Wa() {
            // 0.30 * 0.0 + 0.40 * 100.0 + 0.30 * 100.0 = 0.0 + 40.0 + 30.0 = 70.0 (WA)
            OopScoreCalculator.ScoreResult res = calculator.calculate(0.0, 100.0, 100.0, true);
            assertThat(res.getTotalScore()).isEqualTo(70.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
            assertThat(res.getOopWeightedScore()).isEqualTo(0.0);
            assertThat(res.getUnitWeightedScore()).isEqualTo(40.0);
            assertThat(res.getSocketWeightedScore()).isEqualTo(30.0);
        }

        @Test
        @DisplayName("Permutation: 50% OOP + 50% Unit + 50% Socket = 50.0 WA")
        void permutation_50Oop_50Unit_50Socket_Produces50Wa() {
            // 0.30 * 50.0 + 0.40 * 50.0 + 0.30 * 50.0 = 15.0 + 20.0 + 15.0 = 50.0 (WA)
            OopScoreCalculator.ScoreResult res = calculator.calculate(50.0, 50.0, 50.0, true);
            assertThat(res.getTotalScore()).isEqualTo(50.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
            assertThat(res.getOopWeightedScore()).isEqualTo(15.0);
            assertThat(res.getUnitWeightedScore()).isEqualTo(20.0);
            assertThat(res.getSocketWeightedScore()).isEqualTo(15.0);
        }

        @Test
        @DisplayName("Permutation: 100% OOP + 100% Unit without socket = 100.0 AC")
        void permutation_100Oop_100Unit_NoSocket_Produces100Ac() {
            // Re-balanced: 0.40 * 100.0 + 0.60 * 100.0 = 40.0 + 60.0 = 100.0 (AC)
            OopScoreCalculator.ScoreResult res = calculator.calculate(100.0, 100.0, 0.0, false);
            assertThat(res.getTotalScore()).isEqualTo(100.0);
            assertThat(res.getVerdict()).isEqualTo("AC");
            assertThat(res.getOopWeight()).isEqualTo(0.40);
            assertThat(res.getOopWeightedScore()).isEqualTo(40.0);
            assertThat(res.getUnitWeight()).isEqualTo(0.60);
            assertThat(res.getUnitWeightedScore()).isEqualTo(60.0);
            assertThat(res.getSocketWeight()).isEqualTo(0.0);
            assertThat(res.getSocketWeightedScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Permutation: 100% OOP + 0% Unit without socket = 40.0 WA")
        void permutation_100Oop_0Unit_NoSocket_Produces40Wa() {
            OopScoreCalculator.ScoreResult res = calculator.calculate(100.0, 0.0, 0.0, false);
            assertThat(res.getTotalScore()).isEqualTo(40.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
        }

        @Test
        @DisplayName("Permutation: 0% OOP + 100% Unit without socket = 60.0 WA")
        void permutation_0Oop_100Unit_NoSocket_Produces60Wa() {
            OopScoreCalculator.ScoreResult res = calculator.calculate(0.0, 100.0, 0.0, false);
            assertThat(res.getTotalScore()).isEqualTo(60.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
        }

        @Test
        @DisplayName("Permutation: 100% OOP + 100% Unit + 0% Socket = 70.0 WA")
        void permutation_100Oop_100Unit_0Socket_Produces70Wa() {
            // 0.30 * 100.0 + 0.40 * 100.0 + 0.30 * 0.0 = 30.0 + 40.0 + 0.0 = 70.0 (WA)
            OopScoreCalculator.ScoreResult res = calculator.calculate(100.0, 100.0, 0.0, true);
            assertThat(res.getTotalScore()).isEqualTo(70.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
        }

        @Test
        @DisplayName("Permutation: 100% OOP + 0% Unit + 100% Socket = 60.0 WA")
        void permutation_100Oop_0Unit_100Socket_Produces60Wa() {
            // 0.30 * 100.0 + 0.40 * 0.0 + 0.30 * 100.0 = 30.0 + 0.0 + 30.0 = 60.0 (WA)
            OopScoreCalculator.ScoreResult res = calculator.calculate(100.0, 0.0, 100.0, true);
            assertThat(res.getTotalScore()).isEqualTo(60.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
        }

        @Test
        @DisplayName("Permutation: 0% OOP + 0% Unit + 0% Socket = 0.0 WA")
        void permutation_AllZero_Produces0Wa() {
            OopScoreCalculator.ScoreResult res = calculator.calculate(0.0, 0.0, 0.0, true);
            assertThat(res.getTotalScore()).isEqualTo(0.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
        }

        @Test
        @DisplayName("Permutation: 100% OOP + 100% Unit + 100% Socket = 100.0 AC")
        void permutation_AllHundred_Produces100Ac() {
            OopScoreCalculator.ScoreResult res = calculator.calculate(100.0, 100.0, 100.0, true);
            assertThat(res.getTotalScore()).isEqualTo(100.0);
            assertThat(res.getVerdict()).isEqualTo("AC");
        }
    }

    // =========================================================================
    // CATEGORY 5: DYNAMIC WEIGHTING RESOLUTION & NULL ROBUSTNESS
    // =========================================================================
    @Nested
    @DisplayName("Category 5: Dynamic Weighting Resolution & Null Robustness")
    class DynamicWeightingAndNullSafetyTests {

        @Test
        @DisplayName("Null Configuration defaults to 40% OOP + 60% Unit")
        void nullHarnessConfig_Resolves4060Weights() {
            OopScoreCalculator.Weights weights = calculator.resolveWeights(null);
            assertThat(weights.oop()).isEqualTo(0.40);
            assertThat(weights.unit()).isEqualTo(0.60);
            assertThat(weights.socket()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Two active phases without Unit: OOP (50%) + Socket (50%)")
        void oopAndSocketActiveOnly_Resolves5050Weights() {
            HarnessConfig config = HarnessConfig.builder()
                    .oopInspection(HarnessConfig.OopInspectionConfig.builder().enabled(true).weight(0.50).build())
                    .unitTest(HarnessConfig.UnitTestConfig.builder().enabled(false).weight(0.0).build())
                    .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                            .enabled(true)
                            .weight(0.50)
                            .scenarios(List.of(new SocketScenarioConfig()))
                            .build())
                    .build();

            OopScoreCalculator.Weights weights = calculator.resolveWeights(config);
            assertThat(weights.oop()).isEqualTo(0.50);
            assertThat(weights.unit()).isEqualTo(0.0);
            assertThat(weights.socket()).isEqualTo(0.50);

            OopScoreCalculator.ScoreResult res = calculator.calculateWithWeights(80.0, 0.0, 60.0, weights);
            // 0.50 * 80 + 0.50 * 60 = 40 + 30 = 70.0
            assertThat(res.getTotalScore()).isEqualTo(70.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
        }

        @Test
        @DisplayName("Two active phases without OOP: Unit (57%) + Socket (43%)")
        void unitAndSocketActiveOnly_Resolves5743Weights() {
            HarnessConfig config = HarnessConfig.builder()
                    .oopInspection(HarnessConfig.OopInspectionConfig.builder().enabled(false).weight(0.0).build())
                    .unitTest(HarnessConfig.UnitTestConfig.builder().enabled(true).weight(0.60).build())
                    .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                            .enabled(true)
                            .weight(0.40)
                            .scenarios(List.of(new SocketScenarioConfig()))
                            .build())
                    .build();

            OopScoreCalculator.Weights weights = calculator.resolveWeights(config);
            assertThat(weights.oop()).isEqualTo(0.0);
            assertThat(weights.unit()).isEqualTo(0.57);
            assertThat(weights.socket()).isEqualTo(0.43);
        }

        @Test
        @DisplayName("Null safety in calculate(): null inputs default to 0.0 without NPE")
        void nullInputs_HandledSafely() {
            OopScoreCalculator.ScoreResult res = calculator.calculate(null, null, null, true);
            assertThat(res.getTotalScore()).isEqualTo(0.0);
            assertThat(res.getVerdict()).isEqualTo("WA");
            assertThat(res.getSubmissionVerdict()).isEqualTo(SubmissionVerdict.WA);
        }

        @Test
        @DisplayName("Null safety in aggregateAndEnrich: null report returns null safely")
        void nullReport_ReturnsNull() {
            assertThat(calculator.aggregateAndEnrich(null, (HarnessConfig) null)).isNull();
            assertThat(calculator.aggregateAndEnrich(null, true)).isNull();
        }

        @Test
        @DisplayName("Null safety in mapVerdict: null or unknown defaults to WA")
        void mapVerdict_NullOrUnknown_DefaultsToWa() {
            assertThat(calculator.mapVerdict(null)).isEqualTo(SubmissionVerdict.WA);
            assertThat(calculator.mapVerdict("")).isEqualTo(SubmissionVerdict.WA);
            assertThat(calculator.mapVerdict("UNKNOWN")).isEqualTo(SubmissionVerdict.WA);
            assertThat(calculator.mapVerdict("ac")).isEqualTo(SubmissionVerdict.AC);
            assertThat(calculator.mapVerdict("ce")).isEqualTo(SubmissionVerdict.CE);
            assertThat(calculator.mapVerdict("re")).isEqualTo(SubmissionVerdict.RE);
            assertThat(calculator.mapVerdict("tle")).isEqualTo(SubmissionVerdict.TLE);
            assertThat(calculator.mapVerdict("mle")).isEqualTo(SubmissionVerdict.MLE);
        }

        @Test
        @DisplayName("Null safety in formatScoreBreakdownJson: fallback JSON when serialization encounters issue")
        void formatScoreBreakdownJson_NullSafeFallback() {
            ExecutionReport report = ExecutionReport.builder()
                    .submissionId(777L)
                    .totalScore(55.5)
                    .verdict("WA")
                    .executionPhase("COMPLETED")
                    .build();
            String json = calculator.formatScoreBreakdownJson(report);
            assertThat(json).contains("\"totalScore\":55.5");
            assertThat(json).contains("\"verdict\":\"WA\"");
        }
    }
}
