package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import jsd.toiyeujava.mini_leetcode.judge.MemoryProbe;
import jsd.toiyeujava.mini_leetcode.judge.SandboxHostPathResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class OopContainerExecutorTest {

    private final OopContainerExecutor executor = new OopContainerExecutor(null, new SandboxHostPathResolver());

    private String read(String data, AtomicBoolean truncated) {
        return executor.readLimited(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), truncated);
    }

    @Test
    @DisplayName("stderr > 64KB: output is capped but the trailing memory sentinel survives")
    void readLimited_Overflow_KeepsMemorySentinel() {
        AtomicBoolean truncated = new AtomicBoolean();

        String out = read("e".repeat(100_000) + "\n__MEM_PEAK_KB__=12582912\n", truncated);

        assertThat(truncated).isTrue();
        assertThat(out.length()).isLessThan(OopContainerExecutor.MAX_OUTPUT_BYTES + 300);
        assertThat(MemoryProbe.parse(out).peakMemoryKb()).isEqualTo(12288); // sentinel là byte ⇒ 12 MiB
    }

    @Test
    @DisplayName("Small output is returned unchanged and not flagged as truncated")
    void readLimited_Small_Unchanged() {
        AtomicBoolean truncated = new AtomicBoolean();

        assertThat(read("hello\n__MEM_PEAK_KB__=7\n", truncated)).isEqualTo("hello\n__MEM_PEAK_KB__=7\n");
        assertThat(truncated).isFalse();
    }
}
