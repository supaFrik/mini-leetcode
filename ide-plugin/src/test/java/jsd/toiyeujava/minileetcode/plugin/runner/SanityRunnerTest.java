package jsd.toiyeujava.minileetcode.plugin.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit tests for SanityRunner.
 * Tests genuine Java compilation, subprocess execution, stdin piping, EOF closing, and timeout.
 */
class SanityRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Successfully compiles and runs main() with stdin sample input")
    void testSuccessfulExecutionWithStdin() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String mainCode = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (sc.hasNextInt()) {
                            int a = sc.nextInt();
                            int b = sc.nextInt();
                            System.out.println("RESULT: " + (a + b));
                        }
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), mainCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "15 27\n");

        assertTrue(result.isSuccess(), "Execution should succeed. Stderr: " + result.stderr());
        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.stdout().contains("RESULT: 42"), "Expected stdout to contain 'RESULT: 42' but got: " + result.stdout());
    }

    @Test
    @DisplayName("Compiles and runs multi-file project with package declarations")
    void testMultiFileCompilationAndExecution() throws IOException {
        Path pkgDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(pkgDir);

        String accountCode = """
                package com.example;
                public class Account {
                    private final String owner;
                    private final int balance;
                    public Account(String owner, int balance) {
                        this.owner = owner;
                        this.balance = balance;
                    }
                    public String summary() {
                        return owner + " has " + balance;
                    }
                }
                """;
        Files.writeString(pkgDir.resolve("Account.java"), accountCode);

        String mainCode = """
                package com.example;
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String name = sc.nextLine();
                        int bal = sc.nextInt();
                        Account acc = new Account(name, bal);
                        System.out.println(acc.summary());
                    }
                }
                """;
        Files.writeString(pkgDir.resolve("Main.java"), mainCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "Alice\n5000\n");

        assertTrue(result.isSuccess(), "Execution should succeed. Stderr: " + result.stderr());
        assertTrue(result.stdout().contains("Alice has 5000"));
    }

    @Test
    @DisplayName("Returns compilation error result on invalid Java syntax")
    void testCompilationFailureReturnsError() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String brokenCode = """
                public class Main {
                    public static void main(String[] args) {
                        int broken = "string" // Missing semicolon and type mismatch
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), brokenCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "");

        assertFalse(result.isSuccess());
        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("Compilation failed"));
    }

    @Test
    @DisplayName("Fails gracefully when src/main/java does not exist")
    void testMissingSourceDirReturnsError() {
        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "");

        assertFalse(result.isSuccess());
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Source directory not found"));
    }

    @Test
    @DisplayName("Fails gracefully when no main method is declared in source files")
    void testNoMainMethodFoundReturnsError() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String helperCode = """
                public class Helper {
                    public int add(int a, int b) { return a + b; }
                }
                """;
        Files.writeString(srcDir.resolve("Helper.java"), helperCode);

        SanityRunner runner = new SanityRunner();
        SanityRunResult result = runner.run(tempDir, "");

        assertFalse(result.isSuccess());
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No class with 'public static void main"));
    }

    @Test
    @DisplayName("Detects process timeout and sets timedOut = true")
    void testExecutionTimeout() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);

        String slowCode = """
                public class Main {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(4000);
                        System.out.println("Finished");
                    }
                }
                """;
        Files.writeString(srcDir.resolve("Main.java"), slowCode);

        SanityRunner runner = new SanityRunner(Duration.ofMillis(500));
        SanityRunResult result = runner.run(tempDir, "", Duration.ofMillis(500));

        assertFalse(result.isSuccess());
        assertTrue(result.timedOut(), "Process should have been flagged as timedOut");
    }

    @Test
    @DisplayName("Custom SubprocessExecutor can be injected for testing")
    void testCustomSubprocessExecutor() throws IOException {
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Main.java"), "public class Main { public static void main(String[] args) {} }");

        SanityRunner.SubprocessExecutor mockExec = (w, c, m, s, t) -> new SanityRunResult(0, "MOCK_OUTPUT", "", false);
        SanityRunner runner = new SanityRunner(Duration.ofSeconds(5), mockExec);

        SanityRunResult result = runner.run(tempDir, "");
        assertTrue(result.isSuccess());
        assertEquals("MOCK_OUTPUT", result.stdout());
    }
}
