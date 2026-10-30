package jsd.toiyeujava.mini_leetcode.judge;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DockerExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerExecutor.class);

    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final String CPU_LIMIT = "1";
    private static final String PIDS_LIMIT = "64";
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final int MAX_STDERR_BYTES = 8 * 1024;
    private static final long COMPILE_TIMEOUT_MS = 30_000;
    private static final long STARTUP_ALLOWANCE_MS = 5_000;

    // Thư mục chứa file nộp bài tạm; giống với TEMP_ROOT của JudgeServiceImpl.
    private static final String TEMP_ROOT_NAME = "temp-submissions";

    // Base path trên HOST mà Docker daemon dùng để bind-mount. Cached sau lần phát hiện đầu.
    private volatile String cachedHostBaseDir;

    // Executor riêng cho blocking process I/O (stdout, stdin, stderr) thay vì dùng ForkJoinPool.commonPool
    private final AtomicInteger threadCounter = new AtomicInteger();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "docker-io-" + threadCounter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void destroy() {
        ioExecutor.shutdown();
    }

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
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()), ioExecutor);

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
     * Pha CHẠY với giới hạn bộ nhớ mặc định 256MB.
     */
    public ExecutionResult execute(Path workDir, LanguageRuntime runtime, String input, long timeLimitMs)
            throws IOException, InterruptedException {
        return execute(workDir, runtime, input, timeLimitMs, DEFAULT_MEMORY_LIMIT_MB);
    }

    /**
     * Pha CHẠY (mỗi testcase một container mới) với giới hạn bộ nhớ cấu hình theo bài toán.
     * <p>
     * peakMemoryKb được đo từ {@code /sys/fs/cgroup/memory.peak} của container
     * (đọc từ bên trong container qua wrapper {@code sh -c}); trả về 0 nếu
     * container bị kill trước khi kịp báo (ví dụ hết thời gian).
     * runtimeMs tính từ FinishedAt - StartedAt của container (loại trừ thời gian tạo container,
     * nhưng vẫn bao gồm thời gian khởi động JVM với Java).
     */
    public ExecutionResult execute(Path workDir, LanguageRuntime runtime, String input, long timeLimitMs, int memoryLimitMb)
            throws IOException, InterruptedException {
        String containerName = "judge-" + UUID.randomUUID();
        try {
            writeRunScript(workDir, runtime, memoryLimitMb);
            List<String> runCommand = buildRunCommand(containerName, workDir, runtime, memoryLimitMb);
            log.info("Starting sandbox container {} with command: {}", containerName, String.join(" ", runCommand));
            Process process = new ProcessBuilder(runCommand)
                    .start();
            CompletableFuture<String> stdoutFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream(), MAX_OUTPUT_BYTES), ioExecutor);
            // Đọc stderr bất đồng bộ với giới hạn 8KB, drain sạch để tiến trình không bị nghẽn pipe
            CompletableFuture<String> stderrFuture =
                    CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream(), MAX_STDERR_BYTES), ioExecutor);

            // Ghi stdin bất đồng bộ: tránh block khi pipe đầy (chương trình không đọc input).
            // IOException khi pipe vỡ (container bị kill) là bình thường, bỏ qua.
            CompletableFuture.runAsync(() -> {
                try (var stdin = process.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // pipe vỡ khi container bị kill trước khi đọc hết stdin
                }
            }, ioExecutor);

            // Hard timeout cộng thêm thời gian khởi động container
            long hardTimeoutMs = timeLimitMs + STARTUP_ALLOWANCE_MS;
            boolean finished = process.waitFor(hardTimeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                runDocker("kill", containerName);
                process.destroyForcibly();
                process.waitFor();
            }
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            int runExitCode = process.exitValue();

            String raw = runDocker("inspect", containerName,
                    "--format", "{{.State.OOMKilled}} {{.State.ExitCode}} {{.State.StartedAt}} {{.State.FinishedAt}}");
            String[] state = raw.split(" ");
            if (state.length != 4) {
                // Container không tồn tại (thường do `docker run` thất bại trước khi tạo container,
                // ví dụ mount path sai). Lỗi thật nằm ở stderr của docker CLI, nên phải gom lại.
                throw new IllegalStateException("docker inspect failed: " + raw
                        + " | docker run exitCode: " + runExitCode
                        + " | docker run stdout: " + truncateForLog(stdout)
                        + " | docker run stderr: " + truncateForLog(stderr));
            }
            boolean oomKilled = Boolean.parseBoolean(state[0]);
            int exitCode = Integer.parseInt(state[1]);
            // Nếu exit code trùng mã OOM của runtime (ví dụ JVM ExitOnOutOfMemoryError), phân loại là MLE
            if (runtime.oomExitCode().map(code -> code == exitCode).orElse(false)) {
                oomKilled = true;
            }
            String startedAtRaw = state[2];
            String finishedAtRaw = state[3];

            if (startedAtRaw.startsWith("0001-01-01")) {
                throw new IllegalStateException("Container never started (StartedAt=" + startedAtRaw + ")");
            }

            Instant startedAt = Instant.parse(startedAtRaw);
            Instant finishedAt = Instant.parse(finishedAtRaw);
            int runtimeMs = (int) Math.max(0, Duration.between(startedAt, finishedAt).toMillis());

            boolean timedOut = !finished || (runtimeMs > timeLimitMs);

            // Wrapper `sh -c` in peak memory của cgroup lên stderr; tách nó ra khỏi
            // stderr để thông báo lỗi cho người dùng không bị nhiễu.
            MemoryProbe probe = MemoryProbe.parse(stderr);
            String userStderr = probe.cleanStderr();

            return new ExecutionResult(exitCode, timedOut, oomKilled, stdout, runtimeMs,
                    probe.peakMemoryKb() == null ? 0 : probe.peakMemoryKb(), userStderr);
        } finally {
            runDocker("rm", "-f", containerName);
        }
    }

    public List<String> buildRunCommand(String containerName, Path workDir, LanguageRuntime runtime) {
        return buildRunCommand(containerName, workDir, runtime, DEFAULT_MEMORY_LIMIT_MB);
    }

    /**
     * Ghi script chạy (có wrapper đo peak memory) vào thư mục làm việc.
     *
     * <p>Script được ghi ra file (thay vì truyền làm tham số {@code sh -c})
     * vì trên Windows {@code ProcessBuilder} truyền tham số qua command line
     * của Windows, nơi các dấu nháy của POSIX shell sẽ bị diễn giải lại và
     * làm hỏng script.
     */
    private void writeRunScript(Path workDir, LanguageRuntime runtime, int memoryLimitMb) throws IOException {
        Files.writeString(workDir.resolve(LanguageRuntime.RUN_SCRIPT_FILE), runtime.runScript(memoryLimitMb));
    }

    public List<String> buildRunCommand(String containerName, Path workDir, LanguageRuntime runtime, int memoryLimitMb) {
        return buildCommand(containerName, workDir, runtime, true, runtime.runCommand(memoryLimitMb), memoryLimitMb);
    }

    public List<String> buildCompileCommand(String containerName, Path workDir, LanguageRuntime runtime) {
        return buildCommand(containerName, workDir, runtime, false, runtime.compileCommand(), DEFAULT_MEMORY_LIMIT_MB);
    }

    private List<String> buildCommand(String containerName, Path workDir, LanguageRuntime runtime,
                                      boolean runPhase, List<String> commandInContainer, int memoryLimitMb) {
        String mount = resolveHostPath(workDir) + ":/workspace" + (runPhase ? ":ro" : "");
        String memLimit = memoryLimitMb + "m";
        List<String> command = new ArrayList<>(List.of("docker", "run"));
        if (runPhase) {
            command.add("-i");
            // Chạy non-root user (1000:1000) ở pha run để hạn chế tối đa quyền của code thí sinh.
            // TODO: Pha compile giữ user mặc định (root) vì javac cần quyền ghi Solution.class vào mount /workspace.
            // Hệ quả: trên host Linux, file .class do root tạo có thể khiến deleteQuietly thất bại nếu worker không chạy root.
            command.addAll(List.of("--user", "1000:1000"));
        }
        command.addAll(List.of(
                "--name", containerName,
                "--network", "none",
                // Hardening sandbox: tước bỏ đặc quyền, ngăn leo thang quyền, filesystem chỉ đọc, cấp /tmp trên RAM, giới hạn file descriptor
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges:true",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--ulimit", "nofile=256:256",
                "--memory", memLimit,
                "--memory-swap", memLimit,
                "--cpus", CPU_LIMIT,
                "--pids-limit", PIDS_LIMIT,
                "-v", mount,
                runtime.image()));
        command.addAll(commandInContainer);
        return command;
    }

    /**
     * Xác định đường dẫn trên HOST để bind-mount vào /workspace của sandbox container.
     *
     * <p>Đường dẫn trong {@code -v} do Docker daemon trên HOST phân giải, nên nó phải là đường dẫn
     * nhìn thấy được từ HOST — không phải đường dẫn bên trong container của judge worker.
     * Vì vậy ta tự phát hiện bằng cách đọc bảng mount của chính container đang chạy
     * ({@code /proc/self/mountinfo}) để tìm mount có đích khớp thư mục temp-submissions.
     */
    private String resolveHostPath(Path workDir) {
        String hostBase = resolveHostBaseDir();
        if (hostBase != null) {
            return (hostBase + "/" + workDir.getFileName()).replace('\\', '/');
        }
        // Fallback: nếu không phát hiện được (chạy ngoài Docker), dùng đường dẫn tuyệt đối trong container.
        return workDir.toAbsolutePath().toString().replace('\\', '/');
    }

    private String resolveHostBaseDir() {
        String cached = cachedHostBaseDir;
        if (cached != null) {
            return cached;
        }
        String detected = detectHostBaseDirFromDaemon();
        if (detected == null) {
            detected = detectHostBaseDirFromEnv();
        }
        if (detected != null) {
            cachedHostBaseDir = detected;
            log.info("Resolved sandbox bind-mount host base dir: {}", detected);
        } else {
            log.warn("Could not detect host base dir for temp-submissions; "
                    + "sandbox containers may not see submitted source files. "
                    + "Set HOST_WORK_DIR explicitly to the host path of the temp-submissions directory.");
        }
        return detected;
    }

    /**
     * Hỏi chính Docker daemon về đường dẫn HOST thật sự của thư mục temp-submissions.
     *
     * <p>Cách này đáng tin cậy hơn việc đọc {@code /proc/self/mountinfo}: trên Docker Desktop
     * (Windows/macOS), trường "source" của mountinfo bị biến đổi thành dạng 9p khó hiểu
     * (ví dụ {@code C:\134}) và không dùng làm đường dẫn cho {@code docker run -v} được.
     * {@code docker inspect} trả về đúng đường dẫn mà daemon dùng để bind-mount.
     */
    private String detectHostBaseDirFromDaemon() {
        try {
            String selfId = readSelfContainerId();
            if (selfId == null) {
                return null;
            }
            String output = runDocker("inspect",
                    "--format", "{{range .Mounts}}{{.Destination}}|{{.Source}}{{println}}{{end}}",
                    selfId);
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('|');
                if (separator < 0) {
                    continue;
                }
                String destination = trimmed.substring(0, separator);
                String source = trimmed.substring(separator + 1).trim();
                if (destination.endsWith("/" + TEMP_ROOT_NAME) && !source.isEmpty()) {
                    return stripTrailingSeparators(source);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to detect host base dir via docker inspect: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Lấy ID container của chính judge worker. Ưu tiên hostname (mặc định là container ID),
     * fallback sang đọc {@code /etc/hostname} hoặc {@code /proc/self/cgroup}.
     */
    private String readSelfContainerId() throws IOException {
        Path hostname = Path.of("/etc/hostname");
        if (Files.isReadable(hostname)) {
            String value = Files.readString(hostname).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Fallback khi không hỏi được Docker daemon: dùng biến môi trường HOST_WORK_DIR.
     * Giá trị này phải là đường dẫn trên HOST (được Docker Desktop/daemon phân giải).
     */
    private String detectHostBaseDirFromEnv() {
        String hostWorkDir = System.getenv("HOST_WORK_DIR");
        if (hostWorkDir == null || hostWorkDir.isBlank()) {
            return null;
        }
        return stripTrailingSeparators(hostWorkDir);
    }

    private String stripTrailingSeparators(String path) {
        return path.replaceAll("[/\\\\]+$", "");
    }

    /** Rút gọn output dài để ghi log, không làm mất thông tin quan trọng ở đầu/cuối. */
    private static String truncateForLog(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 800) {
            return trimmed;
        }
        return trimmed.substring(0, 400) + " ... " + trimmed.substring(trimmed.length() - 400);
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
        return readLimited(in, MAX_OUTPUT_BYTES);
    }

    private String readLimited(InputStream in, int maxBytes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int n;
            while ((n = in.read(chunk)) != -1) {
                int room = maxBytes - buffer.size();
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
