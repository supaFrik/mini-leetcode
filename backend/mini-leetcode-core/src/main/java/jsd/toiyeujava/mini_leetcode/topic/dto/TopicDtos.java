package jsd.toiyeujava.mini_leetcode.topic.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanStatus;
import jsd.toiyeujava.mini_leetcode.entity.TopicProgressStatus;
import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;

import java.time.LocalDateTime;
import java.util.List;

/** Hợp đồng API của chủ đề học (specs/topics.spec.md). */
public final class TopicDtos {

    private TopicDtos() {
    }

    // ── Học viên ────────────────────────────────────────────────────────────

    /** Chủ đề gắn vào một stage, kèm tiến độ của người xem. */
    public record TopicRef(Long id, String slug, String title, String summary, int estMinutes,
                           TopicProgressStatus progress, int stepCount, int doneSteps, StudyPlanStatus status) {}

    /** {@code content} đã bỏ đáp án và xáo trộn (MATCHING, ORDERING). */
    public record LearnerStep(Long id, int position, TopicStepType type, String title, JsonNode content,
                              boolean passed, int attempts) {}

    /**
     * {@code lockedUntil}: luyện tập bị khoá tới thời điểm này (sai 3 lần liên tiếp); {@code mustReread}: phải đọc lại
     * hết lý thuyết trước khi luyện tập tiếp. Cả hai mở thì mới được gửi bài luyện tập.
     */
    public record LearnerTopic(Long id, String slug, String title, String summary, int estMinutes,
                               StudyPlanStatus status, TopicProgressStatus progress, int stepCount, int doneSteps,
                               List<LearnerStep> steps, LocalDateTime lockedUntil, boolean mustReread) {}

    /** THEORY gửi {@code {}} để đánh dấu đã đọc. CODE không nhận bài gửi (xong khi đề được AC). */
    public record AttemptRequest(@NotNull JsonNode answer) {}

    /** {@code wrongStreak}: số lần sai liên tiếp ở câu này (khoá ở lần thứ {@code PracticeLockService.LOCK_AFTER}). */
    public record AttemptResult(boolean correct, String message, String explanation, boolean stepPassed,
                                int attempts, TopicProgressStatus progress, int doneSteps, int stepCount,
                                int wrongStreak, LocalDateTime lockedUntil, boolean mustReread) {}

    // ── Soạn (INSTRUCTOR / ADMIN) ───────────────────────────────────────────

    public record TopicSummary(Long id, String slug, String title, String summary, int estMinutes,
                               StudyPlanStatus status, int stepCount, Long createdById, String createdByUsername,
                               LocalDateTime updatedAt) {}

    public record AuthoringStep(Long id, TopicStepType type, String title, JsonNode content, JsonNode answer,
                                Long problemId, String problemSlug) {}

    public record AuthoringTopic(Long id, String slug, String title, String summary, int estMinutes,
                                 StudyPlanStatus status, List<AuthoringStep> steps, Long createdById,
                                 String createdByUsername) {}

    public record UpsertTopicRequest(
            @NotBlank @Size(max = 200) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "slug must be lowercase letters, digits and single hyphens") String slug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String summary,
            @Min(1) @Max(600) int estMinutes,
            @NotNull @Size(max = 50) @Valid List<StepRequest> steps) {}

    /** {@code id}: step đã có (giữ tiến độ học viên khi sửa); bỏ trống = step mới. */
    public record StepRequest(
            Long id,
            @NotNull TopicStepType type,
            @NotBlank @Size(max = 200) String title,
            @NotNull JsonNode content,
            JsonNode answer,
            Long problemId) {}
}
