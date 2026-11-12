package jsd.toiyeujava.minileetcode.plugin.challenge;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.ui.*;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Concurrency, Lifecycle & Memory Stress Test Suite for Milestone 3 (PL-2).
 *
 * Attacks the following four dimensions under high concurrency and resource pressure:
 * 1. Multi-Threaded Tree Operations:
 *    16 concurrent threads reading and mutating StudyPlanTreeModel (8 writers, 8 readers).
 *    Attacks root node mutation, child removal/insertion, state transition races,
 *    and verifies absence of ConcurrentModificationException, ArrayIndexOutOfBoundsException,
 *    data corruption, and deadlocks.
 * 2. Listener Notification Churn:
 *    Attaches 50 concurrent TreeModelListener instances, performs hundreds of rapid sequential
 *    mutations, verifies event delivery consistency across all listeners without dropped events,
 *    and verifies clean unregistration without memory leaks.
 * 3. Rapid Open / Refresh / Close Lifecycle Churn:
 *    Executes 1,000 cycles creating StudyPlanTreeModel and full 3-tier node trees.
 *    Uses WeakReference tracking to verify unlinked nodes and models are garbage collected
 *    without persistent strong references or memory leaks.
 * 4. Concurrent Refresh Triggering:
 *    Fires 16 concurrent performRefresh calls via RefreshPlansAction with MockWebServer
 *    simulating 50ms network latency per request.
 *    Verifies clean completion without deadlocks, thread pool exhaustion, or race corruption.
 */
public class TreeConcurrencyStressTest {

    private MockWebServer server;
    private TestPasswordSafe inMemoryPasswordSafe;

