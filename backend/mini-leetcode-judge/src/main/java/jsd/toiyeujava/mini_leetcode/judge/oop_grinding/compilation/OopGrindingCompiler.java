package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation;

import jakarta.annotation.PreDestroy;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Component
public class OopGrindingCompiler {

    public static final long DEFAULT_TIMEOUT_MS = 10_000L;
    public static final String CLASSES_DIR_NAME = "classes";
    private static final int MAX_ERROR_CHARS = 4000;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private final AtomicInteger threadCounter = new AtomicInteger();
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "oop-compiler-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
    }

    /**
     * Compiles all .java files found in workDir into workDir/classes.
     */
    public CompileResult compile(Path workDir) throws IOException {
        return compile(workDir, null, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Compiles given list of source files (or all in workDir if null/empty) into workDir/classes.
     */
    public CompileResult compile(Path workDir, List<Path> javaSourceFiles) throws IOException {
        return compile(workDir, javaSourceFiles, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Compiles sources with specified timeout.
     */
    public CompileResult compile(Path workDir, List<Path> javaSourceFiles, long timeoutMs) throws IOException {
        Path classesDir = workDir.resolve(CLASSES_DIR_NAME);
        Files.createDirectories(classesDir);

        List<Path> sources = (javaSourceFiles != null && !javaSourceFiles.isEmpty())
                ? javaSourceFiles
                : findJavaFiles(workDir, classesDir);

        if (sources.isEmpty()) {
            return new CompileResult(false, "No Java source files found for compilation");
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler != null) {
            return compileWithSystemCompiler(compiler, workDir, classesDir, sources, timeoutMs);
        } else {
            return compileWithProcess(workDir, classesDir, sources, timeoutMs);
        }
    }

    private CompileResult compileWithSystemCompiler(JavaCompiler compiler, Path workDir, Path classesDir,
                                                    List<Path> sources, long timeoutMs) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter outputWriter = new StringWriter();

        Future<Boolean> taskFuture = executorService.submit(() -> {
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(sources);
                List<String> options = List.of(
                        "-d", classesDir.toAbsolutePath().toString(),
                        "-encoding", "UTF-8",
                        "-proc:none"
                );

                JavaCompiler.CompilationTask task = compiler.getTask(
                        outputWriter, fileManager, diagnostics, options, null, compilationUnits);
                return Boolean.TRUE.equals(task.call());
            }
        });

        try {
            boolean success = taskFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (success) {
                return new CompileResult(true, "Compilation successful");
            } else {
                StringBuilder errorReport = new StringBuilder();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        String sourceName = "unknown";
                        if (d.getSource() != null) {
                            try {
                                sourceName = workDir.relativize(Path.of(d.getSource().getName())).toString();
                            } catch (Exception ignored) {
                                sourceName = d.getSource().getName();
                            }
                        }
                        errorReport.append(sourceName)
                                .append(":").append(d.getLineNumber())
                                .append(": error: ").append(d.getMessage(Locale.ENGLISH))
                                .append("\n");
                    }
                }
                String msg = errorReport.toString().trim();
                if (msg.isEmpty()) {
                    msg = outputWriter.toString().trim();
                }
                if (msg.length() > MAX_ERROR_CHARS) {
                    msg = msg.substring(0, MAX_ERROR_CHARS) + "\n... [truncated]";
                }
                return new CompileResult(false, msg);
            }
        } catch (TimeoutException e) {
            taskFuture.cancel(true);
            return new CompileResult(false, "Compilation timed out after " + timeoutMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CompileResult(false, "Compilation interrupted");
        } catch (ExecutionException e) {
            log.warn("System compiler task failed, falling back to javac process: {}", e.getMessage());
            try {
                return compileWithProcess(workDir, classesDir, sources, timeoutMs);
            } catch (IOException ioException) {
                return new CompileResult(false, "Compilation execution exception: " + e.getCause().getMessage());
            }
        }
    }

    private CompileResult compileWithProcess(Path workDir, Path classesDir, List<Path> sources, long timeoutMs) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(resolveJavacExecutable());
        command.add("-d");
        command.add(classesDir.toAbsolutePath().toString());
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-proc:none");
        for (Path p : sources) {
            command.add(p.toAbsolutePath().toString());
        }

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        try {
            Process process = pb.start();
            Future<String> outputFuture = executorService.submit(() -> {
                try (InputStream in = process.getInputStream()) {
                    return readLimited(in, MAX_OUTPUT_BYTES);
                }
            });

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CompileResult(false, "Compilation timed out after " + timeoutMs + "ms");
            }

            int exitCode = process.exitValue();
            String output = "";
            try {
                output = outputFuture.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }

            if (exitCode == 0) {
                return new CompileResult(true, "Compilation successful");
            } else {
                if (output.length() > MAX_ERROR_CHARS) {
                    output = output.substring(0, MAX_ERROR_CHARS) + "\n... [truncated]";
                }
                return new CompileResult(false, output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CompileResult(false, "Compilation interrupted");
        }
    }

    private List<Path> findJavaFiles(Path workDir, Path classesDir) throws IOException {
        try (Stream<Path> stream = Files.walk(workDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(classesDir))
                    .toList();
        }
    }

    private String resolveJavacExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            Path javacPath = Path.of(javaHome, "bin", isWindows ? "javac.exe" : "javac");
            if (Files.isExecutable(javacPath) || Files.exists(javacPath)) {
                return javacPath.toAbsolutePath().toString();
            }
        }
        return "javac";
    }

    private String readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            int room = maxBytes - buffer.size();
            if (room > 0) {
                buffer.write(chunk, 0, Math.min(n, room));
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
