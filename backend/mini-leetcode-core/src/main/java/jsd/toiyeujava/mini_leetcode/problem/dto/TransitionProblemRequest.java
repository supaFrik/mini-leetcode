package jsd.toiyeujava.mini_leetcode.problem.dto;

import jakarta.validation.constraints.NotNull;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;

/**
 * Yêu cầu chuyển trạng thái vòng đời của bài tập.
 *
 * Tách riêng khỏi {@link UpdateProblemRequest} vì đổi metadata (tiêu đề, mô tả)
 * và đổi trạng thái là hai thao tác khác nhau về mặt nghiệp vụ: đổi trạng
 * thái có luật riêng (cạnh hợp lệ, điều kiện publish) nên phải validate riêng.
 */
public record TransitionProblemRequest(
        @NotNull(message = "Target status is required")
        ProblemStatus status
) {
}
