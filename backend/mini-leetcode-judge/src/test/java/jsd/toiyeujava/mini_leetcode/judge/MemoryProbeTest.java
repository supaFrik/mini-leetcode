package jsd.toiyeujava.mini_leetcode.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryProbeTest {

    @Test
    @DisplayName("Extracts peak memory in KB and removes the sentinel line")
    void parse_SentinelPresent_ExtractsPeakAndStripsSentinel() {
        MemoryProbe probe = MemoryProbe.parse(
                "Traceback...\n__MEM_PEAK_KB__=162484224\n");

        assertThat(probe.peakMemoryKb()).isEqualTo(162484224 / 1024);
        assertThat(probe.cleanStderr()).doesNotContain(LanguageRuntime.MEMORY_SENTINEL);
        assertThat(probe.cleanStderr()).contains("Traceback...");
    }

    @Test
    @DisplayName("Returns null peak and unchanged stderr when the sentinel is absent")
    void parse_NoSentinel_ReturnsNullPeak() {
        MemoryProbe probe = MemoryProbe.parse("boom\n");

        assertThat(probe.peakMemoryKb()).isNull();
        assertThat(probe.cleanStderr()).isEqualTo("boom\n");
    }

    @Test
    @DisplayName("Handles empty and null stderr safely")
    void parse_EmptyOrNull_ReturnsEmptyResult() {
        assertThat(MemoryProbe.parse(null).peakMemoryKb()).isNull();
        assertThat(MemoryProbe.parse(null).cleanStderr()).isEmpty();
        assertThat(MemoryProbe.parse("").peakMemoryKb()).isNull();
    }

    @Test
    @DisplayName("Ignores a non-numeric sentinel value")
    void parse_NonNumericSentinel_ReturnsNullPeak() {
        MemoryProbe probe = MemoryProbe.parse("__MEM_PEAK_KB__=not-a-number\n");

        assertThat(probe.peakMemoryKb()).isNull();
        assertThat(probe.cleanStderr()).doesNotContain(LanguageRuntime.MEMORY_SENTINEL);
    }

    @Test
    @DisplayName("Keeps content preceding the sentinel on the same line")
    void parse_SentinelWithPrefix_KeepsPrefix() {
        MemoryProbe probe = MemoryProbe.parse("prefix__MEM_PEAK_KB__=1048576\n");

        assertThat(probe.peakMemoryKb()).isEqualTo(1024);
        assertThat(probe.cleanStderr()).contains("prefix");
    }
}
