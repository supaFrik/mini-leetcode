package jsd.toiyeujava.mini_leetcode.judge.console;

/**
 * Kết quả một lệnh console.
 *
 * @param stdout        stdout của tiến trình
 * @param stderr        stderr đã loại bỏ sentinel bộ nhớ
 * @param exitCode      mã thoát (null nếu không chạy được)
 * @param runtimeMs     wall-clock (ms)
 * @param peakMemoryKb  peak memory của cgroup (KB)
 * @param compileErrors lỗi biên dịch (null nếu biên dịch thành công)
 * @param truncated     output có bị cắt cụt không
 * @param timedOut      lệnh có bị giết vì hết thời gian không
 * @param errorMessage  thông báo lỗi (VD: Docker không khả dụng)
 */
public record ConsoleResponse(
        String stdout,
        String stderr,
        Integer exitCode,
        Integer runtimeMs,
        Integer peakMemoryKb,
        String compileErrors,
        boolean truncated,
        boolean timedOut,
        String errorMessage
) {
    public static ConsoleResponse success(String stdout, String stderr, Integer exitCode,
                                          Integer runtimeMs, Integer peakMemoryKb,
                                          boolean truncated, boolean timedOut) {
        return new ConsoleResponse(stdout, stderr, exitCode, runtimeMs, peakMemoryKb, null, truncated, timedOut, null);
    }

    public static ConsoleResponse compileError(String compileErrors) {
        return new ConsoleResponse("", "", null, null, null, compileErrors, false, false, null);
    }

    public static ConsoleResponse failure(String errorMessage) {
        return new ConsoleResponse("", "", null, null, null, null, false, false, errorMessage);
    }
}
