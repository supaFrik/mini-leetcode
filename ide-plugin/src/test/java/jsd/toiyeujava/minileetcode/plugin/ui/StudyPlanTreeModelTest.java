package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.icons.AllIcons;
import com.intellij.ide.passwordSafe.PasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit test suite for Milestone 3 (PL-2):
 * - 3-Tier Swing Tree Hierarchy Data Model (PlanTreeNode, ItemTreeNode, StageTreeNode, PlaceholderTreeNode, StudyPlanTreeModel)
 * - Custom Cell Renderer formatting and status icon mapping (StudyPlanTreeCellRenderer, MiniLeetCodeIcons)
 * - TreeModel mutation, reloading, and TreeModelListener lifecycle events
 * - Toolbar Refresh action with MockWebServer API integration (RefreshPlansAction)
 *
 * Runs 100% reliably in a headless JVM (java.awt.headless=true) without HeadlessException or heavy IDE fixtures.
 */
class StudyPlanTreeModelTest {

    private MockWebServer server;
    private StudyPlanTreeModel treeModel;
    private JTree dummyTree;
    private StudyPlanTreeCellRenderer renderer;
    private TestPasswordSafe inMemoryPlatformSafe;

    private static final String SAMPLE_PAT = "mlc_pat_test_token_1234567890abcdef12345678";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        inMemoryPlatformSafe = new TestPasswordSafe();
        MiniLeetCodePasswordSafe.setTestInstance(new MiniLeetCodePasswordSafe(inMemoryPlatformSafe));

        treeModel = new StudyPlanTreeModel();
        dummyTree = new JTree(treeModel);
        renderer = new StudyPlanTreeCellRenderer();

