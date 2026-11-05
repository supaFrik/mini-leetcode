package jsd.toiyeujava.mini_leetcode.console.dto;

import java.util.List;

/**
 * Transcript console của một phiên (user + problem).
 *
 * @param entries danh sách bản ghi, cũ nhất trước
 * @param limit   giới hạn số lệnh của phiên
 * @param used    số lệnh đã dùng trong phiên
 */
public record ConsoleTranscriptResponse(
        List<ConsoleTranscriptEntry> entries,
        int limit,
        int used
) {
    public ConsoleTranscriptResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static ConsoleTranscriptResponse empty(int limit) {
        return new ConsoleTranscriptResponse(List.of(), limit, 0);
    }
}
