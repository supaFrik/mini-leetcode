package jsd.toiyeujava.minileetcode.plugin.model;

import java.util.Objects;

/**
 * Thông tin tổng quan của một lộ trình học tập trong danh sách (GET /api/v1/study-plans).
 */
public record PlanSummary(
        Long id,
        String slug,
        String title,
        String description,
        StudyPlanStatus status,
        int itemCount,
        int stageCount,
        int doneStages
) {
    public PlanSummary {
        slug = Objects.requireNonNullElse(slug, "");
        title = Objects.requireNonNullElse(title, "");
        description = Objects.requireNonNullElse(description, "");
    }

    /** Tính tỷ lệ phần trăm hoàn thành (0 - 100). */
    public int getProgressPercentage() {
        if (stageCount <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(0, (int) Math.round((doneStages * 100.0) / stageCount)));
    }

    public int progressPercent() {
        return getProgressPercentage();
    }

    /** Kiểm tra xem toàn bộ plan đã hoàn thành chưa. */
    public boolean isCompleted() {
        return stageCount > 0 && doneStages >= stageCount;
    }

    // Bean getter aliases for Swing Tree and renderer convenience
    public Long getId() { return id(); }
    public String getSlug() { return slug(); }
    public String getTitle() { return title(); }
    public String getDescription() { return description(); }
    public StudyPlanStatus getStatus() { return status(); }
    public int getItemCount() { return itemCount(); }
    public int getStageCount() { return stageCount(); }
    public int getDoneStages() { return doneStages(); }
}
