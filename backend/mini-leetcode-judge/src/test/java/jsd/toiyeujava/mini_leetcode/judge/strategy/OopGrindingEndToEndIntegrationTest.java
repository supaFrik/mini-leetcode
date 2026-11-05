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
 * End-to-End Integration Test for Slice 4: Score Aggregator & End-to-End Integration Flow.
 * Verifies Gherkin Scenarios 1 to 4 from PROJECT.md Section 7,
 * dynamic weight re-balancing, fail-fast compilation, host defense against malicious code,
 * and database persistence with score_breakdown JSON matching PROJECT.md § 4.4.
 */
@ExtendWith(MockitoExtension.class)
public class OopGrindingEndToEndIntegrationTest {

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

    private ObjectMapper objectMapper;
    private MultiFileSourceExtractor sourceExtractor;
    private OopGrindingCompiler compiler;
    private OopGrindingInspector inspector;
    private OopJUnit5TestRunner testRunner;
    private SocketIntegrationHarness socketHarness;
    private OopScoreCalculator scoreCalculator;
    private OopDockerRunner dockerRunner;
    private OopGrindingJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sourceExtractor = new MultiFileSourceExtractor();
        compiler = new OopGrindingCompiler();
        inspector = new OopGrindingInspector(objectMapper);
        testRunner = new OopJUnit5TestRunner();
        socketHarness = new SocketIntegrationHarness(processLauncher);
        scoreCalculator = new OopScoreCalculator(objectMapper);

        // Real in-process runner fallback with real compiler, reflection, JUnit 5, and loopback socket harness
        dockerRunner = new OopDockerRunner(processLauncher, objectMapper, inspector, testRunner, socketHarness, scoreCalculator);

