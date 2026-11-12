package jsd.toiyeujava.minileetcode.plugin.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial stress and edge-case test suite for {@link SanityRunner} and {@link SampleInputResolver}.
 * <p>
 * Tests:
 * 1. Subprocess Stress Testing:
 *    - Infinite loop with spin-wait terminated by timeout cleanly
 *    - Long sleep (60s) cleanly terminated by timeout
 *    - Scanner loop exits immediately on EOF without hanging until timeout
 *    - Memory / IO stress: 10,000 lines of stdout and simultaneous stderr without pipe deadlock
 *    - Full UTF-8 Vietnamese encoding preservation in stdin and stdout
 *    - System.exit(42) and uncaught runtime exceptions
 *    - Multi-file compilation with package declarations
 * 2. Input Parsing Adversarial Cases:
 *    - Malformed / unclosed markdown code blocks
 *    - Multiple code blocks (problem code vs sample input)
 *    - Markdown with only output block (must return empty fallback)
 *    - Empty input block
 *    - Precedence: stages/sample-input.txt > root sample-input.txt > markdown
 *    - Whitespace-only files falling back cleanly
 *    - UTF-8 Vietnamese text in sample input files
 */
class SanityRunnerStressTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // 1. SUBPROCESS STRESS & RESILIENCE TESTS
    // =========================================================================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Infinite loop subprocess is terminated cleanly by timeout without zombie process")
    void testInfiniteLoopTimeoutTerminatesCleanly() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String infiniteLoopCode = """
                public class Main {
                    public static void main(String[] args) {
                        while (true) {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ignored) {}
                        }
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), infiniteLoopCode);

        SanityRunner runner = new SanityRunner(Duration.ofMillis(1200));
        SanityRunResult result = runner.run(tempDir, "", Duration.ofMillis(1200));

        assertFalse(result.isSuccess(), "Infinite loop must not be reported as success");
        assertTrue(result.timedOut(), "Result must flag timedOut == true");
        assertNotEquals(0, result.exitCode(), "Exit code for timed out execution must not be 0");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Long sleep (60s) subprocess is terminated cleanly by timeout")
    void testLongSleepTimeoutTerminatesCleanly() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String sleepCode = """
                public class Main {
                    public static void main(String[] args) throws InterruptedException {
                        Thread.sleep(60_000);
                        System.out.println("SHOULD_NOT_REACH_HERE");
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), sleepCode);

        SanityRunner runner = new SanityRunner(Duration.ofMillis(1200));
        SanityRunResult result = runner.run(tempDir, "", Duration.ofMillis(1200));

        assertFalse(result.isSuccess());
        assertTrue(result.timedOut(), "Expected timedOut to be true on 60s sleep");
        assertFalse(result.stdout().contains("SHOULD_NOT_REACH_HERE"));
    }

    @Test
    @DisplayName("DEFAULT_TIMEOUT constant is configured to 10 seconds")
    void testDefaultTimeoutConfiguration() {
        assertEquals(Duration.ofSeconds(10), SanityRunner.DEFAULT_TIMEOUT);
        SanityRunner runner = new SanityRunner();
        assertEquals(Duration.ofSeconds(10), runner.getTimeout());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Scanner loop with while(scanner.hasNextLine()) terminates on EOF immediately without hanging")
    void testScannerLoopTerminatesOnEofWithoutHanging() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String scannerCode = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner scanner = new Scanner(System.in);
                        int count = 0;
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            if (!line.isBlank()) {
                                count++;
                            }
                        }
                        System.out.println("TOTAL_LINES_READ=" + count);
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), scannerCode);

        String sampleInput = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n";

        long start = System.currentTimeMillis();
        SanityRunner runner = new SanityRunner(Duration.ofSeconds(8));
        SanityRunResult result = runner.run(tempDir, sampleInput);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isSuccess(), "Execution should succeed. Stderr: " + result.stderr());
        assertFalse(result.timedOut(), "Process should not time out; stdin EOF must close pipe");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("TOTAL_LINES_READ=5"), "Expected TOTAL_LINES_READ=5 but got: " + result.stdout());
        assertTrue(elapsed < 4000, "Execution took " + elapsed + "ms, which is too long; stdin was likely not closed");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Large stdout (10,000 lines) does not deadlock pipe buffer and captures complete output")
    void testLargeStdoutDoesNotDeadlockBufferUnderHeavyOutput() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String largeStdoutCode = """
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 10000; i++) {
                            System.out.println("STRESS_LOG_LINE_" + i + " payload content for buffer saturation verification");
                        }
                        System.out.println("COMPLETED_ALL_10000_LINES");
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), largeStdoutCode);

        SanityRunner runner = new SanityRunner(Duration.ofSeconds(15));
        SanityRunResult result = runner.run(tempDir, "");

        assertTrue(result.isSuccess(),
                "Execution must succeed without pipe deadlock or timeout. Result: timedOut=" + result.timedOut() + ", exitCode=" + result.exitCode() + ", stderr=" + result.stderr());
        assertFalse(result.timedOut(), "Process must not time out due to buffer deadlock");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("STRESS_LOG_LINE_0"));
        assertTrue(result.stdout().contains("STRESS_LOG_LINE_5000"));
        assertTrue(result.stdout().contains("STRESS_LOG_LINE_9999"));
        assertTrue(result.stdout().contains("COMPLETED_ALL_10000_LINES"));

        long lineCount = result.stdout().lines().count();
        assertTrue(lineCount >= 10001, "Expected >= 10001 lines in stdout, but got: " + lineCount);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Simultaneous heavy stdout and stderr (5,000 lines each) does not deadlock")
    void testLargeStdoutAndLargeStderrSimultaneous() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String dualStreamCode = """
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 5000; i++) {
                            System.out.println("STDOUT_LINE_" + i);
                            System.err.println("STDERR_LINE_" + i);
                        }
                        System.out.println("STDOUT_DONE");
                        System.err.println("STDERR_DONE");
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), dualStreamCode);

        SanityRunner runner = new SanityRunner(Duration.ofSeconds(15));
        SanityRunResult result = runner.run(tempDir, "");

        assertTrue(result.isSuccess(), "Execution failed: " + result.stderr());
        assertFalse(result.timedOut());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("STDOUT_DONE"));
        assertTrue(result.stderr().contains("STDERR_DONE"));
    }

    @Test
    @DisplayName("UTF-8 Vietnamese characters in stdin and stdout are preserved without mojibake")
    void testUtf8VietnameseEncodingStdinAndStdout() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String utf8Code = """
                import java.util.Scanner;
                import java.nio.charset.StandardCharsets;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
                        while (sc.hasNextLine()) {
                            String name = sc.nextLine();
                            System.out.println("Xin chào bạn " + name + "!");
                        }
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), utf8Code, StandardCharsets.UTF_8);

        String vietnameseInput = "Nguyễn Văn A\nTrần Thị Bích Ngọc\nĐặng Hoàng Cường\n";
        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, vietnameseInput);

        assertTrue(result.isSuccess(), "Execution failed: " + result.stderr());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Xin chào bạn Nguyễn Văn A!"),
                "Expected stdout to contain 'Xin chào bạn Nguyễn Văn A!' but got: " + result.stdout());
        assertTrue(result.stdout().contains("Xin chào bạn Trần Thị Bích Ngọc!"),
                "Expected stdout to contain 'Xin chào bạn Trần Thị Bích Ngọc!' but got: " + result.stdout());
        assertTrue(result.stdout().contains("Xin chào bạn Đặng Hoàng Cường!"),
                "Expected stdout to contain 'Xin chào bạn Đặng Hoàng Cường!' but got: " + result.stdout());
        assertFalse(result.stdout().contains("?"), "Mojibake/question marks detected in stdout: " + result.stdout());
    }

    @Test
    @DisplayName("System.exit(42) is captured with exact exit code 42 and isSuccess = false")
    void testNonZeroExitCodeWithSystemExit42() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String exitCode = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Before explicit exit");
                        System.err.println("Fatal error signal 42");
                        System.exit(42);
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), exitCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "");

        assertFalse(result.isSuccess(), "Exit code 42 must not be treated as success");
        assertEquals(42, result.exitCode(), "Exit code must match exactly 42");
        assertFalse(result.timedOut());
        assertTrue(result.stdout().contains("Before explicit exit"));
        assertTrue(result.stderr().contains("Fatal error signal 42"));
    }

    @Test
    @DisplayName("Uncaught runtime exception produces non-zero exit code and detailed stderr stack trace")
    void testUncaughtRuntimeExceptionCapturedInStderr() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String crashCode = """
                public class Main {
                    public static void main(String[] args) {
                        throw new IllegalStateException("Adversarial uncaught runtime failure");
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), crashCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "");

        assertFalse(result.isSuccess());
        assertNotEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.stderr().contains("IllegalStateException"), "Stderr should mention exception class");
        assertTrue(result.stderr().contains("Adversarial uncaught runtime failure"), "Stderr should mention exception message");
    }

    @Test
    @DisplayName("Compilation syntax error produces error result with compiler error output")
    void testCompilationSyntaxErrorCapturedInStderr() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String brokenSyntax = """
                public class Main {
                    public static void main(String[] args) {
                        int broken = ; // Syntax error missing expression
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), brokenSyntax);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "");

        assertFalse(result.isSuccess());
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Compilation failed"));
    }

    @Test
    @DisplayName("Multi-class multi-package project compiles and runs with dependency resolution")
    void testMultiClassMultiPackageProject() throws IOException {
        Path pkgDir = tempDir.resolve("src/main/java/com/adversarial/stress");
        Files.createDirectories(pkgDir);

        String modelCode = """
                package com.adversarial.stress;
                public class StressModel {
                    private final String title;
                    public StressModel(String title) { this.title = title; }
                    public String getTitle() { return title; }
                }
                """;
        Files.writeString(pkgDir.resolve("StressModel.java"), modelCode);

        String workerCode = """
                package com.adversarial.stress;
                public class StressWorker {
                    public static String process(StressModel model) {
                        return "WORKER_OK: " + model.getTitle();
                    }
                }
                """;
        Files.writeString(pkgDir.resolve("StressWorker.java"), workerCode);

        String mainCode = """
                package com.adversarial.stress;
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String input = sc.hasNextLine() ? sc.nextLine() : "EMPTY";
                        StressModel model = new StressModel(input);
                        System.out.println(StressWorker.process(model));
                    }
                }
                """;
        Files.writeString(pkgDir.resolve("Main.java"), mainCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "CustomStressPayload\n");

        assertTrue(result.isSuccess(), "Execution failed: " + result.stderr());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("WORKER_OK: CustomStressPayload"));
    }

    // =========================================================================
    // 2. INPUT PARSING ADVERSARIAL CASES (SampleInputResolver)
    // =========================================================================

    @Test
    @DisplayName("Malformed unclosed markdown code block is handled safely without throwing exception")
    void testMalformedMarkdownUnclosedCodeBlock() {
        String unclosedMd = """
                # Problem Title
                
                ### Sample Input
                ```text
                10 20 30
                Unclosed block with no ending fence
                """;
        assertDoesNotThrow(() -> {
            String input = SampleInputResolver.extractSampleInputFromMarkdown(unclosedMd);
            assertNotNull(input);
        });
    }

    @Test
    @DisplayName("Markdown with code blocks before sample input extracts only the sample input block")
    void testMarkdownWithMultipleCodeBlocksSelectsCorrectInput() {
        String md = """
                # Two Sum Problem
                
                Here is the Java starter template structure:
                ```java
                class Solution {
                    public int[] twoSum(int[] nums, int target) {
                        return new int[]{};
                    }
                }
                ```
                
                Follow these instructions to implement the logic.
                
                ### Sample Input
                ```text
                4
                2 7 11 15
                9
                ```
                
                ### Sample Output
                ```text
                0 1
                ```
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertEquals("4\n2 7 11 15\n9", extracted);
        assertFalse(extracted.contains("class Solution"), "Must not extract code template block");
        assertFalse(extracted.contains("Sample Output"), "Must not extract output header");
    }

    @Test
    @DisplayName("Markdown with only output block returns empty fallback string")
    void testMarkdownWithOnlyOutputBlockReturnsEmptyFallback() throws IOException {
        String md = """
                # Problem With Output Only
                
                ### Sample Output
                ```text
                Expected output is 42
                ```
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertEquals("", extracted, "When only sample output is present, extracted input must be empty");

        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);
        Files.writeString(stagesDir.resolve("stage-1.md"), md);

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals("", resolved);
    }

    @Test
    @DisplayName("Markdown with empty input code block returns empty string")
    void testMarkdownWithEmptyInputBlockReturnsEmpty() {
        String md = """
                ### Sample Input
                ```
                
                ```
                ### Sample Output
                ```
                OK
                ```
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertEquals("", extracted);
    }

    @Test
    @DisplayName("Precedence: stages/sample-input.txt wins over root sample-input.txt and markdown")
    void testStagesSampleInputPrecedence() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);

        Files.writeString(stagesDir.resolve("sample-input.txt"), "STAGES_PRIORITY_INPUT\n");
        Files.writeString(tempDir.resolve("sample-input.txt"), "ROOT_LOWER_PRIORITY\n");
        Files.writeString(stagesDir.resolve("stage-1.md"), "### Sample Input\n```\nMD_LOWEST_PRIORITY\n```");

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals("STAGES_PRIORITY_INPUT\n", resolved);
    }

    @Test
    @DisplayName("Whitespace-only stages/sample-input.txt falls back cleanly to root sample-input.txt")
    void testWhitespaceOnlyStagesInputFallsBackToRoot() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);

        Files.writeString(stagesDir.resolve("sample-input.txt"), "   \n\t  \r\n");
        Files.writeString(tempDir.resolve("sample-input.txt"), "ROOT_VALID_INPUT\n");

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals("ROOT_VALID_INPUT\n", resolved);
    }

    @Test
    @DisplayName("Whitespace-only root sample-input.txt falls back cleanly to stage markdown")
    void testWhitespaceOnlyRootInputFallsBackToMarkdown() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);

        Files.writeString(tempDir.resolve("sample-input.txt"), "   \n");
        Files.writeString(stagesDir.resolve("stage-1.md"), "### Sample Input\n```\nMD_INPUT_VALUE\n```");

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals("MD_INPUT_VALUE\n", resolved);
    }

    @Test
    @DisplayName("UTF-8 Vietnamese characters in stages/sample-input.txt are preserved accurately")
    void testVietnameseUtf8InputInStagesSampleInputFile() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);

        String vnContent = "Hà Nội\nĐà Nẵng\nThành phố Hồ Chí Minh\n";
        Files.writeString(stagesDir.resolve("sample-input.txt"), vnContent, StandardCharsets.UTF_8);

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals(vnContent, resolved);
    }

    @Test
    @DisplayName("Single-line input variations are parsed accurately")
    void testSingleLineInputVariations() {
        assertEquals("nums = [2, 7, 11, 15], target = 9",
                SampleInputResolver.extractSampleInputFromMarkdown("Input: nums = [2, 7, 11, 15], target = 9\nOutput: [0, 1]"));

        assertEquals("100 200",
                SampleInputResolver.extractSampleInputFromMarkdown("Sample Input: 100 200\nSample Output: 300"));

        assertEquals("val = true",
                SampleInputResolver.extractSampleInputFromMarkdown("**Input:** val = true\n**Output:** false"));
    }



    @Test
    @DisplayName("Code block with embedded Input: ... Output: extracts only input portion")
    void testCodeBlockEmbeddedInputOutputExtraction() {
        String md = """
                ```
                Input:
                5
                10 20 30 40 50
                Output:
                150
                ```
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertEquals("5\n10 20 30 40 50", extracted);
        assertFalse(extracted.contains("Output:"));
        assertFalse(extracted.contains("150"));
    }
}
