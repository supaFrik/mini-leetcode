package jsd.toiyeujava.mini_leetcode.problem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;

import java.util.List;

/**
 * Cập nhật metadata của bài tập.
 *
 * KHÔNG chứa {@code status}: chuyển trạng thái vòng đời là thao tác riêng,
 * đi qua {@code POST /api/v1/problems/{id}/transitions} để server kiểm tra
 * cạnh trạng thái hợp lệ và điều kiện publish.
 */
public record UpdateProblemRequest(
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        String description,
        Difficulty difficulty,

        @Min(value = 100, message = "Time limit must be at least 100ms")
        Integer timeLimitMs,

        @Min(value = 16, message = "Memory limit must be at least 16MB")
        Integer memoryLimitMb,

        /** Chủ đề mới của bài. {@code null} = giữ nguyên, rỗng = gỡ toàn bộ. */
        List<@Size(max = 50, message = "Tag must not exceed 50 characters") String> tags
) {
}
