package jsd.toiyeujava.mini_leetcode.judge.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.DockerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.calculator.OopScoreCalculator;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.HarnessConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopConstraintRule;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.SocketScenarioConfig;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopDockerRunner;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

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
            @Autowired(required = false) OopScoreCalculator scoreCalculator) {
        this.submissionService = submissionService;
        this.storageService = storageService;
        this.sourceExtractor = sourceExtractor;
        this.compiler = compiler;
        this.objectMapper = objectMapper;
        this.dockerExecutor = dockerExecutor;
        this.oopDockerRunner = oopDockerRunner;
        this.submissionRepository = submissionRepository;
        this.scoreCalculator = scoreCalculator != null ? scoreCalculator : new OopScoreCalculator(this.objectMapper);
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
        this(submissionService, storageService, sourceExtractor, compiler, objectMapper, dockerExecutor, oopDockerRunner, submissionRepository, null);
    }

    public OopGrindingJudgeStrategy(
            SubmissionService submissionService,
            StorageService storageService,
            MultiFileSourceExtractor sourceExtractor,
            OopGrindingCompiler compiler,
            ObjectMapper objectMapper,
            DockerExecutor dockerExecutor,
            SubmissionRepository submissionRepository) {
        this(submissionService, storageService, sourceExtractor, compiler, objectMapper, dockerExecutor, null, submissionRepository, null);
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

        // 4. Phase 2, 3 & 4: OOP Grinding Harness Execution (Docker Runner / In-Process Fallback)
        HarnessConfig harnessConfig = loadOrCreateHarnessConfig(submissionId, problem, workDir);

        if (Boolean.TRUE.equals(message.isQuickRun())) {
            executeQuickRun(submissionId, workDir, harnessConfig);
            return;
        }
        ExecutionReport executionReport = null;
        try {
            executionReport = oopDockerRunner.runHarness(submissionId, workDir, harnessConfig, 30_000L);
        } catch (Exception e) {
            log.warn("runHarness threw exception for submission {}, attempting fallback", submissionId, e);
        }

        if (executionReport == null ||
                (executionReport.getErrorMessage() != null &&
                        (executionReport.getErrorMessage().startsWith("Docker execution failed:")
                                || executionReport.getErrorMessage().contains("Unable to find image")
                                || executionReport.getErrorMessage().contains("Container exited with code 125")))) {
            log.info("Docker daemon or runner image unavailable, running in-process harness for submission {}", submissionId);
            executionReport = oopDockerRunner.runInProcessHarness(submissionId, workDir, harnessConfig);
        }

        // 5. Update submission results
        recordExecutionResults(submissionId, executionReport, harnessConfig);
    }

    private void executeQuickRun(Long submissionId, Path workDir, HarnessConfig config) {
        long runStartTime = System.currentTimeMillis();
        Path classesDir = workDir.resolve("classes");
        String stdout = "";
        String stderr = "";
        int exitCode = 0;

        Path mainClassFile = classesDir.resolve("Main.class");
        if (Files.exists(mainClassFile)) {
            try {
                ProcessBuilder pb = new ProcessBuilder("java", "-cp", classesDir.toAbsolutePath().toString(), "Main");
                pb.directory(classesDir.toFile());
                Process process = pb.start();

                boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    stderr = "Execution timed out after 5 seconds.";
                    exitCode = 124;
                } else {
                    exitCode = process.exitValue();
                    stdout = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    stderr = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.warn("Quick Run error for submission {}", submissionId, e);
                stderr = "Execution error: " + e.getMessage();
                exitCode = 1;
            }
        } else {
            stdout = "Compiled successfully.\n(Note: Add a Main class with a main method to execute custom code on Run)";
        }

        ExecutionReport report = (oopDockerRunner != null)
                ? oopDockerRunner.runInProcessHarness(submissionId, workDir, config)
                : new ExecutionReport();
        if (report == null) {
            report = new ExecutionReport();
        }
        report.setSubmissionId(submissionId);
        report.setExecutionPhase("QUICK_RUN");

        long elapsedMs = Math.max(15L, System.currentTimeMillis() - runStartTime);
        long memUsedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int memKb = (int) Math.max(12288L, memUsedBytes / 1024L);
        report.setExecutionTimeMs((int) elapsedMs);
        report.setMemoryUsedKb(memKb);

        String outputCombined = stdout;
        if (stderr != null && !stderr.isBlank()) {
            outputCombined = (outputCombined.isBlank() ? "" : outputCombined + "\n") + "--- stderr ---\n" + stderr;
        }
        report.setRawOutput(outputCombined);
        if (exitCode != 0) {
            report.setVerdict("RE");
            report.setErrorMessage(stderr);
        } else {
            report.setVerdict("AC");
        }

        recordExecutionResults(submissionId, report, config);
    }

    private HarnessConfig loadOrCreateHarnessConfig(Long submissionId, Problem problem, Path workDir) {
        Path harnessConfigPath = workDir.resolve("harness-config.json");
        if (Files.exists(harnessConfigPath)) {
            try {
                HarnessConfig parsed = objectMapper.readValue(harnessConfigPath.toFile(), HarnessConfig.class);
                boolean hasSocket = parsed.getSocketIntegration() != null
                        && parsed.getSocketIntegration().isEnabled()
                        && parsed.getSocketIntegration().getScenarios() != null
                        && !parsed.getSocketIntegration().getScenarios().isEmpty();
                if (hasSocket && parsed.getSocketIntegration().getWeight() == 0.0) {
                    parsed.getSocketIntegration().setWeight(0.30);
                    if (parsed.getOopInspection() != null) parsed.getOopInspection().setWeight(0.30);
                    if (parsed.getUnitTest() != null) parsed.getUnitTest().setWeight(0.40);
                }
                return parsed;
            } catch (Exception e) {
                log.warn("Failed to parse existing harness-config.json in {}", workDir, e);
            }
        }

        List<OopConstraintRule> rules = new ArrayList<>();
        Path rulesPath = workDir.resolve("rules.json");
        if (Files.exists(rulesPath)) {
            try {
                String rulesJson = Files.readString(rulesPath);
                rules = objectMapper.readValue(rulesJson, new TypeReference<List<OopConstraintRule>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse rules.json in {}", workDir, e);
            }
        }

        List<String> testClasses = new ArrayList<>();
        Path classesDir = workDir.resolve("classes");
        if (Files.exists(classesDir)) {
            try (Stream<Path> stream = Files.walk(classesDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith("Test.class"))
                        .forEach(p -> {
                            Path rel = classesDir.relativize(p);
                            String fqcn = rel.toString()
                                    .replace('/', '.')
                                    .replace('\\', '.')
                                    .replaceAll("\\.class$", "");
                            testClasses.add(fqcn);
                        });
            } catch (Exception e) {
                log.warn("Failed to discover test classes in {}", classesDir, e);
            }
        }

        List<SocketScenarioConfig> socketScenarios = new ArrayList<>();
        Path socketScenariosPath = workDir.resolve("socket-scenarios.json");
        if (!Files.exists(socketScenariosPath)) {
            socketScenariosPath = workDir.resolve("scenarios.json");
        }
        if (Files.exists(socketScenariosPath)) {
            try {
                String scenariosJson = Files.readString(socketScenariosPath);
                socketScenarios = objectMapper.readValue(scenariosJson, new TypeReference<List<SocketScenarioConfig>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse socket scenarios in {}", socketScenariosPath, e);
            }
        }

        boolean hasSocket = !socketScenarios.isEmpty();
        double oopWeight = hasSocket ? 0.30 : 0.40;
        double unitWeight = hasSocket ? 0.40 : 0.60;
        double socketWeight = hasSocket ? 0.30 : 0.0;

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
                        .enabled(true)
                        .weight(oopWeight)
                        .rules(rules)
                        .build())
                .unitTest(HarnessConfig.UnitTestConfig.builder()
                        .enabled(true)
                        .weight(unitWeight)
                        .testClassNames(testClasses)
                        .timeoutSecondsPerTest(2)
                        .build())
                .socketIntegration(HarnessConfig.SocketIntegrationConfig.builder()
                        .enabled(hasSocket)
                        .weight(socketWeight)
                        .scenarios(socketScenarios)
                        .build())
                .build();
    }

    private void recordExecutionResults(Long submissionId, ExecutionReport report, HarnessConfig config) {
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

        String scoreBreakdownJson = scoreCalculator.formatScoreBreakdownJson(report);

        int timeMs = (report != null && report.getExecutionTimeMs() != null) ? report.getExecutionTimeMs() : 35;
        int memKb = (report != null && report.getMemoryUsedKb() != null) ? report.getMemoryUsedKb() : 16384;

        submissionService.updateSubmissionVerdict(
                submissionId,
                SubmissionStatus.COMPLETED,
                verdict,
                timeMs,
                memKb,
                passedCount,
                totalCount,
                report != null ? report.getErrorMessage() : null
        );

        if (submissionRepository != null) {
            submissionRepository.findById(submissionId).ifPresent(sub -> {
                sub.setScore(totalScore);
                sub.setExecutionPhase(executionPhase);
                sub.setScoreBreakdown(scoreBreakdownJson);
                sub.setExecutionTimeMs(timeMs);
                sub.setMemoryUsedKb(memKb);
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
        submissionService.updateSubmissionVerdict(
                submissionId,
                SubmissionStatus.COMPLETED,
                SubmissionVerdict.CE,
                25,
                0,
                0,
                0,
                errorMessage
        );
        if (submissionRepository != null) {
            submissionRepository.findById(submissionId).ifPresent(sub -> {
                sub.setScore(0.0);
                sub.setExecutionPhase(PHASE_COMPILATION_FAILED);
                sub.setScoreBreakdown(breakdown);
                sub.setExecutionTimeMs(25);
                sub.setMemoryUsedKb(0);
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
