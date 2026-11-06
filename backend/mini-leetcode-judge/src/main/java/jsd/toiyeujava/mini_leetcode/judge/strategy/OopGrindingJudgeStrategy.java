package jsd.toiyeujava.mini_leetcode.judge.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.DockerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.OutputComparator;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.calculator.OopScoreCalculator;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopConstraintRule;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopExecutionMetrics;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.SocketScenarioConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopDockerRunner;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopMainClassDetector;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopProbeHarnessGenerator;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
public class OopGrindingJudgeStrategy implements JudgeStrategy {

    public static final String PHASE_COMPILATION_FAILED = "COMPILATION_FAILED";

    private final SubmissionService submissionService;
    private final StorageService storageService;
    private final MultiFileSourceExtractor sourceExtractor;
    private final OopGrindingCompiler compiler;
    private final ObjectMapper objectMapper;
    private final DockerExecutor dockerExecutor;
    private final OopDockerRunner oopDockerRunner;
    private final SubmissionRepository submissionRepository;
    private final OopScoreCalculator scoreCalculator;
    private final OopContainerExecutor oopContainerExecutor;
    private final OopProbeHarnessGenerator probeHarnessGenerator;

    /** Testcase stdin/stdout của bài (bảng test_cases); null trong unit test không cần tới. */
    @Autowired(required = false)
    private TestCaseRepository testCaseRepository;

    @Autowired
    public OopGrindingJudgeStrategy(
            SubmissionService submissionService,
            StorageService storageService,
            MultiFileSourceExtractor sourceExtractor,
            OopGrindingCompiler compiler,
            ObjectMapper objectMapper,
            DockerExecutor dockerExecutor,
            OopDockerRunner oopDockerRunner,
            SubmissionRepository submissionRepository,
            @Autowired(required = false) OopScoreCalculator scoreCalculator,
            @Autowired(required = false) OopContainerExecutor oopContainerExecutor,
            @Autowired(required = false) OopProbeHarnessGenerator probeHarnessGenerator) {
        this.submissionService = submissionService;
        this.storageService = storageService;
        this.sourceExtractor = sourceExtractor;
        this.compiler = compiler;
        this.objectMapper = objectMapper;
        this.dockerExecutor = dockerExecutor;
        this.oopDockerRunner = oopDockerRunner;
        this.submissionRepository = submissionRepository;
        this.scoreCalculator = scoreCalculator != null ? scoreCalculator : new OopScoreCalculator(this.objectMapper);
        this.oopContainerExecutor = oopContainerExecutor;
        this.probeHarnessGenerator = probeHarnessGenerator;
    }

    public OopGrindingJudgeStrategy(
            SubmissionService submissionService,
            StorageService storageService,
            MultiFileSourceExtractor sourceExtractor,
            OopGrindingCompiler compiler,
            ObjectMapper objectMapper,
            DockerExecutor dockerExecutor,
            OopDockerRunner oopDockerRunner,
            SubmissionRepository submissionRepository,
            OopScoreCalculator scoreCalculator) {
        this(submissionService, storageService, sourceExtractor, compiler, objectMapper, dockerExecutor,
                oopDockerRunner, submissionRepository, scoreCalculator, null, null);
    }

    public OopGrindingJudgeStrategy(
            SubmissionService submissionService,
            StorageService storageService,
            MultiFileSourceExtractor sourceExtractor,
            OopGrindingCompiler compiler,
            ObjectMapper objectMapper,
            DockerExecutor dockerExecutor,
            OopDockerRunner oopDockerRunner,
            SubmissionRepository submissionRepository) {
        this(submissionService, storageService, sourceExtractor, compiler, objectMapper, dockerExecutor,
                oopDockerRunner, submissionRepository, null, null, null);
    }

    public OopGrindingJudgeStrategy(
            SubmissionService submissionService,
            StorageService storageService,
            MultiFileSourceExtractor sourceExtractor,
            OopGrindingCompiler compiler,
            ObjectMapper objectMapper,
            DockerExecutor dockerExecutor,
            SubmissionRepository submissionRepository) {
        this(submissionService, storageService, sourceExtractor, compiler, objectMapper, dockerExecutor, null,
                submissionRepository, null, null, null);
    }

