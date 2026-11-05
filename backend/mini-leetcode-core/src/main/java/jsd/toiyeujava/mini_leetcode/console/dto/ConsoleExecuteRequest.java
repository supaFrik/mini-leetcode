package jsd.toiyeujava.mini_leetcode.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.ConsoleMode;

import java.util.List;

/**
 * Yêu cầu chạy một lệnh console cho bài OOP.
 *
 * @param files      mã nguồn hiện tại trong editor (path + content)
 * @param input      nội dung stdin gửi cho {@code main} (có thể rỗng)
 * @param expression biểu thức REPL khi ở chế độ {@link ConsoleMode#EVALUATE}
 * @param contestId  id kỳ thi nếu đang ở chế độ thi (null ngoài kỳ thi) — dùng để chặn console trong exam mode
 * @param mode       chế độ chạy: đánh giá biểu thức hay chạy {@code main}
 */
public record ConsoleExecuteRequest(
        @NotEmpty(message = "At least one source file is required")
        @Size(max = 100, message = "Too many files")
        List<ConsoleFileDto> files,

        @Size(max = 65_536, message = "Input is too long")
        String input,

        @Size(max = 4_096, message = "Expression is too long")
        String expression,

        Long contestId,

        ConsoleMode mode
) {
    public ConsoleExecuteRequest {
        files = files == null ? List.of() : List.copyOf(files);
        input = input == null ? "" : input;
        expression = expression == null ? "" : expression;
        mode = mode == null ? ConsoleMode.EVALUATE : mode;
    }

    /** Một file nguồn gửi kèm. */
    public record ConsoleFileDto(
            @NotBlank(message = "File path is required")
            String path,

            String content
    ) {
        public ConsoleFileDto {
            path = path == null ? "" : path;
            content = content == null ? "" : content;
        }
    }
}
