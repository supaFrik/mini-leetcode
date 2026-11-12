package jsd.toiyeujava.minileetcode.plugin.ui;

import jsd.toiyeujava.minileetcode.plugin.model.ItemDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StudyPlanItemKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Node phân cấp cấp 2 (Item Level) trong Swing Tree của Tool Window Mini LeetCode.
 * Đại diện cho một Item trong lộ trình: Dự án thực tế (PROJECT) hoặc Bài luyện lẻ (PROBLEM).
 * Chứa danh sách các StageTreeNode (Level 3).
 */
public class ItemTreeNode extends DefaultMutableTreeNode {

    private final Long id;
    private final int order;
    private final StudyPlanItemKind kind;
    private final String title;
    private final String description;
    private final boolean done;
    private final String problemSlug;
    private final ItemDto itemDto;

    public ItemTreeNode(@NotNull ItemDto itemDto) {
        this(itemDto, deriveProblemSlug(itemDto));
    }

    public ItemTreeNode(@NotNull ItemDto itemDto, @Nullable String problemSlug) {
        super(Objects.requireNonNull(itemDto, "itemDto must not be null"), true);
        this.itemDto = itemDto;
        this.id = itemDto.id();
        this.order = itemDto.position();
        this.kind = itemDto.kind() != null ? itemDto.kind() : StudyPlanItemKind.PROBLEM;
        this.title = Objects.requireNonNullElse(itemDto.title(), "");
        this.description = Objects.requireNonNullElse(itemDto.description(), "");
        this.done = itemDto.done();
        this.problemSlug = (problemSlug != null && !problemSlug.isBlank())
                ? problemSlug
                : deriveProblemSlug(itemDto);
    }

    private static String deriveProblemSlug(ItemDto itemDto) {
        if (itemDto != null && itemDto.stages() != null && !itemDto.stages().isEmpty()) {
            StageDto first = itemDto.stages().get(0);
            if (first != null && first.slug() != null) {
                return first.slug();
            }
        }
        return "";
    }

    public Long getId() {
        return id;
    }

    public int getOrder() {
        return order;
    }

    public int getPosition() {
        return order;
    }

    public StudyPlanItemKind getKind() {
        return kind;
    }

    public boolean isProject() {
        return kind == StudyPlanItemKind.PROJECT;
    }

    public boolean isProblem() {
        return kind == StudyPlanItemKind.PROBLEM;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDone() {
        return done;
    }

    public String getProblemSlug() {
        return problemSlug;
    }

    public ItemDto getItemDto() {
        return itemDto;
    }

    public int getStageCount() {
        return itemDto != null ? itemDto.getStageCount() : getChildCount();
    }

    public int getDoneStageCount() {
        int count = 0;
        for (StageTreeNode stage : getStageNodes()) {
            if (stage.isDone()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Lấy node Plan cha trực tiếp của item này.
     */
    @Nullable
    public PlanTreeNode getPlanNode() {
        TreeNode parent = getParent();
        return (parent instanceof PlanTreeNode) ? (PlanTreeNode) parent : null;
    }

    /**
     * Lấy danh sách typed StageTreeNode con của item này.
     */
    @NotNull
    public List<StageTreeNode> getStageNodes() {
        List<StageTreeNode> stages = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            TreeNode child = getChildAt(i);
            if (child instanceof StageTreeNode stageNode) {
                stages.add(stageNode);
            }
        }
        return Collections.unmodifiableList(stages);
    }

    @Nullable
    public StageTreeNode findStageBySlug(@Nullable String slug) {
        if (slug == null || slug.isBlank()) return null;
        for (StageTreeNode stage : getStageNodes()) {
            if (slug.equalsIgnoreCase(stage.getSlug())) {
                return stage;
            }
        }
        return null;
    }

    @Nullable
    public StageTreeNode findStageByProblemId(@Nullable Long problemId) {
        if (problemId == null) return null;
        for (StageTreeNode stage : getStageNodes()) {
            if (Objects.equals(stage.getProblemId(), problemId)) {
                return stage;
            }
        }
        return null;
    }

    @Nullable
    public StageTreeNode findStageByNo(int stageNo) {
        for (StageTreeNode stage : getStageNodes()) {
            if (stage.getStageOrder() == stageNo) {
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
        return title;
    }
}