    @Override
    public boolean supports(ProblemType problemType) {
        return problemType == ProblemType.OOP_GRINDING;
    }

    @Override
    public void executeJudge(SubmissionTaskMessage message, Problem problem, Path workDir) throws Exception {
        Long submissionId = message.submissionId();

        if (message.language() != SubmissionLanguage.JAVA) {
            submissionService.failSubmission(submissionId, "OOP Grinding only supports Java, received: " + message.language());
            return;
        }

        Submission submission = submissionService.getSubmissionById(submissionId);

        // 1. Multi-File Source Extraction
        List<Path> extractedSources;
        try {
            if (submission != null && submission.getCodeStorageUri() != null && submission.getCodeStorageUri().endsWith(".zip")) {
                try (InputStream is = storageService.downloadFileByUri(submission.getCodeStorageUri())) {
                    if (is == null) {
                        throw new IllegalStateException("Storage returned null stream for URI: " + submission.getCodeStorageUri());
                    }
                    extractedSources = sourceExtractor.extract(is, workDir);
                }
            } else {
                String code = submissionService.getSubmissionCode(submissionId);
                extractedSources = sourceExtractor.extract(code, workDir);
            }
        } catch (SecurityException | IllegalArgumentException | IllegalStateException | IOException e) {
            log.warn("Source extraction failed for submission {}: {}", submissionId, e.getMessage());
            String breakdown = buildExtractionFailureJson(submissionId, e.getMessage());
            recordFailureVerdict(submissionId, breakdown, "Source Extraction Error: " + e.getMessage());
            return;
        }

        // 2. Validate extracted Java files
        List<Path> javaFiles = extractedSources.stream()
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .toList();
        if (javaFiles.isEmpty()) {
            log.warn("No Java source files found for submission {}", submissionId);
            String errorMsg = "No Java source files (.java) found in submission archive";
            String breakdown = buildExtractionFailureJson(submissionId, errorMsg);
            recordFailureVerdict(submissionId, breakdown, errorMsg);
            return;
        }

        // 3. Phase 1: Compiler Fail-Fast (Without Starting Docker Container)
        CompileResult compileResult = compiler.compile(workDir, javaFiles);
        if (!compileResult.success()) {
            log.info("Fail-Fast: Submission {} failed compilation (CE), halting pipeline without Docker", submissionId);
            String breakdown = buildCompilationFailureJson(submissionId, compileResult.output());
            recordFailureVerdict(submissionId, breakdown, compileResult.output());
            return;
        }

        // Phase 1 passed successfully!
        log.info("Phase 1 compilation succeeded for submission {}.", submissionId);

        if (oopDockerRunner == null) {
            log.info("Ready for Docker Execution (Slice 2).");
            return;
        }

        // 4. Cấu hình chấm CHỈ lấy từ server: problems.grading_config (rule cấu trúc, kịch bản socket)
        //    + bảng test_cases (stdin/stdout). Mọi file .json / lớp *Test trong bài nộp bị bỏ qua,
        //    để học viên không thể tự viết cách chấm cho chính mình.
        HarnessConfig harnessConfig = buildServerHarnessConfig(submissionId, problem);
        List<TestCase> testCases = testCaseRepository == null || problem == null ? List.of()
                : testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId());
        if (!harnessConfig.getOopInspection().isEnabled() && !harnessConfig.getSocketIntegration().isEnabled()
                && testCases.isEmpty()) {
            submissionService.failSubmission(submissionId,
                    "This OOP problem has no grading configuration yet (no rules, socket scenarios or test cases)");
            return;
        }
        harnessConfig.getUnitTest().setEnabled(!testCases.isEmpty());

        // Phase 2: rule cấu trúc bằng reflection (Class.forName(.., false, ..) — không chạy code học viên)
        // + kịch bản socket nếu bài có cấu hình.
        ExecutionReport executionReport = oopDockerRunner.runInProcessHarness(submissionId, workDir, harnessConfig);
        // Phase 3: testcase stdin/stdout — chạy main của học viên trong Docker sandbox, so output như bài thuật toán.
        if (!testCases.isEmpty()) {
            ExecutionReport.UnitTestPhase ioPhase = runTestCases(workDir, problem, testCases);
            if (ioPhase == null) {
                submissionService.failSubmission(submissionId, "Docker sandbox is unavailable, cannot run test cases");
                return;
            }
            executionReport.getPhases().setUnitTest(ioPhase);
        }
        executionReport = scoreCalculator.aggregateAndEnrich(executionReport, harnessConfig);

