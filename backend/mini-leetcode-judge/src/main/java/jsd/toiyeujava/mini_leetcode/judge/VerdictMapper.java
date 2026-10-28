package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.Verdict;

public final class VerdictMapper {

    private VerdictMapper() {
    }

    public static Verdict map(ExecutionResult result, String expectedOutput) {
        if (result.timedOut()) {
            return Verdict.TLE;
        }
        if (result.oomKilled()) {
            return Verdict.MLE;
        }
        if (result.exitCode() != 0) {
            return Verdict.RE;
        }
        return OutputComparator.matches(result.stdout(), expectedOutput)
                ? Verdict.AC
                : Verdict.WA;
    }
}