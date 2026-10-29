package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;

import java.util.List;
import java.util.Optional;

public enum LanguageRuntime {

    PYTHON(
            "python:3.12-slim",
            "solution.py",
            List.of(),
            List.of("python", "/workspace/solution.py"),
            null),

    // -Xmx192m chừa 64m cho non-heap (metaspace, thread stack, code cache) trong container 256m;
    // SerialGC tối ưu bộ nhớ; ExitOnOutOfMemoryError thoát với mã lỗi 3 khi hết heap
    JAVA(
            "eclipse-temurin:17-jdk",
            "Solution.java",
            List.of("javac", "-encoding", "UTF-8", "/workspace/Solution.java"),
            List.of("java", "-Xmx192m", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-cp", "/workspace", "Solution"),
            3);

    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final int MIN_HEAP_MB = 32;
    private static final double HEAP_RATIO = 0.75;

    private final String image;
    private final String sourceFileName;
    private final List<String> compileCommand;
    private final List<String> runCommand;
    private final Integer oomExitCode;

    LanguageRuntime(String image, String sourceFileName,
                    List<String> compileCommand, List<String> runCommand,
                    Integer oomExitCode) {
        this.image = image;
        this.sourceFileName = sourceFileName;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
        this.oomExitCode = oomExitCode;
    }

    public String image() {
        return image;
    }

    public String sourceFileName() {
        return sourceFileName;
    }

    public List<String> compileCommand() {
        return compileCommand;
    }

    public List<String> runCommand() {
        return runCommand(DEFAULT_MEMORY_LIMIT_MB);
    }

    public List<String> runCommand(int memoryLimitMb) {
        if (this == JAVA) {
            // Tỷ lệ heap cố định 75% memory limit với sàn tối thiểu 32MB để chừa chỗ cho metaspace, stack, JIT
            int heapMb = Math.max(MIN_HEAP_MB, (int) (memoryLimitMb * HEAP_RATIO));
            return List.of("java", "-Xmx" + heapMb + "m", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-cp", "/workspace", "Solution");
        }
        return runCommand;
    }

    public Optional<Integer> oomExitCode() {
        return Optional.ofNullable(oomExitCode);
    }

    public boolean needsCompile() {
        return !compileCommand.isEmpty();
    }

    public static Optional<LanguageRuntime> from(SubmissionLanguage language) {
        if (language == null) {
            return Optional.empty();
        }
        return switch (language) {
            case PYTHON -> Optional.of(PYTHON);
            case JAVA -> Optional.of(JAVA);
            default -> Optional.empty();
        };
    }
}
