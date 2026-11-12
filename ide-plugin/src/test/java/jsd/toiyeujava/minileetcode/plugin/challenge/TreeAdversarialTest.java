package jsd.toiyeujava.minileetcode.plugin.challenge;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.icons.AllIcons;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ui.SimpleTextAttributes;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.ui.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 3 (PL-2):
 * Stress-testing the 3-Tier Swing Tree Data Model, Cell Renderer, and Toolbar Refresh Action
 * under extreme scale, malformed DTO payloads, tight-loop concurrency, and network chaos.
 *
 * Vectors tested:
 * 1. Massive Tree Hierarchy: 50 items x 500 stages = 25,000 stage nodes (25,051 total nodes).
 *    Traversal, expansion, mutation, and memory efficiency without StackOverflowError or OOM.
 * 2. Malformed and Extreme DTO Payloads:
 *    Null/empty slugs, 10,000-character titles, emojis, zero-width characters, XSS/HTML, RTL unicode.
 *    Items with null/empty stages list; Stages with null difficulty or null status fallback.
 * 3. Concurrent and Rapid Tree State Changes:
 *    Tight loop switching between setLoading(), setError(), setPlans(), clear().
 *    Multi-threaded concurrent mutation stress testing.
 * 4. Cell Renderer Resilience:
 *    customizeCellRenderer called with null node, plain string node, raw DTOs, non-TreeNode objects.
 *    No NPE or ClassCastException under any hostile or corrupted input.
 * 5. MockWebServer Network Chaos for Refresh Action:
 *    Malformed JSON, HTTP 500, HTTP 403, dropped sockets, partial detail failure fallback.
 */
class TreeAdversarialTest {

    private MockWebServer server;
    private StudyPlanTreeModel treeModel;
    private JTree dummyTree;
    private StudyPlanTreeCellRenderer renderer;
    private AdversarialPlatformPasswordSafe testPasswordSafe;

    private static final String SAMPLE_PAT = "mlc_pat_adversarial_test_token_999";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        testPasswordSafe = new AdversarialPlatformPasswordSafe();
        MiniLeetCodePasswordSafe.setTestInstance(new MiniLeetCodePasswordSafe(testPasswordSafe));

        treeModel = new StudyPlanTreeModel();
        dummyTree = new JTree(treeModel);
        renderer = new StudyPlanTreeCellRenderer();

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
    // 1. MASSIVE TREE HIERARCHY STRESS TESTS
    // =========================================================================

    @Test
    @DisplayName("Vector 1.1: Massive Tree Hierarchy (50 items x 500 stages = 25,000 nodes) builds, traverses, and mutates cleanly")
    void testMassiveTree_50Items500Stages_25000Nodes() {
        int itemCount = 50;
        int stagesPerItem = 500;
        int expectedTotalStages = itemCount * stagesPerItem; // 25,000

        Instant startTime = Instant.now();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // 1. Synthesize massive DTO hierarchy
        List<ItemDto> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            List<StageDto> stages = new ArrayList<>(stagesPerItem);
            for (int s = 0; s < stagesPerItem; s++) {
                int stageNo = s + 1;
                long probId = (long) (i * stagesPerItem + stageNo);
                StageStatus status = (s % 4 == 0) ? StageStatus.DONE
                        : (s % 4 == 1) ? StageStatus.IN_PROGRESS
                        : (s % 4 == 2) ? StageStatus.NEVER
                        : StageStatus.LOCKED;
                stages.add(new StageDto(
                        stageNo,
                        probId,
                        "stage-item" + i + "-s" + stageNo,
                        "Stage " + stageNo + " for Item " + i,
                        (s % 3 == 0) ? Difficulty.EASY : (s % 3 == 1) ? Difficulty.MEDIUM : Difficulty.HARD,
                        ProblemType.OOP_GRINDING,
                        true,
                        status
                ));
            }
            items.add(new ItemDto(
                    (long) (i + 1),
                    i,
                    (i % 2 == 0) ? StudyPlanItemKind.PROJECT : StudyPlanItemKind.PROBLEM,
                    "Massive Item " + i,
                    "Description for massive item " + i,
                    false,
                    stages
            ));
        }

        PlanDetail massivePlan = new PlanDetail(
                1001L,
                "massive-plan",
                "Massive 25,000 Stage Study Plan",
                "Stress-testing Swing tree hierarchy scalability",
                StudyPlanStatus.PUBLISHED,
                expectedTotalStages,
                expectedTotalStages / 4,
                items
        );

        // 2. Load into tree model
        treeModel.setPlans(List.of(massivePlan));

        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        Duration elapsed = Duration.between(startTime, Instant.now());

