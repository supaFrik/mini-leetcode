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
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopRuleResult;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Robustness, Security Boundary, and Database Persistence Stress Test Suite
 * for Slice 4 OOP Grinding Judging Pipeline.
 *
 * <p>Covers:
 * 1. Fail-Fast Compilation Boundary (CE, 0.0, COMPILATION_FAILED, zero Docker launches).
 * 2. Archive Security & Path Traversal Injection (Zip Slip ../../, null bytes \0, cleanup of temp files).
 * 3. Database Persistence Assertions (Direct JPA entity inspection of score, execution_phase, verdict, score_breakdown JSON).
 */
@ExtendWith(MockitoExtension.class)
public class OopGrindingPipelineRobustnessTest {

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
        sourceExtractor = new MultiFileSourceExtractor(objectMapper);
        compiler = new OopGrindingCompiler();
        inspector = new OopGrindingInspector(objectMapper);
        testRunner = new OopJUnit5TestRunner();
        socketHarness = new SocketIntegrationHarness(processLauncher);
        scoreCalculator = new OopScoreCalculator(objectMapper);

        // Real in-process fallback runner with real reflection, junit5 runner, socket harness, and calculator
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

    // =========================================================================
    // 1. FAIL-FAST COMPILATION BOUNDARY TESTS
    // =========================================================================

    @Test
    @DisplayName("Fail-Fast 1.1: Student code with syntax error aborts pipeline with CE, 0.0 score, COMPILATION_FAILED, 0 Docker launches")
    void failFast_SyntaxError_AbortsImmediatelyWithoutContainer(@TempDir Path workDir) throws Exception {
        Long submissionId = 8001L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 201L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(201L).title("Syntax Fail Test").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        String codeBundle = """
                // === FILE: SyntaxErrorClass.java ===
                public class SyntaxErrorClass {
                    int a = ; // Blatant syntax error
                }
                """;

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        // Execute judge pipeline
        strategy.executeJudge(message, problem, workDir);

        // 1. Verify submission service update verdict
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("error: illegal start of expression")
        );

        // 2. Verify submission repository persistence
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        // 3. Verify score_breakdown JSON contents
        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(root.get("executionPhase").asText()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);
        assertThat(root.get("totalScore").asDouble()).isEqualTo(0.0);
        assertThat(root.get("verdict").asText()).isEqualTo("CE");

        JsonNode compPhase = root.get("phases").get("compilation");
        assertThat(compPhase.get("passed").asBoolean()).isFalse();
        assertThat(compPhase.get("errors").asText()).contains("illegal start of expression");

