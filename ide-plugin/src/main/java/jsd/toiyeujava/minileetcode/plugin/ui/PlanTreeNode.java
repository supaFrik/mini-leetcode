package jsd.toiyeujava.minileetcode.plugin.ui;

import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.model.PlanSummary;
import jsd.toiyeujava.minileetcode.plugin.model.StudyPlanStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Node phân cấp cấp 1 (Plan Level) trong Swing Tree của Tool Window Mini LeetCode.
 * Đại diện cho một lộ trình học tập (PlanDetail hoặc PlanSummary).
 * Chứa danh sách các ItemTreeNode (Level 2).
 */
public class PlanTreeNode extends DefaultMutableTreeNode {

    private final Long id;
    private final String slug;
    private final String title;
    private final String description;
    private final StudyPlanStatus status;
    private final int stageCount;
    private final int doneStages;
    private final int itemCount;
    private final PlanDetail planDetail;
    private final PlanSummary planSummary;
    private boolean expanded;

    public PlanTreeNode(@NotNull PlanDetail planDetail) {
        super(Objects.requireNonNull(planDetail, "planDetail must not be null"), true);
        this.planDetail = planDetail;
        this.planSummary = null;
        this.id = planDetail.id();
        this.slug = Objects.requireNonNullElse(planDetail.slug(), "");
        this.title = Objects.requireNonNullElse(planDetail.title(), "");
        this.description = Objects.requireNonNullElse(planDetail.description(), "");
        this.status = planDetail.status() != null ? planDetail.status() : StudyPlanStatus.PUBLISHED;
        this.stageCount = planDetail.stageCount();
        this.doneStages = planDetail.doneStages();
        this.itemCount = planDetail.getItemCount();
        this.expanded = false;
    }

    public PlanTreeNode(@NotNull PlanSummary planSummary) {
        super(Objects.requireNonNull(planSummary, "planSummary must not be null"), true);
        this.planDetail = null;
        this.planSummary = planSummary;
        this.id = planSummary.id();
        this.slug = Objects.requireNonNullElse(planSummary.slug(), "");
        this.title = Objects.requireNonNullElse(planSummary.title(), "");
        this.description = Objects.requireNonNullElse(planSummary.description(), "");
        this.status = planSummary.status() != null ? planSummary.status() : StudyPlanStatus.PUBLISHED;
        this.stageCount = planSummary.stageCount();
        this.doneStages = planSummary.doneStages();
        this.itemCount = planSummary.itemCount();
        this.expanded = false;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public StudyPlanStatus getStatus() {
        return status;
    }

    public int getStageCount() {
        return stageCount;
    }

    public int getDoneStages() {
        return doneStages;
    }

    public int getItemCount() {
        return itemCount;
    }

    /**
     * Tỷ lệ hoàn thành của lộ trình (0 - 100).
     */
    public int getProgressPercentage() {
        if (stageCount <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(0, (int) Math.round((doneStages * 100.0) / stageCount)));
    }

    public boolean isCompleted() {
        return stageCount > 0 && doneStages >= stageCount;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Nullable
    public PlanDetail getPlanDetail() {
        return planDetail;
    }

    @Nullable
    public PlanSummary getPlanSummary() {
        return planSummary;
    }

    /**
     * Lấy danh sách typed ItemTreeNode con của plan này.
     */
    @NotNull
    public List<ItemTreeNode> getItemNodes() {
        List<ItemTreeNode> items = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            TreeNode child = getChildAt(i);
            if (child instanceof ItemTreeNode itemNode) {
                items.add(itemNode);
            }
        }
        return Collections.unmodifiableList(items);
    }

    @Nullable
    public ItemTreeNode findItemById(@Nullable Long itemId) {
        if (itemId == null) return null;
        for (ItemTreeNode item : getItemNodes()) {
            if (Objects.equals(item.getId(), itemId)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    public StageTreeNode findStageBySlug(@Nullable String stageSlug) {
        if (stageSlug == null || stageSlug.isBlank()) return null;
        for (ItemTreeNode item : getItemNodes()) {
            StageTreeNode stage = item.findStageBySlug(stageSlug);
            if (stage != null) {
                return stage;
            }
        }
        return null;
    }

    @Nullable
    public StageTreeNode findStageByProblemId(@Nullable Long problemId) {
        if (problemId == null) return null;
        for (ItemTreeNode item : getItemNodes()) {
            StageTreeNode stage = item.findStageByProblemId(problemId);
            if (stage != null) {
                return stage;
            }
        }
        return null;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }

    @Override
    public String toString() {
        return (title != null && !title.isBlank()) ? title : slug;
    }
}
