package jsd.toiyeujava.mini_leetcode.console.dto;

/**
 * Kết quả một lệnh console.
 *
 * @param stdout        stdout của tiến trình trong container
 * @param stderr        stderr (đã loại sentinel bộ nhớ)
 * @param exitCode      mã thoát (null nếu không chạy được)
 * @param runtimeMs     wall-clock thực tế của container (ms)
 * @param peakMemoryKb  peak memory của cgroup (KB)
 * @param compileErrors lỗi biên dịch (null nếu biên dịch thành công)
 * @param truncated     output có bị cắt cụt không
 * @param timedOut      lệnh có bị giết vì hết thời gian không
 */
public record ConsoleExecuteResponse(
        String stdout,
        String stderr,
        Integer exitCode,
        Integer runtimeMs,
        Integer peakMemoryKb,
        String compileErrors,
        boolean truncated,
        boolean timedOut
) {
    public static ConsoleExecuteResponse empty() {
        return new ConsoleExecuteResponse("", "", null, null, null, null, false, false);
    }
}