        // Configure settings and password test state
        MiniLeetCodeSettingsState.getInstance().setServerUrl(server.url("/").toString());
        MiniLeetCodePasswordSafe.getInstance().setPassword(SAMPLE_PAT);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
        MiniLeetCodePasswordSafe.setTestInstance(null);
    }

    // =========================================================================
    // Scenario 1: testEmptyModel_ShowsPlaceholder
    // =========================================================================

    @Test
    @DisplayName("Scenario 1: testEmptyModel_ShowsPlaceholder - newly initialized or empty model contains single placeholder node")
    void testEmptyModel_ShowsPlaceholder() {
        assertTrue(treeModel.isEmptyState(), "Model should report isEmptyState = true initially");
        assertFalse(treeModel.isLoading(), "Model should not be in loading state");
        assertFalse(treeModel.isErrorState(), "Model should not be in error state");
        assertFalse(treeModel.hasPlans(), "Model should not report hasPlans = true");
        assertEquals(0, treeModel.getPlanCount(), "Plan count should be 0");

        DefaultMutableTreeNode root = treeModel.getRootNode();
        assertNotNull(root, "Root node must not be null");
        assertEquals(1, root.getChildCount(), "Empty root should have exactly 1 placeholder child");

        TreeNode child = root.getChildAt(0);
        assertInstanceOf(PlaceholderTreeNode.class, child, "Child must be instance of PlaceholderTreeNode");
        PlaceholderTreeNode placeholder = (PlaceholderTreeNode) child;
        assertTrue(placeholder.isEmptyState(), "Placeholder should have type EMPTY");
        assertEquals("No study plans available", placeholder.getMessage());
        assertTrue(placeholder.isLeaf(), "Placeholder must be a leaf node");
        assertFalse(placeholder.getAllowsChildren(), "Placeholder must not allow children");

        // Verify renderer formatting for empty placeholder node
        renderer.getTreeCellRendererComponent(dummyTree, placeholder, false, false, true, 0, false);
        String renderedText = renderer.getCharSequence(false).toString();
        assertTrue(renderedText.contains("No study plans available"),
                "Renderer must display 'No study plans available' for empty placeholder node");
        assertSame(AllIcons.General.Information, renderer.getIcon(),
                "Empty placeholder should display General.Information icon");

        // Setting empty list or calling clear() maintains empty state
        treeModel.setPlans(Collections.emptyList());
        assertTrue(treeModel.isEmptyState());
        assertEquals(1, root.getChildCount());

        treeModel.clear();
        assertTrue(treeModel.isEmptyState());
        assertEquals(1, root.getChildCount());
    }

    // =========================================================================
    // Scenario 2: testLoadingState_ShowsLoadingNode
    // =========================================================================

    @Test
    @DisplayName("Scenario 2: testLoadingState_ShowsLoadingNode - setLoading() displays loading placeholder node")
    void testLoadingState_ShowsLoadingNode() {
        treeModel.setLoading();

        assertTrue(treeModel.isLoading(), "Model should report isLoading = true");
        assertFalse(treeModel.isEmptyState(), "Model should not report empty state");
        assertFalse(treeModel.isErrorState(), "Model should not report error state");
        assertFalse(treeModel.hasPlans(), "Model should not report hasPlans");

        DefaultMutableTreeNode root = treeModel.getRootNode();
        assertEquals(1, root.getChildCount(), "Loading root should have exactly 1 child");

        TreeNode child = root.getChildAt(0);
        assertInstanceOf(PlaceholderTreeNode.class, child);
        PlaceholderTreeNode placeholder = (PlaceholderTreeNode) child;
        assertTrue(placeholder.isLoading(), "Placeholder must have type LOADING");
        assertEquals("Loading study plans...", placeholder.getMessage());

        // Verify renderer formatting for loading placeholder
        renderer.getTreeCellRendererComponent(dummyTree, placeholder, false, false, true, 0, false);
        String renderedText = renderer.getCharSequence(false).toString();
        assertTrue(renderedText.contains("Loading study plans..."),
                "Renderer must display 'Loading study plans...' text");
        assertSame(AllIcons.Process.Step_1, renderer.getIcon(),
                "Loading placeholder should display Process.Step_1 progress icon");

        // Test custom loading message
        treeModel.setLoading("Synchronizing with remote server...");
        assertEquals("Synchronizing with remote server...", treeModel.getPlaceholderNode().getMessage());
        renderer.getTreeCellRendererComponent(dummyTree, treeModel.getPlaceholderNode(), false, false, true, 0, false);
        assertTrue(renderer.getCharSequence(false).toString().contains("Synchronizing with remote server..."));
    }

    // =========================================================================
    // Scenario 3: testErrorState_ShowsErrorMessage
    // =========================================================================

    @Test
    @DisplayName("Scenario 3: testErrorState_ShowsErrorMessage - setError(msg) displays error placeholder with failure reason")
    void testErrorState_ShowsErrorMessage() {
        String errorReason = "Connection timed out: Backend host unreachable (504)";
        treeModel.setError(errorReason);

        assertTrue(treeModel.isErrorState(), "Model should report isErrorState = true");
        assertFalse(treeModel.isLoading(), "Model should not report loading state");
        assertFalse(treeModel.isEmptyState(), "Model should not report empty state");
        assertFalse(treeModel.hasPlans(), "Model should not report hasPlans");

        DefaultMutableTreeNode root = treeModel.getRootNode();
        assertEquals(1, root.getChildCount(), "Error root should have exactly 1 child");

        TreeNode child = root.getChildAt(0);
        assertInstanceOf(PlaceholderTreeNode.class, child);
        PlaceholderTreeNode placeholder = (PlaceholderTreeNode) child;
        assertTrue(placeholder.isErrorState(), "Placeholder must have type ERROR");
        assertTrue(placeholder.getMessage().contains(errorReason),
                "Placeholder message must contain failure reason");
        assertEquals(placeholder.getMessage(), treeModel.getErrorMessage());

        // Verify renderer formatting for error placeholder
        renderer.getTreeCellRendererComponent(dummyTree, placeholder, false, false, true, 0, false);
        String renderedText = renderer.getCharSequence(false).toString();
        assertTrue(renderedText.contains("Failed to load study plans"),
                "Renderer must include 'Failed to load study plans' prefix");
        assertTrue(renderedText.contains(errorReason),
                "Renderer must display the specific failure reason");
        assertSame(AllIcons.General.Error, renderer.getIcon(),
                "Error placeholder must display General.Error icon");
    }

    // =========================================================================
    // Scenario 4: testSinglePlanHierarchy
    // =========================================================================

    @Test
    @DisplayName("Scenario 4: testSinglePlanHierarchy - verifies 3-tier hierarchy: Root -> PlanNode -> ItemNode -> StageNode")
    void testSinglePlanHierarchy() {
        // Build 3-tier sample data structure:
        // Plan: "OOP với Java" (2 items)
        //   Item 1 (Project): "Mini Bank Console" (2 stages)
        //     Stage 1: "Tài khoản đơn" (DONE)
        //     Stage 2: "Kế thừa & Đa hình" (IN_PROGRESS)
        //   Item 2 (Problem): "Shape Area Hierarchy" (1 stage)
        //     Stage 1: "Shape Area" (NEVER)

        StageDto stage1 = new StageDto(1, 101L, "mini-bank-stage-1", "Tài khoản đơn",
                Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE);
        StageDto stage2 = new StageDto(2, 102L, "mini-bank-stage-2", "Kế thừa & Đa hình",
                Difficulty.MEDIUM, ProblemType.OOP_GRINDING, true, StageStatus.IN_PROGRESS);
        ItemDto projectItem = new ItemDto(10L, 1, StudyPlanItemKind.PROJECT, "Mini Bank Console",
                "Dự án ngân hàng console", false, List.of(stage1, stage2));

        StageDto stage3 = new StageDto(1, 201L, "shape-area", "Shape Area",
                Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.NEVER);
        ItemDto problemItem = new ItemDto(20L, 2, StudyPlanItemKind.PROBLEM, "Shape Area Hierarchy",
                "Tính diện tích đa hình", false, List.of(stage3));

        PlanDetail plan = new PlanDetail(1L, "oop-with-java", "OOP với Java", "Lộ trình thực chiến OOP",
                StudyPlanStatus.PUBLISHED, 3, 1, List.of(projectItem, problemItem));

        treeModel.setPlans(List.of(plan));

        // 1. Root assertions
        assertTrue(treeModel.hasPlans());
        assertEquals(1, treeModel.getPlanCount());
        DefaultMutableTreeNode root = treeModel.getRootNode();
        assertEquals(1, root.getChildCount(), "Root must have exactly 1 PlanNode child");

        // 2. Plan Level (Tier 1)
        TreeNode tier1 = root.getChildAt(0);
        assertInstanceOf(PlanTreeNode.class, tier1);
        PlanTreeNode planNode = (PlanTreeNode) tier1;
        assertEquals(1L, planNode.getId());
        assertEquals("oop-with-java", planNode.getSlug());
        assertEquals("OOP với Java", planNode.getTitle());
        assertEquals("Lộ trình thực chiến OOP", planNode.getDescription());
        assertEquals(StudyPlanStatus.PUBLISHED, planNode.getStatus());
        assertEquals(3, planNode.getStageCount());
        assertEquals(1, planNode.getDoneStages());
        assertEquals(33, planNode.getProgressPercentage());
        assertFalse(planNode.isCompleted());
        assertEquals(2, planNode.getItemCount());
        assertEquals(2, planNode.getChildCount(), "PlanNode must have 2 ItemNode children");
        assertTrue(planNode.getAllowsChildren());
        assertFalse(planNode.isLeaf());

        // 3. Item Level (Tier 2)
        ItemTreeNode item1Node = (ItemTreeNode) planNode.getChildAt(0);
        assertEquals(10L, item1Node.getId());
        assertEquals(1, item1Node.getPosition());
        assertEquals(1, item1Node.getOrder());
        assertEquals(StudyPlanItemKind.PROJECT, item1Node.getKind());
        assertTrue(item1Node.isProject());
        assertFalse(item1Node.isProblem());
        assertEquals("Mini Bank Console", item1Node.getTitle());
        assertEquals("Dự án ngân hàng console", item1Node.getDescription());
        assertEquals("mini-bank-stage-1", item1Node.getProblemSlug());
        assertEquals(2, item1Node.getStageCount());
        assertEquals(1, item1Node.getDoneStageCount());
        assertEquals(2, item1Node.getChildCount(), "Item 1 must have 2 StageNode children");
        assertSame(planNode, item1Node.getPlanNode(), "ItemNode.getPlanNode() must return parent PlanNode");

        ItemTreeNode item2Node = (ItemTreeNode) planNode.getChildAt(1);
        assertEquals(20L, item2Node.getId());
        assertEquals(2, item2Node.getPosition());
        assertEquals(StudyPlanItemKind.PROBLEM, item2Node.getKind());
        assertFalse(item2Node.isProject());
        assertTrue(item2Node.isProblem());
        assertEquals("Shape Area Hierarchy", item2Node.getTitle());
        assertEquals("shape-area", item2Node.getProblemSlug());
        assertEquals(1, item2Node.getStageCount());
        assertEquals(1, item2Node.getChildCount(), "Item 2 must have 1 StageNode child");

        // 4. Stage Level (Tier 3)
        StageTreeNode stage1Node = (StageTreeNode) item1Node.getChildAt(0);
        assertEquals(1, stage1Node.getStageNo());
        assertEquals(1, stage1Node.getStageOrder());
        assertEquals(101L, stage1Node.getProblemId());
        assertEquals("mini-bank-stage-1", stage1Node.getSlug());
        assertEquals("Tài khoản đơn", stage1Node.getTitle());
        assertEquals(Difficulty.EASY, stage1Node.getDifficulty());
        assertEquals(StageStatus.DONE, stage1Node.getStatus());
        assertTrue(stage1Node.isDone());
        assertFalse(stage1Node.isLocked());
        assertTrue(stage1Node.isLeaf(), "StageNode must be a leaf node");
        assertFalse(stage1Node.getAllowsChildren(), "StageNode must not allow children");
        assertSame(item1Node, stage1Node.getItemNode(), "StageNode.getItemNode() must return parent ItemNode");
        assertSame(planNode, stage1Node.getPlanNode(), "StageNode.getPlanNode() must traverse to PlanNode");

        StageTreeNode stage2Node = (StageTreeNode) item1Node.getChildAt(1);
        assertEquals(2, stage2Node.getStageNo());
        assertEquals("Kế thừa & Đa hình", stage2Node.getTitle());
        assertEquals(StageStatus.IN_PROGRESS, stage2Node.getStatus());
        assertTrue(stage2Node.isInProgress());

        StageTreeNode stage3Node = (StageTreeNode) item2Node.getChildAt(0);
        assertEquals(1, stage3Node.getStageNo());
        assertEquals("Shape Area", stage3Node.getTitle());
        assertEquals(StageStatus.NEVER, stage3Node.getStatus());
        assertTrue(stage3Node.isNever());

        // 5. Query helpers & TreePath navigation
        assertSame(planNode, treeModel.findPlanBySlug("oop-with-java"));
        assertSame(planNode, treeModel.findPlanById(1L));
        assertSame(item1Node, treeModel.findItemById(10L));
        assertSame(stage1Node, treeModel.findStageBySlug("mini-bank-stage-1"));
        assertSame(stage1Node, treeModel.findStageByProblemId(101L));

        TreePath path = new TreePath(new Object[]{root, planNode, item1Node, stage1Node});
        assertEquals(4, path.getPathCount());
        assertSame(root, path.getPathComponent(0));
        assertSame(planNode, path.getPathComponent(1));
        assertSame(item1Node, path.getPathComponent(2));
        assertSame(stage1Node, path.getPathComponent(3));
        assertSame(stage1Node, path.getLastPathComponent());
    }

    // =========================================================================
    // Scenario 5: testMultiplePlansWithAllFourStatusIcons
    // =========================================================================

    @Test
    @DisplayName("Scenario 5: testMultiplePlansWithAllFourStatusIcons - verifies renderer assigns correct Icon for LOCKED, NEVER, IN_PROGRESS, DONE across plans")
    void testMultiplePlansWithAllFourStatusIcons() {
        // Plan 1: 3 stages covering LOCKED, NEVER, IN_PROGRESS
        StageDto stageLocked = new StageDto(1, 301L, "slug-locked", "Khóa", Difficulty.HARD, ProblemType.OOP_GRINDING, true, StageStatus.LOCKED);
        StageDto stageNever = new StageDto(2, 302L, "slug-never", "Chưa làm", Difficulty.MEDIUM, ProblemType.OOP_GRINDING, true, StageStatus.NEVER);
        StageDto stageProgress = new StageDto(3, 303L, "slug-progress", "Đang làm", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.IN_PROGRESS);
        ItemDto item1 = new ItemDto(30L, 1, StudyPlanItemKind.PROJECT, "Project A", "Desc", false,
                List.of(stageLocked, stageNever, stageProgress));
        PlanDetail plan1 = new PlanDetail(3L, "plan-a", "Plan Alpha", "Desc", StudyPlanStatus.PUBLISHED, 3, 0, List.of(item1));

        // Plan 2: 1 stage covering DONE
        StageDto stageDone = new StageDto(1, 401L, "slug-done", "Hoàn thành", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE);
        ItemDto item2 = new ItemDto(40L, 1, StudyPlanItemKind.PROBLEM, "Problem B", "Desc", true, List.of(stageDone));
        PlanDetail plan2 = new PlanDetail(4L, "plan-b", "Plan Beta", "Desc", StudyPlanStatus.PUBLISHED, 1, 1, List.of(item2));

        treeModel.setPlans(List.of(plan1, plan2));

        // Assert 2 plans in root
        assertEquals(2, treeModel.getPlanCount());
        assertEquals(2, treeModel.getRootNode().getChildCount());

        PlanTreeNode planNode1 = (PlanTreeNode) treeModel.getRootNode().getChildAt(0);
        ItemTreeNode itemNode1 = (ItemTreeNode) planNode1.getChildAt(0);
        StageTreeNode nodeLocked = (StageTreeNode) itemNode1.getChildAt(0);
        StageTreeNode nodeNever = (StageTreeNode) itemNode1.getChildAt(1);
        StageTreeNode nodeProgress = (StageTreeNode) itemNode1.getChildAt(2);

        PlanTreeNode planNode2 = (PlanTreeNode) treeModel.getRootNode().getChildAt(1);
        ItemTreeNode itemNode2 = (ItemTreeNode) planNode2.getChildAt(0);
        StageTreeNode nodeDone = (StageTreeNode) itemNode2.getChildAt(0);

        // 1. LOCKED status icon assertion
        renderer.getTreeCellRendererComponent(dummyTree, nodeLocked, false, false, true, 0, false);
        assertSame(MiniLeetCodeIcons.LOCKED, renderer.getIcon(),
                "Stage with LOCKED status must be rendered with MiniLeetCodeIcons.LOCKED");

        // 2. NEVER status icon assertion
        renderer.getTreeCellRendererComponent(dummyTree, nodeNever, false, false, true, 1, false);
        assertSame(MiniLeetCodeIcons.NEVER, renderer.getIcon(),
                "Stage with NEVER status must be rendered with MiniLeetCodeIcons.NEVER");

        // 3. IN_PROGRESS status icon assertion
        renderer.getTreeCellRendererComponent(dummyTree, nodeProgress, false, false, true, 2, false);
        assertSame(MiniLeetCodeIcons.IN_PROGRESS, renderer.getIcon(),
                "Stage with IN_PROGRESS status must be rendered with MiniLeetCodeIcons.IN_PROGRESS");

        // 4. DONE status icon assertion
        renderer.getTreeCellRendererComponent(dummyTree, nodeDone, false, false, true, 3, false);
        assertSame(MiniLeetCodeIcons.DONE, renderer.getIcon(),
                "Stage with DONE status must be rendered with MiniLeetCodeIcons.DONE");

        // 5. Direct MiniLeetCodeIcons helper assertions
        assertSame(MiniLeetCodeIcons.LOCKED, MiniLeetCodeIcons.getStatusIcon(StageStatus.LOCKED));
        assertSame(MiniLeetCodeIcons.NEVER, MiniLeetCodeIcons.getStatusIcon(StageStatus.NEVER));
        assertSame(MiniLeetCodeIcons.IN_PROGRESS, MiniLeetCodeIcons.getStatusIcon(StageStatus.IN_PROGRESS));
        assertSame(MiniLeetCodeIcons.DONE, MiniLeetCodeIcons.getStatusIcon(StageStatus.DONE));
        assertSame(MiniLeetCodeIcons.NEVER, MiniLeetCodeIcons.getStatusIcon(null),
                "getStatusIcon(null) should safely default to NEVER");
    }

    // =========================================================================
    // Scenario 6: testTreeModelReloadAndRefresh
    // =========================================================================

    @Test
    @DisplayName("Scenario 6: testTreeModelReloadAndRefresh - updating plans fires treeStructureChanged and unlinks old nodes")
    void testTreeModelReloadAndRefresh() {
        TestTreeModelListener listener = new TestTreeModelListener();
        treeModel.addTreeModelListener(listener);

        int initialEvents = listener.structureChangedCount.get();

        // 1. First batch of plans
        StageDto s1 = new StageDto(1, 501L, "stage-1", "S1", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE);
        ItemDto i1 = new ItemDto(50L, 1, StudyPlanItemKind.PROBLEM, "I1", "D1", true, List.of(s1));
        PlanDetail p1 = new PlanDetail(5L, "plan-1", "Plan 1", "D", StudyPlanStatus.PUBLISHED, 1, 1, List.of(i1));

        treeModel.setPlans(List.of(p1));
        assertTrue(listener.structureChangedCount.get() > initialEvents,
                "setPlans must fire treeStructureChanged event");

        PlanTreeNode oldPlanNode = (PlanTreeNode) treeModel.getRootNode().getChildAt(0);
        assertNotNull(oldPlanNode.getParent(), "Old plan node must be attached to root");

        // 2. Reload with second batch of plans (simulating refresh)
        StageDto s2 = new StageDto(1, 601L, "stage-2", "S2", Difficulty.MEDIUM, ProblemType.OOP_GRINDING, true, StageStatus.IN_PROGRESS);
        ItemDto i2 = new ItemDto(60L, 1, StudyPlanItemKind.PROJECT, "I2", "D2", false, List.of(s2));
        PlanDetail p2 = new PlanDetail(6L, "plan-2", "Plan 2", "D", StudyPlanStatus.PUBLISHED, 1, 0, List.of(i2));

        int countBeforeReload = listener.structureChangedCount.get();
        treeModel.setPlans(List.of(p2));
        assertEquals(countBeforeReload + 1, listener.structureChangedCount.get(),
                "Reloading plans must fire treeStructureChanged exactly once");

        PlanTreeNode newPlanNode = (PlanTreeNode) treeModel.getRootNode().getChildAt(0);
        assertNotSame(oldPlanNode, newPlanNode, "New plan node must replace old plan node");
        assertEquals("plan-2", newPlanNode.getSlug());
        assertNull(oldPlanNode.getParent(),
                "Crucial: Old plan node must be detached from root to avoid stale references and memory leaks");

        // 3. Transitions to loading, error, and empty all fire treeStructureChanged
        int countBeforeLoading = listener.structureChangedCount.get();
        treeModel.setLoading();
        assertEquals(countBeforeLoading + 1, listener.structureChangedCount.get());
        assertNull(newPlanNode.getParent(), "Previous nodes must be detached on transition to loading");

        int countBeforeError = listener.structureChangedCount.get();
        treeModel.setError("Network failure");
        assertEquals(countBeforeError + 1, listener.structureChangedCount.get());

        int countBeforeEmpty = listener.structureChangedCount.get();
        treeModel.setEmpty();
        assertEquals(countBeforeEmpty + 1, listener.structureChangedCount.get());
    }

    // =========================================================================
    // Scenario 7: testTreeCellRenderer_CustomFormatting
    // =========================================================================

    @Test
    @DisplayName("Scenario 7: testTreeCellRenderer_CustomFormatting - tests styling, badges, and tooltips across Plan, Item, Stage, and Placeholders")
    void testTreeCellRenderer_CustomFormatting() {
        StageDto stage = new StageDto(2, 701L, "bank-s2", "Withdraw Limit",
                Difficulty.MEDIUM, ProblemType.OOP_GRINDING, true, StageStatus.DONE);
        ItemDto projectItem = new ItemDto(70L, 1, StudyPlanItemKind.PROJECT, "Banking",
                "Console banking app", true, List.of(stage));
        ItemDto problemItem = new ItemDto(71L, 2, StudyPlanItemKind.PROBLEM, "Two Sum",
                "Array hash map", false, List.of());
        PlanDetail plan = new PlanDetail(7L, "fintech", "Fintech Track", "Core finance programming",
                StudyPlanStatus.PUBLISHED, 5, 2, List.of(projectItem, problemItem));

        PlanTreeNode planNode = new PlanTreeNode(plan);
        ItemTreeNode projectNode = new ItemTreeNode(projectItem);
        ItemTreeNode problemNode = new ItemTreeNode(problemItem);
        StageTreeNode stageNode = new StageTreeNode(stage);

        // 1. PlanNode formatting
        renderer.getTreeCellRendererComponent(dummyTree, planNode, false, false, false, 0, false);
        String planText = renderer.getCharSequence(false).toString();
        assertTrue(planText.contains("Fintech Track"), "Plan text must contain plan title");
        assertTrue(planText.contains("2/5 stages"), "Plan text must contain stage progress subtitle");
        assertEquals("Core finance programming", renderer.getToolTipText(), "Tooltip must contain plan description");
        assertSame(MiniLeetCodeIcons.TOOL_WINDOW, renderer.getIcon(), "Plan icon should be TOOL_WINDOW");

        // 2. Project ItemNode formatting
        renderer.getTreeCellRendererComponent(dummyTree, projectNode, false, false, false, 0, false);
        String projectText = renderer.getCharSequence(false).toString();
        assertTrue(projectText.contains("[Project]"), "Project item must contain '[Project]' badge");
        assertTrue(projectText.contains("Banking"), "Project item must contain item title");
        assertTrue(projectText.contains("(Done)"), "Finished project item must display '(Done)' badge");
        assertEquals("Console banking app", renderer.getToolTipText());
        assertSame(AllIcons.Nodes.IdeaProject, renderer.getIcon(), "Project item should have IdeaProject icon");

        // 3. Problem ItemNode formatting
        renderer.getTreeCellRendererComponent(dummyTree, problemNode, false, false, false, 0, false);
        String problemText = renderer.getCharSequence(false).toString();
        assertTrue(problemText.contains("[Problem]"), "Problem item must contain '[Problem]' badge");
        assertTrue(problemText.contains("Two Sum"), "Problem item must contain item title");
        assertFalse(problemText.contains("(Done)"), "Unfinished problem must not display '(Done)' badge");
        assertSame(AllIcons.Nodes.Class, renderer.getIcon(), "Problem item should have Class icon");

        // 4. StageNode formatting
        renderer.getTreeCellRendererComponent(dummyTree, stageNode, false, false, true, 0, false);
        String stageText = renderer.getCharSequence(false).toString();
        assertTrue(stageText.contains("Stage 2:"), "Stage text must contain 'Stage 2:' prefix");
        assertTrue(stageText.contains("Withdraw Limit"), "Stage text must contain stage title");
        assertEquals("Stage 2: Withdraw Limit (DONE)", renderer.getToolTipText());
        assertSame(MiniLeetCodeIcons.DONE, renderer.getIcon());
    }

    // =========================================================================
    // Scenario 8: testMockWebServerRefreshAsync
    // =========================================================================

    @Test
    @DisplayName("Scenario 8: testMockWebServerRefreshAsync - uses MockWebServer to verify RefreshPlansAction API fetch and tree population")
    void testMockWebServerRefreshAsync() throws InterruptedException {
        String plansListJson = """
            [
              {
                "id": 1,
                "slug": "oop-with-java",
                "title": "OOP với Java",
                "description": "Lộ trình thực chiến OOP",
                "status": "PUBLISHED",
                "itemCount": 1,
                "stageCount": 2,
                "doneStages": 1
              }
            ]
            """;

        String planDetailJson = """
            {
              "id": 1,
              "slug": "oop-with-java",
              "title": "OOP với Java",
              "description": "Lộ trình thực chiến OOP",
              "status": "PUBLISHED",
              "stageCount": 2,
              "doneStages": 1,
              "items": [
                {
                  "id": 10,
                  "position": 1,
                  "kind": "PROJECT",
                  "title": "Mini Bank Console",
                  "description": "Dự án ngân hàng",
                  "done": false,
                  "stages": [
                    {
                      "stageNo": 1,
                      "problemId": 101,
                      "slug": "mini-bank-stage-1",
                      "title": "Tài khoản đơn",
                      "difficulty": "EASY",
                      "problemType": "OOP_GRINDING",
                      "published": true,
                      "status": "DONE"
                    },
                    {
                      "stageNo": 2,
                      "problemId": 102,
                      "slug": "mini-bank-stage-2",
                      "title": "Kế thừa & Đa hình",
                      "difficulty": "MEDIUM",
                      "problemType": "OOP_GRINDING",
                      "published": true,
                      "status": "IN_PROGRESS"
                    }
                  ]
                }
              ]
            }
            """;

        // Enqueue MockWebServer responses
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(plansListJson));

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(planDetailJson));

        RefreshPlansAction action = new RefreshPlansAction(treeModel,
                (url, pat) -> new ApiClient(url, pat, Duration.ofSeconds(5)));

        // 1. Execute synchronous refresh
        action.refreshPlansSync(null, null);

        // Verify model populated
        assertTrue(treeModel.hasPlans(), "TreeModel must contain plans after successful refresh");
        assertEquals(1, treeModel.getPlanCount());
        PlanTreeNode plan = treeModel.findPlanBySlug("oop-with-java");
        assertNotNull(plan);
        assertEquals("OOP với Java", plan.getTitle());
        assertEquals(1, plan.getItemCount());

        ItemTreeNode item = plan.getItemNodes().get(0);
        assertEquals("Mini Bank Console", item.getTitle());
        assertEquals(2, item.getStageCount());
        assertEquals(StageStatus.DONE, item.getStageNodes().get(0).getStatus());
        assertEquals(StageStatus.IN_PROGRESS, item.getStageNodes().get(1).getStatus());

        // 2. Verify HTTP requests sent to MockWebServer
        RecordedRequest req1 = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req1);
        assertEquals("GET", req1.getMethod());
        assertEquals("/api/v1/study-plans", req1.getPath());
        assertEquals("Bearer " + SAMPLE_PAT, req1.getHeader("Authorization"));
        assertEquals("application/json", req1.getHeader("Accept"));

        RecordedRequest req2 = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req2);
        assertEquals("GET", req2.getMethod());
        assertEquals("/api/v1/study-plans/oop-with-java", req2.getPath());
        assertEquals("Bearer " + SAMPLE_PAT, req2.getHeader("Authorization"));

        // 3. Test Authentication Failure (HTTP 401)
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""
                    {
                      "type": "https://api.mini-leetcode.com/errors/unauthorized",
                      "title": "Unauthorized",
                      "status": 401,
                      "code": "PAT_INVALID",
                      "detail": "Personal access token is expired"
                    }
                    """));

        action.refreshPlansSync(null, null);
        assertTrue(treeModel.isErrorState(), "TreeModel must enter error state on HTTP 401");
        String errorMsg = treeModel.getErrorMessage();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("Token hết hiệu lực"), "401 must use the spec message: " + errorMsg);

        // 4. Test Asynchronous refresh dispatch via performRefresh() with CountDownLatch
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(plansListJson));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(planDetailJson));

        CountDownLatch asyncLatch = new CountDownLatch(1);
        treeModel.addTreeModelListener(new TreeModelListenerAdapter() {
            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                if (treeModel.hasPlans()) {
                    asyncLatch.countDown();
                }
            }
        });

        action.performRefresh(null);
        boolean finished = asyncLatch.await(5, TimeUnit.SECONDS);
        assertTrue(finished, "Asynchronous refresh must complete within 5 seconds");
        assertTrue(treeModel.hasPlans(), "TreeModel must contain plans after async refresh");
    }

    // =========================================================================
    // Additional Edge-Case Tests: Polymorphic Rendering & Headless Icon Safety
    // =========================================================================

    @Test
    @DisplayName("Edge Case: testPolymorphicTreeCellRenderer - renderer cleanly handles raw DTO objects in DefaultMutableTreeNode")
    void testPolymorphicTreeCellRenderer() {
        StageDto stage = new StageDto(1, 801L, "raw-stage", "Raw Stage",
                Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE);
        ItemDto item = new ItemDto(80L, 1, StudyPlanItemKind.PROBLEM, "Raw Item", "D", true, List.of(stage));
        PlanDetail plan = new PlanDetail(8L, "raw-plan", "Raw Plan", "D", StudyPlanStatus.PUBLISHED, 1, 1, List.of(item));

        DefaultMutableTreeNode rawPlanNode = new DefaultMutableTreeNode(plan);
        DefaultMutableTreeNode rawItemNode = new DefaultMutableTreeNode(item);
        DefaultMutableTreeNode rawStageNode = new DefaultMutableTreeNode(stage);

        renderer.getTreeCellRendererComponent(dummyTree, rawPlanNode, false, false, false, 0, false);
        assertTrue(renderer.getCharSequence(false).toString().contains("Raw Plan"));

        renderer.getTreeCellRendererComponent(dummyTree, rawItemNode, false, false, false, 0, false);
        assertTrue(renderer.getCharSequence(false).toString().contains("Raw Item"));

        renderer.getTreeCellRendererComponent(dummyTree, rawStageNode, false, false, true, 0, false);
        assertTrue(renderer.getCharSequence(false).toString().contains("Raw Stage"));
        assertSame(MiniLeetCodeIcons.DONE, renderer.getIcon());
    }

    @Test
    @DisplayName("Edge Case: testHeadlessSafeIcons_AndStatusMapping - verifies 16x16 icon dimensions and null safety")
    void testHeadlessSafeIcons_AndStatusMapping() {
        Icon[] icons = new Icon[]{
                MiniLeetCodeIcons.LOCKED,
                MiniLeetCodeIcons.NEVER,
                MiniLeetCodeIcons.IN_PROGRESS,
                MiniLeetCodeIcons.DONE,
                MiniLeetCodeIcons.TOOL_WINDOW,
                MiniLeetCodeIcons.REFRESH
        };

        for (Icon icon : icons) {
            assertNotNull(icon, "Icon constant must never be null");
            assertEquals(16, icon.getIconWidth(), "Icon width must be standard 16px");
            assertEquals(16, icon.getIconHeight(), "Icon height must be standard 16px");
        }
    }

    // =========================================================================
    // Helper Test Listeners
    // =========================================================================

    private static class TestTreeModelListener implements TreeModelListener {
        final AtomicInteger structureChangedCount = new AtomicInteger();
        final AtomicInteger nodesInsertedCount = new AtomicInteger();
        final AtomicInteger nodesRemovedCount = new AtomicInteger();
        final AtomicInteger nodesChangedCount = new AtomicInteger();
        final List<TreeModelEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void treeNodesChanged(TreeModelEvent e) {
            nodesChangedCount.incrementAndGet();
            events.add(e);
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
            nodesInsertedCount.incrementAndGet();
            events.add(e);
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
            nodesRemovedCount.incrementAndGet();
            events.add(e);
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            structureChangedCount.incrementAndGet();
            events.add(e);
        }
    }

    private abstract static class TreeModelListenerAdapter implements TreeModelListener {
        @Override public void treeNodesChanged(TreeModelEvent e) {}
        @Override public void treeNodesInserted(TreeModelEvent e) {}
        @Override public void treeNodesRemoved(TreeModelEvent e) {}
        @Override public void treeStructureChanged(TreeModelEvent e) {}
    }

    static class TestPasswordSafe extends PasswordSafe {

        private final Map<String, String> credentialMap = new ConcurrentHashMap<>();

        @Override
        public @Nullable Credentials get(@NotNull CredentialAttributes attributes) {
            String password = credentialMap.get(attributes.getServiceName());
            if (password == null) {
                return null;
            }
            return new Credentials(attributes.getUserName(), password);
        }

        @Override
        public void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials) {
            set(attributes, credentials, false);
        }

        @Override
        public void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials, boolean memoryOnly) {
            if (credentials == null || credentials.getPasswordAsString() == null) {
                credentialMap.remove(attributes.getServiceName());
            } else {
                credentialMap.put(attributes.getServiceName(), credentials.getPasswordAsString());
            }
        }

        @Override
        public @Nullable String getPassword(@NotNull CredentialAttributes attributes) {
            return credentialMap.get(attributes.getServiceName());
        }

        @Override
        public void setPassword(@NotNull CredentialAttributes attributes, @Nullable String password) {
            if (password == null || password.isBlank()) {
                credentialMap.remove(attributes.getServiceName());
            } else {
                credentialMap.put(attributes.getServiceName(), password.trim());
            }
        }

        @Override
        public boolean isRememberPasswordByDefault() {
            return true;
        }

        @Override
        public void setRememberPasswordByDefault(boolean isRememberPasswordByDefault) {
        }

        @Override
        public boolean isMemoryOnly() {
            return true;
        }

        @Override
        public boolean isPasswordStoredOnlyInMemory(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials) {
            return true;
        }
    }
}