        // 4. Verify ZERO Docker container launches
        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Fail-Fast 1.2: Student code with unresolved type reference aborts with CE, 0.0, and 0 Docker launches")
    void failFast_UnresolvedTypeReference_AbortsImmediatelyWithoutContainer(@TempDir Path workDir) throws Exception {
        Long submissionId = 8002L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 202L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(202L).title("Unresolved Type Test").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        String codeBundle = """
                // === FILE: MissingType.java ===
                public class MissingType {
                    private UnknownService service; // Type cannot be found
                }
                """;

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(0),
                contains("cannot find symbol")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("phases").get("compilation").get("passed").asBoolean()).isFalse();
        assertThat(root.get("phases").get("compilation").get("errors").asText()).contains("cannot find symbol");

        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Fail-Fast 1.3: Archive with zero Java files aborts with CE, COMPILATION_FAILED, and zero Docker launches")
    void failFast_ZeroJavaFilesArchive_AbortsImmediatelyWithoutContainer(@TempDir Path workDir) throws Exception {
        Long submissionId = 8003L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 203L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(203L).title("Non-Java Archive").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/8003/bundle.zip")
                .problem(problem)
                .build();

        byte[] nonJavaZip = createZipArchive(List.of(
                new TestFile("README.txt", "No java code here"),
                new TestFile("config.json", "{}")
        ));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/8003/bundle.zip"))
                .thenReturn(new ByteArrayInputStream(nonJavaZip));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(0),
                contains("No Java source files (.java) found")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    // =========================================================================
    // 2. ARCHIVE SECURITY & PATH TRAVERSAL INJECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Archive Security 2.1: Zip Slip with ../../ path traversal throws SecurityException, wipes temp dir, sets CE")
    void archiveSecurity_ZipSlipTraversal_CatchesViolationAndWipesTempDir(@TempDir Path workDir) throws Exception {
        Long submissionId = 8101L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 204L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(204L).title("Zip Slip Attack").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/8101/malicious.zip")
                .problem(problem)
                .build();

        byte[] evilZip = createZipArchive(List.of(
                new TestFile("Legit.java", "public class Legit {}"),
                new TestFile("../../evil.sh", "rm -rf /")
        ));

        Path parentDir = workDir.getParent();
        Path escapedEvilFile = parentDir.resolve("evil.sh");

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/8101/malicious.zip"))
                .thenReturn(new ByteArrayInputStream(evilZip));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        // Verify malicious file was NOT written outside workDir
        assertThat(escapedEvilFile).doesNotExist();

        // Verify any partial file in workDir was wiped
        assertThat(workDir.resolve("Legit.java")).doesNotExist();

        // Verify pipeline records CE and COMPILATION_FAILED
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(0),
                contains("Zip Slip")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("verdict").asText()).isEqualTo("CE");
        assertThat(root.get("phases").get("compilation").get("passed").asBoolean()).isFalse();
        assertThat(root.get("phases").get("compilation").get("errors").asText()).contains("Zip Slip");

        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Archive Security 2.2: Null byte '\\0' in ZIP entry name throws SecurityException, wipes temp dir, sets CE")
    void archiveSecurity_NullByteInEntryName_CatchesViolationAndWipesTempDir(@TempDir Path workDir) throws Exception {
        Long submissionId = 8102L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 205L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(205L).title("Null Byte Injection").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/8102/nullbyte.zip")
                .problem(problem)
                .build();

        byte[] nullByteZip = createZipArchive(List.of(
                new TestFile("PrecedingValid.java", "public class PrecedingValid {}"),
                new TestFile("evil.txt\0.java", "INJECTED_NULL_BYTE")
        ));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/8102/nullbyte.zip"))
                .thenReturn(new ByteArrayInputStream(nullByteZip));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        // Preceding files in the zip MUST be wiped
        assertThat(workDir.resolve("PrecedingValid.java")).doesNotExist();

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(0),
                contains("Illegal null byte")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("phases").get("compilation").get("errors").asText()).contains("Illegal null byte");

        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    @Test
    @DisplayName("Archive Security 2.3: Delimited bundle with ../../ traversal throws SecurityException, no leak, sets CE")
    void archiveSecurity_DelimitedBundlePathTraversal_CatchesViolation(@TempDir Path workDir) throws Exception {
        Long submissionId = 8103L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 206L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(206L).title("Delimited Traversal").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        String evilBundle = """
                // === FILE: safe/Safe.java ===
                package safe;
                public class Safe {}

                // === FILE: ../../evil.java ===
                public class Evil {}
                """;

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(evilBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        assertThat(workDir.getParent().resolve("evil.java")).doesNotExist();
        assertThat(workDir.resolve("safe/Safe.java")).doesNotExist();

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(0),
                contains("Zip Slip")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    // =========================================================================
    // 3. DATABASE PERSISTENCE ASSERTIONS
    // =========================================================================

    @Test
    @DisplayName("Database Persistence 3.1: Completed submission entity row directly inspected for non-null fields and valid breakdown JSON")
    void databasePersistence_CompletedSubmission_DirectEntityInspection(@TempDir Path workDir) throws Exception {
        Long submissionId = 8201L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 301L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(301L).title("Full 3-Phase Problem").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        String codeBundle = """
                // === FILE: Service.java ===
                public class Service {
                    private final String name;
                    public Service(String name) {
                        this.name = name;
                    }
                    public String getName() {
                        return name;
                    }
                }
                // === FILE: ServiceTest.java ===
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class ServiceTest {
                    @Test
                    void testName() {
                        Service s = new Service("demo");
                        assertEquals("demo", s.getName());
                    }
                }
                """;

        OopConstraintRule rule = OopConstraintRule.builder()
                .targetClassName("Service")
                .expectedType(ExpectedClassType.CLASS)
                .ruleName("CLASS_SERVICE")
                .fields(List.of(
                        OopFieldConstraint.builder().name("name").type("String").requiredModifiers(List.of("private", "final")).build()
                ))
                .build();
        Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(rule)));

        SocketScenarioConfig socketConfig = SocketScenarioConfig.builder()
                .scenarioName("SERVICE_SOCKET")
                .mode(SocketAssessmentMode.CLIENT_ASSESSMENT)
                .targetClass("Service")
                .role(SocketRole.CLIENT)
                .build();
        Files.writeString(workDir.resolve("socket-scenarios.json"), objectMapper.writeValueAsString(List.of(socketConfig)));

        // Stage mock execution report for in-process harness simulation
        ExecutionReport mockReport = ExecutionReport.builder()
                .submissionId(submissionId)
                .executionPhase("COMPLETED")
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .compilation(ExecutionReport.CompilationPhase.builder().passed(true).durationMs(150L).build())
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder()
                                .score(100.0).weight(0.30).weightedScore(30.0)
                                .rules(List.of(OopRuleResult.builder().ruleName("CLASS_SERVICE").passed(true).build()))
                                .build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder()
                                .score(100.0).weight(0.40).weightedScore(40.0).passedCount(1).totalCount(1)
                                .build())
                        .socketIntegration(ExecutionReport.SocketIntegrationPhase.builder()
                                .score(100.0).weight(0.30).weightedScore(30.0)
                                .scenarios(List.of(ExecutionReport.SocketScenarioResult.builder().scenarioName("SERVICE_SOCKET").passed(true).build()))
                                .build())
                        .build())
                .build();

