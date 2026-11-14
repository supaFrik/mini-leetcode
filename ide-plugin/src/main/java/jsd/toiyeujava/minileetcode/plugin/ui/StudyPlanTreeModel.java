package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import jsd.toiyeujava.minileetcode.plugin.model.ItemDto;
import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.model.PlanSummary;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * TreeModel quản lý cấu trúc cây 3 tầng (Plan › Item › Stage) cho Tool Window Mini LeetCode (PL-2).
 * Hỗ trợ chuyển đổi trạng thái (Loading, Empty, Error, Data), tìm kiếm node theo slug/id,
 * và đảm bảo thread-safe dispatching trên Swing Event Dispatch Thread (EDT).
 */
public class StudyPlanTreeModel extends DefaultTreeModel {

    public static final String ROOT_LABEL = "Study Plans";
    private final DefaultMutableTreeNode rootNode;

    public StudyPlanTreeModel() {
        this(new DefaultMutableTreeNode(ROOT_LABEL));
    }

    public StudyPlanTreeModel(@NotNull DefaultMutableTreeNode root) {
        super(root, false);
        this.rootNode = root;
        setEmpty();
    }

    public StudyPlanTreeModel(@NotNull List<PlanDetail> initialPlans) {
        this(new DefaultMutableTreeNode(ROOT_LABEL));
        setPlans(initialPlans);
    }

