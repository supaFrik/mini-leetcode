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
            List.of("java", "-cp", "/workspace", "Solution"),
            3);

    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final int MIN_HEAP_MB = 32;
    private static final double HEAP_RATIO = 0.75;

    /**
     * Marker trong stderr mà wrapper shell dùng để báo peak memory của
     * container (đọc từ {@code /sys/fs/cgroup/memory.peak}).
     */
    public static final String MEMORY_SENTINEL = "__MEM_PEAK_KB__=";

    /**
     * Tên file script chạy được mount vào container cùng mã nguồn.
     *
     * <p>Script được ghi ra file thay vì truyền trực tiếp làm tham số
     * {@code sh -c}: trên Windows, {@code ProcessBuilder} truyền tham số qua
     * command line của Windows và các dấu nháy đơn/double của shell sẽ bị
     * diễn giải lại, làm hỏng script. Ghi file rồi gọi {@code sh <file>}
     * loại bỏ hoàn toàn vấn đề truyền lệnh.
     */
    public static final String RUN_SCRIPT_FILE = "run.sh";

    /**
     * Đọc peak memory từ cgroup. Cgroup v2 dùng {@code memory.peak}; cgroup v1
     * dùng {@code memory.max_usage_in_bytes}.
     */
    private static final String PEAK_READ_COMMAND =
            "cat /sys/fs/cgroup/memory.peak 2>/dev/null "
                    + "|| cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes 2>/dev/null";

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

    /**
     * Lệnh được truyền cho {@code docker run} ở pha chạy.
     *
     * <p>Chỉ là {@code sh /workspace/run.sh}; nội dung script được ghi bởi
     * {@link #runScript(int)}.
     */
    public List<String> runCommand() {
        return runCommand(DEFAULT_MEMORY_LIMIT_MB);
    }

    public List<String> runCommand(int memoryLimitMb) {
        return List.of("sh", "/workspace/" + RUN_SCRIPT_FILE);
    }

    /**
     * Nội dung script shell chạy trong container.
     *
     * <p>Script chạy lệnh của thí sinh, sau đó in peak memory của cgroup lên
     * stderr (stdout giữ nguyên để so sánh với output kỳ vọng) và thoát với
     * đúng mã lỗi của lệnh gốc.
     */
    public String runScript(int memoryLimitMb) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/sh\n");
        for (String part : baseRunCommand(memoryLimitMb)) {
            script.append(shellQuote(part)).append(' ');
        }
        script.append("\nrc=$?\n");
        script.append("printf '").append(MEMORY_SENTINEL).append("%s\\n' \"$(")
                .append(PEAK_READ_COMMAND)
                .append(")\" >&2\n");
        script.append("exit $rc\n");
        return script.toString();
    }

    /** Lệnh chạy gốc (chưa bọc), dùng để sinh script. */
    private List<String> baseRunCommand(int memoryLimitMb) {
        if (this == JAVA) {
            // Tỷ lệ heap cố định 75% memory limit với sàn tối thiểu 32MB để chừa chỗ cho metaspace, stack, JIT
            int heapMb = Math.max(MIN_HEAP_MB, (int) (memoryLimitMb * HEAP_RATIO));
            return List.of("java", "-Xmx" + heapMb + "m", "-XX:+UseSerialGC",
                    "-XX:+ExitOnOutOfMemoryError", "-cp", "/workspace", "Solution");
        }
        return runCommand;
    }

    /**
     * Escape một tham số để truyền an toàn qua POSIX shell.
     *
     * <p>Code thí sinh nằm trong file riêng và được mount read-only, nên không
     * thể thay đổi script; nhưng vẫn escape để chắc chắn không có tham số nào
     * bị diễn giải lại bởi shell.
     */
    static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
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
