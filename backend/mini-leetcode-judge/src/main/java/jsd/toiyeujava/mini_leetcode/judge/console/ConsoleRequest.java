package jsd.toiyeujava.mini_leetcode.judge.console;

import jsd.toiyeujava.mini_leetcode.entity.ConsoleMode;

import java.util.List;

/**
 * Yêu cầu thực thi một lệnh console (nội bộ, giữa API và judge worker).
 *
 * @param userId     id người dùng (dùng cho transcript + rate limit)
 * @param problemId  id bài OOP
 * @param files      mã nguồn hiện tại trong editor
 * @param input      nội dung stdin (dùng ở chế độ {@link ConsoleMode#PROGRAM})
 * @param expression biểu thức REPL (dùng ở chế độ {@link ConsoleMode#EVALUATE})
 * @param mode       chế độ chạy; null ⇒ {@link ConsoleMode#EVALUATE}
 */
public record ConsoleRequest(
        Long userId,
        Long problemId,
        List<ConsoleFile> files,
        String input,
        String expression,
        ConsoleMode mode
) {
    public ConsoleRequest {
        files = files == null ? List.of() : List.copyOf(files);
        input = input == null ? "" : input;
        expression = expression == null ? "" : expression;
        mode = mode == null ? ConsoleMode.EVALUATE : mode;
    }

    /** Một file mã nguồn gửi kèm. */
    public record ConsoleFile(String path, String content) {
        public ConsoleFile {
            path = path == null ? "" : path;
            content = content == null ? "" : content;
        }
    }
}
