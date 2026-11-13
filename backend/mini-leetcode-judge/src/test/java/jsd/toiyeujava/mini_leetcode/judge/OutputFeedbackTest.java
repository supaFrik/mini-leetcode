package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopRuleResult;
import jsd.toiyeujava.mini_leetcode.judge.strategy.OopGrindingJudgeStrategyFeedbackAccess;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mục 1 + 2 (so sánh output linh hoạt, thông báo lỗi cụ thể) — xem specs/study-plan.spec.md. */
class OutputFeedbackTest {

    private static final OutputPolicy NORMALIZED = new OutputPolicy(OutputPolicy.Mode.NORMALIZED, false);
    private static final OutputPolicy NORMALIZED_NO_CASE = new OutputPolicy(OutputPolicy.Mode.NORMALIZED, true);

    private static TestCase publicCase(String label) {
        return TestCase.builder().isHidden(false).label(label).build();
    }

    private static TestCase hiddenCase(String label) {
        return TestCase.builder().isHidden(true).label(label).build();
    }

    // ---------------------------------------------------------------- policy

    @Test
    void policyIsReadFromGradingConfigAndDefaultsToExact() {
        assertThat(OutputPolicy.fromGradingConfig(null)).isEqualTo(OutputPolicy.EXACT);
        assertThat(OutputPolicy.fromGradingConfig("{\"rules\": []}")).isEqualTo(OutputPolicy.EXACT);
        assertThat(OutputPolicy.fromGradingConfig("not json")).isEqualTo(OutputPolicy.EXACT);
        assertThat(OutputPolicy.fromGradingConfig("{\"output\": {\"mode\": \"weird\"}}")).isEqualTo(OutputPolicy.EXACT);
        assertThat(OutputPolicy.fromGradingConfig("{\"output\": {\"mode\": \"normalized\", \"ignoreCase\": true}}"))
                .isEqualTo(NORMALIZED_NO_CASE);
    }

    // ---------------------------------------------------------------- comparison

    @Test
    void exactModeKeepsTheOldBehaviour() {
        assertThat(OutputComparator.matches("a  \r\nb\n\n", "a\nb")).isTrue();      // khoảng trắng cuối dòng, dòng trống cuối
        assertThat(OutputComparator.matches(" a\nb", "a\nb")).isFalse();             // khoảng trắng đầu dòng vẫn tính
        assertThat(OutputComparator.matches("a\n\nb", "a\nb")).isFalse();            // dòng trống ở giữa vẫn tính
        assertThat(OutputComparator.matches("A", "a")).isFalse();
    }

    @Test
    void normalizedModeIgnoresLayoutButNotContent() {
        String expected = "#1 SAVINGS      An                 100,000\n> Bye!";
        String actual = "\n  #1 SAVINGS An 100,000  \n\n>   Bye!\n";
        assertThat(OutputComparator.matches(actual, expected, NORMALIZED)).isTrue();
        assertThat(OutputComparator.matches("#1 SAVINGS An 100000\n> Bye!", expected, NORMALIZED)).isFalse();
        assertThat(OutputComparator.matches("> BYE!", "> Bye!", NORMALIZED)).isFalse();
        assertThat(OutputComparator.matches("> BYE!", "> Bye!", NORMALIZED_NO_CASE)).isTrue();
    }

    @Test
    void firstDifferenceReportsLineAndBothSides() {
        assertThat(OutputComparator.firstDifference("a\nb\nc", "a\nB\nc", OutputPolicy.EXACT))
                .contains(new OutputComparator.Difference(2, "B", "b"));
        assertThat(OutputComparator.firstDifference("a", "a\nb", OutputPolicy.EXACT))
                .contains(new OutputComparator.Difference(2, "b", null));   // output dừng sớm
        assertThat(OutputComparator.firstDifference("a\nx", "a", OutputPolicy.EXACT))
                .contains(new OutputComparator.Difference(2, null, "x"));   // in thừa
        assertThat(OutputComparator.firstDifference("a\n", "a", OutputPolicy.EXACT)).isEmpty();
    }

    // ---------------------------------------------------------------- feedback

    @Test
    void publicWrongAnswerShowsExpectedAndActualLine() {
        String msg = TestFeedback.wrongAnswer(2, publicCase("stage 1: two-accounts"),
                "> Số dư mới: 50000", "> Số dư mới: 50,000", OutputPolicy.EXACT);

        assertThat(msg).startsWith("Wrong answer on test case #2 (stage 1: two-accounts)");
        assertThat(msg).contains("Line 1: expected \"> Số dư mới: 50,000\"").contains("but got  \"> Số dư mới: 50000\"");
    }

    @Test
    void hiddenWrongAnswerNamesTheStageButNeverLeaksData() {
        String msg = TestFeedback.wrongAnswer(5, hiddenCase("stage 1: two-accounts"),
                "line one\nSECRET-ACTUAL", "line one\nSECRET-EXPECTED", NORMALIZED);

        assertThat(msg).startsWith("Wrong answer on test case #5 (hidden · stage 1: two-accounts)");
        assertThat(msg).contains("compared ignoring extra spaces and blank lines");
        assertThat(msg).contains("differs from the expected output at line 2");
        assertThat(msg).doesNotContain("SECRET");
        assertThat(TestFeedback.wrongAnswer(1, hiddenCase(null), "a", "a\nb", OutputPolicy.EXACT))
                .contains("ended too early: line 2 is missing");
    }

    @Test
    void otherVerdictsNameTheFailingTestCase() {
        assertThat(TestFeedback.failure(SubmissionVerdict.TLE, 3, hiddenCase(null), null))
                .isEqualTo("Time limit exceeded on test case #3 (hidden)");
        assertThat(TestFeedback.failure(SubmissionVerdict.RE, 1, publicCase(null), "java.lang.NullPointerException"))
                .isEqualTo("Runtime error on test case #1\njava.lang.NullPointerException");
    }

    @Test
    void oopSummaryListsFailedRulesThenFirstFailingTestCase() {
        ExecutionReport report = ExecutionReport.builder()
                .phases(ExecutionReport.PhaseBreakdown.builder()
                        .oopInspection(ExecutionReport.OopInspectionPhase.builder().rules(List.of(
                                OopRuleResult.builder().ruleName("ACCOUNT_IS_ABSTRACT_BASE").passed(false)
                                        .message("Account phải là lớp abstract").build(),
                                OopRuleResult.builder().ruleName("OK_RULE").passed(true).build())).build())
                        .unitTest(ExecutionReport.UnitTestPhase.builder().failures(List.of(
                                ExecutionReport.UnitTestFailure.builder().testMethod("Test case #2")
                                        .error("Wrong answer on test case #2 (hidden · stage 1: two-accounts)\n...").build(),
                                ExecutionReport.UnitTestFailure.builder().testMethod("Test case #4").error("Time limit exceeded").build()))
                                .build())
                        .build())
                .build();

        String summary = OopGrindingJudgeStrategyFeedbackAccess.summarize(report);

        assertThat(summary).startsWith("OOP rule ACCOUNT_IS_ABSTRACT_BASE: Account phải là lớp abstract");
        assertThat(summary).contains("Wrong answer on test case #2 (hidden · stage 1: two-accounts)");
        assertThat(summary).contains("(+1 more failing test cases)").doesNotContain("OK_RULE");
        assertThat(OopGrindingJudgeStrategyFeedbackAccess.summarize(ExecutionReport.builder().build())).isNull();
    }
}
