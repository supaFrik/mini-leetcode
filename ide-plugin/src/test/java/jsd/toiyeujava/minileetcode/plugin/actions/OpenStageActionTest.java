package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.ui.ItemTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.PlanTreeNode;
import jsd.toiyeujava.minileetcode.plugin.ui.StageTreeNode;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectOpener;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit test suite for OpenStageAction.
 * Validates PL-3 project initialization, PL-4 markdown preview dispatch, directory chooser interactions,
 * locked stage guards, and error notifications.
 */
class OpenStageActionTest {

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private RecordingNotificationPresenter notificationPresenter;
    private RecordingProjectOpener projectOpener;
    private RecordingMarkdownOpener markdownOpener;
    private TestPasswordSafe testPasswordSafe;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        testPasswordSafe = new TestPasswordSafe();
        MiniLeetCodePasswordSafe.setTestInstance(new MiniLeetCodePasswordSafe(testPasswordSafe));

        MiniLeetCodeSettingsState settings = MiniLeetCodeSettingsState.getInstance();
        settings.setServerUrl(server.url("/").toString());
        MiniLeetCodePasswordSafe.getInstance().setPassword("test-pat-token");

        notificationPresenter = new RecordingNotificationPresenter();
        projectOpener = new RecordingProjectOpener();
        markdownOpener = new RecordingMarkdownOpener();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        MiniLeetCodePasswordSafe.setTestInstance(null);
    }

    private byte[] createZipBytes(Map<String, String> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
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

    @Test
    @DisplayName("ActionUpdateThread must be EDT for Swing tree selection inspection")
    void testActionUpdateThreadIsEdt() {
        OpenStageAction action = new OpenStageAction();
        assertEquals(ActionUpdateThread.EDT, action.getActionUpdateThread());
    }

    @Test
    @DisplayName("Successfully initializes workspace, extracts starter, opens project and markdown preview")
    void testSuccessfulStageOpening() throws Exception {
        StageTreeNode stageNode = buildTreeHierarchy(StageStatus.NEVER);
        Path targetProjectDir = tempDir.resolve("target-student-project");

        // 1. Enqueue starter zip
        byte[] starterZip = createZipBytes(Map.of(
                "src/main/java/Account.java", "public class Account {}"
        ));
        Buffer zipBuf = new Buffer().write(starterZip);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/zip")
                .setBody(zipBuf));

        // 2. Enqueue problem description JSON
        String problemJson = """
                {
                    "id": 101,
                    "title": "Account Class",
                    "slug": "mini-bank-stage-1",
                    "description": "# Stage 1: Account Description",
                    "difficulty": "EASY",
                    "type": "OOP_GRINDING"
                }
                """;
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(problemJson));

        DirectoryChooser chooser = (proj, def) -> targetProjectDir;

        OpenStageAction action = new OpenStageAction(
                null, chooser, ApiClient::new, projectOpener, markdownOpener, notificationPresenter
        );

        WorkspaceResult result = action.performOpenStageSync(null, stageNode, targetProjectDir);

        assertNotNull(result);
        assertEquals(targetProjectDir, result.projectDir());
        assertTrue(Files.isRegularFile(targetProjectDir.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(targetProjectDir.resolve(".mlc/project.yaml")));
        assertTrue(Files.isRegularFile(targetProjectDir.resolve("stages/stage-1.md")));
        assertTrue(Files.isRegularFile(targetProjectDir.resolve("src/main/java/Account.java")));

        // Verify project opened
        assertEquals(1, projectOpener.openedProjects.size());
        assertEquals(targetProjectDir, projectOpener.openedProjects.get(0));

        // Verify markdown opened
        assertEquals(1, markdownOpener.openedMarkdownFiles.size());
        assertEquals(targetProjectDir.resolve("stages/stage-1.md"), markdownOpener.openedMarkdownFiles.get(0));

        // Verify success notification
        assertEquals(1, notificationPresenter.notifications.size());
        assertEquals(NotificationType.INFORMATION, notificationPresenter.notifications.get(0).type());
        assertTrue(notificationPresenter.notifications.get(0).content().contains("Successfully opened Stage 1"));
    }

    @Test
    @DisplayName("User cancellation in DirectoryChooser terminates without opening or error")
    void testUserCancellation() {
        StageTreeNode stageNode = buildTreeHierarchy(StageStatus.NEVER);
        DirectoryChooser cancellingChooser = (proj, def) -> null;

        OpenStageAction action = new OpenStageAction(
                null, cancellingChooser, ApiClient::new, projectOpener, markdownOpener, notificationPresenter
        );

        action.openSelectedNode(null, stageNode);

        assertEquals(0, projectOpener.openedProjects.size());
        assertEquals(0, markdownOpener.openedMarkdownFiles.size());
        assertEquals(0, notificationPresenter.notifications.size());
    }

    @Test
    @DisplayName("Locked stage blocks execution with warning balloon and skips directory chooser")
    void testLockedStageGuard() {
        StageTreeNode stageNode = buildTreeHierarchy(StageStatus.LOCKED);
        boolean[] chooserCalled = {false};
        DirectoryChooser chooser = (proj, def) -> {
            chooserCalled[0] = true;
            return tempDir;
        };

        OpenStageAction action = new OpenStageAction(
                null, chooser, ApiClient::new, projectOpener, markdownOpener, notificationPresenter
        );

        action.openSelectedNode(null, stageNode);

        assertFalse(chooserCalled[0], "Directory chooser must not be called when stage is locked");
        assertEquals(0, projectOpener.openedProjects.size());
        assertEquals(1, notificationPresenter.notifications.size());
        assertEquals(NotificationType.WARNING, notificationPresenter.notifications.get(0).type());
        assertTrue(notificationPresenter.notifications.get(0).content().contains("Stage 1 is locked"));
    }

    @Test
    @DisplayName("Resolves first in-progress or unlocked stage when ItemTreeNode is selected")
    void testResolveTargetStageFromItemNode() {
        PlanDetail plan = new PlanDetail(1L, "oop", "OOP", "", StudyPlanStatus.PUBLISHED, 2, 0, List.of());
        PlanTreeNode planNode = new PlanTreeNode(plan);

        ItemDto item = new ItemDto(10L, 1, StudyPlanItemKind.PROJECT, "Mini Bank", "", false, List.of());
        ItemTreeNode itemNode = new ItemTreeNode(item);
        planNode.add(itemNode);

        StageDto stage1 = new StageDto(1, 101L, "s1", "S1", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE);
        StageDto stage2 = new StageDto(2, 102L, "s2", "S2", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.IN_PROGRESS);

        StageTreeNode st1 = new StageTreeNode(stage1);
        StageTreeNode st2 = new StageTreeNode(stage2);
        itemNode.add(st1);
        itemNode.add(st2);

        OpenStageAction action = new OpenStageAction();
        StageTreeNode resolved = action.resolveTargetStage(itemNode);

        assertNotNull(resolved);
        assertEquals(2, resolved.getStageNo());
        assertEquals("s2", resolved.getSlug());
    }

    @Test
    @DisplayName("API failure displays formatted error notification balloon")
    void testApiFailureErrorHandling() {
        StageTreeNode stageNode = buildTreeHierarchy(StageStatus.NEVER);
        Path targetProjectDir = tempDir.resolve("target-project");

        // Return HTTP 404
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"title\": \"Problem Not Found\"}"));

        DirectoryChooser chooser = (proj, def) -> targetProjectDir;
        OpenStageAction action = new OpenStageAction(
                null, chooser, ApiClient::new, projectOpener, markdownOpener, notificationPresenter
        );

        action.openSelectedNode(null, stageNode);

        assertEquals(0, projectOpener.openedProjects.size());
        assertEquals(1, notificationPresenter.notifications.size());
        assertEquals(NotificationType.ERROR, notificationPresenter.notifications.get(0).type());
    }

    static class RecordingProjectOpener implements ProjectOpener {
        final List<Path> openedProjects = new ArrayList<>();
        @Override
        public void openProject(@NotNull Path projectDirectory) {
            openedProjects.add(projectDirectory);
        }
    }

    static class RecordingMarkdownOpener implements MarkdownOpener {
        final List<Path> openedMarkdownFiles = new ArrayList<>();
        @Override
        public void openMarkdown(@Nullable Project project, @NotNull Path stageMarkdownPath) {
            openedMarkdownFiles.add(stageMarkdownPath);
        }
    }

    static class RecordingNotificationPresenter implements NotificationPresenter {
        record NotificationRecord(String title, String content, NotificationType type) {}
        final List<NotificationRecord> notifications = new ArrayList<>();

        @Override
        public void showNotification(@Nullable Project project, @NotNull String title, @NotNull String content, @NotNull NotificationType type) {
            notifications.add(new NotificationRecord(title, content, type));
        }
    }

    static class TestPasswordSafe extends PasswordSafe {
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
