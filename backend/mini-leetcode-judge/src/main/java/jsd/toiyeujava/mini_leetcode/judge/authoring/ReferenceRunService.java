package jsd.toiyeujava.mini_leetcode.judge.authoring;

import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.OutputComparator;
import jsd.toiyeujava.mini_leetcode.judge.SandboxHostPathResolver;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopContainerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner.OopMainClassDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Chạy lời giải mẫu của giảng viên trên nhiều stdin để sinh output kỳ vọng cho testcase.
 *
 * <p>Đúng điều kiện chấm thật: biên dịch bằng {@link OopGrindingCompiler}, chạy {@code main} trong container
 * sandbox qua {@link OopContainerExecutor#runClass} với time/memory limit của bài, và so output bằng
 * {@link OutputComparator}. Lời giải mẫu cũng là mã do người dùng nhập ⇒ KHÔNG bao giờ chạy in-process.
 */
@Slf4j
@Service
public class ReferenceRunService {

    /** Số lần chạy lời giải mẫu đồng thời tối đa trên một judge (mỗi lần có thể gồm nhiều testcase). */
    static final int MAX_CONCURRENT_RUNS = 2;
    static final int MAX_INPUTS = 100;

    public record SourceFile(String path, String content) {
    }

    /** {@code expectedOutput} null ⇒ không so sánh ({@code matchesExpected} = null). */
    public record CaseInput(String input, String expectedOutput) {
    }

    public record CaseRun(String stdout, String stderr, Integer exitCode, Integer runtimeMs,
                          boolean timedOut, boolean oomKilled, boolean truncated, Boolean matchesExpected) {
        public boolean succeeded() {
            return !timedOut && !oomKilled && !truncated && exitCode != null && exitCode == 0;
        }
    }

    /**
     * @param compiled      {@code false} khi biên dịch lỗi / thiếu main — xem {@code compileOutput}
     * @param error         lỗi hạ tầng (sandbox không chạy được); khi đó {@code cases} có thể thiếu
     */
    public record Result(boolean compiled, String compileOutput, String mainClass, String error, List<CaseRun> cases) {
    }

    /** Judge đang chạy đủ số lần cho phép. */
    public static class BusyException extends RuntimeException {
        BusyException() {
            super("Reference runner is busy, try again in a moment");
        }
    }

    private final OopGrindingCompiler compiler;
    private final MultiFileSourceExtractor extractor;
    private final OopContainerExecutor containerExecutor;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_RUNS);

    public ReferenceRunService(OopGrindingCompiler compiler, MultiFileSourceExtractor extractor,
                               OopContainerExecutor containerExecutor) {
        this.compiler = compiler;
        this.extractor = extractor;
        this.containerExecutor = containerExecutor;
    }

    public Result run(List<SourceFile> files, List<CaseInput> inputs, long timeLimitMs, int memoryLimitMb) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No reference source files provided");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("No inputs to run");
        }
        if (inputs.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("Too many inputs (max " + MAX_INPUTS + ")");
        }
        if (!slots.tryAcquire()) {
            throw new BusyException();
        }
        Path workDir = null;
        try {
            workDir = writeSources(files);
            List<Path> sources;
            try (var walk = Files.walk(workDir.resolve("src"))) {
                sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
            }
            if (sources.isEmpty()) {
                return new Result(false, "No Java source files provided", null, null, List.of());
            }
            CompileResult compiled = compiler.compile(workDir, sources);
            if (!compiled.success()) {
                return new Result(false, compiled.output().replaceAll("(?m)^src/", ""), null, null, List.of());
            }
            Optional<String> mainClass = OopMainClassDetector.detectMainClass(workDir.resolve("classes"));
            if (mainClass.isEmpty()) {
                return new Result(false, "No 'public static void main(String[] args)' found in the reference solution",
                        null, null, List.of());
            }
            List<CaseRun> runs = new ArrayList<>(inputs.size());
            for (CaseInput input : inputs) {
                OopContainerExecutor.SandboxResult r = containerExecutor.runClass(workDir, mainClass.get(), List.of(),
                        input.input() == null ? "" : input.input(), timeLimitMs, memoryLimitMb);
                if (!r.measured()) {
                    log.warn("Reference run aborted, sandbox unavailable: {}", r.errorMessage());
                    return new Result(true, null, mainClass.get(), "Docker sandbox is unavailable: " + r.errorMessage(), runs);
                }
                Boolean matches = input.expectedOutput() == null ? null
                        : OutputComparator.matches(r.stdout() == null ? "" : r.stdout(), input.expectedOutput());
                runs.add(new CaseRun(r.stdout(), r.stderr(), r.exitCode(), r.runtimeMs(),
                        r.timedOut(), r.oomKilled(), r.truncated(), matches));
            }
            return new Result(true, null, mainClass.get(), null, runs);
        } catch (IOException e) {
            log.error("Reference run failed", e);
            return new Result(false, null, null, "Reference run failed: " + e.getMessage(), List.of());
        } finally {
            deleteQuietly(workDir);
            slots.release();
        }
    }

    private Path writeSources(List<SourceFile> files) throws IOException {
        Path root = Path.of(SandboxHostPathResolver.CONSOLE_ROOT_NAME);
        Files.createDirectories(root);
        Path workDir = Files.createTempDirectory(root, "ref-");
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
