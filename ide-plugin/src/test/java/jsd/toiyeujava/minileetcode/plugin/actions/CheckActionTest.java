package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit test suite for CheckAction, DefaultCheckHandler, and NotificationPresenter.
 * Runs 100% headlessly under JUnit 5 without requiring live IntelliJ GUI or application container.
 */
class CheckActionTest {

    @TempDir
    Path tempDir;

    private RecordingNotificationPresenter notificationPresenter;
    private DefaultCheckHandler defaultCheckHandler;
    private CheckAction checkAction;

    @BeforeEach
    void setUp() {
        notificationPresenter = new RecordingNotificationPresenter();
        defaultCheckHandler = new DefaultCheckHandler(tempDir);
        checkAction = new CheckAction(defaultCheckHandler, notificationPresenter);
    }

    private void createValidProjectLayout(int stage, String slug) throws IOException {
        // 1. .mlc/project.yaml
        ProjectManifest manifest = ProjectManifest.builder()
                .plan("oop-with-java")
                .item("1")
                .currentStage(stage)
                .problemSlug(slug)
                .problemId(101L)
                .build();
        manifest.writeToProject(tempDir);

        // 2. pom.xml
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        // 3. stages/stage-N.md
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);
        Files.writeString(stagesDir.resolve("stage-" + stage + ".md"), "# Stage " + stage);