        mockReport = scoreCalculator.aggregateAndEnrich(mockReport, true);
        Files.writeString(workDir.resolve("execution-report.json"), objectMapper.writeValueAsString(mockReport));

        when(processLauncher.launch(any())).thenReturn(process);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        // When
        strategy.executeJudge(message, problem, workDir);

        // DIRECT DATABASE PERSISTENCE ASSERTIONS
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        // 1. Assert non-null columns
        assertThat(saved.getScore()).isNotNull().isEqualTo(100.0);
        assertThat(saved.getExecutionPhase()).isNotNull().isEqualTo("COMPLETED");
        assertThat(saved.getScoreBreakdown()).isNotNull().isNotBlank();

        // 2. Parse score_breakdown JSON directly and assert all 4 phase sections
        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.hasNonNull("submissionId")).isTrue();
        assertThat(root.get("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(root.hasNonNull("executionPhase")).isTrue();
        assertThat(root.get("executionPhase").asText()).isEqualTo("COMPLETED");
        assertThat(root.hasNonNull("totalScore")).isTrue();
        assertThat(root.get("totalScore").asDouble()).isEqualTo(100.0);
        assertThat(root.hasNonNull("verdict")).isTrue();
        assertThat(root.get("verdict").asText()).isEqualTo("AC");

        JsonNode phases = root.get("phases");
        assertThat(phases).isNotNull();

        // Phase 1: Compilation
        JsonNode compNode = phases.get("compilation");
        assertThat(compNode).isNotNull();
        assertThat(compNode.get("passed").asBoolean()).isTrue();

        // Phase 2: OOP Inspection
        JsonNode oopNode = phases.get("oopInspection");
        assertThat(oopNode).isNotNull();
        assertThat(oopNode.get("score").asDouble()).isEqualTo(100.0);
        assertThat(oopNode.get("weight").asDouble()).isEqualTo(0.30);
        assertThat(oopNode.get("weightedScore").asDouble()).isEqualTo(30.0);
        assertThat(oopNode.get("rules")).isNotEmpty();

        // Phase 3: Unit Test
        JsonNode unitNode = phases.get("unitTest");
        assertThat(unitNode).isNotNull();
        assertThat(unitNode.get("score").asDouble()).isEqualTo(100.0);
        assertThat(unitNode.get("weight").asDouble()).isEqualTo(0.40);
        assertThat(unitNode.get("weightedScore").asDouble()).isEqualTo(40.0);
        assertThat(unitNode.get("passedCount").asInt()).isEqualTo(1);
        assertThat(unitNode.get("totalCount").asInt()).isEqualTo(1);

        // Phase 4: Socket Integration
        JsonNode socketNode = phases.get("socketIntegration");
        assertThat(socketNode).isNotNull();
        assertThat(socketNode.get("score").asDouble()).isEqualTo(100.0);
        assertThat(socketNode.get("weight").asDouble()).isEqualTo(0.30);
        assertThat(socketNode.get("weightedScore").asDouble()).isEqualTo(30.0);
        assertThat(socketNode.get("scenarios")).isNotEmpty();

        // Mathematical summation integrity check
        double expectedTotal = oopNode.get("weightedScore").asDouble()
                + unitNode.get("weightedScore").asDouble()
                + socketNode.get("weightedScore").asDouble();
        assertThat(root.get("totalScore").asDouble()).isEqualTo(expectedTotal);
    }

    @Test
    @DisplayName("Database Persistence 3.2: Partial score entity row directly inspected for phase isolation and 60.0 score")
    void databasePersistence_PartialScoringSocketCrash_DirectEntityInspection(@TempDir Path workDir) throws Exception {
        Long submissionId = 8202L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 302L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(302L).title("Partial Scoring Test").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        String codeBundle = """
                // === FILE: EchoServer.java ===
                public class EchoServer {
                    private final int port;
                    public EchoServer(int port) {
                        this.port = port;
                    }
                    public int getPort() {
                        return port;
                    }
                }
                // === FILE: ServerTest.java ===
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                public class ServerTest {
                    @Test
                    void testPort() {
                        EchoServer s = new EchoServer(8080);
                        assertEquals(8080, s.getPort());
                    }
                    @Test
                    void testPositive() {
                        EchoServer s = new EchoServer(8080);
                        assertTrue(s.getPort() > 0);
                    }
                    @Test
                    void testNegative() {
                        EchoServer s = new EchoServer(-1);
                        assertEquals(-1, s.getPort());
                    }
                    @Test
                    void testFailureCase() {
                        fail("Deliberate failure in 1 of 4 unit tests");
                    }
                }
                """;

        OopConstraintRule rule = OopConstraintRule.builder()
                .targetClassName("EchoServer")
                .expectedType(ExpectedClassType.CLASS)
                .ruleName("CLASS_ECHO_SERVER")
                .fields(List.of(
                        OopFieldConstraint.builder().name("port").type("int").requiredModifiers(List.of("private", "final")).build()
                ))
                .build();
        Files.writeString(workDir.resolve("rules.json"), objectMapper.writeValueAsString(List.of(rule)));

        // Socket scenario configured but server crashed -> 0% socket
        SocketScenarioConfig socketConfig = SocketScenarioConfig.builder()
                .scenarioName("SOCKET_TEST")
                .mode(SocketAssessmentMode.CLIENT_ASSESSMENT)
                .targetClass("EchoServer")
                .role(SocketRole.CLIENT)
                .build();
        Files.writeString(workDir.resolve("socket-scenarios.json"), objectMapper.writeValueAsString(List.of(socketConfig)));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(codeBundle);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        // Total score = 0.30 * 100 (OOP) + 0.40 * 75 (Unit 3/4) + 0.30 * 0 (Socket) = 30.0 + 30.0 + 0 = 60.0
        assertThat(saved.getScore()).isNotNull().isEqualTo(60.0);
        assertThat(saved.getExecutionPhase()).isNotNull().isEqualTo("COMPLETED");

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("totalScore").asDouble()).isEqualTo(60.0);
        assertThat(root.get("verdict").asText()).isEqualTo("WA");

        JsonNode phases = root.get("phases");
        assertThat(phases.get("oopInspection").get("weightedScore").asDouble()).isEqualTo(30.0);
        assertThat(phases.get("unitTest").get("weightedScore").asDouble()).isEqualTo(30.0);
        assertThat(phases.get("socketIntegration").get("weightedScore").asDouble()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Database Persistence 3.3: Compilation failed entity row directly inspected for 0.0 score, COMPILATION_FAILED, and errors")
    void databasePersistence_CompilationFailed_DirectEntityInspection(@TempDir Path workDir) throws Exception {
        Long submissionId = 8203L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 303L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(303L).title("Compilation Fail Inspection").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).problem(problem).build();

        String brokenCode = """
                // === FILE: BrokenSyntax.java ===
                public class BrokenSyntax {
                    invalid_keyword x;
                }
                """;

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn(brokenCode);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isNotNull().isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isNotNull().isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        JsonNode root = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(root.get("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(root.get("executionPhase").asText()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);
        assertThat(root.get("totalScore").asDouble()).isEqualTo(0.0);
        assertThat(root.get("verdict").asText()).isEqualTo("CE");

        JsonNode compPhase = root.get("phases").get("compilation");
        assertThat(compPhase.get("passed").asBoolean()).isFalse();
        assertThat(compPhase.get("errors").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Archive Security 2.4: Zip Bomb exceeding 20MB uncompressed limit throws SecurityException and aborts with CE")
    void archiveSecurity_ZipBombExceeding20MB_ThrowsSecurityExceptionAndAbortsWithCE(@TempDir Path workDir) throws Exception {
        Long submissionId = 8104L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 207L, 1L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(207L).title("Zip Bomb Test").problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/8104/bomb.zip")
                .problem(problem)
                .build();

        // 21MB uncompressed data
        byte[] bombContent = new byte[21 * 1024 * 1024];
        byte[] bombZip = createZipArchive(List.of(
                new TestFile("HugeClass.java", bombContent)
        ));

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/8104/bomb.zip"))
                .thenReturn(new ByteArrayInputStream(bombZip));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        // HugeClass.java MUST NOT remain on disk after abort
        assertThat(workDir.resolve("HugeClass.java")).doesNotExist();

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0), eq(0), eq(0), eq(0),
                contains("20MB")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo(OopGrindingJudgeStrategy.PHASE_COMPILATION_FAILED);

        verifyNoInteractions(processLauncher);
        verifyNoInteractions(dockerExecutor);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private record TestFile(String name, byte[] content) {
        TestFile(String name, String content) {
            this(name, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] createZipArchive(List<TestFile> files) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (TestFile file : files) {
                ZipEntry entry = new ZipEntry(file.name());
                zos.putNextEntry(entry);
                zos.write(file.content());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to construct test zip archive", e);
        }
        return baos.toByteArray();
    }
}
