package jsd.toiyeujava.mini_leetcode.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Yêu cầu chạy console cho bài OOP (như terminal IDE).
 *
 * @param files mã nguồn hiện tại trong editor (path + content)
 * @param input nội dung stdin gửi cho {@code main} (có thể rỗng)
 */
public record ConsoleExecuteRequest(
        @NotEmpty(message = "At least one source file is required")
        @Size(max = 100, message = "Too many files")
        List<ConsoleFileDto> files,

        @Size(max = 65_536, message = "Input is too long")
        String input
) {
    public ConsoleExecuteRequest {
        files = files == null ? List.of() : List.copyOf(files);
        input = input == null ? "" : input;
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
