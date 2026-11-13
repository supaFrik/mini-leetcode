package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import jsd.toiyeujava.minileetcode.plugin.actions.OpenStageAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.MouseEvent;

/**
 * ToolWindowFactory creating and registering the "Mini LeetCode" Tool Window.
 * Implements DumbAware so the window remains interactive during file indexing.
 *
 * Structure:
 * - SimpleToolWindowPanel (vertical layout)
 *   - North Toolbar: OpenStageAction, RefreshPlansAction
 *   - Center: JBScrollPane wrapping Tree
 *     - Tree rendered via StudyPlanTreeModel and StudyPlanTreeCellRenderer
 *     - DoubleClickListener installed for StageTreeNode and ItemTreeNode opening
 *     - PopupHandler installed for context popup menu
 * - Automatically triggers initial study plan load upon window creation.
 */
public class MiniLeetCodeToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String TOOL_WINDOW_ID = "Mini LeetCode";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setIcon(MiniLeetCodeIcons.TOOL_WINDOW);

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

        // 3. Attach Double-Click Listener to Tree (PL-3, PL-4)
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
                    return true;
                } else if (node instanceof ItemTreeNode itemNode) {
                    openStageAction.openSelectedNode(project, itemNode);
                    return true;
                }
                return false; // Allows PlanTreeNode expand/collapse
            }
        }.installOn(tree);

        // 4. Attach Context Popup Menu to Tree (PopupHandler)
        DefaultActionGroup popupGroup = new DefaultActionGroup();
        popupGroup.add(openStageAction);
        popupGroup.addSeparator();
        popupGroup.add(refreshAction);

        PopupHandler.installFollowingSelectionTreePopup(
                tree,
                popupGroup,
                "MiniLeetCodeTreePopup"
        );

        // 5. Main Tool Window Panel & Toolbar
        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true);
        DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(openStageAction);
        toolbarGroup.add(refreshAction);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                "MiniLeetCodeToolbar",
                toolbarGroup,
                true
        );
        toolbar.setTargetComponent(tree);
        panel.setToolbar(toolbar.getComponent());

        // 6. Center Viewport
        JBScrollPane scrollPane = new JBScrollPane(tree);
        panel.setContent(scrollPane);

        // 7. Register Content in IntelliJ ToolWindowManager
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // 8. Trigger automatic initial study plan load
        refreshAction.performRefresh(project);
    }
}
