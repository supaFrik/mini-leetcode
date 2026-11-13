package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.treeStructure.Tree;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.ui.ItemTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.PlanTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.RefreshPlansAction.ApiClientFactory;
import jsd.toiyeujava.minileetcode.plugin.ui.StageTreeNode;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectOpener;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceException;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceInitRequest;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceResult;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Action that initializes a student workspace and opens the study plan stage in IntelliJ (PL-3, PL-4).
 * <p>
 * Workflow:
 * 1. Resolves selected StageTreeNode or ItemTreeNode from Tree.
 * 2. Validates stage state (blocks LOCKED stages with warning balloon).
 * 3. Prompts user for local target directory via {@link DirectoryChooser}.
 * 4. Gathers parameters and calls {@link WorkspaceService#initializeProject(WorkspaceInitRequest)} on background thread.
 * 5. Opens the project via {@link ProjectOpener}.
 * 6. Opens {@code stages/stage-N.md} with native platform Markdown split preview via {@link MarkdownOpener}.
 * 7. Reports success or formatted error balloon via {@link NotificationPresenter}.
 */
public class OpenStageAction extends DumbAwareAction {

    private Tree tree;
    private DirectoryChooser directoryChooser;
    private ApiClientFactory apiClientFactory;
    private ProjectOpener projectOpener;
    private MarkdownOpener markdownOpener;
    private NotificationPresenter notificationPresenter;

    public OpenStageAction() {
        this(null, null, null, null, null, null);
    }

    public OpenStageAction(@Nullable Tree tree) {
        this(tree, null, null, null, null, null);
    }

    public OpenStageAction(
            @Nullable Tree tree,
            @Nullable DirectoryChooser directoryChooser,
            @Nullable ApiClientFactory apiClientFactory,
            @Nullable ProjectOpener projectOpener,
            @Nullable MarkdownOpener markdownOpener,
            @Nullable NotificationPresenter notificationPresenter
    ) {
        super("Open Stage", "Initialize local workspace and open study plan stage", AllIcons.Actions.Execute);
        this.tree = tree;
        this.directoryChooser = directoryChooser != null ? directoryChooser : DirectoryChooser.DEFAULT;
        this.apiClientFactory = apiClientFactory != null ? apiClientFactory : ApiClient::new;
        this.projectOpener = projectOpener != null ? projectOpener : ProjectOpener.DEFAULT;
        this.markdownOpener = markdownOpener != null ? markdownOpener : MarkdownOpener.DEFAULT;
        this.notificationPresenter = notificationPresenter != null ? notificationPresenter : NotificationPresenter.DEFAULT;
    }

    public void setTree(@Nullable Tree tree) {
        this.tree = tree;
    }

    public void setDirectoryChooser(@NotNull DirectoryChooser directoryChooser) {
        this.directoryChooser = Objects.requireNonNull(directoryChooser);
    }

    public void setApiClientFactory(@NotNull ApiClientFactory apiClientFactory) {
        this.apiClientFactory = Objects.requireNonNull(apiClientFactory);
    }

    public void setProjectOpener(@NotNull ProjectOpener projectOpener) {
        this.projectOpener = Objects.requireNonNull(projectOpener);
    }

    public void setMarkdownOpener(@NotNull MarkdownOpener markdownOpener) {
        this.markdownOpener = Objects.requireNonNull(markdownOpener);
    }

    public void setNotificationPresenter(@NotNull NotificationPresenter notificationPresenter) {
        this.notificationPresenter = Objects.requireNonNull(notificationPresenter);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Tree targetTree = resolveTree(e);
        TreeNode selectedNode = getSelectedTreeNode(targetTree);

        if (selectedNode instanceof StageTreeNode stageNode) {
            e.getPresentation().setEnabled(true);
            e.getPresentation().setText("Open Stage " + stageNode.getStageNo() + ": " + stageNode.getTitle());
            e.getPresentation().setDescription(stageNode.isLocked() ? "Stage is locked" : "Open stage " + stageNode.getStageNo());
        } else if (selectedNode instanceof ItemTreeNode itemNode) {
            e.getPresentation().setEnabled(true);
            e.getPresentation().setText("Open " + itemNode.getTitle());
            e.getPresentation().setDescription("Open project for " + itemNode.getTitle());
        } else {
            e.getPresentation().setEnabled(false);
            e.getPresentation().setText("Open Stage");
            e.getPresentation().setDescription("Select a stage or item to open");
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Tree targetTree = resolveTree(e);
        TreeNode selectedNode = getSelectedTreeNode(targetTree);
        if (selectedNode == null) {
            return;
        }
        openSelectedNode(e.getProject(), selectedNode);
    }

    /**
     * Programmatic entry point for double-click listener and direct action calls.
     */
    public void openSelectedNode(@Nullable Project project, @NotNull TreeNode node) {
        StageTreeNode targetStage = resolveTargetStage(node);
        if (targetStage == null) {
            return;
        }

        // Guard: Check locked state
        if (targetStage.isLocked()) {
            notificationPresenter.showNotification(project, "Mini LeetCode",
                    "Stage " + targetStage.getStageNo() + " is locked. Please complete previous stages first.",
                    NotificationType.WARNING);
            return;
        }
        // Guard: stage đang bảo trì (đề không PUBLISHED) — không tải được starter / đề (spec Error Handling)
        if (!targetStage.isPublished()) {
            notificationPresenter.showNotification(project, "Mini LeetCode",
                    "Stage " + targetStage.getStageNo() + " đang được cập nhật, thử lại sau.",
                    NotificationType.WARNING);
            return;
        }

        // Prompt user for destination directory
        Path defaultDir = project != null && project.getBasePath() != null
                ? Path.of(project.getBasePath())
                : null;
        Path targetDir = directoryChooser.chooseDirectory(project, defaultDir);
        if (targetDir == null) {
            // User canceled directory chooser
            return;
        }

        // Asynchronous execution
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Opening Stage " + targetStage.getStageNo(), true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        indicator.setIndeterminate(true);
                        indicator.setText("Setting up workspace for " + targetStage.getTitle() + "...");
                        performOpenStageSync(project, targetStage, targetDir);
                    } catch (Exception ex) {
                        handleError(project, ex);
                    }
                }
            });
        } else {
            // Headless / unit test execution
            try {
                performOpenStageSync(project, targetStage, targetDir);
            } catch (Exception ex) {
                handleError(project, ex);
            }
        }
    }

    /**
     * Synchronous execution engine for workspace creation, project opening, and markdown display.
     * Ideal for headless unit and integration testing.
     */
    public WorkspaceResult performOpenStageSync(
            @Nullable Project currentProject,
            @NotNull TreeNode node,
            @NotNull Path targetDir
    ) throws Exception {
        StageTreeNode stageNode = resolveTargetStage(node);
        if (stageNode == null) {
            throw new IllegalArgumentException("Cannot resolve a valid StageTreeNode from: " + node);
        }

        PlanTreeNode planNode = stageNode.getPlanNode();
        String planSlug = planNode != null ? planNode.getSlug() : "study-plan";
        ItemTreeNode itemNode = stageNode.getItemNode();
        String itemSlugOrId = itemNode != null ? String.valueOf(itemNode.getPosition()) : "1";
        int stageNo = stageNode.getStageNo();
        String problemSlug = stageNode.getSlug();
        Long problemId = stageNode.getProblemId();

        WorkspaceInitRequest request = new WorkspaceInitRequest(
                targetDir, planSlug, itemSlugOrId, stageNo, problemSlug, problemId
        );

        // Resolve API Client
        MiniLeetCodeSettingsState settings = MiniLeetCodeSettingsState.getInstance();
        String serverUrl = settings.getNormalizedServerUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalStateException("Server URL is not configured. Please open Settings › Tools › Mini LeetCode.");
        }
        String pat = MiniLeetCodePasswordSafe.getInstance().getPassword();
        ApiClient apiClient = apiClientFactory.create(serverUrl, pat);

        // Orchestrate workspace creation pipeline
        WorkspaceService workspaceService = new WorkspaceService(apiClient, projectOpener);
        WorkspaceResult result = workspaceService.initializeProject(request);

        // Resolve target IDE project instance
        Project targetProject = resolveOpenedProject(currentProject, targetDir);

        // Open Markdown preview on EDT (PL-4)
        markdownOpener.openMarkdown(targetProject, result.stageMarkdownPath());

        // Notify user of success
        notificationPresenter.showNotification(targetProject, "Mini LeetCode",
                "Successfully opened Stage " + stageNo + ": " + stageNode.getTitle(),
                NotificationType.INFORMATION);

        return result;
    }

    @Nullable
    public StageTreeNode resolveTargetStage(@NotNull TreeNode node) {
        if (node instanceof StageTreeNode stageNode) {
            return stageNode;
        }
        if (node instanceof ItemTreeNode itemNode) {
            // Find active or first stage
            for (StageTreeNode child : itemNode.getStageNodes()) {
                if (child.isInProgress()) {
                    return child;
                }
            }
            // Stage hiện tại = stage đầu tiên chưa DONE và chưa LOCKED (không quay lại stage đã xong)
            for (StageTreeNode child : itemNode.getStageNodes()) {
                if (!child.isDone() && !child.isLocked()) {
                    return child;
                }
            }
            // Đã xong cả project: mở stage cuối để xem lại
            if (!itemNode.getStageNodes().isEmpty()) {
                return itemNode.getStageNodes().get(itemNode.getStageNodes().size() - 1);
            }
        }
        return null;
    }

    private Project resolveOpenedProject(@Nullable Project fallback, @NotNull Path targetDir) {
        Application app = ApplicationManager.getApplication();
        if (app != null) {
            try {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null) {
                    Project[] openProjects = pm.getOpenProjects();
                    if (openProjects != null) {
                        for (Project p : openProjects) {
                            if (p != null && !p.isDisposed() && p.getBasePath() != null) {
                                if (Path.of(p.getBasePath()).equals(targetDir)) {
                                    return p;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private void handleError(@Nullable Project project, @NotNull Throwable t) {
        // WorkspaceService bọc lỗi API trong WorkspaceException: tìm nguyên nhân gốc để báo đúng thông điệp spec.
        AuthenticationException auth = findCause(t, AuthenticationException.class);
        MiniLeetCodeApiException api = findCause(t, MiniLeetCodeApiException.class);
        if (auth != null) {
            notificationPresenter.showNotification(project, "Mini LeetCode",
                    "Token hết hiệu lực, tạo token mới trên web rồi dán vào Settings › Tools › Mini LeetCode.",
                    NotificationType.ERROR);
            ErrorMessages.openSettings(project);
            return;
        }
        String message;
        if (api != null && api.getStatusCode() == 403) {
            message = "Thao tác này chỉ làm được trên web.";
        } else if (api != null && api.getStatusCode() == 404) {
            message = "Stage đang được cập nhật hoặc đã bị gỡ, bấm Refresh rồi thử lại.";
        } else if (t instanceof WorkspaceException we) {
            message = we.getMessage();
        } else {
            message = "Failed to open stage: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
        notificationPresenter.showNotification(project, "Mini LeetCode", message, NotificationType.ERROR);
    }

    @Nullable
    static <T extends Throwable> T findCause(@Nullable Throwable t, @NotNull Class<T> type) {
        for (Throwable c = t; c != null; c = (c.getCause() == c ? null : c.getCause())) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    @Nullable
    private TreeNode getSelectedTreeNode(@Nullable Tree targetTree) {
        if (targetTree == null) {
            return null;
        }
        TreePath path = targetTree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object comp = path.getLastPathComponent();
        return (comp instanceof TreeNode) ? (TreeNode) comp : null;
    }

    @Nullable
    private Tree resolveTree(@NotNull AnActionEvent e) {
        if (this.tree != null) {
            return this.tree;
        }
        Component comp = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        return (comp instanceof Tree) ? (Tree) comp : null;
    }
}
