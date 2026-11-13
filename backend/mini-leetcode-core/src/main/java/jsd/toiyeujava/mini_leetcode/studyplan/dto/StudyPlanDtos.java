package jsd.toiyeujava.mini_leetcode.studyplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.StageStatus;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanItemKind;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanStatus;
import jsd.toiyeujava.mini_leetcode.topic.dto.TopicDtos.TopicRef;

import java.time.LocalDateTime;
import java.util.List;

/** Hợp đồng API của Study Plan (web + plugin IntelliJ). */
public final class StudyPlanDtos {

    private StudyPlanDtos() {
    }

    /** Một dòng trong danh sách plan; {@code doneStages} tính theo người gọi. */
    /** {@code createdById / createdByUsername}: màn hình soạn plan chỉ bật nút sửa cho tác giả (ADMIN sửa mọi plan). */
    public record PlanSummary(Long id, String slug, String title, String description, StudyPlanStatus status,
                              int itemCount, int stageCount, int doneStages, Long createdById, String createdByUsername) {}

    /**
     * {@code updatedAt}: gửi lại trong {@link UpsertPlanRequest#expectedUpdatedAt()} để phát hiện sửa ở tab khác.
     * {@code learnerCount}: chỉ trả cho staff (học viên nhận {@code null}) — số USER có bài nộp chính thức trên đề của plan.
     */
    public record PlanDetail(Long id, String slug, String title, String description, StudyPlanStatus status,
                             int stageCount, int doneStages, List<Item> items, Long createdById, String createdByUsername,
                             LocalDateTime updatedAt, Integer learnerCount) {}

    public record Item(Long id, int position, StudyPlanItemKind kind, String title, String description,
                       boolean done, List<Stage> stages) {}

    /**
     * {@code stageNo} bắt đầu từ 1. {@code published = false} nghĩa là đề đang bảo trì
     * (không tải / nộp được) — trạng thái học vẫn giữ nguyên.
     */
    /** {@code problemStatus}, {@code learnersOnStage} (USER đã nộp nhưng chưa AC): chỉ trả cho staff. */
    public record Stage(int stageNo, Long problemId, String slug, String title, Difficulty difficulty,
                        ProblemType problemType, boolean published, StageStatus status, List<TopicRef> topics,
                        ProblemStatus problemStatus, Integer learnersOnStage) {}

    public record UpsertPlanRequest(
            @NotBlank @Size(max = 200) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "slug must be lowercase letters, digits and single hyphens") String slug,
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull @Valid List<ItemRequest> items,
            LocalDateTime expectedUpdatedAt) {}

    /**
     * {@code PROBLEM} có đúng 1 đề; {@code PROJECT} có ≥ 1 đề theo thứ tự stage. Plan DRAFT được lưu mục chưa có đề
     * (Course Builder lưu nháp liên tục); kiểm tra đủ đề khi publish.
     * {@code stageTopics} (không bắt buộc): id chủ đề học cho từng stage, cùng thứ tự với {@code problemIds}.
     */
    public record ItemRequest(
            @NotNull StudyPlanItemKind kind,
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull List<@NotNull Long> problemIds,
            List<List<@NotNull Long>> stageTopics) {}

    public record TransitionRequest(@NotNull StudyPlanStatus status) {}

    /**
     * Một mục trong checklist publish (specs/course-builder.spec.md, BE-4). {@code node} chỉ tới nút trên outline:
     * {@code plan}, {@code item} ({@code itemIndex}), {@code stage} ({@code problemId}), {@code topic} ({@code topicId}).
     */
    public record PublishIssue(String node, Integer itemIndex, Long problemId, Long topicId, String message) {}

    public record PublishCheck(boolean ok, List<PublishIssue> errors, List<PublishIssue> warnings,
                               List<Long> problemsToPublish, List<Long> topicsToPublish) {}
}
