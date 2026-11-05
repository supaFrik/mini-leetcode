package jsd.toiyeujava.mini_leetcode.judge.console;

import jsd.toiyeujava.mini_leetcode.entity.ConsoleMode;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopMainClassDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine thực thi lệnh console OOP.
 *
 * <p>Nguyên tắc bất di bất dịch: mã nguồn học viên CHỈ được chạy trong container
 * Docker. Nếu Docker không khả dụng ⇒ trả lỗi, KHÔNG fallback in-process.
 *
 * <p>Cache biên dịch: mã nguồn được băm (SHA-256) và cache lại tập {@code .class}
 * theo hash. Khi mã nguồn không đổi, lệnh sau không phải biên dịch lại.
 */
@Slf4j
@Service
public class ConsoleSessionService {

    /** Thư mục gốc cho các phiên console. */
    private static final String CONSOLE_ROOT = "temp-console";

    private final ConsoleProperties properties;
    private final MultiFileSourceExtractor sourceExtractor;
    private final OopGrindingCompiler compiler;
    private final OopContainerExecutor containerExecutor;

    /** Cache class đã biên dịch: hash mã nguồn → thư mục chứa {@code classes/}. */
    private final Map<String, Path> compileCache = new ConcurrentHashMap<>();

    @Autowired
    public ConsoleSessionService(ConsoleProperties properties,
                                 MultiFileSourceExtractor sourceExtractor,
                                 OopGrindingCompiler compiler,
                                 OopContainerExecutor containerExecutor) {
        this.properties = properties;
        this.sourceExtractor = sourceExtractor;
        this.compiler = compiler;
        this.containerExecutor = containerExecutor;
    }

    /**
     * Thực thi một lệnh console.
     *
     * @param request yêu cầu (mã nguồn + stdin/biểu thức)
     * @return kết quả chạy
     */
    public ConsoleResponse execute(ConsoleRequest request) {
        if (request == null) {
            return ConsoleResponse.failure("Invalid console request");
        }
        // Giới hạn số lệnh do API (Redis) quản lý; judge là executor stateless.
        List<ConsoleRequest.ConsoleFile> javaFiles = request.files().stream()
                .filter(f -> f.path() != null && f.path().toLowerCase().endsWith(".java"))
                .toList();
        if (javaFiles.isEmpty()) {
            return ConsoleResponse.failure("No Java source files provided");
        }
        if (request.files().size() > properties.getMaxFiles()) {
            return ConsoleResponse.failure("Payload exceeds the allowed limit: too many files");
        }
        long totalBytes = request.files().stream()
                .mapToLong(f -> (f.content() == null ? 0 : f.content().getBytes(StandardCharsets.UTF_8).length))
                .sum();
        if (totalBytes > properties.getMaxSourceBytes()) {
            return ConsoleResponse.failure("Payload exceeds the allowed limit (20MB)");
        }

        // Cache theo từng user: thư mục cache bị ghi (script chạy, probe REPL) mỗi
        // request, nên học viên khác nhau cùng starter template không được dùng chung.
        // ponytail: 2 tab của cùng user vẫn có thể race; tách script ra thư mục riêng nếu cần.
        String sourceHash = request.userId() + "-" + hashSource(javaFiles);
        Path workDir;
        try {
            workDir = prepareWorkspace(request, sourceHash);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            log.warn("Console workspace preparation failed: {}", e.getMessage());
            return ConsoleResponse.failure(e.getMessage());
        }

        // 1. Biên dịch (dùng cache nếu mã nguồn không đổi).
        CompileResult compileResult = compileIfNeeded(workDir, javaFiles, sourceHash);
        if (!compileResult.success()) {
            return ConsoleResponse.compileError(compileResult.output());
        }

        // 2. Chọn entry point theo chế độ người dùng chọn.
        //    - EVALUATE: luôn dùng REPL harness (học viên gõ biểu thức trên lớp của họ).
        //      Không quan tâm học viên có viết main hay không.
        //    - PROGRAM: chạy main của học viên với input làm stdin; nếu không có
        //      main thì báo lỗi rõ ràng thay vì âm thầm chạy REPL.
        ConsoleMode mode = request.mode() == null ? ConsoleMode.EVALUATE : request.mode();
        Optional<String> mainClass = OopMainClassDetector.detectMainClass(workDir.resolve("classes"));
        boolean useRepl;
        if (mode == ConsoleMode.PROGRAM) {
            if (mainClass.isEmpty()) {
                return ConsoleResponse.failure(
                        "No 'public static void main(String[])' found in your code. "
                                + "Switch the console to 'Evaluate expression' mode, or add a main method.");
            }
            useRepl = false;
        } else {
            useRepl = true;
        }

        if (useRepl && !ConsoleReplHarness.writeProbeSource(workDir)) {
            return ConsoleResponse.failure("Failed to prepare console REPL harness");
        }
        if (useRepl) {
            CompileResult replCompile;
            try {
                replCompile = compiler.compile(workDir, List.of(workDir.resolve(ConsoleReplHarness.PROBE_SOURCE_FILE)));
            } catch (IOException e) {
                log.warn("Console REPL harness compilation failed: {}", e.getMessage());
                return ConsoleResponse.failure("Failed to compile console REPL harness: " + e.getMessage());
            }
            if (!replCompile.success()) {
                return ConsoleResponse.failure("Failed to compile console REPL harness: " + replCompile.output());
            }
        }

        // 3. Chuẩn bị input cho container.
        String stdin;
        String classToRun;
        List<String> classArgs = List.of();
        if (useRepl) {
            classToRun = ConsoleReplHarness.PROBE_CLASS_NAME;
            stdin = request.expression().isBlank() ? request.input() : request.expression();
        } else {
            classToRun = mainClass.get();
            stdin = request.input();
        }

        // 4. Chạy trong container sandbox.
        OopContainerExecutor.SandboxResult result = containerExecutor.runClass(
                workDir, classToRun, classArgs, stdin,
                properties.getTimeoutMs(), properties.getMemoryLimitMb());

        if (!result.measured()) {
            String message = result.errorMessage() == null ? "Docker sandbox is unavailable" : result.errorMessage();
            if (message.contains("Docker execution failed")) {
                message = "Docker sandbox is unavailable: " + message;
            }
            return ConsoleResponse.failure(message);
        }

        String stdout = result.stdout() == null ? "" : result.stdout();
        String stderr = result.stderr() == null ? "" : result.stderr();
        // Với REPL: dòng báo lỗi đánh giá biểu thức được đưa vào stderr cho gọn.
        int errorMarker = stdout.indexOf("__CONSOLE_ERROR__=");
        if (errorMarker >= 0) {
            String errorLine = stdout.substring(errorMarker).trim();
            stdout = stdout.substring(0, errorMarker);
            stderr = (stderr.isBlank() ? "" : stderr + "\n") + errorLine;
        }
        stdout = stdout.replace("__CONSOLE_PROMPT__", "").trim();

        return ConsoleResponse.success(stdout, stderr, result.exitCode(),
                result.runtimeMs(), result.peakMemoryKb(), result.truncated(), result.timedOut());
    }

