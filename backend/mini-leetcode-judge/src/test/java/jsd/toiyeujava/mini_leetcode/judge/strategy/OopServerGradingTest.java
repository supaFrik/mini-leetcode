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
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopDockerRunner;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopJUnit5TestRunner;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.socket.SocketIntegrationHarness;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chấm OOP bằng cấu hình phía server: rule cấu trúc (compiler + reflection thật) và testcase stdin/stdout
 * (sandbox Docker được mock). Không có gì trong bài nộp được dùng làm cấu hình chấm.
 */
class OopServerGradingTest {

    private static final long SUBMISSION_ID = 4242L;
    private static final String RULES = "{\"rules\":["
            + "{\"ruleName\":\"ACCOUNT_ABSTRACT\",\"targetClassName\":\"Account\",\"expectedType\":\"ABSTRACT_CLASS\"},"
            + "{\"ruleName\":\"SAVINGS_EXTENDS\",\"targetClassName\":\"Savings\",\"expectedSuperclass\":\"Account\"}]}";
    private static final String GOOD = """
            // === FILE: Account.java ===
            public abstract class Account { public abstract long fee(); }
            // === FILE: Savings.java ===
            public class Savings extends Account { public long fee() { return 0; } }
            // === FILE: Main.java ===
            public class Main { public static void main(String[] a) { System.out.println(new Savings().fee()); } }
            """;
    private static final String NOT_ABSTRACT = GOOD.replace("public abstract class Account { public abstract long fee(); }",
            "public class Account { public long fee() { return 1; } }");

    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final StorageService storageService = mock(StorageService.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final OopContainerExecutor container = mock(OopContainerExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OopGrindingJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        OopScoreCalculator calculator = new OopScoreCalculator(objectMapper);
        OopDockerRunner runner = new OopDockerRunner(pb -> {
            throw new AssertionError("Docker harness must not be used");
        }, objectMapper, new OopGrindingInspector(objectMapper), new OopJUnit5TestRunner(),
                new SocketIntegrationHarness(), calculator);
        strategy = new OopGrindingJudgeStrategy(submissionService, storageService, new MultiFileSourceExtractor(),
                new OopGrindingCompiler(), objectMapper, mock(DockerExecutor.class), runner, submissionRepository,
                calculator, container, null);
        ReflectionTestUtils.setField(strategy, "testCaseRepository", testCaseRepository);
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of());
    }

    private Submission judge(String code, String gradingConfig, Path workDir) throws Exception {
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING)
                .timeLimitMs(2000).memoryLimitMb(256).gradingConfig(gradingConfig).build();
        Submission submission = Submission.builder().id(SUBMISSION_ID).build();
        when(submissionService.getSubmissionById(SUBMISSION_ID)).thenReturn(submission);
        when(submissionService.getSubmissionCode(SUBMISSION_ID)).thenReturn(code);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        strategy.executeJudge(new SubmissionTaskMessage(SUBMISSION_ID, 1L, 1L, SubmissionLanguage.JAVA), problem, workDir);
        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    /** Hai testcase; sandbox trả đúng output cho case 1, sai cho case 2 (case 2 là testcase ẩn). */
    private void twoCasesHalfPassing() {
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                TestCase.builder().inputFileUri("in1").expectedOutputUri("out1").isHidden(false).build(),
                TestCase.builder().inputFileUri("in2").expectedOutputUri("out2").isHidden(true).build()));
        for (String uri : List.of("in1", "in2", "out1", "out2")) {
            String content = uri.startsWith("out") ? "0" : "input " + uri;
            when(storageService.downloadFileByUri(uri))
                    .thenAnswer(inv -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }
        when(container.runClass(any(), eq("Main"), anyList(), eq("input in1"), anyLong(), anyInt()))
                .thenReturn(sandbox(0, "0\n"));
        when(container.runClass(any(), eq("Main"), anyList(), eq("input in2"), anyLong(), anyInt()))
                .thenReturn(sandbox(0, "1\n"));
    }

    private static OopContainerExecutor.SandboxResult sandbox(int exitCode, String stdout) {
        return new OopContainerExecutor.SandboxResult(100, 20_000, exitCode, stdout, "", false, false, false, true, null);
    }

    @Test
    void rulesOnly_correctStructureIsAcceptedAndWrongStructureIsNot(@TempDir Path good, @TempDir Path bad) throws Exception {
        assertThat(judge(GOOD, RULES, good).getScore()).isEqualTo(100.0);
        verify(submissionService).updateSubmissionVerdict(eq(SUBMISSION_ID), eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.AC), any(), any(), any(), any(), any());

        Submission wrong = judge(NOT_ABSTRACT, RULES, bad);
        assertThat(wrong.getScore()).isEqualTo(50.0); // 1/2 rule
        verify(submissionService).updateSubmissionVerdict(eq(SUBMISSION_ID), eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.WA), any(), any(), any(), any(), any());
    }

    @Test
    void testCasesOnly_scoreIsShareOfPassingCases(@TempDir Path workDir) throws Exception {
        twoCasesHalfPassing();
        Submission saved = judge(GOOD, null, workDir);

        assertThat(saved.getScore()).isEqualTo(50.0);
        verify(submissionService).updateSubmissionVerdict(eq(SUBMISSION_ID), eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.WA), any(), any(), eq(1), eq(2), any());
        JsonNode failures = objectMapper.readTree(saved.getScoreBreakdown()).get("phases").get("unitTest").get("failures");
        assertThat(failures.get(0).get("testMethod").asText()).isEqualTo("Test case #2 (hidden)");
        // Thông báo cụ thể: testcase ẩn chỉ nêu số dòng lệch, không lộ dữ liệu (TestFeedback).
        assertThat(failures.get(0).get("error").asText())
                .startsWith("Wrong answer on test case #2 (hidden)")
                .contains("differs from the expected output at line 1");
        verify(submissionService).updateSubmissionVerdict(eq(SUBMISSION_ID), eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.WA), any(), any(), eq(1), eq(2),
                org.mockito.ArgumentMatchers.startsWith("Wrong answer on test case #2 (hidden)"));
    }

    @Test
    void rulesAndTestCases_areWeighted40And60(@TempDir Path workDir) throws Exception {
        twoCasesHalfPassing();
        assertThat(judge(GOOD, RULES, workDir).getScore()).isEqualTo(70.0); // 0.4*100 + 0.6*50
    }

    @Test
    void sandboxUnavailable_failsInsteadOfRunningInProcess(@TempDir Path workDir) throws Exception {
        twoCasesHalfPassing();
        when(container.runClass(any(), any(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(OopContainerExecutor.SandboxResult.unavailable("docker down"));
        Problem problem = Problem.builder().id(1L).problemType(ProblemType.OOP_GRINDING).timeLimitMs(2000)
                .memoryLimitMb(256).build();
        when(submissionService.getSubmissionById(SUBMISSION_ID)).thenReturn(Submission.builder().id(SUBMISSION_ID).build());
        when(submissionService.getSubmissionCode(SUBMISSION_ID)).thenReturn(GOOD);

        strategy.executeJudge(new SubmissionTaskMessage(SUBMISSION_ID, 1L, 1L, SubmissionLanguage.JAVA), problem, workDir);

        verify(submissionService).failSubmission(eq(SUBMISSION_ID), contains("Docker sandbox is unavailable"));
        verify(submissionService, never()).updateSubmissionVerdict(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
