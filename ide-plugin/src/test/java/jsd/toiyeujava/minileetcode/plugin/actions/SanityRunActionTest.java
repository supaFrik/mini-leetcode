package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import jsd.toiyeujava.minileetcode.plugin.runner.SanityRunResult;
import jsd.toiyeujava.minileetcode.plugin.runner.SanityRunner;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit tests for SanityRunAction.
 */
class SanityRunActionTest {

    @TempDir
    Path tempDir;

    private RecordingNotificationPresenter notificationPresenter;

    @BeforeEach
    void setUp() {
        notificationPresenter = new RecordingNotificationPresenter();
    }

    private void createValidManifest(int stage, String slug) throws IOException {
        ProjectManifest manifest = ProjectManifest.builder()
                .plan("oop-with-java")
                .item("1")
                .currentStage(stage)
                .problemSlug(slug)
                .problemId(101L)
                .build();
        manifest.writeToProject(tempDir);
    }

    @Test
    @DisplayName("getActionUpdateThread must return ActionUpdateThread.BGT")
    void testActionUpdateThreadIsBgt() {
        SanityRunAction action = new SanityRunAction();
        assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread(),
                "SanityRunAction must update on BGT to avoid freezing EDT on disk I/O");
    }

    @Test
    @DisplayName("Successful sanity run posts INFORMATION notification with output preview")
    void testSuccessfulSanityRun() throws IOException {
        createValidManifest(1, "mini-bank-stage-1");

        SanityRunner.SubprocessExecutor mockExec = (w, c, m, s, t) -> new SanityRunResult(0, "ALL_PASSED", "", false);
        SanityRunner runner = new SanityRunner(Duration.ofSeconds(5), mockExec);

        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Main.java"), "public class Main { public static void main(String[] a){} }");

        SanityRunAction action = new SanityRunAction(runner, notificationPresenter);
        SanityRunResult result = action.executeSanityRunSync(null, tempDir);

        assertTrue(result.isSuccess());
        assertEquals(1, notificationPresenter.notifications.size());
        RecordingNotificationPresenter.NotificationRecord record = notificationPresenter.notifications.get(0);
        assertEquals(NotificationType.INFORMATION, record.type());
        assertEquals("Sanity Run Passed", record.title());
        assertTrue(record.content().contains("ALL_PASSED"));
    }

    @Test
    @DisplayName("Timeout sanity run posts WARNING notification")
    void testTimeoutSanityRun() throws IOException {
        createValidManifest(1, "mini-bank-stage-1");

        SanityRunner.SubprocessExecutor timeoutExec = (w, c, m, s, t) -> SanityRunResult.timeout("", "Time limit exceeded");
        SanityRunner runner = new SanityRunner(Duration.ofSeconds(5), timeoutExec);

        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Main.java"), "public class Main { public static void main(String[] a){} }");

        SanityRunAction action = new SanityRunAction(runner, notificationPresenter);
        SanityRunResult result = action.executeSanityRunSync(null, tempDir);

        assertTrue(result.timedOut());
        assertEquals(1, notificationPresenter.notifications.size());
        RecordingNotificationPresenter.NotificationRecord record = notificationPresenter.notifications.get(0);
        assertEquals(NotificationType.WARNING, record.type());
        assertEquals("Execution Timed Out", record.title());
    }

    @Test
    @DisplayName("Failing sanity run posts ERROR notification with exit code and error preview")
    void testFailingSanityRun() throws IOException {
        createValidManifest(1, "mini-bank-stage-1");

        SanityRunner.SubprocessExecutor failExec = (w, c, m, s, t) -> new SanityRunResult(42, "", "NullPointerException in Main.java", false);
        SanityRunner runner = new SanityRunner(Duration.ofSeconds(5), failExec);

        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Main.java"), "public class Main { public static void main(String[] a){} }");

        SanityRunAction action = new SanityRunAction(runner, notificationPresenter);
        SanityRunResult result = action.executeSanityRunSync(null, tempDir);

        assertFalse(result.isSuccess());
        assertEquals(42, result.exitCode());
        assertEquals(1, notificationPresenter.notifications.size());
        RecordingNotificationPresenter.NotificationRecord record = notificationPresenter.notifications.get(0);
        assertEquals(NotificationType.ERROR, record.type());
        assertEquals("Execution Failed (Exit Code 42)", record.title());
        assertTrue(record.content().contains("NullPointerException"));
    }

    @Test
    @DisplayName("Missing or invalid manifest posts ERROR notification")
    void testMissingManifest() {
        SanityRunAction action = new SanityRunAction(new SanityRunner(), notificationPresenter);
        SanityRunResult result = action.executeSanityRunSync(null, tempDir);

        assertFalse(result.isSuccess());
        assertEquals(1, notificationPresenter.notifications.size());
        assertEquals(NotificationType.ERROR, notificationPresenter.notifications.get(0).type());
        assertEquals("Manifest Error", notificationPresenter.notifications.get(0).title());
    }

    static class RecordingNotificationPresenter implements NotificationPresenter {
        record NotificationRecord(String title, String content, NotificationType type) {}
        final List<NotificationRecord> notifications = new ArrayList<>();

        @Override
        public void showNotification(com.intellij.openapi.project.Project project, String title, String content, NotificationType type) {
            notifications.add(new NotificationRecord(title, content, type));
        }
    }
}
