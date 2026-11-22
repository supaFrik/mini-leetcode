package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.NotificationType;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.SubmissionDto;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RunStageAction Unit Tests")
class RunStageActionTest {

    @TempDir
    Path tempDir;

    private static class RecordingNotifier implements NotificationPresenter {
        record Record(String title, String content, NotificationType type) {}
        final List<Record> records = new ArrayList<>();

        @Override
        public void showNotification(com.intellij.openapi.project.Project project, String title, String content, NotificationType type) {
            records.add(new Record(title, content, type));
        }
    }

    private RecordingNotifier notifier;

    @BeforeEach
    void setUp() throws IOException {
        notifier = new RecordingNotifier();

        // Scaffold valid MLC project structure
        Files.createDirectories(tempDir.resolve(".mlc"));
        Files.createDirectories(tempDir.resolve("src/main/java/example"));
        Files.writeString(tempDir.resolve("src/main/java/example/Main.java"), "package example;\npublic class Main {}\n");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        ProjectManifest manifest = new ProjectManifest("oop-java", "1", 1, "mini-bank-1", 42L);
        manifest.writeTo(tempDir.resolve(".mlc/project.yaml"));
    }

    @Test
    @DisplayName("executeRunSync: calls runStage and displays sample test results")
    void testExecuteRunSyncSuccess() {
        SubmissionDto completedSub = new SubmissionDto(
                555L, 42L, "COMPLETED", "AC", null, 2, 2, null, null, 35, 12000
        );

        // Dummy mock client
        SubmitCheckHandler.ClientSupplier mockSupplier = () -> new ApiClient("http://localhost:8080") {
            @Override
            public SubmissionDto runStage(long problemId, byte[] zipBytes) {
                return completedSub;
            }

            @Override
            public SubmissionDto getSubmission(long submissionId) {
                return completedSub;
            }

            @Override
            public ProblemDto getProblem(String idOrSlug) {
                return new ProblemDto(42L, "Stage 1", "mini-bank-1", "Description");
            }
        };

        RunStageAction action = new RunStageAction(mockSupplier, notifier);
        SubmissionDto result = action.executeRunSync(null, tempDir);

        assertNotNull(result);
        assertEquals(555L, result.id());
        assertTrue(result.isAccepted());
        assertEquals(2, result.passTestCases());

        assertEquals(1, notifier.records.size());
        RecordingNotifier.Record notif = notifier.records.get(0);
        assertEquals(NotificationType.INFORMATION, notif.type());
        assertTrue(notif.content().contains("AC"));
        assertTrue(notif.content().contains("2/2 passed"));
    }

    @Test
    @DisplayName("executeRunSync: handles failure with error message")
    void testExecuteRunSyncFailure() {
        SubmissionDto failedSub = new SubmissionDto(
                556L, 42L, "COMPLETED", "WA", null, 1, 2, "Test 2 failed: Expected 100 but was 0", null
        );

        SubmitCheckHandler.ClientSupplier mockSupplier = () -> new ApiClient("http://localhost:8080") {
            @Override
            public SubmissionDto runStage(long problemId, byte[] zipBytes) {
                return failedSub;
            }

            @Override
            public SubmissionDto getSubmission(long submissionId) {
                return failedSub;
            }

            @Override
            public ProblemDto getProblem(String idOrSlug) {
                return new ProblemDto(42L, "Stage 1", "mini-bank-1", "Description");
            }
        };

        RunStageAction action = new RunStageAction(mockSupplier, notifier);
        SubmissionDto result = action.executeRunSync(null, tempDir);

        assertNotNull(result);
        assertFalse(result.isAccepted());
        assertEquals("WA", result.verdict());

        assertEquals(1, notifier.records.size());
        RecordingNotifier.Record notif = notifier.records.get(0);
        assertEquals(NotificationType.WARNING, notif.type());
        assertTrue(notif.content().contains("WA"));
        assertTrue(notif.content().contains("Expected 100"));
    }

    @Test
    @DisplayName("executeRunSync: missing src/main/java produces error notification")
    void testMissingSrcMainJava() throws IOException {
        Path emptyProject = Files.createTempDirectory("empty-mlc");
        Files.createDirectories(emptyProject.resolve(".mlc"));
        ProjectManifest manifest = new ProjectManifest("oop-java", "1", 1, "mini-bank-1", 42L);
        manifest.writeTo(emptyProject.resolve(".mlc/project.yaml"));

        RunStageAction action = new RunStageAction(null, notifier);
        SubmissionDto result = action.executeRunSync(null, emptyProject);

        assertNull(result);
        assertEquals(1, notifier.records.size());
        assertEquals(NotificationType.ERROR, notifier.records.get(0).type());
        assertTrue(notifier.records.get(0).content().contains("src/main/java does not exist"));
    }
}
