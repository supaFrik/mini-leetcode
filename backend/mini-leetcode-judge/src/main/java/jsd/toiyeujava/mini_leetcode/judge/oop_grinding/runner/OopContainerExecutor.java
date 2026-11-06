package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import jsd.toiyeujava.mini_leetcode.judge.LanguageRuntime;
import jsd.toiyeujava.mini_leetcode.judge.MemoryProbe;
import jsd.toiyeujava.mini_leetcode.judge.SandboxHostPathResolver;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.OopExecutionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chạy mã nguồn OOP trong MỘT container Docker ephemeral để đo được số liệu thật.
 *
 * <p>Vì sao cần container riêng? Phần chấm điểm OOP chạy harness (unit test,
 * socket scenario) và harness đó có thể chạy in-process trong JVM của judge
 * worker. Khi đó không đọc được {@code cgroup memory.peak} của container thí sinh
 * và cũng không có wall-clock thật ⇒ cột {@code execution_time_ms} và
 * {@code memory_used_kb} bị ghi 0 ⇒ UI hiển thị "Runtime: 0 ms | Peak memory: —".
 *
 * <p>Lớp này giải quyết đúng chỗ đó: chạy 1 container riêng với đúng các cờ
 * sandbox đã dùng cho bài thuật toán, đo wall-clock qua
 * {@code docker inspect StartedAt/FinishedAt} và peak memory qua sentinel
 * {@code __MEM_PEAK_KB__} (tái dụng {@link MemoryProbe}).
 *
 * <p>TUYỆT ĐỐI không fallback sang chạy in-process: nếu Docker không khả dụng thì
 * trả {@link OopExecutionMetrics#unavailable(String)} để số đo được ghi là
 * {@code null} (hiển thị "—") thay vì số bịa.
 */
@Slf4j
@Component
public class OopContainerExecutor {

    public static final String DEFAULT_RUNNER_IMAGE = "eclipse-temurin:17-jdk";
    public static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    public static final int MAX_OUTPUT_BYTES = 64 * 1024;
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;
    public static final String CLASSES_DIR = "classes";
    private static final long STARTUP_ALLOWANCE_MS = 5_000L;
    public static final String SCRIPT_FILE = "oop-probe-run.sh";

    private final ProcessLauncher processLauncher;
    private final SandboxHostPathResolver hostPathResolver;
    private final AtomicInteger threadCounter = new AtomicInteger();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "oop-container-io-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    /** Cờ báo output bị cắt cụt — dùng chung cho cả stdout và stderr của một lần chạy. */
    private final AtomicBoolean outputTruncated = new AtomicBoolean(false);

    @Autowired
    public OopContainerExecutor(SandboxHostPathResolver hostPathResolver) {
        this(ProcessBuilder::start, hostPathResolver);
    }

    public OopContainerExecutor(ProcessLauncher processLauncher, SandboxHostPathResolver hostPathResolver) {
        this.processLauncher = processLauncher != null ? processLauncher : ProcessBuilder::start;
        this.hostPathResolver = hostPathResolver != null ? hostPathResolver : new SandboxHostPathResolver();
    }

    /**
     * Đo số liệu của mã nguồn đã biên dịch bằng cách chạy một lớp probe.
     *
     * @param workDir        thư mục làm việc chứa {@code classes/}
     * @param probeClassName lớp probe cần chạy (VD: {@code __OopMetricsProbe})
     * @param input          nội dung stdin
     * @param timeoutMs      giới hạn thời gian cho code thí sinh
     * @param memoryLimitMb  giới hạn bộ nhớ của container
     */
    public OopExecutionMetrics measure(Path workDir, String probeClassName, String input,
                                        long timeoutMs, int memoryLimitMb) {
        SandboxResult result = runClass(workDir, probeClassName, List.of(), input, timeoutMs, memoryLimitMb);
        if (!result.measured()) {
            return OopExecutionMetrics.unavailable(result.errorMessage());
        }
        return new OopExecutionMetrics(
                result.runtimeMs(),
                result.peakMemoryKb(),
                result.exitCode(),
                result.stdout(),
                result.stderr(),
                result.timedOut(),
                result.oomKilled(),
                result.truncated(),
                true,
                null);
    }

    /**
     * Chạy một lớp đã biên dịch trong container sandbox và trả về đầy đủ
     * stdout/stderr/số đo.
     *
     * @param workDir       thư mục làm việc chứa {@code classes/}
     * @param className     tên lớp đầy đủ (VD: {@code com.example.Circle})
     * @param args          tham số truyền cho {@code main}
     * @param input         nội dung stdin
     * @param timeoutMs     giới hạn thời gian cho code thí sinh
     * @param memoryLimitMb giới hạn bộ nhớ của container
     */
    public SandboxResult runClass(Path workDir, String className, List<String> args, String input,
                                   long timeoutMs, int memoryLimitMb) {
        Path classesDir = workDir.resolve(CLASSES_DIR);
        if (!Files.isDirectory(classesDir)) {
            return SandboxResult.unavailable("Compiled classes directory not found: " + classesDir);
        }
        if (className == null || className.isBlank()) {
            return SandboxResult.unavailable("Class name is required");
        }

        String containerName = "judge-oop-probe-" + UUID.randomUUID();
        outputTruncated.set(false);
        try {
            Files.writeString(workDir.resolve(SCRIPT_FILE),
                    LanguageRuntime.runScriptFor(buildRunCommand(className, args, memoryLimitMb)));

            List<String> command = buildDockerCommand(containerName, workDir, memoryLimitMb);
            log.info("Starting OOP sandbox container {} with command: {}", containerName, String.join(" ", command));

            Process process = processLauncher.launch(new ProcessBuilder(command));
            CompletableFuture<String> stdoutFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()), ioExecutor);
            CompletableFuture<String> stderrFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream()), ioExecutor);

            // Ghi stdin bất đồng bộ để không bị nghẽn pipe khi chương trình không đọc input.
            CompletableFuture.runAsync(() -> {
                try (var stdin = process.getOutputStream()) {
                    stdin.write((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // pipe vỡ khi container bị kill — bình thường.
                }
            }, ioExecutor);

            long effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
            long hardTimeoutMs = effectiveTimeout + STARTUP_ALLOWANCE_MS;

            boolean finished = process.waitFor(hardTimeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("OOP sandbox container {} timed out after {} ms", containerName, hardTimeoutMs);
                runDockerQuietly("kill", containerName);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return SandboxResult.timedOut(effectiveTimeout);
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            boolean truncated = outputTruncated.get();

            if (exitCode == 137) {
                log.warn("OOP sandbox container {} was killed with exit code 137 (OOM)", containerName);
                return SandboxResult.oomKilled(stdout, stderr);
            }

            String inspect = runDocker("inspect", containerName,
                    "--format", "{{.State.OOMKilled}} {{.State.ExitCode}} {{.State.StartedAt}} {{.State.FinishedAt}}");
            String[] state = inspect.trim().split("\\s+");
            if (state.length < 4) {
                return SandboxResult.unavailable(
                        "docker inspect failed: " + inspect.trim() + " | exitCode: " + exitCode);
            }
            boolean oomKilled = Boolean.parseBoolean(state[0]);
            int containerExitCode = parseIntOrDefault(state[1], exitCode);
            if (state[2].startsWith("0001-01-01")) {
                return SandboxResult.unavailable("Container never started (StartedAt=" + state[2] + ")");
            }
            Instant startedAt = Instant.parse(state[2]);
            Instant finishedAt = Instant.parse(state[3]);
            int runtimeMs = (int) Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
            boolean timedOut = runtimeMs > effectiveTimeout;

            MemoryProbe probe = MemoryProbe.parse(stderr);
            String userStderr = probe.cleanStderr();
            Integer peakKb = probe.peakMemoryKb();

            if (peakKb == null) {
                log.warn("No {} sentinel in stderr of OOP sandbox container {}; peak memory not measured",
                        LanguageRuntime.MEMORY_SENTINEL, containerName);
            }

            return new SandboxResult(runtimeMs, peakKb, containerExitCode, stdout, userStderr,
                    timedOut, oomKilled, truncated, true, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runDockerQuietly("kill", containerName);
            return SandboxResult.unavailable("Execution was interrupted: " + e.getMessage());
        } catch (IOException e) {
            log.error("Failed to launch OOP sandbox container {}", containerName, e);
            return SandboxResult.unavailable("Docker execution failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error while running OOP sandbox container {}", containerName, e);
            return SandboxResult.unavailable("Docker execution failed: " + e.getMessage());
        } finally {
            cleanupContainerQuietly(containerName);
        }
    }

    /**
     * Lệnh chạy bên trong container.
     *
     * <p>Classpath là {@code /workspace/classes} vì probe/class của học viên nằm ở đó.
     */
    List<String> buildRunCommand(String className, List<String> args, int memoryLimitMb) {
        int heapMb = Math.max(32, (int) (memoryLimitMb * 0.75));
        List<String> command = new ArrayList<>(List.of(
                "java", "-Xmx" + heapMb + "m", "-XX:+UseSerialGC",
                "-cp", "/workspace/classes", className));
        if (args != null) {
            command.addAll(args);
        }
        return command;
    }

    /**
     * Lệnh {@code docker run} đầy đủ, với đúng các cờ sandbox của bài thuật toán.
     *
     * <p>KHÔNG dùng {@code --rm}: sau khi container kết thúc, ta còn cần đọc
     * {@code docker inspect} để lấy {@code StartedAt}/{@code FinishedAt} và
     * {@code OOMKilled}. Với {@code --rm}, Docker xoá container ngay khi nó exit
     * nên {@code inspect} trả về lỗi "No such container" ⇒ không đo được số liệu.
     * Việc dọn dẹp được đảm bảo bởi {@link #cleanupContainerQuietly(String)}.
     */
    public List<String> buildDockerCommand(String containerName, Path workDir, int memoryLimitMb) {
        String mount = hostPathResolver.resolveHostPath(workDir) + ":/workspace:ro";
        String mem = memoryLimitMb + "m";
        return new ArrayList<>(List.of(
                "docker", "run", "-i",
                "--name", containerName,
                "--network", "none",
                "--memory", mem,
                "--memory-swap", mem,
                "--cpus", "1.0",
                "--pids-limit", "64",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges:true",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--user", "1000:1000",
                "-v", mount,
                DEFAULT_RUNNER_IMAGE,
                "sh", "/workspace/" + SCRIPT_FILE));
    }

    private static int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void cleanupContainerQuietly(String containerName) {
        runDockerQuietly("rm", "-f", containerName);
    }

    private void runDockerQuietly(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Quiet cleanup
        }
    }

    private String runDocker(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        } catch (IOException e) {
            return "docker inspect failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "docker inspect interrupted";
        }
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
                } else if (n > 0) {
                    outputTruncated.set(true);
                }
            }
        } catch (IOException ignored) {
            // luồng bị đóng khi container bị kill — giữ phần đã đọc được.
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * Kết quả chạy một lớp trong container sandbox.
     *
     * @param measured {@code true} khi container đã chạy thật
     */
    public record SandboxResult(
            Integer runtimeMs,
            Integer peakMemoryKb,
            Integer exitCode,
            String stdout,
            String stderr,
            boolean timedOut,
            boolean oomKilled,
            boolean truncated,
            boolean measured,
            String errorMessage
    ) {
        public static SandboxResult unavailable(String errorMessage) {
            return new SandboxResult(null, null, null, "", "", false, false, false, false, errorMessage);
        }

        public static SandboxResult timedOut(long timeoutMs) {
            return new SandboxResult(null, null, 124, "", "Execution timed out after " + timeoutMs + "ms",
                    true, false, false, true, null);
        }

        public static SandboxResult oomKilled(String stdout, String stderr) {
            return new SandboxResult(null, null, 137, stdout,
                    stderr + (stderr == null || stderr.isBlank() ? "" : "\n") + "Memory Limit Exceeded",
                    false, true, false, true, null);
        }
    }
}
