package jsd.toiyeujava.mini_leetcode.judge.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.SandboxHostPathResolver;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopMainClassDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Một lần "Run" của console live: biên dịch (javac in-process, không thực thi), rồi chạy
 * {@code main} trong container sandbox {@code docker run -i}, chuyển stdout/stderr ra WebSocket
 * ngay khi đọc được và đẩy stdin của người dùng vào tiến trình.
 *
 * <p>Mã nguồn học viên CHỈ chạy trong container. Mọi đường thoát đều {@code docker rm -f}.
 */
@Slf4j
class LiveConsoleRun {

    /** Tên lớp main hợp lệ — chặn mọi ký tự shell trước khi ghi vào script. */
    static final String CLASS_NAME_PATTERN = "[A-Za-z_$][A-Za-z0-9_$]*([.][A-Za-z_$][A-Za-z0-9_$]*)*";

    record SourceFile(String path, String content) {
    }

    private final WebSocketSession out;
    private final ConsoleProperties props;
    private final OopGrindingCompiler compiler;
    private final MultiFileSourceExtractor extractor;
    private final OopContainerExecutor containerExecutor;
    private final ObjectMapper mapper;

    private final String containerName = "judge-console-" + UUID.randomUUID();
    private final AtomicReference<String> killReason = new AtomicReference<>();
    private final AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger outputBytes = new AtomicInteger();
    /** stdin gõ trước khi container kịp khởi động (type-ahead) — ghi vào ngay khi có tiến trình. */
    private final ByteArrayOutputStream pendingStdin = new ByteArrayOutputStream();
    private boolean pendingEof;
    /** volatile: kill() đọc không cần khoá, để không bị chặn khi stdin() đang ghi vào pipe đầy. */
    private volatile Process process;

    LiveConsoleRun(WebSocketSession out, ConsoleProperties props, OopGrindingCompiler compiler,
                   MultiFileSourceExtractor extractor, OopContainerExecutor containerExecutor, ObjectMapper mapper) {
        this.out = out;
        this.props = props;
        this.compiler = compiler;
        this.extractor = extractor;
        this.containerExecutor = containerExecutor;
        this.mapper = mapper;
    }

    void run(List<SourceFile> files) {
        Path workDir = null;
        long start = System.currentTimeMillis();
        try {
            workDir = writeSources(files);
            List<Path> sources;
            try (var walk = Files.walk(workDir.resolve("src"))) {
                sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
            }
            if (sources.isEmpty()) {
                send(frame("error", "message", "No Java source files provided"));
                return;
            }
            CompileResult compiled = compiler.compile(workDir, sources);
            if (!compiled.success()) {
                // Bỏ tiền tố thư mục tạm "src/" để lỗi hiện đúng tên file như trong editor.
                send(frame("stderr", "data", compiled.output().replaceAll("(?m)^src/", "") + "\n"));
                sendExit(null, System.currentTimeMillis() - start, "compile-error");
                return;
            }
            Optional<String> mainClass = OopMainClassDetector.detectMainClass(workDir.resolve("classes"));
            if (mainClass.isEmpty() || !mainClass.get().matches(CLASS_NAME_PATTERN)) {
                send(frame("stderr", "data", "No 'public static void main(String[] args)' found in your code.\n"));
                sendExit(null, System.currentTimeMillis() - start, "compile-error");
                return;
            }
            Files.writeString(workDir.resolve(OopContainerExecutor.SCRIPT_FILE),
                    runScript(mainClass.get(), props.getMemoryLimitMb()));

            if (!startProcess(workDir)) {
                if (killReason.get() != null) {
                    sendExit(null, System.currentTimeMillis() - start, killReason.get());
                }
                return;
            }
            if (killReason.get() != null) {
                kill(killReason.get()); // kill tới đúng lúc container vừa khởi động
            }
            start = System.currentTimeMillis();
            Thread stdoutPump = pump(process.getInputStream(), "stdout");
            Thread stderrPump = pump(process.getErrorStream(), "stderr");
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                long now = System.currentTimeMillis();
                if (now - start > props.getMaxWallMs()) {
                    kill("timeout");
                } else if (now - lastActivity.get() > props.getIdleMs()) {
                    kill("idle");
                }
            }
            stdoutPump.join(2_000);
            stderrPump.join(2_000);
            sendExit(process.exitValue(), System.currentTimeMillis() - start, killReason.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Console run {} failed", containerName, e);
            send(frame("error", "message", "Console run failed"));
        } finally {
            if (process != null) {
                process.destroyForcibly();
                docker("rm", "-f", containerName);
            }
            deleteQuietly(workDir);
            try {
                out.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
                // đã đóng
            }
        }
    }

