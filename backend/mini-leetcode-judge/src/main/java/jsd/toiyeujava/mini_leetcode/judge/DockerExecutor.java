package jsd.toiyeujava.mini_leetcode.judge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

public class DockerExecutor {

    private static final String IMAGE = "python:3.12-slim";
    private static final String MEMORY_LIMIT = "256m";
    private static final String CPU_LIMIT = "1";
    private static final String PIDS_LIMIT = "64";
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

    public ExecutionResult execute(Path workDir, String scriptName, String input, long timeLimitMs)
            throws IOException, InterruptedException {
        String containerName = "judge-" + UUID.randomUUID();
        long start = System.nanoTime();
        try {
            Process process = new ProcessBuilder(buildCommand(containerName, workDir, scriptName))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            CompletableFuture<String> stdoutFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));

            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // chương trình có thể thoát trước khi đọc hết input, ko phải lỗi của judge
            }

            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                runDocker("kill", containerName);
                process.destroyForcibly();
                process.waitFor();
            }
            int runtimeMs = (int) ((System.nanoTime() - start) / 1_000_000);
            String stdout = stdoutFuture.join();

            String raw = runDocker("inspect", containerName,
                    "--format", "{{.State.OOMKilled}} {{.State.ExitCode}}");
            String[] state = raw.split(" ");
            if (state.length != 2) {
                throw new IllegalStateException("docker inspect failed: " + raw);
            }
            boolean oomKilled = Boolean.parseBoolean(state[0]);
            int exitCode = Integer.parseInt(state[1]);

            return new ExecutionResult(exitCode, !finished, oomKilled, stdout, runtimeMs, 0);
        } finally {
            runDocker("rm", "-f", containerName);
        }
    }

    public List<String> buildCommand(String containerName, Path workDir, String scriptName) {
        String mount = workDir.toAbsolutePath() + ":/workspace:ro";
        return List.of(
                "docker", "run",
                "-i",
                "--name", containerName,
                "--network", "none",
                "--memory", MEMORY_LIMIT,
                "--memory-swap", MEMORY_LIMIT,
                "--cpus", CPU_LIMIT,
                "--pids-limit", PIDS_LIMIT,
                "-v", mount,
                IMAGE,
                "python", "/workspace/" + scriptName
        );
    }

    private String runDocker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        return out;
    }

    private String readLimited(InputStream in) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int n;
            while ((n = in.read(chunk)) != -1) {
                int room = MAX_OUTPUT_BYTES - buffer.size();
                if (room > 0) {
                    buffer.write(chunk, 0, Math.min(n, room));
                }
            }
        } catch (IOException e) {
            // luồng bị đóng khi container bị kill, giữ phần đã đọc đc
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}