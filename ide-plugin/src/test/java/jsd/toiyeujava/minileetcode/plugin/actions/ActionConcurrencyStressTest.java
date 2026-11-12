package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import jsd.toiyeujava.minileetcode.plugin.ui.ItemTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.PlanTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.StageTreeNode;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Test Suite for Action Lifecycle, Concurrency, and Fault Injection.
 * <p>
 * Stresses:
 * 1. Concurrency & Reentrancy across 20 threads (CheckAction, CheckHandlerRegistry volatile swapping).
 * 2. Edge Cases & Fault Injection (corrupted YAML syntax/missing keys/non-numeric stage, deeply nested paths,
 *    deleted src/main/java and pom.xml, rapid directory chooser cancellations, and locked stage guards).
 */
public class ActionConcurrencyStressTest {

    @TempDir
    Path tempDir;

    private RecordingNotificationPresenter notificationPresenter;
    private Path projectDir;

    @BeforeEach
    void setUp() throws IOException {
        notificationPresenter = new RecordingNotificationPresenter();
        projectDir = tempDir.resolve("mlc-student-project");
        Files.createDirectories(projectDir);
        CheckHandlerRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        CheckHandlerRegistry.reset();
    }

    private void createValidProjectLayout(Path root, int stage, String slug) throws IOException {
        ProjectManifest manifest = ProjectManifest.builder()
                .plan("oop-with-java")
                .item("1")
                .currentStage(stage)
                .problemSlug(slug)
                .problemId(101L)
                .build();
        manifest.writeToProject(root);

        Files.writeString(root.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");

        Path stagesDir = root.resolve("stages");
        Files.createDirectories(stagesDir);
        Files.writeString(stagesDir.resolve("stage-" + stage + ".md"), "# Stage " + stage + "\nProblem statement");

        Path srcMainJava = root.resolve("src/main/java");
        Files.createDirectories(srcMainJava);
        Files.writeString(srcMainJava.resolve("Main.java"),
                "package app;\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println(\"HELLO\");\n    }\n}\n");
    }

    private StageTreeNode buildTreeHierarchy(StageStatus stageStatus) {
        PlanDetail plan = new PlanDetail(
                1L, "oop-with-java", "OOP with Java", "Learn OOP", StudyPlanStatus.PUBLISHED,
                1, 0, List.of()
        );
        PlanTreeNode planNode = new PlanTreeNode(plan);

        ItemDto item = new ItemDto(
                10L, 1, StudyPlanItemKind.PROJECT, "Mini Bank", "Console banking app",
                false, List.of()
        );
        ItemTreeNode itemNode = new ItemTreeNode(item);
        planNode.add(itemNode);

        StageDto stage = new StageDto(
                1, 101L, "mini-bank-stage-1", "Account Class",
                Difficulty.EASY, ProblemType.OOP_GRINDING, true, stageStatus
        );
        StageTreeNode stageNode = new StageTreeNode(stage);
        itemNode.add(stageNode);

        return stageNode;
    }

    private Project createMockProject(Path basePath) {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[]{Project.class},
                (proxy, method, args) -> {
                    if ("getBasePath".equals(method.getName())) {
                        return basePath != null ? basePath.toAbsolutePath().toString() : null;
                    }
                    if ("isDisposed".equals(method.getName())) {
                        return false;
                    }
                    if ("getName".equals(method.getName())) {
                        return "MockProject";
                    }
                    return null;
                }
        );
    }

    @Nested
    @DisplayName("1. Concurrency & Reentrancy Stress")
    class ConcurrencyAndReentrancyStressTests {

