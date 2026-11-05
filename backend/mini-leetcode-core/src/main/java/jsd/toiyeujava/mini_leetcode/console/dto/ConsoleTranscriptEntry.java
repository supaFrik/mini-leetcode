package jsd.toiyeujava.mini_leetcode.console.dto;

import java.time.Instant;

/**
 * Một bản ghi trong transcript console.
 *
 * @param input         nội dung người dùng gõ (biểu thức hoặc stdin)
 * @param stdout        stdout của lệnh
 * @param stderr        stderr của lệnh
 * @param exitCode      mã thoát
 * @param runtimeMs     wall-clock (ms)
 * @param peakMemoryKb  peak memory của cgroup (KB)
 * @param compileErrors lỗi biên dịch (null nếu biên dịch thành công)
 * @param at            thời điểm ghi
 */
public record ConsoleTranscriptEntry(
        String input,
        String stdout,
        String stderr,
        Integer exitCode,
        Integer runtimeMs,
        Integer peakMemoryKb,
        String compileErrors,
        Instant at
) {
    public ConsoleTranscriptEntry(String input,
                                  String stdout,
                                  String stderr,
                                  Integer exitCode,
                                  Integer runtimeMs,
                                  Integer peakMemoryKb,
                                  Instant at) {
        this(input, stdout, stderr, exitCode, runtimeMs, peakMemoryKb, null, at);
    }
}