        strategy = new OopGrindingJudgeStrategy(
                submissionService,
                storageService,
                sourceExtractor,
                compiler,
                objectMapper,
                dockerExecutor,
                dockerRunner,
                submissionRepository,
                scoreCalculator
        );
    }

    @Test
    @DisplayName("Gherkin Scenario 1: Partial scoring when Socket fails (100% OOP + 75% Unit + 0% Socket = 60.0 score, WA)")
    void scenario1_PartialScoringWhenSocketFails_Produces60ScoreAndWaVerdict(@TempDir Path workDir) throws Exception {
        Long submissionId = 1284L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 101L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(101L).title("Chat Server").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        // Given: Chat Server submission code that compiles
        String codeBundle = """
                // === FILE: ChatServer.java ===
                public class ChatServer {
                    private final int port;
                    public ChatServer(int port) {
                        this.port = port;
                    }
                    public int getPort() {
                        return port;
                    }
                    public boolean isRunning() {
                        return false; // Server crashed on start
                    }
                }
                // === FILE: ServerTest.java ===
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class ServerTest {
                    @Test
                    void testPort() {
                        ChatServer s = new ChatServer(8080);
                        assertEquals(8080, s.getPort());
                    }
                    @Test
                    void testPortZero() {
                        ChatServer s = new ChatServer(0);
                        assertEquals(0, s.getPort());
                    }
                    @Test
                    void testPositivePort() {
                        ChatServer s = new ChatServer(9090);
                        assertTrue(s.getPort() > 0);
                    }
                    @Test
                    void testIsRunning() {
                        ChatServer s = new ChatServer(8080);
                        assertTrue(s.isRunning(), "Expected server to be running"); // Fails! (3 of 4 pass = 75%)
                    }
                }
                """;

        // Stage rules for 100% OOP inspection
        OopConstraintRule oopRule = OopConstraintRule.builder()
                .targetClassName("ChatServer")
                .expectedType(ExpectedClassType.CLASS)
                .ruleName("ENCAPSULATION_CHAT_SERVER")
                .fields(List.of(
                        OopFieldConstraint.builder()
                                .name("port")
                                .type("int")
                                .requiredModifiers(List.of("private", "final"))
                                .build()
                ))
                .build();
        Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(oopRule)));

        // Stage socket scenario (which will fail with CONNECTION_FAILURE because server is not running on port)
        SocketScenarioConfig socketConfig = SocketScenarioConfig.builder()
                .scenarioName("TCP_ECHO_SCENARIO")
                .mode(SocketAssessmentMode.CLIENT_ASSESSMENT)
                .targetClass("ChatServer")
                .role(SocketRole.CLIENT)
                .build();
        Files.writeString(workDir.resolve("socket-scenarios.json"), objectMapper.writeValueAsString(List.of(socketConfig)));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        // When: judging executes
        strategy.executeJudge(message, problem, workDir);

        // Then: execution_phase = "COMPLETED", totalScore = 60.0, verdict = WA
        // Formula: 0.30 * 100.0 (OOP) + 0.40 * 75.0 (Unit) + 0.30 * 0.0 (Socket) = 30.0 + 30.0 + 0.0 = 60.0
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.WA),
                isNull(),
                isNull(),
                eq(3), // 3 passed test cases
                eq(4), // 4 total test cases
                isNull()
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(60.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPLETED");

        // Validate score_breakdown JSON structure adhering to PROJECT.md § 4.4
        String breakdownJson = saved.getScoreBreakdown();
        assertThat(breakdownJson).isNotNull().isNotBlank();

        JsonNode root = objectMapper.readTree(breakdownJson);
        assertThat(root.get("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(root.get("executionPhase").asText()).isEqualTo("COMPLETED");
        assertThat(root.get("totalScore").asDouble()).isEqualTo(60.0);
        assertThat(root.get("verdict").asText()).isEqualTo("WA");

        JsonNode phases = root.get("phases");
        assertThat(phases.get("compilation").get("passed").asBoolean()).isTrue();

        JsonNode oopPhase = phases.get("oopInspection");
        assertThat(oopPhase.get("score").asDouble()).isEqualTo(100.0);
        assertThat(oopPhase.get("weight").asDouble()).isEqualTo(0.30);
        assertThat(oopPhase.get("weightedScore").asDouble()).isEqualTo(30.0);

        JsonNode unitPhase = phases.get("unitTest");
        assertThat(unitPhase.get("score").asDouble()).isEqualTo(75.0);
        assertThat(unitPhase.get("weight").asDouble()).isEqualTo(0.40);
        assertThat(unitPhase.get("weightedScore").asDouble()).isEqualTo(30.0);
        assertThat(unitPhase.get("passedCount").asInt()).isEqualTo(3);
        assertThat(unitPhase.get("totalCount").asInt()).isEqualTo(4);

        JsonNode socketPhase = phases.get("socketIntegration");
        assertThat(socketPhase.get("weight").asDouble()).isEqualTo(0.30);
        assertThat(socketPhase.get("weightedScore").asDouble()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Gherkin Scenario 2: Host defense against malicious code (System.exit(0), rm -rf /), worker survives")
    void scenario2_MaliciousCodeDefense_WorkerSurvivesAndRecordsExecutionFailed(@TempDir Path workDir) throws Exception {
        Long submissionId = 6666L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 102L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(102L).title("Security Sandbox").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        String maliciousCode = """
                // === File: Exploit.java ===
                public class Exploit {
                    public static void main(String[] args) {
                        try {
                            Runtime.getRuntime().exec("rm -rf /");
                        } catch (Exception ignored) {}
                        System.exit(0);
                    }
                }
                """;

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(maliciousCode);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        // Mock process launcher to simulate malicious code triggering non-zero/exit without report
        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream("Container exited due to exploit\n".getBytes(StandardCharsets.UTF_8)));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("System.exit(0) triggered inside container\n".getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(1); // Non-zero exit from runner

        // When: judging malicious submission
        strategy.executeJudge(message, problem, workDir);

        // Then: host worker survives completely (did NOT exit), records RE / EXECUTION_FAILED with score 0.0
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.RE),
                isNull(),
                isNull(),
                eq(0),
                eq(0),
                contains("Container exited with code 1")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("EXECUTION_FAILED");
        assertThat(saved.getScoreBreakdown()).contains("\"verdict\":\"RE\"");
    }

    @Test
    @DisplayName("Gherkin Scenario 3: Fail-Fast on compilation error (CE, score 0.0, zero container launched)")
    void scenario3_FailFastCompilationError_AbortsPipelineWithoutContainer(@TempDir Path workDir) throws Exception {
        Long submissionId = 5555L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 103L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(103L).title("Syntax Error Problem").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        String uncompilableCode = """
                // === File: Broken.java ===
                public class Broken {
                    int x = ; // Syntax error!
                }
                """;

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(uncompilableCode);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        // When: judging broken submission
        strategy.executeJudge(message, problem, workDir);

        // Then: Fail-fast activated! verdict = CE, score = 0.0, execution_phase = COMPILATION_FAILED
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                isNull(),
                isNull(),
                eq(0),
                eq(0),
                contains("error: illegal start of expression")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("verdict").asText()).isEqualTo("CE");
        assertThat(root.get("totalScore").asDouble()).isEqualTo(0.0);
        assertThat(root.get("phases").get("compilation").get("passed").asBoolean()).isFalse();

        // VERIFY ZERO CONTAINER SPAWNED
        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Gherkin Scenario 4A: Dynamic re-balancing without socket (100% OOP + 100% Unit = 100.0 AC)")
    void scenario4A_DynamicRebalancing_PerfectScore_Produces100AcVerdict(@TempDir Path workDir) throws Exception {
        Long submissionId = 4444L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 104L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(104L).title("Pure OOP Shape").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        String codeBundle = """
                // === FILE: Shape.java ===
                public abstract class Shape {
                    public abstract double area();
                }
                // === FILE: Circle.java ===
                public class Circle extends Shape {
                    private final double radius;
                    public Circle(double radius) {
                        this.radius = radius;
                    }
                    @Override
                    public double area() {
                        return Math.PI * radius * radius;
                    }
                }
                // === FILE: CircleTest.java ===
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class CircleTest {
                    @Test
                    void testArea() {
                        Circle c = new Circle(2.0);
                        assertEquals(12.566, c.area(), 0.01);
                    }
                    @Test
                    void testAreaZero() {
                        Circle c = new Circle(0.0);
                        assertEquals(0.0, c.area(), 0.001);
                    }
                }
                """;

        OopConstraintRule shapeRule = OopConstraintRule.builder()
                .targetClassName("Shape")
                .expectedType(ExpectedClassType.ABSTRACT_CLASS)
                .ruleName("ABSTRACT_SHAPE")
                .methods(List.of(
                        OopMethodConstraint.builder()
                                .name("area")
                                .returnType("double")
                                .requiredModifiers(List.of("public", "abstract"))
                                .build()
                ))
                .build();
        Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(shapeRule)));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        // When: judging without socket scenarios
        strategy.executeJudge(message, problem, workDir);

        // Then: 40% OOP + 60% Unit Test. 100% OOP (40.0) + 100% Unit (60.0) = 100.0 (AC)
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.AC),
                isNull(),
                isNull(),
                eq(2),
                eq(2),
                isNull()
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(100.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPLETED");

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("totalScore").asDouble()).isEqualTo(100.0);
        assertThat(root.get("verdict").asText()).isEqualTo("AC");

        // Verify dynamic re-balanced weights: 0.40 for OOP, 0.60 for Unit Test, 0.0 for Socket
        assertThat(root.get("phases").get("oopInspection").get("weight").asDouble()).isEqualTo(0.40);
        assertThat(root.get("phases").get("oopInspection").get("weightedScore").asDouble()).isEqualTo(40.0);
        assertThat(root.get("phases").get("unitTest").get("weight").asDouble()).isEqualTo(0.60);
        assertThat(root.get("phases").get("unitTest").get("weightedScore").asDouble()).isEqualTo(60.0);
        assertThat(root.get("phases").get("socketIntegration").get("weight").asDouble()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Gherkin Scenario 4B: Dynamic re-balancing without socket (100% OOP + 50% Unit = 70.0 WA)")
    void scenario4B_DynamicRebalancing_PartialScore_Produces70WaVerdict(@TempDir Path workDir) throws Exception {
        Long submissionId = 4445L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 104L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(104L).title("Pure OOP Shape").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        String codeBundle = """
                // === FILE: Shape.java ===
                public abstract class Shape {
                    public abstract double area();
                }
                // === FILE: Circle.java ===
                public class Circle extends Shape {
                    private final double radius;
                    public Circle(double radius) {
                        this.radius = radius;
                    }
                    @Override
                    public double area() {
                        return radius; // Broken logic!
                    }
                }
                // === FILE: CircleTest.java ===
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class CircleTest {
                    @Test
                    void testRadius() {
                        Circle c = new Circle(2.0);
                        assertEquals(2.0, c.area(), 0.01); // Passes!
                    }
                    @Test
                    void testFormula() {
                        Circle c = new Circle(1.0);
                        assertEquals(Math.PI, c.area(), 0.001); // Fails! (1 of 2 passes = 50%)
                    }
                }
                """;

        OopConstraintRule shapeRule = OopConstraintRule.builder()
                .targetClassName("Shape")
                .expectedType(ExpectedClassType.ABSTRACT_CLASS)
                .ruleName("ABSTRACT_SHAPE")
                .build();
        Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(shapeRule)));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        // Then: 0.40 * 100.0 (OOP) + 0.60 * 50.0 (Unit) = 40.0 + 30.0 = 70.0 (WA)
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.WA),
                isNull(),
                isNull(),
                eq(1),
                eq(2),
                isNull()
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(70.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPLETED");

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("totalScore").asDouble()).isEqualTo(70.0);
        assertThat(root.get("verdict").asText()).isEqualTo("WA");
    }

    @Test
    @DisplayName("End-to-End Test 5: Full pass with Socket (100% OOP + 100% Unit + 100% Socket = 100.0 AC)")
    void scenario5_FullPassWithSocket_Produces100AcVerdict(@TempDir Path workDir) throws Exception {
        Long submissionId = 3333L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 105L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(105L).title("Echo Client").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        String codeBundle = """
                // === FILE: EchoClient.java ===
                import java.io.*;
                import java.net.*;

                public class EchoClient {
                    private final String host;
                    private final int port;

                    public EchoClient(String host, int port) {
                        this.host = host;
                        this.port = port;
                    }

                    public static void main(String[] args) throws Exception {
                        String h = args[0];
                        int p = Integer.parseInt(args[1]);
                        try (Socket s = new Socket(h, p);
                             BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                             PrintWriter w = new PrintWriter(s.getOutputStream(), true)) {
                            w.println("PING");
                            String res = r.readLine();
                        }
                    }
                }
                // === FILE: EchoTest.java ===
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class EchoTest {
                    @Test
                    void testConstruct() {
                        EchoClient c = new EchoClient("localhost", 1234);
                        assertNotNull(c);
                    }
                }
                """;

        OopConstraintRule oopRule = OopConstraintRule.builder()
                .targetClassName("EchoClient")
                .expectedType(ExpectedClassType.CLASS)
                .ruleName("CLASS_ECHO_CLIENT")
                .build();
        Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(oopRule)));

        SocketScenarioConfig socketConfig = SocketScenarioConfig.builder()
                .scenarioName("ECHO_HANDSHAKE")
                .mode(SocketAssessmentMode.CLIENT_ASSESSMENT)
                .targetClass("EchoClient")
                .steps(List.of(
                        SocketStep.builder().sendPayload("HELLO").expectedPayload("HELLO_ACK").receiveFirst(false).build()
                ))
                .build();
        Files.writeString(workDir.resolve("socket-scenarios.json"), objectMapper.writeValueAsString(List.of(socketConfig)));

        // Stage mock report from runner simulating full 100% pass across all 3 phases
        ExecutionReport mockReport = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .compilation(ExecutionReport.CompilationPhase.builder().passed(true).durationMs(200L).build())
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder().score(100.0).weight(0.30).weightedScore(30.0).build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder().score(100.0).weight(0.40).weightedScore(40.0).passedCount(1).totalCount(1).build())
                        .socketIntegration(ExecutionReport.SocketIntegrationPhase.builder().score(100.0).weight(0.30).weightedScore(30.0).build())
                        .build())
                .build();

        // Enrich through scoreCalculator to guarantee formula consistency
        mockReport = scoreCalculator.aggregateAndEnrich(mockReport, true);

        // When process launcher launches, simulate exit 0 with execution-report.json
        Path reportFile = workDir.resolve("execution-report.json");
        objectMapper.writeValue(reportFile.toFile(), mockReport);

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        // 30.0 + 40.0 + 30.0 = 100.0 (AC)
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.AC),
                isNull(),
                isNull(),
                eq(1),
                eq(1),
                isNull()
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(100.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPLETED");

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("totalScore").asDouble()).isEqualTo(100.0);
        assertThat(root.get("verdict").asText()).isEqualTo("AC");
        assertThat(root.get("phases").get("oopInspection").get("weight").asDouble()).isEqualTo(0.30);
        assertThat(root.get("phases").get("unitTest").get("weight").asDouble()).isEqualTo(0.40);
        assertThat(root.get("phases").get("socketIntegration").get("weight").asDouble()).isEqualTo(0.30);
    }
}
