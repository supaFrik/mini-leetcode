package jsd.toiyeujava.mini_leetcode.problem.authoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.exception.InvalidGradingConfigException;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.AuthoringResponse;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.CaseResult;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.CaseStatus;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.GradingValidationResponse;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.ReferenceRunRequest;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.ReferenceRunResponse;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.SourceFile;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemLifecycle;
import jsd.toiyeujava.mini_leetcode.submission.service.SourcePathValidator;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import jsd.toiyeujava.mini_leetcode.testcase.service.TestCaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Soạn bài console/stdin cho INSTRUCTOR/ADMIN: cấu hình chấm ({@code grading_config}), lời giải mẫu và
 * sinh output kỳ vọng cho testcase bằng cách chạy lời giải mẫu trong sandbox của judge.
 *
 * <p>Thay cho quy trình script cũ ({@code seed_grading.py}, {@code oop_console_cases.py}).
 */
@Slf4j
@Service
public class ProblemAuthoringService {

    /** Kích thước tối đa một stdin / output kỳ vọng đưa vào lần chạy lời giải mẫu. */
    static final int MAX_CASE_BYTES = 1024 * 1024;
    /** Tổng stdin tối đa của một lần chạy. */
    static final long MAX_TOTAL_INPUT_BYTES = 4L * 1024 * 1024;
    static final int MAX_CASES = 100;
    static final int MAX_GRADING_CONFIG_BYTES = 256 * 1024;

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseService testCaseService;
    private final JudgeAuthoringClient judge;
    private final ConsoleGatewayProperties limits;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProblemAuthoringService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                                   TestCaseService testCaseService, JudgeAuthoringClient judge,
                                   ConsoleGatewayProperties limits) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.testCaseService = testCaseService;
        this.judge = judge;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public AuthoringResponse get(Long problemId) {
        return toResponse(load(problemId));
    }

    /** Kiểm tra cấu hình bằng đúng model của judge; không lưu. */
    public GradingValidationResponse validateGradingConfig(String gradingConfig) {
        if (gradingConfig != null && gradingConfig.getBytes(StandardCharsets.UTF_8).length > MAX_GRADING_CONFIG_BYTES) {
            throw new PayloadTooLargeException("Grading configuration exceeds " + MAX_GRADING_CONFIG_BYTES / 1024 + " KB");
        }
        JudgeAuthoringClient.ValidationResult r = judge.validateGradingConfig(gradingConfig);
        return new GradingValidationResponse(r.valid(), r.errors() == null ? List.of() : r.errors(),
                r.ruleCount(), r.socketScenarioCount(), r.totalRuleWeight());
    }

    /** Lưu cấu hình chấm. Rỗng ⇒ xoá. Bài PUBLISHED bị khoá (đổi rule giữa kỳ chấm là không công bằng). */
    public AuthoringResponse updateGradingConfig(Long problemId, String gradingConfig) {
        Problem problem = load(problemId);
        if (ProblemLifecycle.isTestCaseFrozen(problem.getStatus())) {
            throw new StateConflictException("Grading configuration of a PUBLISHED problem is frozen");
        }
        String normalized = null;
        if (gradingConfig != null && !gradingConfig.isBlank()) {
            GradingValidationResponse validation = validateGradingConfig(gradingConfig);
            if (!validation.valid()) {
                throw new InvalidGradingConfigException(validation.errors());
            }
            normalized = gradingConfig.strip();
        }
        problem.setGradingConfig(normalized);
        return toResponse(problemRepository.save(problem));
    }

    /** Lưu lời giải mẫu (không bị khoá khi PUBLISHED vì không ảnh hưởng tới chấm). Rỗng ⇒ xoá. */
    public AuthoringResponse updateReferenceSolution(Long problemId, List<SourceFile> files) {
        Problem problem = load(problemId);
        List<SourceFile> validated = files == null || files.isEmpty() ? List.of() : validateFiles(files);
        try {
            problem.setReferenceSolution(validated.isEmpty() ? null : mapper.writeValueAsString(validated));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize reference solution", e);
        }
        return toResponse(problemRepository.save(problem));
    }

    /**
     * Chạy lời giải mẫu đã lưu. KHÔNG giữ transaction trong lúc chờ judge (có thể mất vài phút);
     * mỗi lần ghi output là một transaction riêng của {@link TestCaseService#replaceExpectedOutput}.
     */
    public ReferenceRunResponse runReference(Long problemId, ReferenceRunRequest request) {
        Problem problem = load(problemId);
        List<SourceFile> solution = parseFiles(problem.getReferenceSolution());
        if (solution.isEmpty()) {
            throw new IllegalArgumentException("Save a reference solution before running it");
        }
        boolean adHoc = request != null && request.inputs() != null && !request.inputs().isEmpty();
        boolean apply = request != null && Boolean.TRUE.equals(request.apply());
        if (adHoc && apply) {
            throw new IllegalArgumentException("'apply' only works with the problem's own test cases");
        }
        if (apply && ProblemLifecycle.isTestCaseFrozen(problem.getStatus())) {
            throw new StateConflictException("Test cases of a PUBLISHED problem are frozen and cannot be modified");
        }

        List<TestCase> cases = adHoc ? List.of() : selectCases(problemId, request == null ? null : request.testCaseIds());
        List<JudgeAuthoringClient.CaseInput> inputs = new ArrayList<>();
        long totalBytes = 0;
        if (adHoc) {
            if (request.inputs().size() > MAX_CASES) {
                throw new IllegalArgumentException("Too many inputs (max " + MAX_CASES + ")");
            }
            for (String input : request.inputs()) {
                String stdin = input == null ? "" : input;
                totalBytes += checkCaseSize(stdin);
                inputs.add(new JudgeAuthoringClient.CaseInput(stdin, null));
            }
        } else {
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("This problem has no test cases to run");
            }
            for (TestCase tc : cases) {
                String stdin = testCaseService.readFullContent(tc.getInputFileUri(), MAX_CASE_BYTES);
                if (stdin == null) {
                    throw new PayloadTooLargeException("Input of test case #" + tc.getOrderIndex()
                            + " is larger than " + MAX_CASE_BYTES / 1024 + " KB");
                }
                totalBytes += stdin.getBytes(StandardCharsets.UTF_8).length;
                // null ⇒ output kỳ vọng quá lớn để so: case vẫn chạy, trạng thái OK.
                String expected = testCaseService.readFullContent(tc.getExpectedOutputUri(), MAX_CASE_BYTES);
                inputs.add(new JudgeAuthoringClient.CaseInput(stdin, expected));
            }
        }
        if (totalBytes > MAX_TOTAL_INPUT_BYTES) {
            throw new PayloadTooLargeException("Total input exceeds " + MAX_TOTAL_INPUT_BYTES / (1024 * 1024) + " MB");
        }

        List<JudgeAuthoringClient.SourceFile> files = solution.stream()
                .map(f -> new JudgeAuthoringClient.SourceFile(f.path(), f.content())).toList();
        JudgeAuthoringClient.RunResult run = judge.runReference(files, inputs,
                problem.getTimeLimitMs(), problem.getMemoryLimitMb());

        List<JudgeAuthoringClient.CaseRun> runs = run.cases() == null ? List.of() : run.cases();
        List<CaseResult> results = new ArrayList<>(runs.size());
        int applied = 0;
        for (int i = 0; i < runs.size(); i++) {
            JudgeAuthoringClient.CaseRun r = runs.get(i);
            TestCase tc = adHoc ? null : cases.get(i);
            CaseStatus status = statusOf(r);
            boolean didApply = false;
            if (apply && status == CaseStatus.DIFF) {
                testCaseService.replaceExpectedOutput(problemId, tc.getId(), r.stdout() == null ? "" : r.stdout());
                didApply = true;
                applied++;
            }
            results.add(new CaseResult(tc == null ? null : tc.getId(), tc == null ? null : tc.getOrderIndex(),
                    tc == null ? null : tc.getIsHidden(), status, r.stdout(), r.stderr(), r.exitCode(),
                    r.runtimeMs(), didApply));
        }
        if (applied > 0) {
            log.info("Reference run updated {} expected outputs of problem {}", applied, problemId);
        }
        return new ReferenceRunResponse(run.compiled(), run.compileOutput(), run.mainClass(), run.error(),
                results, applied);
    }

    static CaseStatus statusOf(JudgeAuthoringClient.CaseRun r) {
        if (r.timedOut()) {
            return CaseStatus.TIME_LIMIT;
        }
        if (r.oomKilled()) {
            return CaseStatus.MEMORY_LIMIT;
        }
        if (r.truncated()) {
            return CaseStatus.OUTPUT_LIMIT;
        }
        if (r.exitCode() == null || r.exitCode() != 0) {
            return CaseStatus.RUNTIME_ERROR;
        }
        if (r.matchesExpected() == null) {
            return CaseStatus.OK;
        }
        return r.matchesExpected() ? CaseStatus.MATCH : CaseStatus.DIFF;
    }

    private List<TestCase> selectCases(Long problemId, List<Long> ids) {
        List<TestCase> all = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId);
        List<TestCase> selected = all;
        if (ids != null && !ids.isEmpty()) {
            Set<Long> wanted = new LinkedHashSet<>(ids);
            selected = all.stream().filter(tc -> wanted.contains(tc.getId())).toList();
            if (selected.size() != wanted.size()) {
                throw new ResourceNotFoundException("Some test cases do not belong to problem " + problemId);
            }
        }
        if (selected.size() > MAX_CASES) {
            throw new IllegalArgumentException("Too many test cases in one run (max " + MAX_CASES + ")");
        }
        return selected;
    }

    private static long checkCaseSize(String text) {
        long size = text.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_CASE_BYTES) {
            throw new PayloadTooLargeException("An input is larger than " + MAX_CASE_BYTES / 1024 + " KB");
        }
        return size;
    }

    private List<SourceFile> validateFiles(List<SourceFile> raw) {
        if (raw.size() > limits.getMaxFiles()) {
            throw new PayloadTooLargeException("Too many files (max " + limits.getMaxFiles() + ")");
        }
        long totalBytes = 0;
        boolean hasJava = false;
        Set<String> seen = new HashSet<>();
        List<SourceFile> files = new ArrayList<>(raw.size());
        for (SourceFile file : raw) {
            if (file == null) {
                throw new IllegalArgumentException("File entry must not be null");
            }
            String path = SourcePathValidator.sanitizeAndValidatePath(file.path());
            for (char c : path.toCharArray()) {
                if (";&|<>`$".indexOf(c) >= 0) {
                    throw new InvalidSubmissionPathException("Invalid character '" + c + "' in path: " + file.path());
                }
            }
            if (!seen.add(path.toLowerCase())) {
                throw new IllegalArgumentException("Duplicate file: " + path);
            }
            String content = file.content() == null ? "" : file.content();
            totalBytes += content.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > limits.getMaxSourceBytes()) {
                throw new PayloadTooLargeException("Reference solution exceeds "
                        + limits.getMaxSourceBytes() / 1024 + " KB");
            }
            hasJava |= path.toLowerCase().endsWith(".java");
            files.add(new SourceFile(path, content));
        }
        if (!hasJava) {
            throw new IllegalArgumentException("Reference solution needs at least one .java file");
        }
        return files;
    }

    private List<SourceFile> parseFiles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<SourceFile>>() {});
        } catch (JsonProcessingException e) {
            log.error("Stored reference solution is not valid JSON", e);
            return List.of();
        }
    }

    private Problem load(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));
    }

    private AuthoringResponse toResponse(Problem p) {
        return new AuthoringResponse(p.getId(), p.getStatus(), p.getProblemType(), p.getGradingConfig(),
                parseFiles(p.getReferenceSolution()), ProblemLifecycle.isTestCaseFrozen(p.getStatus()),
                p.getTimeLimitMs(), p.getMemoryLimitMb());
    }
}
