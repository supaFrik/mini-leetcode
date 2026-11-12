package jsd.toiyeujava.minileetcode.plugin.ui;

import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemType;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Objects;

/**
 * Node phân cấp cấp 3 (Stage Level) trong Swing Tree của Tool Window Mini LeetCode.
 * Đại diện cho một Stage bài nộp, có 4 trạng thái: LOCKED, NEVER, IN_PROGRESS, DONE.
 * Là node lá (Leaf node), không cho phép chứa node con.
 */
public class StageTreeNode extends DefaultMutableTreeNode {

    private final StageDto stageDto;
    private final int stageOrder;
    private final Long problemId;
    private final String slug;
    private final String title;
    private final Difficulty difficulty;
    private final ProblemType problemType;
    private final boolean published;
    private volatile StageStatus status;

    public StageTreeNode(@NotNull StageDto stageDto) {
        super(Objects.requireNonNull(stageDto, "stageDto must not be null"), false);
        this.stageDto = stageDto;
        this.stageOrder = stageDto.stageNo();
        this.problemId = stageDto.problemId();
        this.slug = Objects.requireNonNullElse(stageDto.slug(), "");
        this.title = Objects.requireNonNullElse(stageDto.title(), "");
        this.difficulty = stageDto.difficulty() != null ? stageDto.difficulty() : Difficulty.EASY;
        this.problemType = stageDto.problemType() != null ? stageDto.problemType() : ProblemType.OOP_GRINDING;
        this.published = stageDto.published();
        this.status = stageDto.status() != null ? stageDto.status() : StageStatus.LOCKED;
    }

    public int getStageOrder() {
        return stageOrder;
    }

    public int getStageNo() {
        return stageOrder;
    }

    public Long getProblemId() {
        return problemId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public ProblemType getProblemType() {
        return problemType;
    }

    public boolean isPublished() {
        return published;
    }

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(@NotNull StageStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
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

    public StageDto getStageDto() {
        return stageDto;
    }

    /**
     * Lấy node Item cha trực tiếp của stage này.
     */
    @Nullable
    public ItemTreeNode getItemNode() {
        TreeNode parent = getParent();
        return (parent instanceof ItemTreeNode) ? (ItemTreeNode) parent : null;
    }

    /**
     * Lấy node Plan cấp cao nhất của stage này.
     */
    @Nullable
    public PlanTreeNode getPlanNode() {
        ItemTreeNode item = getItemNode();
        return item != null ? item.getPlanNode() : null;
    }

    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public String toString() {
        return "Stage " + stageOrder + ": " + title;
    }
}