    /**
     * Chuẩn bị thư mục làm việc: nếu mã nguồn đã được cache thì dùng lại thư mục
     * có {@code classes/}, ngược lại tạo thư mục mới và ghi file nguồn vào đó.
     */
    private Path prepareWorkspace(ConsoleRequest request, String sourceHash) throws IOException {
        Path cached = compileCache.get(sourceHash);
        if (cached != null && Files.isDirectory(cached.resolve("classes"))) {
            return cached;
        }

        Path sessionRoot = Path.of(CONSOLE_ROOT);
        Files.createDirectories(sessionRoot);
        Path workDir = Files.createTempDirectory(sessionRoot, "cmd-");
        relaxPermissions(workDir);
        Path sourceDir = workDir.resolve("src");
        Files.createDirectories(sourceDir);
        relaxPermissions(sourceDir);

        for (ConsoleRequest.ConsoleFile file : request.files()) {
            if (file.path() == null || file.path().isBlank()) {
                continue;
            }
            // Dùng validator của extractor để chống Zip Slip / path traversal.
            Path resolved = sourceExtractor.validateAndResolvePath(file.path(), sourceDir.toAbsolutePath().normalize());
            if (resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }
            Files.writeString(resolved, file.content() == null ? "" : file.content(), StandardCharsets.UTF_8);
        }
        return workDir;
    }

    private CompileResult compileIfNeeded(Path workDir, List<ConsoleRequest.ConsoleFile> javaFiles, String sourceHash) {
        Path classesDir = workDir.resolve("classes");
        if (Files.isDirectory(classesDir)) {
            CompileResult cached = new CompileResult(true, "Compilation successful (cached)");
            return cached;
        }
        List<Path> sources;
        try (var stream = Files.walk(workDir.resolve("src"))) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            return new CompileResult(false, "Cannot read source files: " + e.getMessage());
        }
        if (sources.isEmpty()) {
            return new CompileResult(false, "No Java source files found");
        }
        CompileResult result;
        try {
            result = compiler.compile(workDir, sources);
        } catch (IOException e) {
            log.warn("Console compilation failed: {}", e.getMessage());
            return new CompileResult(false, "Compilation failed: " + e.getMessage());
        }
        if (result.success()) {
            compileCache.put(sourceHash, workDir);
        }
        return result;
    }

    /**
     * Nới lỏng quyền của thư mục làm việc để sandbox container (chạy user
     * {@code 1000:1000}) đọc được script và các file {@code .class}.
     *
     * <p>{@link Files#createTempDirectory(Path, String)} tạo thư mục với quyền
     * {@code 700} ⇒ user 1000 trong container không vào được ⇒ lỗi
     * "Permission denied" khi chạy script. Trên Windows NTFS thao tác này là
     * no-op (POSIX permission không được hỗ trợ).
     */
    private void relaxPermissions(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Safe to ignore on Windows NTFS
        }
    }

    private String hashSource(List<ConsoleRequest.ConsoleFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<ConsoleRequest.ConsoleFile> sorted = files.stream()
                    .sorted((a, b) -> a.path().compareTo(b.path()))
                    .toList();
            for (ConsoleRequest.ConsoleFile file : sorted) {
                digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update((file.content() == null ? "" : file.content()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "fallback-" + System.nanoTime();
        }
    }
}
