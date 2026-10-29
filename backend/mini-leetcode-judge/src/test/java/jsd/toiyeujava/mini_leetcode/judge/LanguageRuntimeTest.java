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
    @DisplayName("Java calculates -Xmx based on memory limit with floor, Python ignores it")
    void runCommand_CustomMemoryLimit_JavaCalculatesHeapAndPythonIgnores() {
        assertThat(LanguageRuntime.PYTHON.runCommand(512))
                .containsExactly("python", "/workspace/solution.py");

        assertThat(LanguageRuntime.JAVA.runCommand(256))
                .contains("-Xmx192m");

        assertThat(LanguageRuntime.JAVA.runCommand(512))
                .contains("-Xmx384m");

        assertThat(LanguageRuntime.JAVA.runCommand(32))
                .contains("-Xmx32m");
    }
}
