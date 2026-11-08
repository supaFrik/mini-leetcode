package jsd.toiyeujava.mini_leetcode.studyplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.StageStatus;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanItemKind;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanStatus;

import java.util.List;

/** Hợp đồng API của Study Plan (web + plugin IntelliJ). */
public final class StudyPlanDtos {

    private StudyPlanDtos() {
    }

    /** Một dòng trong danh sách plan; {@code doneStages} tính theo người gọi. */
    public record PlanSummary(Long id, String slug, String title, String description, StudyPlanStatus status,
                              int itemCount, int stageCount, int doneStages) {}

    public record PlanDetail(Long id, String slug, String title, String description, StudyPlanStatus status,
                             int stageCount, int doneStages, List<Item> items) {}

    public record Item(Long id, int position, StudyPlanItemKind kind, String title, String description,
                       boolean done, List<Stage> stages) {}

    /**
     * {@code stageNo} bắt đầu từ 1. {@code published = false} nghĩa là đề đang bảo trì
     * (không tải / nộp được) — trạng thái học vẫn giữ nguyên.
     */
    public record Stage(int stageNo, Long problemId, String slug, String title, Difficulty difficulty,
                        ProblemType problemType, boolean published, StageStatus status) {}

    public record UpsertPlanRequest(
            @NotBlank @Size(max = 200) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "slug must be lowercase letters, digits and single hyphens") String slug,
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull @Valid List<ItemRequest> items) {}

    /** {@code PROBLEM} có đúng 1 đề; {@code PROJECT} có ≥ 1 đề theo thứ tự stage. */
    public record ItemRequest(
            @NotNull StudyPlanItemKind kind,
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotEmpty List<@NotNull Long> problemIds) {}

    public record TransitionRequest(@NotNull StudyPlanStatus status) {}
}
