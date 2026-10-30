package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageRuntimeTest {

    @Test
    @DisplayName("Maps PYTHON and JAVA to their matching runtimes")
    void from_PythonAndJava_ReturnsMatchingRuntime() {
        assertThat(LanguageRuntime.from(SubmissionLanguage.PYTHON)).contains(LanguageRuntime.PYTHON);
        assertThat(LanguageRuntime.from(SubmissionLanguage.JAVA)).contains(LanguageRuntime.JAVA);
    }

    @Test
    @DisplayName("Returns empty for unsupported language CPP")
    void from_Cpp_ReturnsEmpty() {
        assertThat(LanguageRuntime.from(SubmissionLanguage.CPP)).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for null language")
    void from_Null_ReturnsEmpty() {
        assertThat(LanguageRuntime.from(null)).isEmpty();
    }

    @Test
    @DisplayName("Only Java requires a compile step, Python does not")
    void needsCompile_PythonAndJava_OnlyJavaNeedsCompile() {
        assertThat(LanguageRuntime.PYTHON.needsCompile()).isFalse();
        assertThat(LanguageRuntime.JAVA.needsCompile()).isTrue();
    }

    @Test
    @DisplayName("Java defines an OOM exit code for ExitOnOutOfMemoryError, Python does not")
    void oomExitCode_PythonAndJava_OnlyJavaDefinesExitCode() {
        assertThat(LanguageRuntime.PYTHON.oomExitCode()).isEmpty();
        assertThat(LanguageRuntime.JAVA.oomExitCode()).contains(3);
    }

    @Test
    @DisplayName("Runs the mounted wrapper script instead of passing the command inline")
    void runCommand_InvokesMountedScript() {
        assertThat(LanguageRuntime.PYTHON.runCommand(256))
                .containsExactly("sh", "/workspace/run.sh");
        assertThat(LanguageRuntime.JAVA.runCommand(256))
                .containsExactly("sh", "/workspace/run.sh");
    }

    @Test
    @DisplayName("The wrapper script runs the user command and reports the cgroup peak memory on stderr")
    void runScript_ContainsCommandAndMemoryProbe() {
        String script = LanguageRuntime.PYTHON.runScript(256);

        assertThat(script).startsWith("#!/bin/sh\n");
        assertThat(script).contains("'python' '/workspace/solution.py'");
        assertThat(script).contains("/sys/fs/cgroup/memory.peak");
        assertThat(script).contains(LanguageRuntime.MEMORY_SENTINEL);
        // stdout phải sạch: peak memory chỉ in ra stderr.
        assertThat(script).contains(">&2");
        assertThat(script).contains("exit $rc");
    }

    @Test
    @DisplayName("Java calculates -Xmx based on memory limit with floor, Python ignores it")
    void runScript_CustomMemoryLimit_JavaCalculatesHeapAndPythonIgnores() {
        assertThat(LanguageRuntime.PYTHON.runScript(512))
                .doesNotContain("-Xmx");

        assertThat(LanguageRuntime.JAVA.runScript(256))
                .contains("-Xmx192m");

        assertThat(LanguageRuntime.JAVA.runScript(512))
                .contains("-Xmx384m");

        assertThat(LanguageRuntime.JAVA.runScript(32))
                .contains("-Xmx32m");
    }

    @Test
    @DisplayName("Quotes arguments so the shell cannot reinterpret them")
    void shellQuote_EscapesSingleQuotes() {
        assertThat(LanguageRuntime.shellQuote("plain")).isEqualTo("'plain'");
        assertThat(LanguageRuntime.shellQuote("it's")).isEqualTo("'it'\\''s'");
        assertThat(LanguageRuntime.shellQuote(null)).isEqualTo("''");
    }
}
