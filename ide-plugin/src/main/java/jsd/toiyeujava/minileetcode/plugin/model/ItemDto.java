package jsd.toiyeujava.minileetcode.plugin.model;

import java.util.List;
import java.util.Objects;

/**
 * Thông tin một mục (Project hoặc Problem đơn lẻ) trong lộ trình học tập.
 */
public record ItemDto(
        Long id,
        int position,
        StudyPlanItemKind kind,
        String title,
        String description,
        boolean done,
        List<StageDto> stages
) {
    public ItemDto {
        kind = (kind != null) ? kind : StudyPlanItemKind.PROBLEM;
        title = Objects.requireNonNullElse(title, "");
        description = Objects.requireNonNullElse(description, "");
        stages = (stages != null) ? List.copyOf(stages) : List.of();
    }

    public int getStageCount() {
        return stages.size();
    }

    public int getDoneStageCount() {
        int count = 0;
        for (StageDto stage : stages) {
            if (stage.status() == StageStatus.DONE) {
                count++;
            }
        }
        return count;
    }

    public boolean isProject() {
        return kind == StudyPlanItemKind.PROJECT;
    }

    public boolean isProblem() {
        return kind == StudyPlanItemKind.PROBLEM;
    }

    // Bean getter aliases
    public Long getId() { return id(); }
    public int getPosition() { return position(); }
    public StudyPlanItemKind getKind() { return kind(); }
    public String getTitle() { return title(); }
    public String getDescription() { return description(); }
    public boolean isDone() { return done(); }
    public List<StageDto> getStages() { return stages(); }
}
