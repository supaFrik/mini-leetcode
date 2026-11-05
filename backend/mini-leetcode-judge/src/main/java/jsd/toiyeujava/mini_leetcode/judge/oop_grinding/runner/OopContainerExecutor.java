package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import jsd.toiyeujava.mini_leetcode.judge.LanguageRuntime;
import jsd.toiyeujava.mini_leetcode.judge.MemoryProbe;
import jsd.toiyeujava.mini_leetcode.judge.SandboxHostPathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
 * Chạy một lớp Java đã biên dịch (console OOP) trong MỘT container Docker ephemeral
 * với các cờ sandbox của bài thuật toán; đo wall-clock qua
 * {@code docker inspect StartedAt/FinishedAt} và peak memory qua sentinel
 * {@code __MEM_PEAK_KB__} ({@link MemoryProbe}).
 *
 * <p>TUYỆT ĐỐI không fallback sang chạy in-process: Docker không khả dụng ⇒
 * {@link SandboxResult#unavailable(String)}.
 */
@Slf4j
@Component
public class OopContainerExecutor {

    public static final String DEFAULT_RUNNER_IMAGE = "eclipse-temurin:17-jdk";
    public static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    public static final int MAX_OUTPUT_BYTES = 64 * 1024;
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;
    public static final String CLASSES_DIR = "classes";
    // ponytail: hằng số khớp app.console.startup-allowance-ms mặc định; nối vào ConsoleProperties nếu cần chỉnh.
    private static final long STARTUP_ALLOWANCE_MS = 2_000L;
    private static final String SCRIPT_FILE = "oop-probe-run.sh";

    private final ProcessLauncher processLauncher;
    private final SandboxHostPathResolver hostPathResolver;
    private final AtomicInteger threadCounter = new AtomicInteger();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "oop-container-io-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public OopContainerExecutor(SandboxHostPathResolver hostPathResolver) {
        this(ProcessBuilder::start, hostPathResolver);
    }

    public OopContainerExecutor(ProcessLauncher processLauncher, SandboxHostPathResolver hostPathResolver) {
        this.processLauncher = processLauncher != null ? processLauncher : ProcessBuilder::start;
        this.hostPathResolver = hostPathResolver != null ? hostPathResolver : new SandboxHostPathResolver();
    }

    /**
     * Kéo sẵn image sandbox khi worker khởi động. Nếu không, lần chạy đầu trên máy
     * mới sẽ tính cả thời gian {@code docker pull} vào timeout ⇒ báo "timed out" sai.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void prePullRunnerImage() {
        try {
            new ProcessBuilder("docker", "pull", DEFAULT_RUNNER_IMAGE)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            log.warn("Could not pre-pull sandbox image {}: {}", DEFAULT_RUNNER_IMAGE, e.getMessage());
        }
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
        // Cục bộ theo từng lần chạy: executor là singleton dùng chung giữa các request.
        AtomicBoolean outputTruncated = new AtomicBoolean(false);
        try {
            Files.writeString(workDir.resolve(SCRIPT_FILE),
                    LanguageRuntime.runScriptFor(buildRunCommand(className, args, memoryLimitMb)));

            List<String> command = buildDockerCommand(containerName, workDir, memoryLimitMb);
            log.info("Starting OOP sandbox container {} with command: {}", containerName, String.join(" ", command));

            Process process = processLauncher.launch(new ProcessBuilder(command));
            CompletableFuture<String> stdoutFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream(), outputTruncated), ioExecutor);
            CompletableFuture<String> stderrFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream(), outputTruncated), ioExecutor);

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
                // `docker rm -f` trong finally đã kill container; không cần `docker kill` riêng (tốn thêm ~3s).
                process.destroyForcibly();
                // Giữ phần output chương trình đã in trước khi treo (client chết ⇒ pipe EOF).
                String partialOut = stdoutFuture.completeOnTimeout("", 1, TimeUnit.SECONDS).join();
                String partialErr = stderrFuture.completeOnTimeout("", 1, TimeUnit.SECONDS).join();
                return SandboxResult.timedOut(effectiveTimeout, partialOut,
                        MemoryProbe.parse(partialErr).cleanStderr(), outputTruncated.get());
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            boolean truncated = outputTruncated.get();

            String inspect = runDocker("inspect", containerName,
                    "--format", "{{.State.OOMKilled}} {{.State.ExitCode}} {{.State.StartedAt}} {{.State.FinishedAt}}");
            String[] state = inspect.trim().split("\\s+");
            if (state.length < 4) {
                return SandboxResult.unavailable(
                        "docker inspect failed: " + inspect.trim() + " | exitCode: " + exitCode);
            }
            boolean oomKilled = Boolean.parseBoolean(state[0]);
            int containerExitCode = parseIntOrDefault(state[1], exitCode);
            // Chỉ tin cờ OOMKilled của Docker: exit 137 cũng có thể là System.exit(137) hay SIGKILL khác.
            if (oomKilled) {
                log.warn("OOP sandbox container {} was OOM-killed", containerName);
                return SandboxResult.oomKilled(stdout, MemoryProbe.parse(stderr).cleanStderr());
            }
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

    /** Số byte cuối luôn được giữ lại để không mất sentinel bộ nhớ khi output bị cắt. */
    private static final int TAIL_BYTES = 256;

    String readLimited(InputStream in, AtomicBoolean outputTruncated) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        byte[] tail = new byte[0];
        boolean overflowed = false;
        try {
            int n;
            while ((n = in.read(chunk)) != -1) {
                int room = MAX_OUTPUT_BYTES - buffer.size();
                if (room > 0) {
                    buffer.write(chunk, 0, Math.min(n, room));
                }
                if (n > room) {
                    overflowed = true;
                    outputTruncated.set(true);
                }
                // Giữ TAIL_BYTES cuối cùng đã đọc.
                byte[] joined = new byte[tail.length + n];
                System.arraycopy(tail, 0, joined, 0, tail.length);
                System.arraycopy(chunk, 0, joined, tail.length, n);
                tail = java.util.Arrays.copyOfRange(joined, Math.max(0, joined.length - TAIL_BYTES), joined.length);
            }
        } catch (IOException ignored) {
            // luồng bị đóng khi container bị kill — giữ phần đã đọc được.
        }
        String out = buffer.toString(StandardCharsets.UTF_8);
        if (overflowed) {
            // Sentinel __MEM_PEAK_KB__ là dòng cuối stderr: phần đầu bị cắt thì lấy lại từ đuôi.
            String tailText = new String(tail, StandardCharsets.UTF_8);
            int i = tailText.lastIndexOf(LanguageRuntime.MEMORY_SENTINEL);
            if (i >= 0) {
                out += "\n" + tailText.substring(i);
            }
        }
        return out;
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

        public static SandboxResult timedOut(long timeoutMs, String stdout, String stderr, boolean truncated) {
            String message = "Execution timed out after " + timeoutMs + "ms";
            return new SandboxResult(null, null, 124, stdout,
                    stderr == null || stderr.isBlank() ? message : stderr + "\n" + message,
                    true, false, truncated, true, null);
        }

        public static SandboxResult oomKilled(String stdout, String stderr) {
            return new SandboxResult(null, null, 137, stdout,
                    stderr + (stderr == null || stderr.isBlank() ? "" : "\n") + "Memory Limit Exceeded",
                    false, true, false, true, null);
        }
    }
}