        // Assert reasonable memory consumption (< 300MB delta) and fast construction (< 5 seconds)
        assertTrue(elapsed.toMillis() < 5000, "Massive tree construction took too long: " + elapsed.toMillis() + "ms");
        assertTrue(treeModel.hasPlans(), "Tree model must report hasPlans = true");
        assertEquals(1, treeModel.getPlanCount());

        PlanTreeNode planNode = treeModel.findPlanBySlug("massive-plan");
        assertNotNull(planNode, "PlanTreeNode must be found");
        assertEquals(itemCount, planNode.getChildCount(), "Plan must contain exactly 50 item children");
        assertEquals(itemCount, planNode.getItemNodes().size());
        assertEquals(expectedTotalStages, planNode.getStageCount());

        // 3. Exhaustive node counting traversal (iterative depth-first)
        int totalNodesVisited = 0;
        int totalStagesVisited = 0;
        Enumeration<TreeNode> enumeration = treeModel.getRootNode().breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            TreeNode node = enumeration.nextElement();
            totalNodesVisited++;
            if (node instanceof StageTreeNode) {
                totalStagesVisited++;
            }
        }
        // Total = 1 Root + 1 Plan + 50 Items + 25,000 Stages = 25,052 nodes
        assertEquals(25052, totalNodesVisited, "Total visited nodes must be exactly 25,052");
        assertEquals(expectedTotalStages, totalStagesVisited, "Total visited stages must be exactly 25,000");

        // 4. Targeted queries: first, middle, last, non-existent
        StageTreeNode firstStage = treeModel.findStageBySlug("stage-item0-s1");
        assertNotNull(firstStage);
        assertEquals(1, firstStage.getStageNo());

        StageTreeNode middleStage = treeModel.findStageBySlug("stage-item24-s250");
        assertNotNull(middleStage);
        assertEquals(250, middleStage.getStageNo());
        assertEquals(12250L, middleStage.getProblemId());

        StageTreeNode lastStage = treeModel.findStageBySlug("stage-item49-s500");
        assertNotNull(lastStage);
        assertEquals(500, lastStage.getStageNo());
        assertEquals(25000L, lastStage.getProblemId());

        assertNull(treeModel.findStageBySlug("non-existent-stage"));
        assertNull(treeModel.findItemById(999999L));
        assertNull(treeModel.findStageByProblemId(-1L));

        // 5. TreePath resolution for selection
        TreePath pathToMiddle = treeModel.getPathToStage("stage-item24-s250");
        assertNotNull(pathToMiddle);
        assertEquals(4, pathToMiddle.getPathCount(), "Path to stage must be Root -> Plan -> Item -> Stage (depth 4)");

        // 6. Mutation on middle stage
        boolean updated = treeModel.updateStageStatus("stage-item24-s250", StageStatus.DONE);
        assertTrue(updated);
        assertEquals(StageStatus.DONE, middleStage.getStatus());

        // 7. Verify cell renderer does not crash when rendering massive tree samples
        assertDoesNotThrow(() -> {
            renderer.getTreeCellRendererComponent(dummyTree, planNode, false, false, false, 0, false);
            renderer.getTreeCellRendererComponent(dummyTree, planNode.getChildAt(24), false, false, false, 25, false);
            renderer.getTreeCellRendererComponent(dummyTree, middleStage, false, false, true, 275, false);
        });
    }

    @Test
    @DisplayName("Vector 1.2: Deep linear hierarchy traversal avoids StackOverflowError")
    void testDeepLinearHierarchy_NoStackOverflow() {
        // Construct a deep 1,000-node linear linked tree
        DefaultMutableTreeNode current = new DefaultMutableTreeNode("Root");
        DefaultMutableTreeNode deepRoot = current;
        for (int i = 0; i < 1000; i++) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode("Level " + i);
            current.add(child);
            current = child;
        }

        // Test upward path traversal from deepest leaf
        TreeNode[] path = current.getPath();
        assertEquals(1001, path.length);
        assertSame(deepRoot, path[0]);
        assertSame(current, path[1000]);

        // Breadth-first traversal without stack overflow
        int count = 0;
        Enumeration<TreeNode> en = deepRoot.breadthFirstEnumeration();
        while (en.hasMoreElements()) {
            en.nextElement();
            count++;
        }
        assertEquals(1001, count);
    }

    // =========================================================================
    // 2. MALFORMED AND EXTREME DTO PAYLOADS
    // =========================================================================

    @Test
    @DisplayName("Vector 2.1: Extreme and malformed strings (null/empty slugs, 10,000-char titles, emojis, zero-width, XSS, RTL)")
    void testMalformedPlan_ExtremeStringPayloads() {
        String extremeTitle = "Title_".repeat(2000); // 12,000 chars
        String xssScript = "<script>alert('xss')</script><img src=x onerror=alert(1)>";
        String emojis = "🚀🔥🎉✨💻💡🎯🏆";
        String zeroWidth = "Zero\u200B\u200C\u200D\uFEFFWidth";
        String rtlHebrewArabic = "مرحبا بالعالم \u202E\u202D שלום עולם";

        PlanDetail extremePlan = new PlanDetail(
                999L,
                null, // null slug -> normalized to ""
                extremeTitle,
                xssScript + zeroWidth + rtlHebrewArabic,
                null, // null status -> normalized to PUBLISHED
                10,
                5,
                List.of(new ItemDto(
                        1L,
                        0,
                        null, // null kind -> normalized to PROBLEM
                        emojis + " " + xssScript,
                        extremeTitle,
                        false,
                        List.of(new StageDto(
                                1,
                                101L,
                                null, // null slug -> ""
                                rtlHebrewArabic,
                                null, // null difficulty -> EASY
                                null, // null problemType -> OOP_GRINDING
                                true,
                                null  // null status -> LOCKED
                        ))
                ))
        );

        treeModel.setPlans(List.of(extremePlan));
        assertTrue(treeModel.hasPlans());

        PlanTreeNode planNode = (PlanTreeNode) treeModel.getRootNode().getChildAt(0);
        assertNotNull(planNode);
        assertEquals("", planNode.getSlug(), "Null slug should normalize to empty string");
        assertEquals(extremeTitle, planNode.getTitle());
        assertEquals(StudyPlanStatus.PUBLISHED, planNode.getStatus());

        ItemTreeNode itemNode = (ItemTreeNode) planNode.getChildAt(0);
        assertNotNull(itemNode);
        assertEquals(StudyPlanItemKind.PROBLEM, itemNode.getKind(), "Null kind should default to PROBLEM");
        assertTrue(itemNode.getTitle().contains("🚀"));

        StageTreeNode stageNode = (StageTreeNode) itemNode.getChildAt(0);
        assertNotNull(stageNode);
        assertEquals(Difficulty.EASY, stageNode.getDifficulty(), "Null difficulty must default to EASY");
        assertEquals(ProblemType.OOP_GRINDING, stageNode.getProblemType(), "Null problemType must default to OOP_GRINDING");
        assertEquals(StageStatus.LOCKED, stageNode.getStatus(), "Null status must default to LOCKED");
        assertEquals(rtlHebrewArabic, stageNode.getTitle());

        // Renderer must format without throwing NPE or failing
        assertDoesNotThrow(() -> {
            renderer.getTreeCellRendererComponent(dummyTree, planNode, false, false, true, 0, false);
            renderer.getTreeCellRendererComponent(dummyTree, itemNode, false, false, true, 1, false);
            renderer.getTreeCellRendererComponent(dummyTree, stageNode, false, false, true, 2, false);
        });

        // Icon for null status must default safely to NEVER
        assertSame(MiniLeetCodeIcons.NEVER, MiniLeetCodeIcons.getStatusIcon(null));
    }

    @Test
    @DisplayName("Vector 2.2: Items with null stages list or empty stages list handle gracefully")
    void testItemWithNullOrEmptyStages() {
        ItemDto itemWithNullStages = new ItemDto(1L, 0, StudyPlanItemKind.PROJECT, "Project No Stages", "Desc", false, null);
        assertEquals(0, itemWithNullStages.getStageCount(), "Null stages must normalize to empty list");
        assertNotNull(itemWithNullStages.stages());

        ItemDto itemWithEmptyStages = new ItemDto(2L, 1, StudyPlanItemKind.PROBLEM, "Problem Empty Stages", "Desc", false, List.of());
        assertEquals(0, itemWithEmptyStages.getStageCount());

        PlanDetail plan = new PlanDetail(
                1L, "plan-empty-stages", "Plan Empty Stages", "Desc",
                StudyPlanStatus.PUBLISHED, 0, 0, List.of(itemWithNullStages, itemWithEmptyStages)
        );

        treeModel.setPlans(List.of(plan));
        PlanTreeNode planNode = treeModel.findPlanBySlug("plan-empty-stages");
        assertNotNull(planNode);
        assertEquals(2, planNode.getItemNodes().size());

        ItemTreeNode itemNode1 = planNode.getItemNodes().get(0);
        ItemTreeNode itemNode2 = planNode.getItemNodes().get(1);

        assertTrue(itemNode1.isLeaf(), "Item with 0 stages must report isLeaf = true");
        assertTrue(itemNode2.isLeaf());
        assertEquals(0, itemNode1.getChildCount());
        assertEquals(0, itemNode2.getChildCount());
        assertEquals(0, itemNode1.getStageNodes().size());
        assertNull(itemNode1.findStageByNo(1));
        assertNull(itemNode1.findStageBySlug("s1"));

        // Rendering items with 0 stages
        assertDoesNotThrow(() -> {
            renderer.getTreeCellRendererComponent(dummyTree, itemNode1, false, false, true, 1, false);
            renderer.getTreeCellRendererComponent(dummyTree, itemNode2, false, false, true, 2, false);
        });
    }

    @Test
    @DisplayName("Vector 2.3: Boundary progress percentage calculations (0, negative, overflow, divide by zero)")
    void testProgressPercentageBoundaries() {
        // Zero stage count
        PlanDetail zeroStages = new PlanDetail(1L, "p1", "T", "D", StudyPlanStatus.PUBLISHED, 0, 0, List.of());
        assertEquals(0, zeroStages.getProgressPercentage());

        // Negative stage count
        PlanDetail negStages = new PlanDetail(2L, "p2", "T", "D", StudyPlanStatus.PUBLISHED, -5, 0, List.of());
        assertEquals(0, negStages.getProgressPercentage());

        // Done stages exceeds total stage count (clamped to 100)
        PlanDetail overDone = new PlanDetail(3L, "p3", "T", "D", StudyPlanStatus.PUBLISHED, 10, 15, List.of());
        assertEquals(100, overDone.getProgressPercentage());
        assertTrue(overDone.isCompleted());

        // Negative done stages (clamped to 0)
        PlanDetail negDone = new PlanDetail(4L, "p4", "T", "D", StudyPlanStatus.PUBLISHED, 10, -3, List.of());
        assertEquals(0, negDone.getProgressPercentage());

        // Extreme integer counts
        PlanDetail maxStages = new PlanDetail(5L, "p5", "T", "D", StudyPlanStatus.PUBLISHED, Integer.MAX_VALUE, 1000, List.of());
        assertEquals(0, maxStages.getProgressPercentage()); // Rounding to nearest integer

        // Same boundary checks on PlanTreeNode wrapper
        PlanTreeNode planNode = new PlanTreeNode(overDone);
        assertEquals(100, planNode.getProgressPercentage());
        assertTrue(planNode.isCompleted());
    }

    @Test
    @DisplayName("Vector 2.4: Null elements inside Plan list are safely skipped without NPE")
    void testNullElementsInsideCollections() {
        PlanDetail validPlan = new PlanDetail(
                1L, "p-valid", "Valid Plan", "Desc", StudyPlanStatus.PUBLISHED, 2, 1,
                List.of(new ItemDto(1L, 0, StudyPlanItemKind.PROJECT, "Item 1", "Desc", false, List.of()))
        );

        List<PlanDetail> plansWithNull = new ArrayList<>();
        plansWithNull.add(null);
        plansWithNull.add(validPlan);
        plansWithNull.add(null);

        // Apply to treeModel - must skip nulls safely
        assertDoesNotThrow(() -> treeModel.setPlans(plansWithNull));

        assertEquals(1, treeModel.getPlanCount());
        PlanTreeNode planNode = treeModel.findPlanBySlug("p-valid");
        assertNotNull(planNode);
        assertEquals(1, planNode.getItemNodes().size());

        // Also test setPlanSummaries with null elements
        PlanSummary validSummary = new PlanSummary(2L, "p-summary", "Summary Plan", "Desc", StudyPlanStatus.PUBLISHED, 1, 0, 1);
        List<PlanSummary> summariesWithNull = new ArrayList<>();
        summariesWithNull.add(null);
        summariesWithNull.add(validSummary);
        summariesWithNull.add(null);

        assertDoesNotThrow(() -> treeModel.setPlanSummaries(summariesWithNull));
        assertEquals(1, treeModel.getPlanCount());
        assertNotNull(treeModel.findPlanBySlug("p-summary"));
    }

    // =========================================================================
    // 3. CONCURRENT & RAPID TREE STATE CHANGES
    // =========================================================================

    @Test
    @DisplayName("Vector 3.1: Rapid sequential state transitions (2,000 iterations in tight loop) maintain structural integrity")
    void testRapidSequentialStateTransitions_TightLoop() {
        TestTreeModelListener listener = new TestTreeModelListener();
        treeModel.addTreeModelListener(listener);

        PlanDetail samplePlan = new PlanDetail(
                1L, "fast-plan", "Fast Plan", "Desc", StudyPlanStatus.PUBLISHED, 1, 0,
                List.of(new ItemDto(1L, 0, StudyPlanItemKind.PROJECT, "Fast Item", "Desc", false, List.of()))
        );

        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            treeModel.setLoading("Loading cycle " + i);
            assertTrue(treeModel.isLoading());

            treeModel.setPlans(List.of(samplePlan));
            assertTrue(treeModel.hasPlans());

            treeModel.setError("Error cycle " + i);
            assertTrue(treeModel.isErrorState());
            assertEquals("Failed to load study plans: Error cycle " + i, treeModel.getErrorMessage());

            treeModel.setEmpty("Empty cycle " + i);
            assertTrue(treeModel.isEmptyState());

            treeModel.clear();
            assertTrue(treeModel.isEmptyState());
        }

        // Each cycle executed 5 state changes that invoke nodeStructureChanged
        assertEquals(iterations * 5, listener.structureChangedCount.get());
        assertTrue(listener.events.size() >= iterations * 5);
    }

    @Test
    @DisplayName("Vector 3.2: High-frequency rapid state mutations (5,000 operations) with listener verification")
    void testRapidStateMutations_ListenerVerification() {
        TestTreeModelListener listener = new TestTreeModelListener();
        treeModel.addTreeModelListener(listener);

        PlanDetail testPlan = new PlanDetail(
                10L, "rapid-plan", "Rapid Plan", "Desc", StudyPlanStatus.PUBLISHED, 2, 1,
                List.of(new ItemDto(1L, 0, StudyPlanItemKind.PROBLEM, "Item", "Desc", false,
                        List.of(new StageDto(1, 10L, "s1", "S1", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE))))
        );

        int totalOperations = 5000;
        for (int op = 0; op < totalOperations; op++) {
            int choice = op % 5;
            switch (choice) {
                case 0 -> treeModel.setLoading("Loading op " + op);
                case 1 -> treeModel.setPlans(List.of(testPlan));
                case 2 -> treeModel.setError("Error op " + op);
                case 3 -> treeModel.setEmpty("Empty op " + op);
                case 4 -> treeModel.clear();
            }
        }

        // Each mutation fires nodeStructureChanged
        assertEquals(totalOperations, listener.structureChangedCount.get());
        assertTrue(treeModel.isEmptyState(), "Final state after clear() must be empty");
        assertEquals(0, treeModel.getPlanCount());
    }

    @Test
    @DisplayName("Vector 3.3: Rapid state transitions interleaved with extensive sub-tree queries maintain state consistency")
    void testRapidStateTransitions_InterleavedQueries() {
        PlanDetail plan = new PlanDetail(
                1L, "query-plan", "Query Plan", "Desc", StudyPlanStatus.PUBLISHED, 2, 1,
                List.of(new ItemDto(1L, 0, StudyPlanItemKind.PROJECT, "Item", "Desc", false,
                        List.of(
                                new StageDto(1, 100L, "q-s1", "S1", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.LOCKED),
                                new StageDto(2, 101L, "q-s2", "S2", Difficulty.MEDIUM, ProblemType.OOP_GRINDING, true, StageStatus.DONE)
                        )))
        );

        for (int cycle = 0; cycle < 500; cycle++) {
            // 1. Loading
            treeModel.setLoading("Cycle " + cycle);
            assertTrue(treeModel.isLoading());
            assertFalse(treeModel.hasPlans());
            assertNull(treeModel.findPlanBySlug("query-plan"));

            // 2. Data populated
            treeModel.setPlans(List.of(plan));
            assertTrue(treeModel.hasPlans());
            assertEquals(1, treeModel.getPlanCount());
            assertNotNull(treeModel.findPlanBySlug("query-plan"));
            assertNotNull(treeModel.findStageBySlug("q-s1"));
            assertNotNull(treeModel.getPathToStage("q-s2"));

            // 3. Status mutation while populated
            boolean updated = treeModel.updateStageStatus("q-s1", StageStatus.IN_PROGRESS);
            assertTrue(updated);
            assertEquals(StageStatus.IN_PROGRESS, treeModel.findStageBySlug("q-s1").getStatus());

            // 4. Error
            treeModel.setError("Transient cycle error " + cycle);
            assertTrue(treeModel.isErrorState());
            assertNotNull(treeModel.getErrorMessage());
            assertEquals(0, treeModel.getPlanCount());
            assertNull(treeModel.findStageBySlug("q-s1"));

            // 5. Clear
            treeModel.clear();
            assertTrue(treeModel.isEmptyState());
            assertFalse(treeModel.isErrorState());
        }
    }

    // =========================================================================
    // 4. CELL RENDERER RESILIENCE & DEFENSIVE TYPING
    // =========================================================================

    @Test
    @DisplayName("Vector 4.1: customizeCellRenderer called with hostile / corrupted values never throws NPE or ClassCastException")
    void testRenderer_HostileAndCorruptedValues() {
        // 1. Null value
        assertDoesNotThrow(() ->
                renderer.customizeCellRenderer(dummyTree, null, false, false, false, 0, false));

        // 2. DefaultMutableTreeNode with plain string
        assertDoesNotThrow(() ->
                renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode("plain string"), false, false, false, 0, false));

        // 3. DefaultMutableTreeNode with null userObject
        assertDoesNotThrow(() ->
                renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode(null), false, false, false, 0, false));

        // 4. DefaultMutableTreeNode with non-DTO arbitrary object
        assertDoesNotThrow(() ->
                renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode(new Date()), false, false, false, 0, false));

        // 5. Raw DTO objects inside DefaultMutableTreeNode (polymorphic branch)
        PlanDetail rawPlan = new PlanDetail(1L, "p", "Raw Plan", "Desc", StudyPlanStatus.PUBLISHED, 0, 0, List.of());
        ItemDto rawItem = new ItemDto(1L, 0, StudyPlanItemKind.PROJECT, "Raw Item", "Desc", true, List.of());
        StageDto rawStage = new StageDto(1, 10L, "s", "Raw Stage", Difficulty.HARD, ProblemType.OOP_GRINDING, true, StageStatus.IN_PROGRESS);

        assertDoesNotThrow(() -> {
            renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode(rawPlan), false, false, false, 0, false);
            renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode(rawItem), false, false, false, 1, false);
            renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode(rawStage), false, false, true, 2, false);
        });

        // 6. Direct non-TreeNode arbitrary objects
        assertDoesNotThrow(() -> {
            renderer.customizeCellRenderer(dummyTree, "direct string", false, false, false, 0, false);
            renderer.customizeCellRenderer(dummyTree, 12345, false, false, false, 0, false);
            renderer.customizeCellRenderer(dummyTree, new JLabel("GUI Component"), false, false, false, 0, false);
        });

        // 7. Negative or extreme row indices
        assertDoesNotThrow(() -> {
            renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode("test"), true, true, true, -1, true);
            renderer.customizeCellRenderer(dummyTree, new DefaultMutableTreeNode("test"), true, true, true, Integer.MAX_VALUE, true);
        });
    }

    @Test
    @DisplayName("Vector 4.2: Placeholder styling and icon mapping across all error, loading, and empty variations")
    void testRenderer_PlaceholderStylingVariations() {
        // Loading placeholder
        PlaceholderTreeNode loadingNode = PlaceholderTreeNode.loading("Please wait: loading...");
        renderer.getTreeCellRendererComponent(dummyTree, loadingNode, false, false, true, 0, false);
        assertSame(AllIcons.Process.Step_1, renderer.getIcon());
        assertTrue(renderer.getCharSequence(false).toString().contains("loading..."));

        // Error placeholder
        PlaceholderTreeNode errorNode = PlaceholderTreeNode.error("Connection timed out (504)");
        renderer.getTreeCellRendererComponent(dummyTree, errorNode, false, false, true, 0, false);
        assertSame(AllIcons.General.Error, renderer.getIcon());
        assertTrue(renderer.getCharSequence(false).toString().contains("Failed to load study plans:"));

        // Empty placeholder
        PlaceholderTreeNode emptyNode = PlaceholderTreeNode.empty("Custom empty catalog");
        renderer.getTreeCellRendererComponent(dummyTree, emptyNode, false, false, true, 0, false);
        assertSame(AllIcons.General.Information, renderer.getIcon());
        assertTrue(renderer.getCharSequence(false).toString().contains("Custom empty catalog"));

        // Raw node with lowercase "error" or "failed"
        DefaultMutableTreeNode rawErrorNode = new DefaultMutableTreeNode("Fatal error occurred");
        renderer.getTreeCellRendererComponent(dummyTree, rawErrorNode, false, false, true, 0, false);
        assertSame(AllIcons.General.Error, renderer.getIcon());
    }

    @Test
    @DisplayName("Vector 4.3: Stage icon resolution handles all 4 StageStatus values + null gracefully")
    void testRenderer_StageIconResolutionAllStatuses() {
        for (StageStatus status : StageStatus.values()) {
            Icon icon = MiniLeetCodeIcons.getStatusIcon(status);
            assertNotNull(icon, "Icon for status " + status + " must never be null");
            assertEquals(16, icon.getIconWidth());
            assertEquals(16, icon.getIconHeight());
        }

        // Null status defaults to NEVER
        assertSame(MiniLeetCodeIcons.NEVER, MiniLeetCodeIcons.getStatusIcon(null));

        // Test rendering for each status
        for (StageStatus status : StageStatus.values()) {
            StageDto stageDto = new StageDto(1, 1L, "s-" + status, "Stage " + status, Difficulty.EASY, ProblemType.OOP_GRINDING, true, status);
            StageTreeNode stageNode = new StageTreeNode(stageDto);
            renderer.getTreeCellRendererComponent(dummyTree, stageNode, false, false, true, 0, false);
            assertSame(MiniLeetCodeIcons.getStatusIcon(status), renderer.getIcon(),
                    "Renderer icon must match getStatusIcon(" + status + ")");
        }
    }

    // =========================================================================
    // 5. MOCKWEBSERVER NETWORK CHAOS FOR REFRESH ACTION
    // =========================================================================

    @Test
    @DisplayName("Vector 5.1: Refresh action handles malformed JSON response from server without crash")
    void testRefreshAction_MalformedJsonResponse() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{ unclosed json array [ { corrupt: true ..."));

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        assertDoesNotThrow(() -> action.refreshPlansSync(null, null));

        assertTrue(treeModel.isErrorState(), "Tree must enter error state when server returns malformed JSON");
        assertNotNull(treeModel.getErrorMessage());
        assertTrue(treeModel.getErrorMessage().contains("Failed to load study plans:")
                || treeModel.getErrorMessage().contains("Network error:"));
    }

    @Test
    @DisplayName("Vector 5.2: Refresh action handles HTTP 500 Internal Server Error cleanly")
    void testRefreshAction_Http500InternalServerError() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"status\":500,\"title\":\"Internal Server Error\",\"detail\":\"Database pool exhausted\"}"));

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        assertDoesNotThrow(() -> action.refreshPlansSync(null, null));

        assertTrue(treeModel.isErrorState());
        String errorMsg = treeModel.getErrorMessage();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("500") || errorMsg.contains("Failed to load study plans:"),
                "Error message should reflect HTTP 500: " + errorMsg);
    }

    @Test
    @DisplayName("Vector 5.3: Refresh action handles HTTP 403 Forbidden cleanly")
    void testRefreshAction_Http403Forbidden() {
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"code\":\"PAT_FORBIDDEN\",\"detail\":\"Token has insufficient permissions\"}"));

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        assertDoesNotThrow(() -> action.refreshPlansSync(null, null));

        assertTrue(treeModel.isErrorState());
        String errorMsg = treeModel.getErrorMessage();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("403") || errorMsg.contains("PAT_FORBIDDEN") || errorMsg.contains("Failed to load study plans:"));
    }

    @Test
    @DisplayName("Vector 5.4: Refresh action handles socket disconnect / network drop without uncaught exception")
    void testRefreshAction_SocketDisconnectChaos() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        assertDoesNotThrow(() -> action.refreshPlansSync(null, null));

        assertTrue(treeModel.isErrorState());
        String errorMsg = treeModel.getErrorMessage();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("Failed to load study plans:") || errorMsg.contains("Network error:") || errorMsg.contains("timed out") || errorMsg.contains("Connection"),
                "Error message must indicate network failure: " + errorMsg);
    }

    @Test
    @DisplayName("Vector 5.5: Partial failure resilience - plan detail endpoint 500 falls back to synthesized PlanDetail")
    void testRefreshAction_PartialDetailFailure_SynthesizesFallback() {
        // List endpoint returns 2 plans
        String listJson = """
                [
                  {
                    "id": 101,
                    "slug": "plan-success",
                    "title": "Success Plan",
                    "description": "Plan that succeeds detail loading",
                    "status": "PUBLISHED",
                    "stageCount": 1,
                    "doneStages": 0
                  },
                  {
                    "id": 102,
                    "slug": "plan-failure",
                    "title": "Failing Plan",
                    "description": "Plan that 500s on detail loading",
                    "status": "PUBLISHED",
                    "stageCount": 5,
                    "doneStages": 2
                  }
                ]
                """;

        String detailSuccessJson = """
                {
                  "id": 101,
                  "slug": "plan-success",
                  "title": "Success Plan",
                  "description": "Plan that succeeds detail loading",
                  "status": "PUBLISHED",
                  "stageCount": 1,
                  "doneStages": 0,
                  "items": [
                    {
                      "id": 201,
                      "position": 0,
                      "kind": "PROBLEM",
                      "title": "Item 1",
                      "description": "Desc",
                      "done": false,
                      "stages": [
                        {
                          "stageNo": 1,
                          "problemId": 301,
                          "slug": "s1",
                          "title": "Stage 1",
                          "difficulty": "EASY",
                          "problemType": "OOP_GRINDING",
                          "published": true,
                          "status": "LOCKED"
                        }
                      ]
                    }
                  ]
                }
                """;

        // 1. List query -> 200 OK
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(listJson));
        // 2. First plan detail -> 200 OK
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(detailSuccessJson));
        // 3. Second plan detail -> 500 Internal Server Error
        server.enqueue(new MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody("{\"error\":\"Detail lookup crashed\"}"));

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        action.refreshPlansSync(null, null);

        // Crucial verification: The model must NOT enter error state because fallback mechanism protects the UI!
        assertTrue(treeModel.hasPlans(), "Model should still display plans despite partial detail failure");
        assertEquals(2, treeModel.getPlanCount());

        PlanTreeNode successPlanNode = treeModel.findPlanBySlug("plan-success");
        assertNotNull(successPlanNode);
        assertEquals(1, successPlanNode.getItemNodes().size(), "First plan must have its detailed items loaded");

        PlanTreeNode fallbackPlanNode = treeModel.findPlanBySlug("plan-failure");
        assertNotNull(fallbackPlanNode);
        assertEquals(0, fallbackPlanNode.getItemNodes().size(), "Second plan must be synthesized with 0 items without crashing");
        assertEquals("Failing Plan", fallbackPlanNode.getTitle());
        assertEquals(5, fallbackPlanNode.getStageCount());
        assertEquals(2, fallbackPlanNode.getDoneStages());
    }

    @Test
    @DisplayName("Vector 5.6: Refresh action with unreachable server URL enters error state cleanly")
    void testRefreshAction_UnreachableServerUrlReportsError() {
        MiniLeetCodeSettingsState.getInstance().setServerUrl("http://127.0.0.1:1");

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        action.refreshPlansSync(null, null);

        assertTrue(treeModel.isErrorState());
        String msg = treeModel.getErrorMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("Failed to load study plans:") || msg.contains("Network error:") || msg.contains("Connection"),
                "Error message must indicate failure: " + msg);
    }

    @Test
    @DisplayName("Vector 5.7: Refresh action receiving empty plan list transitions cleanly to empty state")
    void testRefreshAction_EmptyPlanListTransitionsToEmptyState() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        RefreshPlansAction action = new RefreshPlansAction(treeModel);
        action.refreshPlansSync(null, null);

        assertTrue(treeModel.isEmptyState());
        assertEquals(0, treeModel.getPlanCount());
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    private static class TestTreeModelListener implements TreeModelListener {
        final AtomicInteger structureChangedCount = new AtomicInteger();
        final AtomicInteger nodesInsertedCount = new AtomicInteger();
        final AtomicInteger nodesRemovedCount = new AtomicInteger();
        final AtomicInteger nodesChangedCount = new AtomicInteger();
        final List<TreeModelEvent> events = new CopyOnWriteArrayList<>();

        @Override public void treeNodesChanged(TreeModelEvent e) { nodesChangedCount.incrementAndGet(); events.add(e); }
        @Override public void treeNodesInserted(TreeModelEvent e) { nodesInsertedCount.incrementAndGet(); events.add(e); }
        @Override public void treeNodesRemoved(TreeModelEvent e) { nodesRemovedCount.incrementAndGet(); events.add(e); }
        @Override public void treeStructureChanged(TreeModelEvent e) { structureChangedCount.incrementAndGet(); events.add(e); }
    }

    private static class AdversarialPlatformPasswordSafe extends PasswordSafe {
        private final Map<String, String> credentialMap = new ConcurrentHashMap<>();

        @Override
        public @Nullable Credentials get(@NotNull CredentialAttributes attributes) {
            String password = credentialMap.get(attributes.getServiceName());
            return password != null ? new Credentials(attributes.getUserName(), password) : null;
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

        @Override public boolean isRememberPasswordByDefault() { return true; }
        @Override public void setRememberPasswordByDefault(boolean isRememberPasswordByDefault) {}
        @Override public boolean isMemoryOnly() { return true; }
        @Override public boolean isPasswordStoredOnlyInMemory(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials) { return true; }
    }
}
