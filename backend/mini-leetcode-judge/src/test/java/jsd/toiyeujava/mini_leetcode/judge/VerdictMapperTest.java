package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictMapperTest {

    private ExecutionResult result(int exitCode, boolean timedOut, boolean oomKilled, String stdout) {
        return new ExecutionResult(exitCode, timedOut, oomKilled, stdout, 0, 0);
    }

    @Test
    @DisplayName("Returns AC when output matches expected")
    void map_OutputMatches_ReturnsAccepted() {
        assertThat(VerdictMapper.map(result(0, false, false, "42\n"), "42"))
                .isEqualTo(SubmissionVerdict.AC);
    }

    @Test
    @DisplayName("Returns WA when output differs from expected")
    void map_OutputDiffers_ReturnsWrongAnswer() {
        assertThat(VerdictMapper.map(result(0, false, false, "41"), "42"))
                .isEqualTo(SubmissionVerdict.WA);
    }

    @Test
    @DisplayName("Returns TLE when timed out, even if exit code is 137")
    void map_TimedOutWithExitCode137_ReturnsTimeLimitExceeded() {
        assertThat(VerdictMapper.map(result(137, true, false, ""), "42"))
                .isEqualTo(SubmissionVerdict.TLE);
    }

    @Test
    @DisplayName("Returns MLE when the container was OOM killed")
    void map_OomKilled_ReturnsMemoryLimitExceeded() {
        assertThat(VerdictMapper.map(result(137, false, true, ""), "42"))
                .isEqualTo(SubmissionVerdict.MLE);
    }

    @Test
    @DisplayName("Returns RE when exit code is non-zero and it is not TLE or MLE")
    void map_NonZeroExitCode_ReturnsRuntimeError() {
        assertThat(VerdictMapper.map(result(1, false, false, ""), "42"))
                .isEqualTo(SubmissionVerdict.RE);
    }
}