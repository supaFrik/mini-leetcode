package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;

public final class VerdictMapper {

    private VerdictMapper() {
    }

    public static SubmissionVerdict map(ExecutionResult result, String expectedOutput) {
        return map(result, expectedOutput, Integer.MAX_VALUE);
    }

    /**
     * Xác định verdict cho một lần chạy.
     *
     * @param memoryLimitKb giới hạn bộ nhớ của bài toán (KB), dùng để đối chiếu
     *                     với peak memory đo được khi Docker không báo OOM.
     */
    public static SubmissionVerdict map(ExecutionResult result, String expectedOutput, int memoryLimitKb) {
        if (result.timedOut()) {
            return SubmissionVerdict.TLE;
        }
        if (result.oomKilled()) {
            return SubmissionVerdict.MLE;
        }
        // Peak memory chạm sát trần của container: dù OOMKilled không được báo
        // (ví dụ JVM tự thoát với ExitOnOutOfMemoryError) thì vẫn là MLE.
        int peakKb = result.peakMemoryKb();
        if (peakKb > 0 && memoryLimitKb > 0 && peakKb >= memoryLimitKb) {
            return SubmissionVerdict.MLE;
        }
        if (result.exitCode() != 0) {
            return SubmissionVerdict.RE;
        }
        return OutputComparator.matches(result.stdout(), expectedOutput)
                ? SubmissionVerdict.AC
                : SubmissionVerdict.WA;
    }
}
