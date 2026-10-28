package jsd.toiyeujava.mini_leetcode.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jsd.toiyeujava.mini_leetcode.entity.Verdict;
import org.junit.jupiter.api.Test;

class VerdictMapperTest {

    private ExecutionResult result(int exitCode, boolean timedOut, boolean oomKilled, String stdout) {
        return new ExecutionResult(exitCode, timedOut, oomKilled, stdout, 0, 0);
    }

    @Test
    void acWhenOutputMatches() {
        assertEquals(Verdict.AC, VerdictMapper.map(result(0, false, false, "42\n"), "42"));
    }

    @Test
    void waWhenOutputDiffers() {
        assertEquals(Verdict.WA, VerdictMapper.map(result(0, false, false, "41"), "42"));
    }

    @Test
    void tleEvenWhenExitCodeIsNonZero() {
        assertEquals(Verdict.TLE, VerdictMapper.map(result(137, true, false, ""), "42"));
    }

    @Test
    void mleWhenOomKilled() {
        assertEquals(Verdict.MLE, VerdictMapper.map(result(137, false, true, ""), "42"));
    }

    @Test
    void reWhenExitCodeNonZero() {
        assertEquals(Verdict.RE, VerdictMapper.map(result(1, false, false, ""), "42"));
    }
}