package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;

public final class VerdictMapper {

    private VerdictMapper() {
    }

    public static SubmissionVerdict map(ExecutionResult result, String expectedOutput) {
        if (result.timedOut()) {
            return SubmissionVerdict.TLE;
        }
        if (result.oomKilled()) {
            return SubmissionVerdict.MLE;
        }
        if (result.exitCode() != 0) {
            return SubmissionVerdict.RE;
        }
        return OutputComparator.matches(result.stdout(), expectedOutput)
                ? SubmissionVerdict.AC
                : SubmissionVerdict.WA;
    }
}