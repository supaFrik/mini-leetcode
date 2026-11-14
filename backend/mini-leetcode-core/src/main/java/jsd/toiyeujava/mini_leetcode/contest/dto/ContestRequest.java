package jsd.toiyeujava.mini_leetcode.contest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ScoringPolicy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tạo / sửa kỳ thi (thay thế toàn bộ). Thời gian theo UTC.
 * {@code problemIds} theo thứ tự hiển thị; nhãn A, B, C... được gán tự động.
 */
public record ContestRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        ContestStatus status,
        ScoringPolicy scoringPolicy,
        @Min(0) Integer maxViolationsAllowed,
        List<Long> problemIds
) {
}
