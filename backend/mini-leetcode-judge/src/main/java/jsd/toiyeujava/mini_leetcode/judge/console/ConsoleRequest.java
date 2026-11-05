package jsd.toiyeujava.mini_leetcode.judge.console;

import java.util.List;

/**
 * Yêu cầu chạy console (nội bộ, giữa API và judge worker).
 *
 * @param files mã nguồn hiện tại trong editor
 * @param input nội dung stdin cho {@code main}
 */
public record ConsoleRequest(List<ConsoleFile> files, String input) {
    public ConsoleRequest {
        files = files == null ? List.of() : List.copyOf(files);
        input = input == null ? "" : input;
    }

    /** Một file mã nguồn gửi kèm. */
    public record ConsoleFile(String path, String content) {
        public ConsoleFile {
            path = path == null ? "" : path;
            content = content == null ? "" : content;
        }
    }
}
