package jsd.toiyeujava.minileetcode.plugin.runner;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Execution engine running a student project's main() method with sample input for local sanity checking.
 * <p>
 * Workflow:
 * 1. Scans {@code src/main/java} for all .java source files.
 * 2. Identifies the class containing {@code main(String[] args)}.
 * 3. Compiles sources into a temporary build output directory using {@link JavaCompiler} (with javac fallback).
 * 4. Executes {@code java -cp <outDir> <MainClass>} via {@link CapturingProcessHandler} (or {@link ProcessBuilder}).
 * 5. Pipes sample input to stdin and immediately closes stdin (sending EOF).
 * 6. Enforces a 10-second timeout limit.
 * 7. Captures exit code, stdout, and stderr into {@link SanityRunResult}.
 */
public class SanityRunner {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "(?:public\\s+static\\s+void|static\\s+public\\s+void|void)\\s+main\\s*\\("
    );
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;"
    );

    @FunctionalInterface
    public interface SubprocessExecutor {
        SanityRunResult execute(
                Path workingDir,
                Path classesDir,
                String mainClassName,
                String sampleInput,
                Duration timeout
        ) throws Exception;
    }

    private final Duration timeout;
    private final SubprocessExecutor subprocessExecutor;

    public SanityRunner() {
        this(DEFAULT_TIMEOUT, null);
    }

    public SanityRunner(Duration timeout) {
        this(timeout, null);
    }

    public SanityRunner(Duration timeout, SubprocessExecutor subprocessExecutor) {
        this.timeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;
        this.subprocessExecutor = (subprocessExecutor != null)
                ? subprocessExecutor
                : this::defaultSubprocessExecute;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Executes the main() method of the project located at {@code projectDir}.
     *
     * @param projectDir  root path of the project containing src/main/java
     * @param sampleInput sample input string to pipe to stdin
     * @return outcome record
     */
    public SanityRunResult run(Path projectDir, String sampleInput) {
        return run(projectDir, sampleInput, this.timeout);
    }

    /**
     * Executes the main() method of the project located at {@code projectDir} with a custom timeout.
     *
     * @param projectDir  root path of the project containing src/main/java
     * @param sampleInput sample input string to pipe to stdin
     * @param runTimeout  timeout limit
     * @return outcome record
     */
    public SanityRunResult run(Path projectDir, String sampleInput, Duration runTimeout) {
        Objects.requireNonNull(projectDir, "projectDir must not be null");
        Duration effectiveTimeout = (runTimeout != null) ? runTimeout : this.timeout;

        Path srcDir = projectDir.resolve("src/main/java");
        if (!Files.isDirectory(srcDir)) {
            return SanityRunResult.error(1, "Source directory not found: " + srcDir);
        }

        // 1. Collect all .java source files
        List<Path> sourceFiles;
        try (Stream<Path> walk = Files.walk(srcDir)) {
            sourceFiles = walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            return SanityRunResult.error(1, "Failed to read source files in " + srcDir + ": " + e.getMessage());
        }

        if (sourceFiles.isEmpty()) {
            return SanityRunResult.error(1, "No Java source files found under " + srcDir);
        }

        // 2. Discover main class
        String mainClassName = findMainClass(sourceFiles);
        if (mainClassName == null) {
            return SanityRunResult.error(1, "No class with 'public static void main(String[] args)' found in " + srcDir);
        }

        // 3. Compile sources into temporary directory
        Path tempClassesDir = null;
        try {
            tempClassesDir = Files.createTempDirectory("mlc-classes-");
            String compileError = compileSources(sourceFiles, tempClassesDir);
            if (compileError != null) {
                return SanityRunResult.error(1, "Compilation failed:\n" + compileError);
            }

            // 4. Execute subprocess
            return subprocessExecutor.execute(projectDir, tempClassesDir, mainClassName, sampleInput, effectiveTimeout);

        } catch (Exception ex) {
            return SanityRunResult.error(1, "Execution failed: " + ex.getMessage());
        } finally {
            if (tempClassesDir != null) {
                deleteDirectoryRecursively(tempClassesDir);
            }
        }
    }

    /**
     * Finds the fully qualified name of the class containing the main method.
     */
    public String findMainClass(List<Path> sourceFiles) {
        // Check Main.java first if present
        for (Path path : sourceFiles) {
            if (path.getFileName().toString().equals("Main.java")) {
                String candidate = inspectForMain(path);
                if (candidate != null) return candidate;
            }
        }
        for (Path path : sourceFiles) {
            String candidate = inspectForMain(path);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private String inspectForMain(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!MAIN_METHOD_PATTERN.matcher(content).find()) {
                return null;
            }
            String fileName = path.getFileName().toString();
            String simpleName = fileName.substring(0, fileName.lastIndexOf('.'));
            Matcher pkgMatcher = PACKAGE_PATTERN.matcher(content);
            if (pkgMatcher.find()) {
                return pkgMatcher.group(1).trim() + "." + simpleName;
            }
            return simpleName;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Compiles source files using JavaCompiler, falling back to javac executable.
     * Returns null if compilation succeeded, or error output string if compilation failed.
     */
    private String compileSources(List<Path> sourceFiles, Path targetClassesDir) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler != null) {
            ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
            List<String> options = List.of(
                    "-encoding", "UTF-8",
                    "-d", targetClassesDir.toAbsolutePath().toString()
            );
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
                JavaCompiler.CompilationTask task = compiler.getTask(
                        new OutputStreamWriter(errBaos, StandardCharsets.UTF_8),
                        fileManager,
                        null,
                        options,
                        null,
                        units
                );
                boolean success = Boolean.TRUE.equals(task.call());
                if (!success) {
                    return errBaos.toString(StandardCharsets.UTF_8);
                }
                return null;
            }
        }

        // Fallback: spawn javac process
        String javacPath = locateJavac();
        List<String> cmd = new ArrayList<>();
        cmd.add(javacPath);
        cmd.add("-encoding");
        cmd.add("UTF-8");
        cmd.add("-d");
        cmd.add(targetClassesDir.toAbsolutePath().toString());
        for (Path sf : sourceFiles) {
            cmd.add(sf.toAbsolutePath().toString());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        return (exit == 0) ? null : (err.isBlank() ? "javac exited with code " + exit : err);
    }

    /**
     * Default subprocess execution using GeneralCommandLine / CapturingProcessHandler with ProcessBuilder fallback.
     */
    private SanityRunResult defaultSubprocessExecute(
            Path workingDir,
            Path classesDir,
            String mainClassName,
            String sampleInput,
            Duration timeout
    ) throws Exception {
        String javaExe = locateJava();

        try {
            GeneralCommandLine commandLine = new GeneralCommandLine();
            commandLine.setExePath(javaExe);
            commandLine.addParameters(
                    "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8",
                    "-Dstderr.encoding=UTF-8",
                    "-Dsun.stdout.encoding=UTF-8",
                    "-Dsun.stderr.encoding=UTF-8",
                    "-cp",
                    classesDir.toAbsolutePath().toString(),
                    mainClassName
            );
            commandLine.setWorkDirectory(workingDir.toFile());
            commandLine.setCharset(StandardCharsets.UTF_8);
            commandLine.withEnvironment("LC_ALL", "C.UTF-8");
            commandLine.withEnvironment("LANG", "C.UTF-8");
            commandLine.withEnvironment("PYTHONIOENCODING", "UTF-8");
            commandLine.withEnvironment("PYTHONUTF8", "1");

            CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
            handler.setShouldDestroyProcessRecursively(true);

            if (sampleInput != null) {
                try (OutputStream os = handler.getProcessInput()) {
                    if (os != null) {
                        os.write(sampleInput.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }
            } else {
                OutputStream os = handler.getProcessInput();
                if (os != null) {
                    os.close();
                }
            }

            ProcessOutput output;
            try {
                output = handler.runProcess((int) timeout.toMillis());
            } finally {
                if (!handler.isProcessTerminated()) {
                    handler.destroyProcess();
                    Process proc = handler.getProcess();
                    if (proc != null && proc.isAlive()) {
                        proc.destroyForcibly();
                    }
                    handler.waitFor(500);
                }
            }

            if (output.isTimeout()) {
                return SanityRunResult.timeout(output.getStdout(), output.getStderr());
            }

            return new SanityRunResult(
                    output.getExitCode(),
                    output.getStdout(),
                    output.getStderr(),
                    false
            );

        } catch (Throwable fallback) {
            // ProcessBuilder fallback with disk file redirection to prevent OS pipe deadlock
            Path tempStdout = null;
            Path tempStderr = null;
            Process p = null;
            try {
                tempStdout = Files.createTempFile("mlc-stdout-", ".tmp");
                tempStderr = Files.createTempFile("mlc-stderr-", ".tmp");

                ProcessBuilder pb = new ProcessBuilder(
                        javaExe,
                        "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        "-Dsun.stdout.encoding=UTF-8",
                        "-Dsun.stderr.encoding=UTF-8",
                        "-cp",
                        classesDir.toAbsolutePath().toString(),
                        mainClassName
                );
                pb.directory(workingDir.toFile());
                pb.environment().put("LC_ALL", "C.UTF-8");
                pb.environment().put("LANG", "C.UTF-8");
                pb.environment().put("PYTHONIOENCODING", "UTF-8");
                pb.environment().put("PYTHONUTF8", "1");
                pb.redirectOutput(tempStdout.toFile());
                pb.redirectError(tempStderr.toFile());

                p = pb.start();

                if (sampleInput != null) {
                    try (OutputStream os = p.getOutputStream()) {
                        os.write(sampleInput.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                } else {
                    p.getOutputStream().close();
                }

                boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    p.waitFor(500, TimeUnit.MILLISECONDS);
                    String stdout = Files.exists(tempStdout) ? Files.readString(tempStdout, StandardCharsets.UTF_8) : "";
                    String stderr = Files.exists(tempStderr) ? Files.readString(tempStderr, StandardCharsets.UTF_8) : "";
                    return SanityRunResult.timeout(stdout, stderr);
                }

                int exitCode = p.exitValue();
                String stdout = Files.exists(tempStdout) ? Files.readString(tempStdout, StandardCharsets.UTF_8) : "";
                String stderr = Files.exists(tempStderr) ? Files.readString(tempStderr, StandardCharsets.UTF_8) : "";
                return new SanityRunResult(exitCode, stdout, stderr, false);

            } finally {
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                }
                if (tempStdout != null) {
                    try { Files.deleteIfExists(tempStdout); } catch (Exception ignored) {}
                }
                if (tempStderr != null) {
                    try { Files.deleteIfExists(tempStderr); } catch (Exception ignored) {}
                }
            }
        }
    }

    private String locateJava() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            Path binJava = Path.of(javaHome, "bin", isWindows ? "java.exe" : "java");
            if (Files.exists(binJava)) {
                return binJava.toAbsolutePath().toString();
            }
        }
        return "java";
    }

    private String locateJavac() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            Path binJavac = Path.of(javaHome, "bin", isWindows ? "javac.exe" : "javac");
            if (Files.exists(binJavac)) {
                return binJavac.toAbsolutePath().toString();
            }
        }
        return "javac";
    }

    private void deleteDirectoryRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignored) {}
    }
}