        // 4. src/main/java
        Files.createDirectories(tempDir.resolve("src/main/java"));
    }

    @Nested
    @DisplayName("Feature 13: ActionUpdateThread and Root Detection")
    class ActionUpdateThreadAndRootTests {

        @Test
        @DisplayName("getActionUpdateThread must strictly return ActionUpdateThread.BGT")
        void testActionUpdateThreadIsBgt() {
            assertEquals(ActionUpdateThread.BGT, checkAction.getActionUpdateThread(),
                    "CheckAction must update on BGT to avoid freezing EDT on disk I/O");
        }

        @Test
        @DisplayName("ProjectManifest.existsIn returns true when .mlc/project.yaml exists")
        void testFindMlcRootSuccess() throws IOException {
            createValidProjectLayout(1, "mini-bank-stage-1");
            assertTrue(ProjectManifest.existsIn(tempDir));
        }

        @Test
        @DisplayName("ProjectManifest.existsIn returns false when .mlc is absent")
        void testFindMlcRootAbsent() {
            assertFalse(ProjectManifest.existsIn(tempDir));
        }
    }

    @Nested
    @DisplayName("Feature 14: Stage Status Notification & Validation")
    class StageStatusNotificationTests {

        @Test
        @DisplayName("Valid project layout produces SUCCESS result and INFORMATION balloon")
        void testValidProjectCheck() throws IOException {
            createValidProjectLayout(1, "mini-bank-stage-1");

            CheckResult result = checkAction.executeCheckSync(null, tempDir);

            assertTrue(result.isSuccess());
            assertEquals(CheckStatus.SUCCESS, result.status());
            assertEquals("Mini LeetCode — Stage Status", result.title());
            assertTrue(result.message().contains("Stage 1 (mini-bank-stage-1): Ready for local check/submission"));
            assertTrue(result.message().contains("oop-with-java"));

            // Verify notification balloon
            assertEquals(1, notificationPresenter.notifications.size());
            RecordingNotificationPresenter.NotificationRecord record = notificationPresenter.notifications.get(0);
            assertEquals("Mini LeetCode — Stage Status", record.title());
            assertEquals(NotificationType.INFORMATION, record.type());
            assertTrue(record.content().contains("Ready for local check/submission"));
        }

        @Test
        @DisplayName("Missing stage markdown produces WARNING result and WARNING balloon")
        void testMissingStageMarkdownProducesWarning() throws IOException {
            createValidProjectLayout(2, "mini-bank-stage-2");
            // Delete stage-2.md
            Files.deleteIfExists(tempDir.resolve("stages/stage-2.md"));

            CheckResult result = checkAction.executeCheckSync(null, tempDir);

            assertEquals(CheckStatus.WARNING, result.status());
            assertTrue(result.hasWarnings());
            assertTrue(result.missingFiles().contains("stages/stage-2.md"));

            assertEquals(1, notificationPresenter.notifications.size());
            RecordingNotificationPresenter.NotificationRecord record = notificationPresenter.notifications.get(0);
            assertEquals(NotificationType.WARNING, record.type());
            assertTrue(record.content().contains("stages/stage-2.md"));
        }

        @Test
        @DisplayName("Missing pom.xml produces WARNING result")
        void testMissingPomProducesWarning() throws IOException {
            createValidProjectLayout(1, "mini-bank-stage-1");
            Files.deleteIfExists(tempDir.resolve("pom.xml"));

            CheckResult result = checkAction.executeCheckSync(null, tempDir);

            assertEquals(CheckStatus.WARNING, result.status());
            assertTrue(result.missingFiles().contains("pom.xml"));
            assertEquals(NotificationType.WARNING, notificationPresenter.notifications.get(0).type());
        }

        @Test
        @DisplayName("Missing src/main/java produces WARNING result")
        void testMissingSrcMainJavaProducesWarning() throws IOException {
            createValidProjectLayout(1, "mini-bank-stage-1");
            Files.deleteIfExists(tempDir.resolve("src/main/java"));

            CheckResult result = checkAction.executeCheckSync(null, tempDir);

            assertEquals(CheckStatus.WARNING, result.status());
            assertTrue(result.missingFiles().contains("src/main/java/"));
            assertEquals(NotificationType.WARNING, notificationPresenter.notifications.get(0).type());
        }

        @Test
        @DisplayName("Missing .mlc/project.yaml produces ERROR result and ERROR balloon")
        void testMissingManifestProducesError() {
            CheckResult result = checkAction.executeCheckSync(null, tempDir);

            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.message().contains("Missing metadata file"));
            assertEquals(NotificationType.ERROR, notificationPresenter.notifications.get(0).type());
        }

        @Test
        @DisplayName("Corrupted .mlc/project.yaml produces ERROR result and ERROR balloon")
        void testCorruptedManifestProducesError() throws IOException {
            Path mlcDir = tempDir.resolve(".mlc");
            Files.createDirectories(mlcDir);
            Files.writeString(mlcDir.resolve("project.yaml"), "corrupted: [invalid yaml");

            CheckResult result = checkAction.executeCheckSync(null, tempDir);

            assertEquals(CheckStatus.ERROR, result.status());
            assertTrue(result.message().contains("Corrupted .mlc/project.yaml"));
            assertEquals(NotificationType.ERROR, notificationPresenter.notifications.get(0).type());
        }
    }

    @Nested
    @DisplayName("Slice 5 Submission Extension Seam Tests")
    class Slice5SeamTests {

        @Test
        @DisplayName("CheckAction cleanly delegates to custom CheckHandler strategy")
        void testCustomCheckHandlerDelegation() throws IOException {
            createValidProjectLayout(3, "mini-bank-stage-3");

            // Custom Slice 5 submission handler stub
            CheckHandler customHandler = (project, manifest) -> new CheckResult(
                    CheckStatus.SUCCESS,
                    "Mini LeetCode — Stage Status",
                    "Slice 5 Submission: AC 100/100 (Verdict: ACCEPTED)",
                    manifest,
                    List.of(),
                    List.of()
            );

            CheckAction customAction = new CheckAction(customHandler, notificationPresenter);
            CheckResult result = customAction.executeCheckSync(null, tempDir);

            assertEquals(CheckStatus.SUCCESS, result.status());
            assertTrue(result.message().contains("Slice 5 Submission: AC 100/100"));
            assertEquals(NotificationType.INFORMATION, notificationPresenter.notifications.get(0).type());
        }

        @Test
        @DisplayName("CheckHandlerRegistry supports global replacement and reset")
        void testCheckHandlerRegistry() {
            CheckHandler dummy = (p, m) -> null;
            CheckHandlerRegistry.setHandler(dummy);
            assertSame(dummy, CheckHandlerRegistry.getHandler());

            CheckHandlerRegistry.reset();
            assertInstanceOf(DefaultCheckHandler.class, CheckHandlerRegistry.getHandler());
        }
    }

    /**
     * In-memory test spy for recording balloon notifications headlessly.
     */
    static class RecordingNotificationPresenter implements NotificationPresenter {
        record NotificationRecord(String title, String content, NotificationType type) {}
        final List<NotificationRecord> notifications = new ArrayList<>();

        @Override
        public void showNotification(com.intellij.openapi.project.Project project, String title, String content, NotificationType type) {
            notifications.add(new NotificationRecord(title, content, type));
        }
    }
}
