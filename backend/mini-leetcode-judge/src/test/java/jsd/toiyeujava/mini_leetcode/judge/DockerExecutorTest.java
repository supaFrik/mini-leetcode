package jsd.toiyeujava.mini_leetcode.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

    // ── 4 test mới để tái hiện 3 lỗi ──────────────────────────────────────────

    /**
     * Test 1: Python print("hello") với timeLimitMs = 1000 ms.
     * Bắt lỗi TLE giả: container khởi động mất > 1 s nên bài đúng bị báo TLE.
     * Kỳ vọng ĐÚNG: timedOut == false, exitCode == 0.
     */
    @Test
    @Timeout(20)
    @DisplayName("[Lỗi 2] Python hello không bị TLE giả khi limit = 1000 ms")
    void execute_PythonHello_NotTimedOutWithShortLimit() throws Exception {
        Files.writeString(workDir.resolve(LanguageRuntime.PYTHON.sourceFileName()), "print(\"hello\")\n");

        ExecutionResult result = dockerExecutor.execute(workDir, LanguageRuntime.PYTHON, "", 1_000);

        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
    }

    /**
     * Test 2: Python vòng lặp vô hạn, limit 2000 ms.
     * Kỳ vọng: timedOut == true, hàm trả về trong vài giây (không treo).
     */
    @Test
    @Timeout(20)
    @DisplayName("[Lỗi 2] Python vòng lặp vô hạn bị TLE và trả về nhanh")
    void execute_PythonInfiniteLoop_TimedOut() throws Exception {
        Files.writeString(workDir.resolve(LanguageRuntime.PYTHON.sourceFileName()), "while True: pass\n");

        ExecutionResult result = dockerExecutor.execute(workDir, LanguageRuntime.PYTHON, "", 2_000);

        assertThat(result.timedOut()).isTrue();
    }

    /**
     * Test 3: Python vòng lặp vô hạn với input rất lớn (~4 MB).
     * Bắt lỗi stdin blocking: ghi đồng bộ pipe đầy → waitFor() không bao giờ được gọi.
     * Kỳ vọng: timedOut == true, hàm trả về trong @Timeout (không treo).
     */
    @Test
    @Timeout(20)
    @DisplayName("[Lỗi 1] Stdin lớn + vòng lặp vô hạn không làm treo worker")
    void execute_LargeStdinInfiniteLoop_NotBlocked() throws Exception {
        Files.writeString(workDir.resolve(LanguageRuntime.PYTHON.sourceFileName()), "while True: pass\n");
        // ~4 MB input để làm đầy pipe OS
        String bigInput = "x".repeat(4 * 1024 * 1024);

        ExecutionResult result = dockerExecutor.execute(workDir, LanguageRuntime.PYTHON, bigInput, 2_000);

        assertThat(result.timedOut()).isTrue();
    }

    /**
     * Test 4: Java cấp phát heap liên tục cho đến khi OOM.
     * Bắt lỗi phân loại: JVM thoát với exit code ≠ 137, OOMKilled=false → bị xếp RE thay vì MLE.
     * In ra exitCode để xác định giá trị thực tế (dùng ở Bước 4).
     * Kỳ vọng: oomKilled == true.
     */
    @Test
    @Timeout(60)
    @DisplayName("[Lỗi 3] Java hết heap được phân loại là MLE (oomKilled = true)")
    void execute_JavaHeapExhaustion_OomKilled() throws Exception {
        String source = """
                import java.util.ArrayList;
                import java.util.List;

                public class Solution {
                    public static void main(String[] args) {
                        List<long[]> sink = new ArrayList<>();
                        while (true) {
                            sink.add(new long[1024 * 1024]); // 8 MB mỗi lần
                        }
                    }
                }
                """;
        Files.writeString(workDir.resolve(LanguageRuntime.JAVA.sourceFileName()), source);

        CompileResult compile = dockerExecutor.compile(workDir, LanguageRuntime.JAVA);
        assertThat(compile.success()).as("compile output: %s", compile.output()).isTrue();

        ExecutionResult result = dockerExecutor.execute(workDir, LanguageRuntime.JAVA, "", 30_000);

        // In để tôi thấy giá trị thực tế (phục vụ Bước 4)
        System.out.println("[OOM test] exitCode=" + result.exitCode()
                + " oomKilled=" + result.oomKilled()
                + " timedOut=" + result.timedOut());

        assertThat(result.oomKilled()).isTrue();
    }
}
