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
    @DisplayName("Output khớp expected thì trả về AC")
    void map_OutputMatches_ReturnsAccepted() {
        assertThat(VerdictMapper.map(result(0, false, false, "42\n"), "42"))
                .isEqualTo(SubmissionVerdict.AC);
    }

    @Test
    @DisplayName("Output khác expected thì trả về WA")
    void map_OutputDiffers_ReturnsWrongAnswer() {
        assertThat(VerdictMapper.map(result(0, false, false, "41"), "42"))
                .isEqualTo(SubmissionVerdict.WA);
    }

    @Test
    @DisplayName("Hết giờ thì trả về TLE dù exit code là 137")
    void map_TimedOutWithExitCode137_ReturnsTimeLimitExceeded() {
        assertThat(VerdictMapper.map(result(137, true, false, ""), "42"))
                .isEqualTo(SubmissionVerdict.TLE);
    }

    @Test
    @DisplayName("Bị OOM kill thì trả về MLE")
    void map_OomKilled_ReturnsMemoryLimitExceeded() {
        assertThat(VerdictMapper.map(result(137, false, true, ""), "42"))
                .isEqualTo(SubmissionVerdict.MLE);
    }

    @Test
    @DisplayName("Exit code khác 0 (không phải TLE/MLE) thì trả về RE")
    void map_NonZeroExitCode_ReturnsRuntimeError() {
        assertThat(VerdictMapper.map(result(1, false, false, ""), "42"))
                .isEqualTo(SubmissionVerdict.RE);
    }
}