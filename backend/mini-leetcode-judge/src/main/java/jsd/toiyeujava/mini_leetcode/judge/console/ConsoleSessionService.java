package jsd.toiyeujava.mini_leetcode.judge.console;

import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopMainClassDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Console OOP kiểu terminal IDE: biên dịch mã nguồn, chạy {@code main} với
 * {@code input} làm stdin, trả stdout/stderr.
 *
 * <p>Mã nguồn học viên CHỈ chạy trong container Docker. Docker không khả dụng
 * ⇒ trả lỗi, KHÔNG fallback in-process. Mỗi lần chạy dùng một thư mục tạm riêng
 * và xoá ngay sau đó.
 */
@Slf4j
@Service
public class ConsoleSessionService {

    /** Thư mục gốc; được bind-mount ra HOST (xem {@code SandboxHostPathResolver}). */
    private static final String CONSOLE_ROOT = "temp-console";

    private final ConsoleProperties properties;
    private final MultiFileSourceExtractor sourceExtractor;
    private final OopGrindingCompiler compiler;
    private final OopContainerExecutor containerExecutor;
    private final Semaphore runSlots;

    public ConsoleSessionService(ConsoleProperties properties,
                                 MultiFileSourceExtractor sourceExtractor,
                                 OopGrindingCompiler compiler,
                                 OopContainerExecutor containerExecutor) {
        this.properties = properties;
        this.sourceExtractor = sourceExtractor;
        this.compiler = compiler;
        this.containerExecutor = containerExecutor;
        this.runSlots = new Semaphore(Math.max(1, properties.getMaxConcurrentRuns()));
    }

    public ConsoleResponse execute(ConsoleRequest request) {
        if (request == null) {
            return ConsoleResponse.failure("Invalid console request");
        }
        // Mỗi lượt chạy = 1 container 256MB; không chặn thì N request song song có thể làm host hết RAM
        // và tranh tài nguyên với việc chấm bài. Đầy ⇒ 503 ngay, không xếp hàng giữ thread.
        if (!runSlots.tryAcquire()) {
            return ConsoleResponse.failure("Docker sandbox is unavailable: console is busy, try again in a moment");
        }
        try {
            return run(request);
        } finally {
            runSlots.release();
        }
    }

    private ConsoleResponse run(ConsoleRequest request) {
        if (request.files().size() > properties.getMaxFiles()) {
            return ConsoleResponse.failure("Payload exceeds the allowed limit: too many files");
        }
        long totalBytes = request.files().stream()
                .mapToLong(f -> f.content().getBytes(StandardCharsets.UTF_8).length)
                .sum();
        if (totalBytes > properties.getMaxSourceBytes()) {
            return ConsoleResponse.failure("Payload exceeds the allowed limit");
        }

        Path workDir = null;
        try {
            workDir = writeSources(request);
            List<Path> sources;
            try (var stream = Files.walk(workDir.resolve("src"))) {
                sources = stream.filter(p -> p.toString().endsWith(".java")).toList();
            }
            if (sources.isEmpty()) {
                return ConsoleResponse.failure("No Java source files provided");
            }

            CompileResult compiled = compiler.compile(workDir, sources);
            if (!compiled.success()) {
                return ConsoleResponse.compileError(compiled.output());
            }

            List<String> mains = OopMainClassDetector.detectMainClasses(workDir.resolve("classes"));
            if (mains.isEmpty()) {
                return ConsoleResponse.failure(
                        "No 'public static void main(String[])' found in your code. Add a main method to run it.");
            }
            if (mains.size() > 1) {
                return ConsoleResponse.failure("Found more than one main method (" + String.join(", ", mains)
                        + "). Keep main in only one class to run it.");
            }

            OopContainerExecutor.SandboxResult result = containerExecutor.runClass(
                    workDir, mains.get(0), List.of(), request.input(),
                    properties.getTimeoutMs(), properties.getMemoryLimitMb());
            if (!result.measured()) {
                return ConsoleResponse.failure("Docker sandbox is unavailable: " + result.errorMessage());
            }
            return ConsoleResponse.success(result.stdout(), result.stderr(), result.exitCode(),
                    result.runtimeMs(), result.peakMemoryKb(), result.truncated(), result.timedOut());
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            log.warn("Console run failed: {}", e.getMessage());
            return ConsoleResponse.failure(e.getMessage());
        } finally {
            if (workDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(workDir);
                } catch (IOException e) {
                    log.warn("Failed to delete console workspace {}: {}", workDir, e.getMessage());
                }
            }
        }
    }

    /** Tạo thư mục tạm và ghi file nguồn (qua validator chống Zip Slip / path traversal). */
    private Path writeSources(ConsoleRequest request) throws IOException {
        Path root = Path.of(CONSOLE_ROOT);
        Files.createDirectories(root);
        Path workDir = Files.createTempDirectory(root, "cmd-");
        Path sourceDir = workDir.resolve("src");
        Files.createDirectories(sourceDir);
        // createTempDirectory tạo quyền 700 ⇒ user 1000 trong sandbox không đọc được.
        relaxPermissions(workDir);
        relaxPermissions(sourceDir);

        Path base = sourceDir.toAbsolutePath().normalize();
        for (ConsoleRequest.ConsoleFile file : request.files()) {
            if (file.path().isBlank()) {
                continue;
            }
            Path resolved = sourceExtractor.validateAndResolvePath(file.path(), base);
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, file.content(), StandardCharsets.UTF_8);
        }
        return workDir;
    }

    private void relaxPermissions(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // no-op trên Windows NTFS
        }
    }
}
