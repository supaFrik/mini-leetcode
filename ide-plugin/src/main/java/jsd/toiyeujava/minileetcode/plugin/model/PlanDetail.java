package jsd.toiyeujava.minileetcode.plugin.model;

import java.util.List;
import java.util.Objects;

/**
 * Chi tiết toàn bộ lộ trình học gồm danh sách item và stage (GET /api/v1/study-plans/{slug}).
 */
public record PlanDetail(
        Long id,
        String slug,
        String title,
        String description,
        StudyPlanStatus status,
        int stageCount,
        int doneStages,
        List<ItemDto> items
) {
    public PlanDetail {
        slug = Objects.requireNonNullElse(slug, "");
        title = Objects.requireNonNullElse(title, "");
        description = Objects.requireNonNullElse(description, "");
        items = (items != null) ? List.copyOf(items) : List.of();
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

    public boolean isCompleted() {
        return stageCount > 0 && doneStages >= stageCount;
    }

    public int getItemCount() {
        return items.size();
    }

    // Bean getter aliases
    public Long getId() { return id(); }
    public String getSlug() { return slug(); }
    public String getTitle() { return title(); }
    public String getDescription() { return description(); }
    public StudyPlanStatus getStatus() { return status(); }
    public int getStageCount() { return stageCount(); }
    public int getDoneStages() { return doneStages(); }
    public List<ItemDto> getItems() { return items(); }
}
