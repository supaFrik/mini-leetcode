package jsd.toiyeujava.mini_leetcode.judge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerExecutorTest {

    @Test
    void execute_printsHello_whenPythonScriptSucceeds() throws Exception {
        Path workDir = Files.createTempDirectory("judge-hello-");
        try {
            Files.writeString(workDir.resolve("hello.py"), "print(\"hello\")\n");

            ExecutionResult result = new DockerExecutor().execute(workDir, "hello.py", "", 30_000);

            assertEquals(0, result.exitCode());
            assertFalse(result.timedOut());
            assertTrue(result.stdout().contains("hello"), () -> "stdout was: " + result.stdout());
        } finally {
            Files.deleteIfExists(workDir.resolve("hello.py"));
            Files.deleteIfExists(workDir);
        }
    }
}