    /**
     * Thực thi an toàn trên Swing EDT:
     * - Khi chạy trong IDE: Sử dụng Application.invokeLater nếu không phải EDT.
     * - Khi chạy trong headless unit tests: Thực thi đồng bộ ngay trên luồng gọi để assertion không bị race condition.
     */
    protected void runOnEdt(@NotNull Runnable runnable) {
        Application app = ApplicationManager.getApplication();
        if (app != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                runnable.run();
            } else {
                app.invokeLater(runnable);
            }
        } else if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            // Chế độ test không có Application container -> chạy đồng bộ
            runnable.run();
        }
    }

    /**
     * Nạp danh sách PlanDetail và dựng cây phân cấp 3 tầng: Root -> PlanTreeNode -> ItemTreeNode -> StageTreeNode.
     * Nếu danh sách rỗng, tự động hiển thị placeholder EMPTY.
     */
    public void setPlans(@Nullable List<PlanDetail> plans) {
        runOnEdt(() -> applyPlans(plans));
    }

    private void applyPlans(@Nullable List<PlanDetail> plans) {
        synchronized (rootNode) {
            rootNode.removeAllChildren();
            if (plans == null || plans.isEmpty()) {
                rootNode.add(PlaceholderTreeNode.empty());
            } else {
                for (PlanDetail plan : plans) {
                    if (plan == null) continue;
                    rootNode.add(buildPlanNode(plan));
                }
            }
            nodeStructureChanged(rootNode);
        }
    }

    /** Cây plan › item › stage của một plan (dùng cho tool window và cho lệnh mở stage từ web). */
    @NotNull
    public static PlanTreeNode buildPlanNode(@NotNull PlanDetail plan) {
        PlanTreeNode planNode = new PlanTreeNode(plan);
        if (plan.items() != null) {
            for (ItemDto item : plan.items()) {
                if (item == null) continue;
                ItemTreeNode itemNode = new ItemTreeNode(item);
                if (item.stages() != null) {
                    for (StageDto stage : item.stages()) {
                        if (stage != null) itemNode.add(new StageTreeNode(stage));
                    }
                }
                planNode.add(itemNode);
            }
        }
        return planNode;
    }

    /**
     * Nạp danh sách tóm tắt PlanSummary (cấp 1) khi chưa tải chi tiết.
     */
    public void setPlanSummaries(@Nullable List<PlanSummary> summaries) {
        runOnEdt(() -> {
            synchronized (rootNode) {
                rootNode.removeAllChildren();
                if (summaries == null || summaries.isEmpty()) {
                    rootNode.add(PlaceholderTreeNode.empty());
                } else {
                    for (PlanSummary summary : summaries) {
                        if (summary == null) continue;
                        rootNode.add(new PlanTreeNode(summary));
                    }
                }
                nodeStructureChanged(rootNode);
            }
        });
    }

    /**
     * Đặt cây vào trạng thái đang tải (LOADING).
     */
    public void setLoading() {
        setLoading("Loading study plans...");
    }

    public void setLoading(@Nullable String message) {
        runOnEdt(() -> {
            synchronized (rootNode) {
                rootNode.removeAllChildren();
                rootNode.add(PlaceholderTreeNode.loading(message));
                nodeStructureChanged(rootNode);
            }
        });
    }

    /**
     * Đặt cây vào trạng thái lỗi (ERROR).
     */
    public void setError(@NotNull String errorMessage) {
        runOnEdt(() -> {
            synchronized (rootNode) {
                rootNode.removeAllChildren();
                rootNode.add(PlaceholderTreeNode.error(errorMessage));
                nodeStructureChanged(rootNode);
            }
        });
    }

    /**
     * Đặt cây vào trạng thái rỗng (EMPTY).
     */
    public void setEmpty() {
        setEmpty("No study plans available");
    }

    public void setEmpty(@Nullable String message) {
        runOnEdt(() -> {
            synchronized (rootNode) {
                rootNode.removeAllChildren();
                rootNode.add(PlaceholderTreeNode.empty(message));
                nodeStructureChanged(rootNode);
            }
        });
    }

    /**
     * Xoá toàn bộ nội dung và đưa về trạng thái rỗng.
     */
    public void clear() {
        setEmpty();
    }

    // ==========================================
    // State Inspection Methods
    // ==========================================

    public boolean isLoading() {
        synchronized (rootNode) {
            PlaceholderTreeNode placeholder = getPlaceholderNode();
            return placeholder != null && placeholder.isLoading();
        }
    }

    public boolean isEmptyState() {
        synchronized (rootNode) {
            if (rootNode.getChildCount() == 0) return true;
            PlaceholderTreeNode placeholder = getPlaceholderNode();
            return placeholder != null && placeholder.isEmptyState();
        }
    }

    public boolean isErrorState() {
        synchronized (rootNode) {
            PlaceholderTreeNode placeholder = getPlaceholderNode();
            return placeholder != null && placeholder.isErrorState();
        }
    }

    public boolean hasPlans() {
        synchronized (rootNode) {
            return getPlanCount() > 0 && !isLoading() && !isEmptyState() && !isErrorState();
        }
    }

    @Nullable
    public String getErrorMessage() {
        synchronized (rootNode) {
            PlaceholderTreeNode placeholder = getPlaceholderNode();
            return (placeholder != null && placeholder.isErrorState()) ? placeholder.getMessage() : null;
        }
    }

    @Nullable
    public PlaceholderTreeNode getPlaceholderNode() {
        synchronized (rootNode) {
            if (rootNode.getChildCount() == 1) {
                TreeNode first = rootNode.getChildAt(0);
                if (first instanceof PlaceholderTreeNode placeholder) {
                    return placeholder;
                }
            }
            return null;
        }
    }

    // ==========================================
    // Navigation & Query Helpers
    // ==========================================

    @NotNull
    public DefaultMutableTreeNode getRootNode() {
        return rootNode;
    }

    @NotNull
    public List<PlanTreeNode> getPlanNodes() {
        synchronized (rootNode) {
            List<PlanTreeNode> list = new ArrayList<>();
            int count = rootNode.getChildCount();
            for (int i = 0; i < count; i++) {
                TreeNode child = rootNode.getChildAt(i);
                if (child instanceof PlanTreeNode planNode) {
                    list.add(planNode);
                }
            }
            return Collections.unmodifiableList(list);
        }
    }

    public int getPlanCount() {
        synchronized (rootNode) {
            int count = 0;
            int childCount = rootNode.getChildCount();
            for (int i = 0; i < childCount; i++) {
                if (rootNode.getChildAt(i) instanceof PlanTreeNode) {
                    count++;
                }
            }
            return count;
        }
    }

    @Nullable
    public PlanTreeNode findPlanBySlug(@Nullable String slug) {
        if (slug == null || slug.isBlank()) return null;
        synchronized (rootNode) {
            for (PlanTreeNode plan : getPlanNodes()) {
                if (slug.equalsIgnoreCase(plan.getSlug())) {
                    return plan;
                }
            }
            return null;
        }
    }

    @Nullable
    public PlanTreeNode findPlanById(@Nullable Long id) {
        if (id == null) return null;
        synchronized (rootNode) {
            for (PlanTreeNode plan : getPlanNodes()) {
                if (Objects.equals(plan.getId(), id)) {
                    return plan;
                }
            }
            return null;
        }
    }

    @Nullable
    public ItemTreeNode findItemById(@Nullable Long itemId) {
        if (itemId == null) return null;
        synchronized (rootNode) {
            for (PlanTreeNode plan : getPlanNodes()) {
                ItemTreeNode item = plan.findItemById(itemId);
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
    }

    @Nullable
    public StageTreeNode findStageBySlug(@Nullable String stageSlug) {
        if (stageSlug == null || stageSlug.isBlank()) return null;
        synchronized (rootNode) {
            for (PlanTreeNode plan : getPlanNodes()) {
                StageTreeNode stage = plan.findStageBySlug(stageSlug);
                if (stage != null) {
                    return stage;
                }
            }
            return null;
        }
    }

    @Nullable
    public StageTreeNode findStageByProblemId(@Nullable Long problemId) {
        if (problemId == null) return null;
        synchronized (rootNode) {
            for (PlanTreeNode plan : getPlanNodes()) {
                StageTreeNode stage = plan.findStageByProblemId(problemId);
                if (stage != null) {
                    return stage;
                }
            }
            return null;
        }
    }

    // ==========================================
    // TreePath Generators for JTree Selection
    // ==========================================

    @Nullable
    public TreePath getPathToPlan(@Nullable String slug) {
        synchronized (rootNode) {
            PlanTreeNode plan = findPlanBySlug(slug);
            return plan != null ? new TreePath(plan.getPath()) : null;
        }
    }

    @Nullable
    public TreePath getPathToItem(@Nullable Long itemId) {
        synchronized (rootNode) {
            ItemTreeNode item = findItemById(itemId);
            return item != null ? new TreePath(item.getPath()) : null;
        }
    }

    @Nullable
    public TreePath getPathToStage(@Nullable String stageSlug) {
        synchronized (rootNode) {
            StageTreeNode stage = findStageBySlug(stageSlug);
            return stage != null ? new TreePath(stage.getPath()) : null;
        }
    }

    // ==========================================
    // TreeModel Overrides for Thread-Safe Read Access
    // ==========================================

    @Override
    public Object getChild(Object parent, int index) {
        synchronized (rootNode) {
            return super.getChild(parent, index);
        }
    }

    @Override
    public int getChildCount(Object parent) {
        synchronized (rootNode) {
            return super.getChildCount(parent);
        }
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        synchronized (rootNode) {
            return super.getIndexOfChild(parent, child);
        }
    }

    // ==========================================
    // Fine-grained Status Mutation
    // ==========================================

    public boolean updateStageStatus(@NotNull String stageSlug, @NotNull StageStatus newStatus) {
        Objects.requireNonNull(stageSlug, "stageSlug must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        synchronized (rootNode) {
            StageTreeNode stage = findStageBySlug(stageSlug);
            if (stage == null) {
                return false;
            }

            runOnEdt(() -> {
                stage.setStatus(newStatus);
                nodeChanged(stage);
            });
            return true;
        }
    }

    @Override
    public void reload() {
        runOnEdt(() -> {
            synchronized (rootNode) {
                super.reload(rootNode);
            }
        });
    }

    @Override
    public void reload(TreeNode node) {
        runOnEdt(() -> {
            synchronized (rootNode) {
                super.reload(node);
            }
        });
    }
}
