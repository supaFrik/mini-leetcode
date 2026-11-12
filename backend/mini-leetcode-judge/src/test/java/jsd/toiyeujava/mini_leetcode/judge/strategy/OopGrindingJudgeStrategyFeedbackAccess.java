package jsd.toiyeujava.mini_leetcode.judge.strategy;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;

/** Cho test ở package khác gọi hàm package-private {@code summarizeFailures}. */
public final class OopGrindingJudgeStrategyFeedbackAccess {

    private OopGrindingJudgeStrategyFeedbackAccess() {
    }

    public static String summarize(ExecutionReport report) {
        return OopGrindingJudgeStrategy.summarizeFailures(report);
    }
}