        // 5. Đo số liệu thật (wall-clock + peak memory) trong 1 container ephemeral.
        //    Bắt buộc phải chạy trong container thì mới đọc được cgroup memory.peak
        //    và có wall-clock thật; nếu không đo được thì ghi null (UI hiển thị "—").
        OopExecutionMetrics metrics = measureExecutionMetrics(submissionId, workDir);

        // 6. Update submission results
        recordExecutionResults(submissionId, executionReport, harnessConfig, metrics);
    }

    /**
     * Chạy 1 container ephemeral để đo wall-clock và peak memory của mã nguồn OOP.
     *
     * <p>Trả về {@link OopExecutionMetrics#unavailable(String)} nếu không đo được
     * (thiếu Docker, không sinh được probe...). Khi đó số đo được ghi là {@code null}
     * chứ không phải {@code 0} — vì {@code 0} bị UI hiển thị thành "0 KB" gây hiểu nhầm.
     */
    private OopExecutionMetrics measureExecutionMetrics(Long submissionId, Path workDir) {
        if (oopContainerExecutor == null || probeHarnessGenerator == null) {
            log.info("OOP metrics executor is not configured for submission {}; metrics will be null", submissionId);
            return OopExecutionMetrics.unavailable("Metrics executor not configured");
        }
        try {
            String mainClass = OopMainClassDetector.detectMainClass(workDir.resolve("classes")).orElse(null);
            if (!probeHarnessGenerator.generateAndCompile(workDir, mainClass)) {
                return OopExecutionMetrics.unavailable("Failed to compile metrics probe");
            }
            OopExecutionMetrics metrics = oopContainerExecutor.measure(
                    workDir,
                    OopProbeHarnessGenerator.PROBE_CLASS_NAME,
                    "",
                    10_000L,
                    256);
            if (metrics.measured()) {
                log.info("OOP metrics for submission {}: runtimeMs={}, peakMemoryKb={}, exitCode={}",
                        submissionId, metrics.runtimeMs(), metrics.peakMemoryKb(), metrics.exitCode());
            } else {
                log.warn("OOP metrics unavailable for submission {}: {}", submissionId, metrics.errorMessage());
            }
            return metrics;
        } catch (Exception e) {
            log.warn("Failed to measure OOP execution metrics for submission {}: {}", submissionId, e.getMessage());
            return OopExecutionMetrics.unavailable("Failed to measure metrics: " + e.getMessage());
        }
    }

    /**
     * Cấu hình chấm từ {@code problems.grading_config}. KHÔNG đọc bất kỳ file nào trong bài nộp.
     * Phase không có cấu hình thì bị tắt (trọng số 0) thay vì tự được 100 điểm.
     */
    private HarnessConfig buildServerHarnessConfig(Long submissionId, Problem problem) {
        List<OopConstraintRule> rules = new ArrayList<>();
        List<SocketScenarioConfig> socketScenarios = new ArrayList<>();
        String raw = problem != null ? problem.getGradingConfig() : null;
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(raw);
                if (root.has("rules")) {
                    rules = objectMapper.convertValue(root.get("rules"), new TypeReference<List<OopConstraintRule>>() {});
                }
                if (root.has("socketScenarios")) {
                    socketScenarios = objectMapper.convertValue(root.get("socketScenarios"),
                            new TypeReference<List<SocketScenarioConfig>>() {});
                }
            } catch (Exception e) {
                log.error("Invalid grading_config for problem {}; treating it as empty", problem.getId(), e);
            }
        }
        boolean hasSocket = !socketScenarios.isEmpty();
        return HarnessConfig.builder()
                .submissionId(submissionId)
                .problemId(problem != null ? problem.getId() : null)
                .studentClassesDir("/workspace/classes")
                .testClassesDir("/workspace/classes")
                .outputReportPath("/workspace/execution-report.json")
                .timeoutBudget(HarnessConfig.TimeoutBudget.builder()
                        .containerTimeoutSeconds(30)
                        .unitTestTimeoutSeconds(2)
                        .socketScenarioTimeoutSeconds(3)
                        .build())
                .oopInspection(HarnessConfig.OopInspectionConfig.builder()
                        .enabled(!rules.isEmpty())
                        .weight(hasSocket ? 0.30 : 0.40)
                        .rules(rules)
                        .build())
                // "unitTest" chứa kết quả testcase stdin/stdout (bật khi bài có test_cases).
                .unitTest(HarnessConfig.UnitTestConfig.builder()
                        .enabled(false)
                        .weight(hasSocket ? 0.40 : 0.60)
                        .testClassNames(List.of())
                        .timeoutSecondsPerTest(2)
                        .build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                        .enabled(hasSocket)
                        .weight(hasSocket ? 0.30 : 0.0)
                        .scenarios(socketScenarios)
                        .build())
                .build();
    }

    /**
     * Chạy {@code main} của học viên với stdin của từng testcase trong container sandbox và so stdout.
     *
     * @return kết quả phase, hoặc {@code null} nếu không có sandbox (không bao giờ chạy in-process)
     */
    private ExecutionReport.UnitTestPhase runTestCases(Path workDir, Problem problem, List<TestCase> testCases)
            throws IOException {
        if (oopContainerExecutor == null) {
            return null;
        }
        Optional<String> mainClass = OopMainClassDetector.detectMainClass(workDir.resolve("classes"));
        List<ExecutionReport.UnitTestFailure> failures = new ArrayList<>();
        int passed = 0;
        int index = 0;
        for (TestCase testCase : testCases) {
            index++;
            String label = "Test case #" + index + (Boolean.TRUE.equals(testCase.getIsHidden()) ? " (hidden)" : "");
            String error;
            if (mainClass.isEmpty()) {
                error = "No 'public static void main(String[] args)' found";
            } else {
                OopContainerExecutor.SandboxResult result = oopContainerExecutor.runClass(workDir, mainClass.get(),
                        List.of(), readText(testCase.getInputFileUri()), problem.getTimeLimitMs(), problem.getMemoryLimitMb());
                if (!result.measured()) {
                    log.warn("Sandbox unavailable while running {}: {}", label, result.errorMessage());
                    return null;
                }
                if (result.timedOut()) {
                    error = "Time limit exceeded";
                } else if (result.oomKilled()) {
                    error = "Memory limit exceeded";
                } else if (result.exitCode() != null && result.exitCode() != 0) {
                    error = "Runtime error (exit code " + result.exitCode() + ")";
                } else if (!OutputComparator.matches(result.stdout(), readText(testCase.getExpectedOutputUri()))) {
                    error = "Wrong answer";
                } else {
                    error = null;
                }
            }
            if (error == null) {
                passed++;
            } else {
                failures.add(ExecutionReport.UnitTestFailure.builder()
                        .testClass("stdin/stdout").testMethod(label).error(error).build());
            }
        }
        return ExecutionReport.UnitTestPhase.builder()
                .score(Math.round(passed * 1000.0 / testCases.size()) / 10.0)
                .weight(0.0)
                .weightedScore(0.0)
                .passedCount(passed)
                .totalCount(testCases.size())
                .failures(failures)
                .build();
    }

    private String readText(String uri) throws IOException {
        try (InputStream in = storageService.downloadFileByUri(uri)) {
            if (in == null) {
                throw new IOException("Missing test case file in storage: " + uri);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void recordExecutionResults(Long submissionId, ExecutionReport report, HarnessConfig config,
                                        OopExecutionMetrics metrics) {
        if (report != null && scoreCalculator != null) {
            if (report.getTotalScore() == null) {
                report = scoreCalculator.aggregateAndEnrich(report, config);
            }
        }

        Double totalScore = report != null && report.getTotalScore() != null ? report.getTotalScore() : 0.0;
        String executionPhase = report != null && report.getExecutionPhase() != null ? report.getExecutionPhase() : "COMPLETED";
        String verdictStr = report != null && report.getVerdict() != null ? report.getVerdict() : (totalScore >= 100.0 ? "AC" : "WA");
        SubmissionVerdict verdict = scoreCalculator.mapVerdict(verdictStr);

        int passedCount = 0;
        int totalCount = 0;
        if (report != null && report.getPhases() != null && report.getPhases().getUnitTest() != null) {
            passedCount = report.getPhases().getUnitTest().getPassedCount();
            totalCount = report.getPhases().getUnitTest().getTotalCount();
        }

        // Số đo thật: null khi không đo được (UI hiển thị "—"), không phải 0.
        Integer runtimeMs = metrics != null ? metrics.runtimeMs() : null;
        Integer peakMemoryKb = metrics != null ? metrics.peakMemoryKb() : null;
        if (report != null) {
            report.setRuntimeMs(runtimeMs);
            report.setPeakMemoryKb(peakMemoryKb);
        }

        String scoreBreakdownJson = scoreCalculator.formatScoreBreakdownJson(report);

        submissionService.updateSubmissionVerdict(
                submissionId,
                SubmissionStatus.COMPLETED,
                verdict,
                runtimeMs,
                peakMemoryKb,
                passedCount,
                totalCount,
                report != null ? report.getErrorMessage() : null
        );

        if (submissionRepository != null) {
            submissionRepository.findById(submissionId).ifPresent(sub -> {
                sub.setScore(totalScore);
                sub.setExecutionPhase(executionPhase);
                sub.setScoreBreakdown(scoreBreakdownJson);
                submissionRepository.save(sub);
                try {
                    submissionRepository.saveAndFlush(sub);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private SubmissionVerdict mapVerdict(String verdictStr) {
        if (verdictStr == null) return SubmissionVerdict.WA;
        return switch (verdictStr.toUpperCase(Locale.ROOT)) {
            case "AC" -> SubmissionVerdict.AC;
            case "CE" -> SubmissionVerdict.CE;
            case "RE" -> SubmissionVerdict.RE;
            case "TLE" -> SubmissionVerdict.TLE;
            case "MLE" -> SubmissionVerdict.MLE;
            default -> SubmissionVerdict.WA;
        };
    }

    private void recordFailureVerdict(Long submissionId, String breakdown, String errorMessage) {
        // CE (lỗi biên dịch / lỗi trích xuất): KHÔNG có số đo thực thi nào để báo,
        // nên ghi null cho cả hai cột — UI hiển thị "Runtime: — ms | Peak memory: —".
        submissionService.updateSubmissionVerdict(
                submissionId,
                SubmissionStatus.COMPLETED,
                SubmissionVerdict.CE,
                null,
                null,
                0,
                0,
                errorMessage
        );
        if (submissionRepository != null) {
            submissionRepository.findById(submissionId).ifPresent(sub -> {
                sub.setScore(0.0);
                sub.setExecutionPhase(PHASE_COMPILATION_FAILED);
                sub.setScoreBreakdown(breakdown);
                submissionRepository.save(sub);
                try {
                    submissionRepository.saveAndFlush(sub);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private String buildCompilationFailureJson(Long submissionId, String errors) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("submissionId", submissionId);
            root.put("executionPhase", PHASE_COMPILATION_FAILED);
            root.put("totalScore", 0.0);
            root.put("verdict", "CE");

            Map<String, Object> phases = new LinkedHashMap<>();
            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("passed", false);
            comp.put("errors", errors);
            phases.put("compilation", comp);

            root.put("phases", phases);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize compilation failure breakdown", e);
            return "{\"verdict\":\"CE\",\"totalScore\":0.0,\"phases\":{\"compilation\":{\"passed\":false}}}";
        }
    }

    private String buildExtractionFailureJson(Long submissionId, String errors) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("submissionId", submissionId);
            root.put("executionPhase", PHASE_COMPILATION_FAILED);
            root.put("totalScore", 0.0);
            root.put("verdict", "CE");

            Map<String, Object> phases = new LinkedHashMap<>();
            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("passed", false);
            comp.put("errors", "Extraction Error: " + errors);
            phases.put("compilation", comp);

            root.put("phases", phases);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"verdict\":\"CE\",\"totalScore\":0.0}";
        }
    }
}
