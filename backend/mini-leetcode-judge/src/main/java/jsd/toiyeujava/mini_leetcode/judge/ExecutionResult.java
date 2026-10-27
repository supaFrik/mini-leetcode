package jsd.toiyeujava.mini_leetcode.judge;

public record ExecutionResult(
        int exitCode,
        boolean timedOut,
        boolean oomKilled,
        String stdout,
        int runtimeMs,
        int peakMemoryKb
) {
}