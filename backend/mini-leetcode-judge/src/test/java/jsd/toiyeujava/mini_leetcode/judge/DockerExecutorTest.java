package jsd.toiyeujava.mini_leetcode.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DockerExecutorTest {

    @TempDir
    Path workDir;

    private final DockerExecutor dockerExecutor = new DockerExecutor();

    @Test
    @DisplayName("Runs a Python hello script with exit code 0 and hello in stdout")
    void execute_PythonHelloScript_ReturnsHelloStdout() throws Exception {
        Files.writeString(workDir.resolve(LanguageRuntime.PYTHON.sourceFileName()), "print(\"hello\")\n");

        ExecutionResult result = dockerExecutor.execute(workDir, LanguageRuntime.PYTHON, "", 30_000);

        assertThat(result.exitCode()).isZero();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.stdout()).contains("hello");
    }

    @Test
    @DisplayName("Compiles valid Java, runs it, and prints the doubled number")
    void compileAndExecute_ValidJava_PrintsDoubledNumber() throws Exception {
        String source = """
                import java.util.Scanner;

                public class Solution {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        System.out.println(n * 2);
                    }
                }
                """;
        Files.writeString(workDir.resolve(LanguageRuntime.JAVA.sourceFileName()), source);

        CompileResult compile = dockerExecutor.compile(workDir, LanguageRuntime.JAVA);
        assertThat(compile.success())
                .as("compile output: %s", compile.output())
                .isTrue();

        ExecutionResult result = dockerExecutor.execute(workDir, LanguageRuntime.JAVA, "21\n", 30_000);

        assertThat(result.exitCode()).isZero();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.stdout().trim()).isEqualTo("42");
    }

    @Test
    @DisplayName("Fails to compile Java with a syntax error and returns an error message")
    void compile_JavaSyntaxError_ReturnsFailureWithMessage() throws Exception {
        Files.writeString(workDir.resolve(LanguageRuntime.JAVA.sourceFileName()), "public class Solution {");

        CompileResult compile = dockerExecutor.compile(workDir, LanguageRuntime.JAVA);

        assertThat(compile.success()).isFalse();
        assertThat(compile.output()).isNotBlank();
    }
}