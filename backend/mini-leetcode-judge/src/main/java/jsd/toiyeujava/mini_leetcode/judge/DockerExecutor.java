package jsd.toiyeujava.mini_leetcode.judge;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class DockerExecutor {

    private static final String MEMORY_LIMIT = "256m";
    private static final String CPU_LIMIT = "1";
    private static final String PIDS_LIMIT = "64";
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final long COMPILE_TIMEOUT_MS = 30_000;
    private static final long STARTUP_ALLOWANCE_MS = 5_000;

    /**
     * Pha COMPILE (chỉ Java, một lần mỗi bài nộp). Mount ghi được vì javac phải ghi Solution.class.
     * stderr gộp vào stdout vì javac in lỗi ra stderr và ta cần nó làm thông báo CE.
     */
    public CompileResult compile(Path workDir, LanguageRuntime runtime)
            throws IOException, InterruptedException {
        String containerName = "judge-compile-" + UUID.randomUUID();
        try {
            Process process = new ProcessBuilder(buildCompileCommand(containerName, workDir, runtime))
                    .redirectErrorStream(true)
                    .start();

            CompletableFuture<String> outputFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));

            boolean finished = process.waitFor(COMPILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                runDocker("kill", containerName);
                process.destroyForcibly();
                process.waitFor();
                return new CompileResult(false, "Compilation timed out");
            }
            return new CompileResult(process.exitValue() == 0, outputFuture.join());
        } finally {
            runDocker("rm", "-f", containerName);
        }
    }

    /**
     * Pha CHẠY (mỗi testcase một container mới). Mount chỉ đọc, input đưa qua stdin.
     * TODO: peakMemoryKb chưa đo nên luôn là 0.
     * runtimeMs tính từ FinishedAt - StartedAt của container (loại trừ thời gian tạo container,
     * nhưng vẫn bao gồm thời gian khởi động JVM với Java).
     */
    public ExecutionResult execute(Path workDir, LanguageRuntime runtime, String input, long timeLimitMs)
            throws IOException, InterruptedException {
        String containerName = "judge-" + UUID.randomUUID();
        try {
            Process process = new ProcessBuilder(buildRunCommand(containerName, workDir, runtime))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            CompletableFuture<String> stdoutFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));

            // Ghi stdin bất đồng bộ: tránh block khi pipe đầy (chương trình không đọc input).
            // IOException khi pipe vỡ (container bị kill) là bình thường, bỏ qua.
            CompletableFuture.runAsync(() -> {
                try (var stdin = process.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // pipe vỡ khi container bị kill trước khi đọc hết stdin
                }
            });

            // Hard timeout cộng thêm thời gian khởi động container
            long hardTimeoutMs = timeLimitMs + STARTUP_ALLOWANCE_MS;
            boolean finished = process.waitFor(hardTimeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                runDocker("kill", containerName);
                process.destroyForcibly();
                process.waitFor();
            }
            String stdout = stdoutFuture.join();

            String raw = runDocker("inspect", containerName,
                    "--format", "{{.State.OOMKilled}} {{.State.ExitCode}} {{.State.StartedAt}} {{.State.FinishedAt}}");
            String[] state = raw.split(" ");
            if (state.length != 4) {
                throw new IllegalStateException("docker inspect failed: " + raw);
            }
            boolean oomKilled = Boolean.parseBoolean(state[0]);
            int exitCode = Integer.parseInt(state[1]);
            // Nếu exit code trùng mã OOM của runtime (ví dụ JVM ExitOnOutOfMemoryError), phân loại là MLE
            if (runtime.oomExitCode().map(code -> code == exitCode).orElse(false)) {
                oomKilled = true;
            }
            String startedAtRaw = state[2];
            String finishedAtRaw = state[3];

            if ("0001-01-01T00:00:00Z".equals(startedAtRaw) || startedAtRaw.startsWith("0001-01-01")) {
                throw new IllegalStateException("Container never started (StartedAt=" + startedAtRaw + ")");
            }

            Instant startedAt = Instant.parse(startedAtRaw);
            Instant finishedAt = Instant.parse(finishedAtRaw);
            int runtimeMs = (int) Math.max(0, Duration.between(startedAt, finishedAt).toMillis());

            boolean timedOut = !finished || (runtimeMs > timeLimitMs);

            return new ExecutionResult(exitCode, timedOut, oomKilled, stdout, runtimeMs, 0);
        } finally {
            runDocker("rm", "-f", containerName);
        }
    }

    public List<String> buildRunCommand(String containerName, Path workDir, LanguageRuntime runtime) {
        return buildCommand(containerName, workDir, runtime, true, runtime.runCommand());
    }

    public List<String> buildCompileCommand(String containerName, Path workDir, LanguageRuntime runtime) {
        return buildCommand(containerName, workDir, runtime, false, runtime.compileCommand());
    }

    private List<String> buildCommand(String containerName, Path workDir, LanguageRuntime runtime,
                                      boolean runPhase, List<String> commandInContainer) {
        String mount = workDir.toAbsolutePath() + ":/workspace" + (runPhase ? ":ro" : "");
        List<String> command = new ArrayList<>(List.of("docker", "run"));
        if (runPhase) {
            command.add("-i");
        }
        command.addAll(List.of(
                "--name", containerName,
                "--network", "none",
                "--memory", MEMORY_LIMIT,
                "--memory-swap", MEMORY_LIMIT,
                "--cpus", CPU_LIMIT,
                "--pids-limit", PIDS_LIMIT,
                "-v", mount,
                runtime.image()));
        command.addAll(commandInContainer);
        return command;
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
            // luồng bị đóng khi container bị kill, giữ phần đã đọc được
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
