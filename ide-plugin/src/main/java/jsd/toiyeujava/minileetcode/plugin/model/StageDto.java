package jsd.toiyeujava.minileetcode.plugin.model;

import java.util.Objects;

/**
 * Thông tin chi tiết một stage trong dự án hoặc bài luyện tập.
 */
public record StageDto(
        int stageNo,
        Long problemId,
        String slug,
        String title,
        Difficulty difficulty,
        ProblemType problemType,
        boolean published,
        StageStatus status
) {
    public StageDto {
        slug = Objects.requireNonNullElse(slug, "");
        title = Objects.requireNonNullElse(title, "");
        difficulty = (difficulty != null) ? difficulty : Difficulty.EASY;
        problemType = (problemType != null) ? problemType : ProblemType.OOP_GRINDING;
        status = (status != null) ? status : StageStatus.LOCKED;
    }

    public boolean isLocked() {
        return status == StageStatus.LOCKED;
    }

    public boolean isDone() {
        return status == StageStatus.DONE;
    }

    public boolean isInProgress() {
        return status == StageStatus.IN_PROGRESS;
    }

    public boolean isNever() {
        return status == StageStatus.NEVER;
    }

    public boolean canAttempt() {
        return published && status != StageStatus.LOCKED;
    }

    // Bean getter aliases
    public int getStageNo() { return stageNo(); }
    public Long getProblemId() { return problemId(); }
    public String getSlug() { return slug(); }
    public String getTitle() { return title(); }
    public Difficulty getDifficulty() { return difficulty(); }
    public ProblemType getProblemType() { return problemType(); }
    public boolean isPublished() { return published(); }
    public StageStatus getStatus() { return status(); }
}