    /** Script chạy trong container; {@code exec} để tín hiệu kill tới thẳng JVM. */
    static String runScript(String mainClass, int memoryLimitMb) {
        if (!mainClass.matches(CLASS_NAME_PATTERN)) {
            throw new IllegalArgumentException("Invalid main class: " + mainClass);
        }
        int heapMb = Math.max(32, (int) (memoryLimitMb * 0.75));
        return "#!/bin/sh\nexec java -Xmx" + heapMb + "m -XX:+UseSerialGC -cp /workspace/classes " + mainClass + "\n";
    }

    /** Khởi động container; trả false nếu đã bị kill trong lúc biên dịch hoặc Docker không chạy được. */
    private synchronized boolean startProcess(Path workDir) throws IOException {
        if (killReason.get() != null) {
            return false;
        }
        List<String> command = containerExecutor.buildDockerCommand(containerName, workDir, props.getMemoryLimitMb());
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.error("Cannot launch console sandbox: {}", e.getMessage());
            send(frame("error", "message", "Docker sandbox is unavailable"));
            return false;
        }
        lastActivity.set(System.currentTimeMillis());
        OutputStream stdin = process.getOutputStream();
        pendingStdin.writeTo(stdin);
        stdin.flush();
        if (pendingEof) {
            stdin.close();
        }
        return true;
    }

    /** Dòng người dùng gõ; ghi ngay vào stdin của tiến trình (hoặc giữ lại nếu chưa khởi động). */
    synchronized void stdin(String data) {
        lastActivity.set(System.currentTimeMillis());
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        try {
            if (process == null) {
                pendingStdin.write(bytes);
            } else {
                process.getOutputStream().write(bytes);
                process.getOutputStream().flush();
            }
        } catch (IOException ignored) {
            // stdin đã đóng (EOF hoặc tiến trình đã thoát)
        }
    }

    /** Đóng stdin — tương đương Ctrl+D trong terminal. */
    synchronized void eof() {
        if (process == null) {
            pendingEof = true;
            return;
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // đã đóng
        }
    }

    /** Dừng lần chạy; lý do đầu tiên được giữ lại để báo về client. */
    void kill(String reason) {
        killReason.compareAndSet(null, reason);
        Process p = process;
        if (p != null && p.isAlive()) {
            docker("kill", containerName);
            p.destroyForcibly();
        }
    }

    private Thread pump(InputStream stream, String type) {
        Thread thread = new Thread(() -> {
            char[] buffer = new char[4096];
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, n);
                    lastActivity.set(System.currentTimeMillis());
                    if (outputBytes.addAndGet(chunk.getBytes(StandardCharsets.UTF_8).length) > props.getMaxOutputBytes()) {
                        kill("output-limit");
                        return;
                    }
                    send(frame(type, "data", chunk));
                }
            } catch (IOException ignored) {
                // luồng đóng khi container bị kill
            }
        }, containerName + "-" + type);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private Path writeSources(List<SourceFile> files) throws IOException {
        Path root = Path.of(SandboxHostPathResolver.CONSOLE_ROOT_NAME);
        Files.createDirectories(root);
        Path workDir = Files.createTempDirectory(root, "live-");
        Path srcDir = workDir.resolve("src");
        Files.createDirectories(srcDir);
        relax(workDir);
        relax(srcDir);
        Path srcNorm = srcDir.toAbsolutePath().normalize();
        for (SourceFile file : files) {
            Path target = extractor.validateAndResolvePath(file.path(), srcNorm);
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content() == null ? "" : file.content(), StandardCharsets.UTF_8);
        }
        return workDir;
    }

    /** Container chạy user 1000:1000 — thư mục tạm mặc định 700 thì nó không đọc được. */
    private static void relax(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows NTFS
        }
    }

    private void sendExit(Integer code, long elapsedMs, String reason) {
        Map<String, Object> exit = new LinkedHashMap<>();
        exit.put("type", "exit");
        exit.put("code", code);
        exit.put("elapsedMs", elapsedMs);
        exit.put("reason", reason);
        send(exit);
        log.info("Console run {} finished code={} reason={} elapsedMs={}", containerName, code, reason, elapsedMs);
    }

    private static Map<String, Object> frame(String type, String key, String value) {
        return Map.of("type", type, key, value);
    }

    private void send(Map<String, Object> payload) {
        try {
            out.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            kill("killed"); // client đã ngắt — không giữ container vô ích
        }
    }

    private static void docker(String... args) {
        try {
            List<String> cmd = new ArrayList<>(List.of("docker"));
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException ignored) {
            // docker không có — không còn gì để dọn
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // dọn dẹp best-effort
        }
    }
}
