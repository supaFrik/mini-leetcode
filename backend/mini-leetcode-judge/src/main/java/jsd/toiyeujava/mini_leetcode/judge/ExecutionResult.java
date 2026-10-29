package jsd.toiyeujava.mini_leetcode.judge;

public record ExecutionResult(
        int exitCode,
        boolean timedOut,
        boolean oomKilled,
        String stdout,
        int runtimeMs,
        int peakMemoryKb,
        String stderr
) {

    // Constructor 6 tham số cũ để giữ tương thích ngược với VerdictMapperTest và các test cũ
    public ExecutionResult(int exitCode, boolean timedOut, boolean oomKilled, String stdout, int runtimeMs, int peakMemoryKb) {
        this(exitCode, timedOut, oomKilled, stdout, runtimeMs, peakMemoryKb, "");
    }
}