        @Test
        @DisplayName("Stress: 20 concurrent threads running check() on the same project root verify determinism and thread safety")
        void testConcurrentCheckExecution20Threads() throws Exception {
            createValidProjectLayout(projectDir, 1, "mini-bank-stage-1");

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<CheckResult>> futures = new ArrayList<>();

            DefaultCheckHandler defaultHandler = new DefaultCheckHandler(projectDir);
            CheckAction checkAction = new CheckAction(defaultHandler, notificationPresenter);

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await(); // Simultaneous burst
                    return checkAction.executeCheckSync(null, projectDir);
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            List<CheckResult> results = new ArrayList<>();
            for (Future<CheckResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(threadCount, results.size());
            CheckResult baseline = results.get(0);
            assertEquals(CheckStatus.SUCCESS, baseline.status());
            assertTrue(baseline.isSuccess());

            for (CheckResult r : results) {
                assertEquals(CheckStatus.SUCCESS, r.status());
                assertEquals(baseline.title(), r.title());
                assertEquals(baseline.message(), r.message());
                assertEquals(0, r.missingFiles().size());
                assertNotNull(r.manifest());
                assertEquals("oop-with-java", r.manifest().plan());
                assertEquals(1, r.manifest().currentStage());
            }

            // Notification presenter received thread-safe calls
            assertEquals(threadCount, notificationPresenter.notifications.size());
            for (RecordingNotificationPresenter.NotificationRecord record : notificationPresenter.notifications) {
                assertEquals(NotificationType.INFORMATION, record.type());
                assertEquals("Mini LeetCode — Stage Status", record.title());
            }
        }

        @Test
        @DisplayName("Stress: Dynamic swapping of CheckHandlerRegistry.setHandler() under concurrent reader execution")
        void testCheckHandlerRegistryDynamicSwappingUnderConcurrentExecution() throws Exception {
            createValidProjectLayout(projectDir, 1, "mini-bank-stage-1");
            ProjectManifest manifest = ProjectManifest.readFromProject(projectDir);

            CheckHandler handlerA = (proj, man) -> new CheckResult(
                    CheckStatus.SUCCESS, "A", "Handler-A Output", man, List.of(), List.of()
            );
            CheckHandler handlerB = (proj, man) -> new CheckResult(
                    CheckStatus.SUCCESS, "B", "Handler-B Output", man, List.of(), List.of()
            );

            int readerCount = 20;
            int iterationsPerReader = 50;
            ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
            AtomicBoolean running = new AtomicBoolean(true);
            ConcurrentLinkedQueue<CheckResult> observedResults = new ConcurrentLinkedQueue<>();
            AtomicInteger errorCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch firstSwapLatch = new CountDownLatch(1);

            // 1 Mutator thread actively swapping handlers
            Future<?> mutatorFuture = executor.submit(() -> {
                try {
                    startLatch.await();
                    CheckHandlerRegistry.setHandler(handlerA);
                    firstSwapLatch.countDown();
                    for (int k = 0; k < 200 && running.get(); k++) {
                        CheckHandlerRegistry.setHandler((k % 2 == 0) ? handlerB : handlerA);
                        Thread.sleep(1);
                    }
                } catch (Throwable t) {
                    errorCount.incrementAndGet();
                }
            });

            // 20 Reader threads calling CheckHandlerRegistry.getHandler().check()
            List<Future<?>> readerFutures = new ArrayList<>();
            for (int i = 0; i < readerCount; i++) {
                readerFutures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        firstSwapLatch.await();
                        for (int j = 0; j < iterationsPerReader; j++) {
                            CheckHandler h = CheckHandlerRegistry.getHandler();
                            assertNotNull(h, "CheckHandler from registry must never be null");
                            CheckResult r = h.check(null, projectDir, manifest);
                            assertNotNull(r, "CheckResult must never be null");
                            observedResults.add(r);
                            Thread.yield();
                        }
                    } catch (Throwable t) {
                        errorCount.incrementAndGet();
                    }
                }));
            }

            startLatch.countDown();
            for (Future<?> rf : readerFutures) {
                rf.get(10, TimeUnit.SECONDS);
            }
            running.set(false);
            mutatorFuture.get(10, TimeUnit.SECONDS);

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(0, errorCount.get(), "Zero exceptions allowed during concurrent registry reading and swapping");
            assertFalse(observedResults.isEmpty(), "Observed results must not be empty");

            // Every result must strictly belong to either Handler A, Handler B, or DefaultCheckHandler
            boolean sawA = false;
            boolean sawB = false;
            for (CheckResult r : observedResults) {
                if ("A".equals(r.title())) {
                    sawA = true;
                    assertEquals("Handler-A Output", r.message());
                } else if ("B".equals(r.title())) {
                    sawB = true;
                    assertEquals("Handler-B Output", r.message());
                } else {
                    assertEquals("Mini LeetCode — Stage Status", r.title());
                }
            }

            assertTrue(sawA || sawB, "Readers must successfully observe dynamically swapped handlers");

            // Clean reset
            CheckHandlerRegistry.reset();
            assertInstanceOf(SubmitCheckHandler.class, CheckHandlerRegistry.getHandler());
        }

        @Test
        @DisplayName("Lifecycle observation: CheckAction retains constructor-time CheckHandler when instantiated explicitly")
        void testCheckActionConstructorHandlerBinding() throws IOException {
            createValidProjectLayout(projectDir, 1, "mini-bank-stage-1");

            CheckHandler customHandler = (proj, man) -> new CheckResult(
                    CheckStatus.SUCCESS, "Custom", "Custom Result", man, List.of(), List.of()
            );

            // When CheckAction is created with an explicit handler, it uses that handler
            CheckAction explicitAction = new CheckAction(customHandler, notificationPresenter);
            CheckResult res = explicitAction.executeCheckSync(null, projectDir);
            assertEquals("Custom", res.title());
            assertEquals("Custom Result", res.message());

            // When CheckHandlerRegistry is globally set BEFORE creating CheckAction
            CheckHandler globalHandler = (proj, man) -> new CheckResult(
                    CheckStatus.SUCCESS, "Global", "Global Result", man, List.of(), List.of()
            );
            CheckHandlerRegistry.setHandler(globalHandler);

            CheckAction actionFromRegistry = new CheckAction(null, notificationPresenter);
            CheckResult regRes = actionFromRegistry.executeCheckSync(null, projectDir);
            assertEquals("Global", regRes.title());
            assertEquals("Global Result", regRes.message());
        }
    }

    @Nested
    @DisplayName("2. Edge Cases & Fault Injection Scenarios")
    class EdgeCasesAndFaultInjectionTests {

        @Test
        @DisplayName("Fault: Corrupted .mlc/project.yaml (missing colons) produces ERROR result and notification")
        void testCorruptedManifestMissingColonsProducesError() throws IOException {
            Path mlcDir = projectDir.resolve(".mlc");
            Files.createDirectories(mlcDir);
            Files.writeString(mlcDir.resolve("project.yaml"), "this is not yaml\nno colon anywhere\n");

            CheckAction checkAction = new CheckAction(new DefaultCheckHandler(projectDir), notificationPresenter);
            CheckResult result = checkAction.executeCheckSync(null, projectDir);

            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.message().contains("Corrupted .mlc/project.yaml"));
            assertEquals(1, notificationPresenter.notifications.size());
            assertEquals(NotificationType.ERROR, notificationPresenter.notifications.get(0).type());

        }

        @Test
        @DisplayName("Fault: Corrupted manifest missing mandatory keys (plan, item, current_stage, problem_slug)")
        void testCorruptedManifestMissingMandatoryKeys() throws IOException {
            Path mlcDir = projectDir.resolve(".mlc");
            Files.createDirectories(mlcDir);

            // 1. Missing plan
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "item: 1\ncurrent_stage: 1\nproblem_slug: test-slug\n");
            CheckAction checkAction = new CheckAction(new DefaultCheckHandler(projectDir), notificationPresenter);
            CheckResult r1 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r1.status());
            assertTrue(r1.message().contains("Missing required manifest field: 'plan'"));

            // 2. Missing item
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\ncurrent_stage: 1\nproblem_slug: test-slug\n");
            CheckResult r2 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r2.status());
            assertTrue(r2.message().contains("Missing required manifest field: 'item'"));

            // 3. Missing current_stage
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\nitem: 1\nproblem_slug: test-slug\n");
            CheckResult r3 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r3.status());
            assertTrue(r3.message().contains("Missing required manifest field: 'current_stage'"));

            // 4. Missing problem_slug
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\nitem: 1\ncurrent_stage: 1\n");
            CheckResult r4 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r4.status());
            assertTrue(r4.message().contains("Missing required manifest field: 'problem_slug'"));
        }

        @Test
        @DisplayName("Fault: Corrupted manifest with non-numeric or non-positive current_stage")
        void testCorruptedManifestNonNumericCurrentStage() throws IOException {
            Path mlcDir = projectDir.resolve(".mlc");
            Files.createDirectories(mlcDir);

            // Non-numeric string
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\nitem: 1\ncurrent_stage: NOT_A_NUMBER\nproblem_slug: test-slug\n");
            CheckAction checkAction = new CheckAction(new DefaultCheckHandler(projectDir), notificationPresenter);
            CheckResult r1 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r1.status());
            assertTrue(r1.message().contains("Invalid integer for 'current_stage'"));

            // Non-positive zero
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\nitem: 1\ncurrent_stage: 0\nproblem_slug: test-slug\n");
            CheckResult r2 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r2.status());
            assertTrue(r2.message().contains("Invalid 'current_stage'"));

            // Negative stage
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\nitem: 1\ncurrent_stage: -5\nproblem_slug: test-slug\n");
            CheckResult r3 = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r3.status());
            assertTrue(r3.message().contains("Invalid 'current_stage'"));
        }

        @Test
        @DisplayName("Fault: Corrupted manifest with non-numeric problem_id")
        void testCorruptedManifestNonNumericProblemId() throws IOException {
            Path mlcDir = projectDir.resolve(".mlc");
            Files.createDirectories(mlcDir);
            Files.writeString(mlcDir.resolve("project.yaml"),
                    "plan: oop\nitem: 1\ncurrent_stage: 1\nproblem_slug: test-slug\nproblem_id: xyz\n");

            CheckAction checkAction = new CheckAction(new DefaultCheckHandler(projectDir), notificationPresenter);
            CheckResult r = checkAction.executeCheckSync(null, projectDir);
            assertEquals(CheckStatus.ERROR, r.status());
            assertTrue(r.message().contains("Invalid integer for 'problem_id'"));
        }

        @Test
        @DisplayName("Edge: Deeply nested directory structure (15 levels) resolves project root cleanly via CheckAction.findMlcRoot")
        void testDeeplyNestedDirectoryStructureTraversal() throws IOException {
            createValidProjectLayout(projectDir, 1, "mini-bank-stage-1");

            // Build deep directory tree: projectDir/l1/l2/l3/.../l15
            Path deepDir = projectDir;
            for (int i = 1; i <= 15; i++) {
                deepDir = deepDir.resolve("level-" + i);
            }
            Files.createDirectories(deepDir);
            Path nestedFile = deepDir.resolve("DeepNestedClass.java");
            Files.writeString(nestedFile, "package level; public class DeepNestedClass {}");

            // Verify CheckAction.findMlcRoot finds root from 15 levels deep
            Project mockDeepProject = createMockProject(deepDir);
            Optional<Path> foundRoot = CheckAction.findMlcRoot(mockDeepProject, null);
            assertTrue(foundRoot.isPresent());
            assertEquals(projectDir.toAbsolutePath().normalize(), foundRoot.get().toAbsolutePath().normalize());

            // Boundary test: Ancestor search on path without any .mlc resolves Optional.empty()
            Path outsideDir = tempDir.resolve("unrelated-folder/sub1/sub2");
            Files.createDirectories(outsideDir);
            Project mockOutsideProject = createMockProject(outsideDir);
            Optional<Path> notFound = CheckAction.findMlcRoot(mockOutsideProject, null);
            assertTrue(notFound.isEmpty());
        }

        @Test
        @DisplayName("Fault: Deleted src/main/java after manifest creation produces WARNING in CheckAction")
        void testDeletedSrcMainJavaAfterProjectCreation() throws IOException {
            createValidProjectLayout(projectDir, 1, "mini-bank-stage-1");

            // Delete src/main/java
            Path srcMainJava = projectDir.resolve("src/main/java");
            Files.deleteIfExists(srcMainJava.resolve("Main.java"));
            Files.deleteIfExists(srcMainJava);
            assertFalse(Files.exists(srcMainJava));

            // CheckAction: Produces WARNING with missingFiles containing src/main/java/
            CheckAction checkAction = new CheckAction(new DefaultCheckHandler(projectDir), notificationPresenter);
            CheckResult checkResult = checkAction.executeCheckSync(null, projectDir);

            assertEquals(CheckStatus.WARNING, checkResult.status());
            assertTrue(checkResult.missingFiles().contains("src/main/java/"));
            assertEquals(1, notificationPresenter.notifications.size());
            assertEquals(NotificationType.WARNING, notificationPresenter.notifications.get(0).type());

        }

        @Test
        @DisplayName("Fault: Missing pom.xml or stages/stage-N.md produces WARNING with missingFiles")
        void testMissingPomAndMarkdownProducesWarnings() throws IOException {
            createValidProjectLayout(projectDir, 1, "mini-bank-stage-1");

            // Delete pom.xml
            Files.deleteIfExists(projectDir.resolve("pom.xml"));
            // Delete stage-1.md
            Files.deleteIfExists(projectDir.resolve("stages/stage-1.md"));

            CheckAction checkAction = new CheckAction(new DefaultCheckHandler(projectDir), notificationPresenter);
            CheckResult checkResult = checkAction.executeCheckSync(null, projectDir);

            assertEquals(CheckStatus.WARNING, checkResult.status());
            assertTrue(checkResult.missingFiles().contains("pom.xml"));
            assertTrue(checkResult.missingFiles().contains("stages/stage-1.md"));
            assertEquals(2, checkResult.missingFiles().size());
            assertEquals(NotificationType.WARNING, notificationPresenter.notifications.get(0).type());
        }

        @Test
        @DisplayName("Edge: Rapid DirectoryChooser cancellations (50 times) in OpenStageAction terminate gracefully")
        void testRapidDirectoryChooserCancellationsInOpenStageAction() {
            StageTreeNode stageNode = buildTreeHierarchy(StageStatus.NEVER);
            AtomicInteger chooserCallCount = new AtomicInteger(0);

            DirectoryChooser cancellingChooser = (proj, def) -> {
                chooserCallCount.incrementAndGet();
                return null; // Simulate user cancel
            };

            RecordingProjectOpener projectOpener = new RecordingProjectOpener();
            RecordingMarkdownOpener markdownOpener = new RecordingMarkdownOpener();

            OpenStageAction action = new OpenStageAction(
                    null, cancellingChooser, jsd.toiyeujava.minileetcode.plugin.client.ApiClient::new,
                    projectOpener, markdownOpener, notificationPresenter
            );

            for (int i = 0; i < 50; i++) {
                action.openSelectedNode(null, stageNode);
            }

            assertEquals(50, chooserCallCount.get());
            assertEquals(0, projectOpener.openedProjects.size(), "Zero projects should be opened on cancellation");
            assertEquals(0, markdownOpener.openedMarkdownFiles.size(), "Zero markdown files should be opened");
            assertEquals(0, notificationPresenter.notifications.size(), "Zero notifications should be shown on cancellation");
        }

        @Test
        @DisplayName("Guard: Locked stage attempts in OpenStageAction block directory chooser and show WARNING balloon")
        void testLockedStageGuardsInOpenStageAction() {
            StageTreeNode lockedNode = buildTreeHierarchy(StageStatus.LOCKED);
            AtomicInteger chooserCallCount = new AtomicInteger(0);

            DirectoryChooser chooser = (proj, def) -> {
                chooserCallCount.incrementAndGet();
                return tempDir;
            };

            RecordingProjectOpener projectOpener = new RecordingProjectOpener();
            RecordingMarkdownOpener markdownOpener = new RecordingMarkdownOpener();

            OpenStageAction action = new OpenStageAction(
                    null, chooser, jsd.toiyeujava.minileetcode.plugin.client.ApiClient::new,
                    projectOpener, markdownOpener, notificationPresenter
            );

            // Rapid 30 attempts on locked stage
            for (int i = 0; i < 30; i++) {
                action.openSelectedNode(null, lockedNode);
            }

            assertEquals(0, chooserCallCount.get(), "Directory chooser must NEVER be called for locked stage");
            assertEquals(0, projectOpener.openedProjects.size());
            assertEquals(0, markdownOpener.openedMarkdownFiles.size());
            assertEquals(30, notificationPresenter.notifications.size());

            for (RecordingNotificationPresenter.NotificationRecord nr : notificationPresenter.notifications) {
                assertEquals(NotificationType.WARNING, nr.type());
                assertTrue(nr.content().contains("is locked"));
            }
        }

        @Test
        @DisplayName("Stress: 20 concurrent threads attempting to open a locked stage")
        void testConcurrentLockedStageAttempts20Threads() throws Exception {
            StageTreeNode lockedNode = buildTreeHierarchy(StageStatus.LOCKED);
            AtomicInteger chooserCallCount = new AtomicInteger(0);

            DirectoryChooser chooser = (proj, def) -> {
                chooserCallCount.incrementAndGet();
                return tempDir;
            };

            RecordingProjectOpener projectOpener = new RecordingProjectOpener();
            RecordingMarkdownOpener markdownOpener = new RecordingMarkdownOpener();

            OpenStageAction action = new OpenStageAction(
                    null, chooser, jsd.toiyeujava.minileetcode.plugin.client.ApiClient::new,
                    projectOpener, markdownOpener, notificationPresenter
            );

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        action.openSelectedNode(null, lockedNode);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(0, chooserCallCount.get(), "Zero chooser calls across 20 threads");
            assertEquals(0, projectOpener.openedProjects.size());
            assertEquals(threadCount, notificationPresenter.notifications.size());
            for (RecordingNotificationPresenter.NotificationRecord nr : notificationPresenter.notifications) {
                assertEquals(NotificationType.WARNING, nr.type());
                assertTrue(nr.content().contains("is locked"));
            }
        }
    }

    /**
     * Test spies and recording stubs
     */
    static class RecordingNotificationPresenter implements NotificationPresenter {
        record NotificationRecord(String title, String content, NotificationType type) {}
        final List<NotificationRecord> notifications = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void showNotification(Project project, String title, String content, NotificationType type) {
            notifications.add(new NotificationRecord(title, content, type));
        }
    }

    static class RecordingProjectOpener implements jsd.toiyeujava.minileetcode.plugin.workspace.ProjectOpener {
        final List<Path> openedProjects = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void openProject(Path projectDirectory) {
            openedProjects.add(projectDirectory);
        }
    }

    static class RecordingMarkdownOpener implements MarkdownOpener {
        final List<Path> openedMarkdownFiles = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void openMarkdown(Project project, Path markdownFile) {
            openedMarkdownFiles.add(markdownFile);
        }
    }
}
