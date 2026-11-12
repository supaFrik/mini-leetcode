package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import jsd.toiyeujava.minileetcode.plugin.model.ItemDto;
import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import jsd.toiyeujava.minileetcode.plugin.model.StudyPlanItemKind;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Custom tree cell renderer for the Mini LeetCode Study Plan tree view.
 * Renders the 3-tier hierarchy:
 * 1. Plan node: Bold title, stage count and progress subtitle (SimpleTextAttributes.GRAYED_ATTRIBUTES)
 * 2. Item node: Item title with [Project] or [Problem] badge
 * 3. Stage node: Stage title with status icon corresponding to StageStatus (LOCKED, NEVER, IN_PROGRESS, DONE)
 *
 * Also handles placeholder nodes (loading, empty, error) with styled text and appropriate icons.
 */
public class StudyPlanTreeCellRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(
            @NotNull JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
    ) {
        if (value == null) {
            return;
        }

        if (value instanceof PlanTreeNode planNode) {
            renderPlan(planNode);
        } else if (value instanceof ItemTreeNode itemNode) {
            renderItem(itemNode);
        } else if (value instanceof StageTreeNode stageNode) {
            renderStage(stageNode);
        } else if (value instanceof PlaceholderTreeNode placeholderNode) {
            renderPlaceholder(placeholderNode.getMessage());
        } else if (value instanceof DefaultMutableTreeNode dmtn) {
            Object userObject = dmtn.getUserObject();
            if (userObject instanceof PlanDetail planDetail) {
                renderPlanDetail(planDetail);
            } else if (userObject instanceof ItemDto itemDto) {
                renderItemDto(itemDto);
            } else if (userObject instanceof StageDto stageDto) {
                renderStageDto(stageDto);
            } else {
                renderPlaceholder(userObject != null ? userObject.toString() : "");
            }
        } else {
            append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    private void renderPlan(@NotNull PlanTreeNode planNode) {
        setIcon(MiniLeetCodeIcons.TOOL_WINDOW);
        append(planNode.getTitle(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

        int doneStages = planNode.getDoneStages();
        int stageCount = planNode.getStageCount();
        if (stageCount > 0) {
            append(" (" + doneStages + "/" + stageCount + " stages)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else if (planNode.getItemCount() > 0) {
            append(" (" + planNode.getItemCount() + " items)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        if (planNode.getDescription() != null && !planNode.getDescription().isBlank()) {
            setToolTipText(planNode.getDescription());
        }
    }

    private void renderPlanDetail(@NotNull PlanDetail planDetail) {
        setIcon(MiniLeetCodeIcons.TOOL_WINDOW);
        append(planDetail.getTitle(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

        int doneStages = planDetail.getDoneStages();
        int stageCount = planDetail.getStageCount();
        if (stageCount > 0) {
            append(" (" + doneStages + "/" + stageCount + " stages)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else if (planDetail.getItemCount() > 0) {
            append(" (" + planDetail.getItemCount() + " items)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        if (planDetail.getDescription() != null && !planDetail.getDescription().isBlank()) {
            setToolTipText(planDetail.getDescription());
        }
    }

    private void renderItem(@NotNull ItemTreeNode itemNode) {
        StudyPlanItemKind kind = itemNode.getKind();
        if (kind == StudyPlanItemKind.PROJECT) {
            setIcon(AllIcons.Nodes.IdeaProject);
            append("[Project] ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else {
            setIcon(AllIcons.Nodes.Class);
            append("[Problem] ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        append(itemNode.getTitle(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

        if (itemNode.isDone()) {
            append(" (Done)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        if (itemNode.getDescription() != null && !itemNode.getDescription().isBlank()) {
            setToolTipText(itemNode.getDescription());
        }
    }

    private void renderItemDto(@NotNull ItemDto itemDto) {
        StudyPlanItemKind kind = itemDto.kind();
        if (kind == StudyPlanItemKind.PROJECT) {
            setIcon(AllIcons.Nodes.IdeaProject);
            append("[Project] ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else {
            setIcon(AllIcons.Nodes.Class);
            append("[Problem] ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        append(itemDto.title(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

        if (itemDto.done()) {
            append(" (Done)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        if (itemDto.description() != null && !itemDto.description().isBlank()) {
            setToolTipText(itemDto.description());
        }
    }

    private void renderStage(@NotNull StageTreeNode stageNode) {
        StageStatus status = stageNode.getStatus();
        setIcon(MiniLeetCodeIcons.getStatusIcon(status));

        int stageNo = stageNode.getStageNo();
        append("Stage " + stageNo + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append(stageNode.getTitle(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

        setToolTipText("Stage " + stageNo + ": " + stageNode.getTitle() + " (" + status + ")");
    }

    private void renderStageDto(@NotNull StageDto stageDto) {
        StageStatus status = stageDto.status();
        setIcon(MiniLeetCodeIcons.getStatusIcon(status));

        int stageNo = stageDto.stageNo();
        append("Stage " + stageNo + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append(stageDto.title(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

        setToolTipText("Stage " + stageNo + ": " + stageDto.title() + " (" + status + ")");
    }

    private void renderPlaceholder(@NotNull String text) {
        String lower = text.toLowerCase();
        if (lower.contains("loading")) {
            setIcon(AllIcons.Process.Step_1);
            append(text, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
        } else if (lower.contains("failed") || lower.contains("error")) {
            setIcon(AllIcons.General.Error);
            append(text, SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else {
            setIcon(AllIcons.General.Information);
            append(text, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
        }
    }
}
