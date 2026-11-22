package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import jsd.toiyeujava.minileetcode.plugin.actions.CheckAction;
import jsd.toiyeujava.minileetcode.plugin.actions.OpenStageAction;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Optional;

/**
 * ToolWindowFactory creating and registering the "Mini LeetCode" Tool Window.
 * Implements DumbAware so the window remains interactive during file indexing.
 *
 * Tabbed Layout (Decision D1, Slice C):
 * - Tab 0: "Study Plans" (Tree hierarchy of plans, items, and stages).
 * - Tab 1: "Problem View" (Header, Run/Submit action bar, Result Section, and rendered Markdown statement).
 */
public class MiniLeetCodeToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String TOOL_WINDOW_ID = "Mini LeetCode";
    public static final Key<ProblemViewPanel> PROBLEM_VIEW_KEY = Key.create("MINI_LEETCODE_PROBLEM_VIEW");
    public static final Key<JBTabbedPane> TABBED_PANE_KEY = Key.create("MINI_LEETCODE_TABBED_PANE");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setIcon(MiniLeetCodeIcons.TOOL_WINDOW);
        ContentFactory contentFactory = ContentFactory.getInstance();

        // 1. Initialize Tree Model & Tree View
        StudyPlanTreeModel treeModel = new StudyPlanTreeModel();
        Tree tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new StudyPlanTreeCellRenderer());
        tree.getEmptyText().setText("No study plans available");

        // 2. Initialize Actions
        RefreshPlansAction refreshAction = new RefreshPlansAction(treeModel);
        OpenStageAction openStageAction = new OpenStageAction(tree);

        // 3. Initialize Tab 2: Problem View Panel
        ProblemViewPanel problemViewPanel = new ProblemViewPanel(project, () -> refreshAction.performRefresh(project));

        // 4. Create Tab 1: Study Plans Panel
        SimpleToolWindowPanel studyPlansPanel = new SimpleToolWindowPanel(true, true);
        DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(openStageAction);
        toolbarGroup.add(refreshAction);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "MiniLeetCodeToolbar",
                toolbarGroup,
                true
        );
        toolbar.setTargetComponent(tree);
        studyPlansPanel.setToolbar(toolbar.getComponent());

        JBScrollPane scrollPane = new JBScrollPane(tree);
        studyPlansPanel.setContent(scrollPane);

        // 5. Construct Visual JBTabbedPane (Decision D1, Slice C)
        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab("Study Plans", AllIcons.Nodes.Folder, studyPlansPanel);
        tabbedPane.addTab("Problem View", AllIcons.Toolwindows.Documentation, problemViewPanel);

        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 1) {
                problemViewPanel.updateActionButtonsState();
            }
        });

        // Store references on Project for global actions
        project.putUserData(PROBLEM_VIEW_KEY, problemViewPanel);
        project.putUserData(TABBED_PANE_KEY, tabbedPane);

        // 6. Tree Selection Listener: single click on a Stage node updates Problem View (D2)
        tree.addTreeSelectionListener(e -> {
            TreePath path = e.getPath();
            if (path != null && path.getLastPathComponent() instanceof StageTreeNode stageNode) {
                loadStageIntoProblemView(stageNode, problemViewPanel, tabbedPane, false);
            }
        });

        // 7. Double-Click Listener on Tree: open workspace and switch to Problem View
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                TreePath path = tree.getPathForLocation(event.getX(), event.getY());
                if (path == null) {
                    path = tree.getSelectionPath();
                }
                if (path == null) {
                    return false;
                }
                Object node = path.getLastPathComponent();
                if (node instanceof StageTreeNode stageNode) {
                    openStageAction.openSelectedNode(project, stageNode);
                    loadStageIntoProblemView(stageNode, problemViewPanel, tabbedPane, true);
                    return true;
                } else if (node instanceof ItemTreeNode itemNode) {
                    openStageAction.openSelectedNode(project, itemNode);
                    return true;
                }
                return false;
            }
        }.installOn(tree);

        // 8. Attach Context Popup Menu to Tree
        DefaultActionGroup popupGroup = new DefaultActionGroup();
        popupGroup.add(openStageAction);
        popupGroup.addSeparator();
        popupGroup.add(refreshAction);

        PopupHandler.installFollowingSelectionTreePopup(
                tree,
                popupGroup,
                "MiniLeetCodeTreePopup"
        );

        // 9. Register Root Content wrapping the JBTabbedPane
        Content content = contentFactory.createContent(tabbedPane, "", false);
        toolWindow.getContentManager().addContent(content);

        // 10. Auto-detect open Mini LeetCode project and preload stage into Problem View (FR-PV-02)
        detectAndLoadActiveProjectStage(project, problemViewPanel, tabbedPane);

        // 11. Trigger automatic initial study plan load in background
        refreshAction.performRefresh(project);
    }

    public static void loadStageIntoProblemView(
            @NotNull StageTreeNode stageNode,
            @NotNull ProblemViewPanel problemViewPanel,
            @Nullable JBTabbedPane tabbedPane,
            boolean switchToTab
    ) {
        if (stageNode.isLocked()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                problemViewPanel.setStage(stageNode, createFallbackProblem(stageNode, "Stage is locked. Complete previous stages first."));
                if (switchToTab && tabbedPane != null) {
                    tabbedPane.setSelectedIndex(1);
                }
            });
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String serverUrl = MiniLeetCodeSettingsState.getInstance().getNormalizedServerUrl();
                String pat = MiniLeetCodePasswordSafe.getInstance().getPassword();
                if (serverUrl == null || serverUrl.isBlank()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        problemViewPanel.setStage(stageNode, createFallbackProblem(stageNode, "Server URL not configured in Settings."));
                        if (switchToTab && tabbedPane != null) {
                            tabbedPane.setSelectedIndex(1);
                        }
                    });
                    return;
                }

                ApiClient client = new ApiClient(serverUrl, pat);
                ProblemDto problemDto = client.getProblem(stageNode.getSlug());
                ApplicationManager.getApplication().invokeLater(() -> {
                    problemViewPanel.setStage(stageNode, problemDto);
                    if (switchToTab && tabbedPane != null) {
                        tabbedPane.setSelectedIndex(1);
                    }
                });
            } catch (Throwable t) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    problemViewPanel.setStage(stageNode, createFallbackProblem(stageNode, t.getMessage()));
                    if (switchToTab && tabbedPane != null) {
                        tabbedPane.setSelectedIndex(1);
                    }
                });
            }
        });
    }

    private static void detectAndLoadActiveProjectStage(
            @NotNull Project project,
            @NotNull ProblemViewPanel problemViewPanel,
            @Nullable JBTabbedPane tabbedPane
    ) {
        if (project.getBasePath() == null) {
            return;
        }
        Path projectRoot = Path.of(project.getBasePath());
        String dirName = projectRoot.getFileName() != null ? projectRoot.getFileName().toString() : "";

        Optional<Path> mlcRoot = CheckAction.findMlcRoot(project, null);
        if (mlcRoot.isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ProjectManifest manifest = ProjectManifest.readFromProject(mlcRoot.get());
                if (manifest.problemSlug() == null || !dirName.equalsIgnoreCase(manifest.problemSlug())) {
                    return;
                }
                String serverUrl = MiniLeetCodeSettingsState.getInstance().getNormalizedServerUrl();
                String pat = MiniLeetCodePasswordSafe.getInstance().getPassword();
                if (serverUrl == null || serverUrl.isBlank()) {
                    return;
                }

                ApiClient client = new ApiClient(serverUrl, pat);
                ProblemDto problemDto = client.getProblem(manifest.problemSlug());
                if (problemDto != null) {
                    StageDto stageDto = new StageDto(
                            manifest.currentStage(),
                            problemDto.id(),
                            problemDto.slug(),
                            problemDto.title(),
                            problemDto.difficulty(),
                            problemDto.type(),
                            true,
                            StageStatus.IN_PROGRESS
                    );
                    StageTreeNode stageNode = new StageTreeNode(stageDto);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        problemViewPanel.setStage(stageNode, problemDto);
                        if (tabbedPane != null) {
                            tabbedPane.setSelectedIndex(1);
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static ProblemDto createFallbackProblem(StageTreeNode stageNode, String detail) {
        return new ProblemDto(
                stageNode.getProblemId(),
                stageNode.getTitle(),
                stageNode.getSlug(),
                "# " + stageNode.getTitle() + "\n\n*(Could not load description: " + detail + ")*"
        );
    }
}