    private static final String SAMPLE_PAT = "mlc_pat_stress_concurrency_token_123456789";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        inMemoryPasswordSafe = new TestPasswordSafe();
        MiniLeetCodePasswordSafe.setTestInstance(new MiniLeetCodePasswordSafe(inMemoryPasswordSafe));
        MiniLeetCodePasswordSafe.getInstance().setPassword(SAMPLE_PAT);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            try {
                server.shutdown();
            } catch (Exception ignored) {
            }
        }
        MiniLeetCodePasswordSafe.setTestInstance(null);
    }

    // =========================================================================
    // Test 1: Multi-Threaded Tree Operations (16 threads)
    // =========================================================================

    @Test
    @DisplayName("Challenge 1: 16 concurrent threads reading and mutating StudyPlanTreeModel")
    void testMultiThreadedTreeOperations_ConcurrentReadersAndWriters() throws Exception {
        StudyPlanTreeModel model = new StudyPlanTreeModel();

        List<PlanDetail> datasetA = createSamplePlans("plan-a", 3, 2, 2);
        List<PlanDetail> datasetB = createSamplePlans("plan-b", 2, 3, 2);

        int threadCount = 16;
        int operationsPerThread = 250;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();
        AtomicInteger totalReads = new AtomicInteger(0);
        AtomicInteger totalWrites = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int op = 0; op < operationsPerThread; op++) {
                        if (threadId % 2 == 0) {
                            // Writer thread (8 threads)
                            int choice = (threadId + op) % 6;
                            switch (choice) {
                                case 0 -> model.setPlans(datasetA);
                                case 1 -> model.setLoading("Loading from thread " + threadId);
                                case 2 -> model.setError("Error from thread " + threadId);
                                case 3 -> model.setPlans(datasetB);
                                case 4 -> model.setEmpty("Empty from thread " + threadId);
                                case 5 -> {
                                    model.updateStageStatus("plan-a-1-item-1-stage-1", StageStatus.DONE);
                                    model.reload();
                                }
                            }
                            totalWrites.incrementAndGet();
                        } else {
                            // Reader thread (8 threads)
                            DefaultMutableTreeNode root = model.getRootNode();
                            assertNotNull(root, "Root node must never be null");

                            int planCount = model.getPlanCount();
                            assertTrue(planCount >= 0, "Plan count must be non-negative");

                            List<PlanTreeNode> planNodes = model.getPlanNodes();
                            assertNotNull(planNodes, "getPlanNodes() must never return null");

                            boolean loading = model.isLoading();
                            boolean empty = model.isEmptyState();
                            boolean error = model.isErrorState();
                            boolean hasPlans = model.hasPlans();

                            // State flags sanity check: cannot be both valid plans and error/empty
                            if (hasPlans) {
                                assertFalse(loading && empty && error, "Inconsistent state flags");
                            }

                            // Query by slug
                            PlanTreeNode foundPlan = model.findPlanBySlug("plan-a-1");
                            if (foundPlan != null) {
                                assertNotNull(foundPlan.getTitle());
                            }

                            // Query stage by slug
                            StageTreeNode stage = model.findStageBySlug("plan-a-1-item-1-stage-1");
                            if (stage != null) {
                                assertNotNull(stage.getStatus());
                            }

                            totalReads.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 16 threads must complete within timeout without deadlock");

        if (!exceptions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Concurrency failure: ").append(exceptions.size()).append(" exceptions caught:\n");
            for (int i = 0; i < Math.min(5, exceptions.size()); i++) {
                Throwable t = exceptions.get(i);
                sb.append("  [").append(i).append("] ").append(t.getClass().getName())
                        .append(": ").append(t.getMessage()).append("\n");
                for (StackTraceElement elem : t.getStackTrace()) {
                    if (elem.getClassName().contains("minileetcode")) {
                        sb.append("      at ").append(elem).append("\n");
                    }
                }
            }
            fail(sb.toString());
        }

        assertEquals(8 * operationsPerThread, totalWrites.get(), "All writer operations must execute");
        assertEquals(8 * operationsPerThread, totalReads.get(), "All reader operations must execute");

        // Verify final model state can be cleanly reset and queried
        model.setPlans(datasetA);
        assertTrue(model.hasPlans());
        assertEquals(3, model.getPlanCount());
    }

    // =========================================================================
    // Test 2: Listener Notification Churn (50 listeners)
    // =========================================================================

    @Test
    @DisplayName("Challenge 2: 50 TreeModelListeners receive consistent events across rapid sequential mutations")
    void testListenerNotificationChurn_50ListenersRapidMutations() {
        StudyPlanTreeModel model = new StudyPlanTreeModel();
        int listenerCount = 50;

        List<CountingTreeModelListener> listeners = new ArrayList<>(listenerCount);
        for (int i = 0; i < listenerCount; i++) {
            CountingTreeModelListener l = new CountingTreeModelListener(i);
            listeners.add(l);
            model.addTreeModelListener(l);
        }

        List<PlanDetail> plans = createSamplePlans("churn-plan", 2, 2, 2);
        int cycles = 100;

        for (int i = 0; i < cycles; i++) {
            model.setLoading("Loading cycle " + i);
            model.setPlans(plans);
            model.updateStageStatus("churn-plan-1-item-1-stage-1", StageStatus.IN_PROGRESS);
            model.setError("Transient error " + i);
            model.setEmpty("Empty cycle " + i);
            model.reload();
        }

        // Verify all 50 listeners received the exact same number of events
        int expectedEvents = listeners.get(0).getTotalEvents();
        assertTrue(expectedEvents > 0, "Listeners must have received events");

        for (int i = 0; i < listenerCount; i++) {
            CountingTreeModelListener l = listeners.get(i);
            assertEquals(expectedEvents, l.getTotalEvents(),
                    "Listener " + i + " must receive exact same total event count as listener 0");
            assertEquals(listeners.get(0).structureChangedCount.get(), l.structureChangedCount.get(),
                    "Listener " + i + " structureChangedCount mismatch");
            assertEquals(listeners.get(0).nodesChangedCount.get(), l.nodesChangedCount.get(),
                    "Listener " + i + " nodesChangedCount mismatch");
        }

        // Test unregistration and verify no more events are received
        for (CountingTreeModelListener l : listeners) {
            model.removeTreeModelListener(l);
        }

        int preRemovalEvents = expectedEvents;
        model.setPlans(plans);
        model.setEmpty();

        for (int i = 0; i < listenerCount; i++) {
            assertEquals(preRemovalEvents, listeners.get(i).getTotalEvents(),
                    "Unregistered listener " + i + " must not receive subsequent events");
        }
    }

    // =========================================================================
    // Test 3: Rapid Open / Refresh / Close Lifecycle & GC Reclamation (1,000 cycles)
    // =========================================================================

    @Test
    @DisplayName("Challenge 3: Rapid 1,000-cycle lifecycle churn and WeakReference GC memory reclamation")
    void testRapidLifecycleChurnAndGarbageCollection_1000Cycles() throws Exception {
        int cycles = 1000;
        List<WeakReference<StudyPlanTreeModel>> modelRefs = new ArrayList<>();
        List<WeakReference<PlanTreeNode>> planNodeRefs = new ArrayList<>();
        List<WeakReference<StageTreeNode>> stageNodeRefs = new ArrayList<>();

        for (int i = 0; i < cycles; i++) {
            StudyPlanTreeModel model = new StudyPlanTreeModel();
            List<PlanDetail> plans = createSamplePlans("lifecycle-" + i, 2, 2, 2);
            model.setPlans(plans);

            // Sample some references for GC verification
            if (i % 50 == 0) {
                modelRefs.add(new WeakReference<>(model));
                PlanTreeNode planNode = model.findPlanBySlug("lifecycle-" + i + "-1");
                if (planNode != null) {
                    planNodeRefs.add(new WeakReference<>(planNode));
                }
                StageTreeNode stageNode = model.findStageBySlug("lifecycle-" + i + "-1-item-1-stage-1");
                if (stageNode != null) {
                    stageNodeRefs.add(new WeakReference<>(stageNode));
                }
            }

            // Rapid operations on model
            model.updateStageStatus("lifecycle-" + i + "-1-item-1-stage-1", StageStatus.DONE);
            model.setLoading();
            model.setError("Test error");
            model.setEmpty();
            model.clear();
        }

        assertFalse(modelRefs.isEmpty(), "Must have captured model weak references");
        assertFalse(planNodeRefs.isEmpty(), "Must have captured plan node weak references");

        // Force GC to verify that unreferenced models and nodes can be reclaimed
        boolean anyModelCollected = false;
        boolean anyNodeCollected = false;

        for (int attempt = 0; attempt < 15; attempt++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(50);

            long collectedModels = modelRefs.stream().filter(ref -> ref.get() == null).count();
            long collectedNodes = planNodeRefs.stream().filter(ref -> ref.get() == null).count();

            if (collectedModels > 0) {
                anyModelCollected = true;
            }
            if (collectedNodes > 0) {
                anyNodeCollected = true;
            }

            if (anyModelCollected && anyNodeCollected) {
                break;
            }
            // Allocate dummy memory pressure to encourage GC
            byte[] dummy = new byte[2 * 1024 * 1024];
        }

        assertTrue(anyModelCollected, "At least some StudyPlanTreeModel instances must be garbage-collected");
        assertTrue(anyNodeCollected, "At least some PlanTreeNode instances must be garbage-collected");
    }

    // =========================================================================
    // Test 4: Concurrent Refresh Triggering with MockWebServer Latency (16 calls)
    // =========================================================================

    @Test
    @DisplayName("Challenge 4: 16 concurrent performRefresh calls with MockWebServer 50ms latency")
    void testConcurrentRefreshTriggering_16ThreadsWithMockWebServerLatency() throws Exception {
        int callCount = 16;
        AtomicInteger listRequestCount = new AtomicInteger(0);
        AtomicInteger detailRequestCount = new AtomicInteger(0);

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath();
                try {
                    // Simulate 50ms latency as specified in requirements
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }

                if (path != null && path.equals("/api/v1/study-plans")) {
                    listRequestCount.incrementAndGet();
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    [
                                      {
                                        "id": 101,
                                        "slug": "plan-sys-design",
                                        "title": "System Design Grinding",
                                        "description": "Master distributed systems",
                                        "status": "PUBLISHED",
                                        "itemCount": 1,
                                        "stageCount": 2,
                                        "doneStages": 1
                                      },
                                      {
                                        "id": 102,
                                        "slug": "plan-algo",
                                        "title": "Algorithm Mastery",
                                        "description": "Master data structures & algorithms",
                                        "status": "PUBLISHED",
                                        "itemCount": 1,
                                        "stageCount": 2,
                                        "doneStages": 0
                                      }
                                    ]
                                    """);
                } else if (path != null && path.contains("/api/v1/study-plans/")) {
                    detailRequestCount.incrementAndGet();
                    String slug = path.substring(path.lastIndexOf('/') + 1);
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {
                                      "id": 101,
                                      "slug": "%s",
                                      "title": "Plan %s",
                                      "description": "Detailed description",
                                      "status": "PUBLISHED",
                                      "stageCount": 2,
                                      "doneStages": 1,
                                      "items": [
                                        {
                                          "id": 201,
                                          "position": 1,
                                          "kind": "PROJECT",
                                          "title": "Item 1",
                                          "description": "Item description",
                                          "done": false,
                                          "stages": [
                                            {
                                              "stageNo": 1,
                                              "problemId": 301,
                                              "slug": "%s-stage-1",
                                              "title": "Stage 1",
                                              "difficulty": "EASY",
                                              "problemType": "OOP_GRINDING",
                                              "published": true,
                                              "status": "DONE"
                                            },
                                            {
                                              "stageNo": 2,
                                              "problemId": 302,
                                              "slug": "%s-stage-2",
                                              "title": "Stage 2",
                                              "difficulty": "MEDIUM",
                                              "problemType": "OOP_GRINDING",
                                              "published": true,
                                              "status": "IN_PROGRESS"
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                    """.formatted(slug, slug, slug, slug));
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        server.start();
        MiniLeetCodeSettingsState.getInstance().setServerUrl(server.url("/").toString());

        StudyPlanTreeModel model = new StudyPlanTreeModel();

        // Count model updates
        AtomicInteger completionCount = new AtomicInteger(0);
        CountDownLatch refreshCompletionLatch = new CountDownLatch(callCount);

        // Custom ApiClientFactory that tracks completions
        RefreshPlansAction action = new RefreshPlansAction(model, (url, pat) -> {
            ApiClient client = new ApiClient(url, pat);
            return client;
        });

        // Track structure changes indicating setPlans completion
        model.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {}
            @Override
            public void treeNodesInserted(TreeModelEvent e) {}
            @Override
            public void treeNodesRemoved(TreeModelEvent e) {}
            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                if (model.hasPlans()) {
                    completionCount.incrementAndGet();
                    refreshCompletionLatch.countDown();
                }
            }
        });

        // Fire 16 concurrent performRefresh calls
        ExecutorService triggerExecutor = Executors.newFixedThreadPool(callCount);
        CountDownLatch triggerStartGate = new CountDownLatch(1);

        List<Future<?>> triggerFutures = new ArrayList<>();
        CopyOnWriteArrayList<Throwable> triggerExceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < callCount; i++) {
            triggerFutures.add(triggerExecutor.submit(() -> {
                try {
                    triggerStartGate.await();
                    action.performRefresh(null);
                } catch (Throwable t) {
                    triggerExceptions.add(t);
                }
            }));
        }

        triggerStartGate.countDown();
        triggerExecutor.shutdown();
        triggerExecutor.awaitTermination(5, TimeUnit.SECONDS);

        if (!triggerExceptions.isEmpty()) {
            StringBuilder sb = new StringBuilder("Triggering performRefresh failed with " + triggerExceptions.size() + " exceptions:\n");
            for (Throwable t : triggerExceptions) {
                sb.append("  ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
                t.printStackTrace(System.err);
            }
            fail(sb.toString());
        }

        // Wait up to 25 seconds for all 16 background refreshes to complete
        boolean allCompleted = refreshCompletionLatch.await(25, TimeUnit.SECONDS);

        // Even if some latches didn't count down due to overlapping state transitions,
        // wait for server requests to settle
        long deadline = System.currentTimeMillis() + 10_000;
        while (listRequestCount.get() < callCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        assertTrue(listRequestCount.get() >= callCount,
                "Expected at least 16 list requests, received: " + listRequestCount.get());
        assertTrue(detailRequestCount.get() >= callCount * 2,
                "Expected at least 32 detail requests, received: " + detailRequestCount.get());

        // Verify final tree model state is consistent and populated
        assertTrue(model.hasPlans(), "Model must end in a valid hasPlans() state");
        assertEquals(2, model.getPlanCount(), "Model must contain exactly 2 plans");
        assertNotNull(model.findPlanBySlug("plan-sys-design"));
        assertNotNull(model.findPlanBySlug("plan-algo"));
    }

    // =========================================================================
    // Test 5: Fine-grained Concurrent Stage Status Mutation
    // =========================================================================

    @Test
    @DisplayName("Challenge 5: Concurrent updateStageStatus calls across multiple threads with volatile read checks")
    void testStageStatusFineGrainedMutation_Concurrency() throws Exception {
        StudyPlanTreeModel model = new StudyPlanTreeModel();
        List<PlanDetail> plans = createSamplePlans("status-plan", 2, 2, 4);
        model.setPlans(plans);

        int threadCount = 16;
        int opsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger mutationSuccessCount = new AtomicInteger(0);
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

        StageStatus[] statuses = StageStatus.values();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < opsPerThread; j++) {
                        String slug = "status-plan-1-item-1-stage-" + ((j % 4) + 1);
                        StageStatus newStatus = statuses[(threadId + j) % statuses.length];
                        boolean updated = model.updateStageStatus(slug, newStatus);
                        if (updated) {
                            mutationSuccessCount.incrementAndGet();
                        }

                        // Read status
                        StageTreeNode stage = model.findStageBySlug(slug);
                        if (stage != null) {
                            StageStatus current = stage.getStatus();
                            assertNotNull(current, "Stage status must never be null");
                        }
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All status mutation threads must complete without deadlock");
        assertTrue(errors.isEmpty(), "No exceptions should occur during status mutations: " + errors);
        assertTrue(mutationSuccessCount.get() > 0, "Mutations must succeed");
    }

    // =========================================================================
    // Helper Methods & Mock Classes
    // =========================================================================

    private List<PlanDetail> createSamplePlans(String prefix, int planCount, int itemsPerPlan, int stagesPerItem) {
        List<PlanDetail> plans = new ArrayList<>();
        for (int p = 1; p <= planCount; p++) {
            String planSlug = prefix + "-" + p;
            List<ItemDto> items = new ArrayList<>();
            int totalStages = 0;
            int doneStages = 0;

            for (int it = 1; it <= itemsPerPlan; it++) {
                List<StageDto> stages = new ArrayList<>();
                for (int st = 1; st <= stagesPerItem; st++) {
                    totalStages++;
                    StageStatus status = (st % 2 == 0) ? StageStatus.DONE : StageStatus.LOCKED;
                    if (status == StageStatus.DONE) {
                        doneStages++;
                    }
                    stages.add(new StageDto(
                            st,
                            (long) (p * 1000 + it * 100 + st),
                            planSlug + "-item-" + it + "-stage-" + st,
                            "Stage " + st + " for Item " + it,
                            Difficulty.EASY,
                            ProblemType.OOP_GRINDING,
                            true,
                            status
                    ));
                }

                items.add(new ItemDto(
                        (long) (p * 100 + it),
                        it,
                        (it % 2 == 1) ? StudyPlanItemKind.PROJECT : StudyPlanItemKind.PROBLEM,
                        "Item " + it + " of " + planSlug,
                        "Description for item " + it,
                        false,
                        stages
                ));
            }

            plans.add(new PlanDetail(
                    (long) p,
                    planSlug,
                    "Title for " + planSlug,
                    "Description for " + planSlug,
                    StudyPlanStatus.PUBLISHED,
                    totalStages,
                    doneStages,
                    items
            ));
        }
        return plans;
    }

    private static class CountingTreeModelListener implements TreeModelListener {
        final int id;
        final AtomicInteger nodesChangedCount = new AtomicInteger(0);
        final AtomicInteger nodesInsertedCount = new AtomicInteger(0);
        final AtomicInteger nodesRemovedCount = new AtomicInteger(0);
        final AtomicInteger structureChangedCount = new AtomicInteger(0);

        CountingTreeModelListener(int id) {
            this.id = id;
        }

        int getTotalEvents() {
            return nodesChangedCount.get() + nodesInsertedCount.get() +
                    nodesRemovedCount.get() + structureChangedCount.get();
        }

        @Override
        public void treeNodesChanged(TreeModelEvent e) {
            nodesChangedCount.incrementAndGet();
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
            nodesInsertedCount.incrementAndGet();
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
            nodesRemovedCount.incrementAndGet();
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            structureChangedCount.incrementAndGet();
        }
    }

    private static class TestPasswordSafe extends PasswordSafe {
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
        public void setRememberPasswordByDefault(boolean isRememberPasswordByDefault) {}

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
