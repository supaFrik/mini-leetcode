package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.NotificationType;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectOpener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Slice 5: PL-5 Check nộp lên server + PL-6 Next stage (specs/study-plan.spec.md, AC-001..AC-008). */
class SubmitCheckAndNextStageTest {

    @TempDir Path projectDir;
    private MockWebServer server;
    private final List<NotificationType> notifications = new ArrayList<>();
    private final List<Path> openedMarkdown = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        Files.writeString(projectDir.resolve("pom.xml"), "<project/>");
        Files.createDirectories(projectDir.resolve("stages"));
        Files.writeString(projectDir.resolve("stages/stage-1.md"), "# Stage 1");
        Files.createDirectories(projectDir.resolve("src/main/java/bank"));
        Files.writeString(projectDir.resolve("src/main/java/Main.java"), "public class Main {}");
        Files.writeString(projectDir.resolve("src/main/java/bank/Bank.java"), "package bank; public class Bank {}");
        ProjectManifest.of("oop-voi-java", "2", 1, "mini-bank-stage-1", 101L).writeToProject(projectDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private ApiClient client() {
        return new ApiClient(server.url("/").toString(), "mlc_pat_test", Duration.ofSeconds(5));
    }

    private SubmitCheckHandler handler(Duration timeout) {
        return new SubmitCheckHandler(new DefaultCheckHandler(projectDir), this::client, d -> { }, Duration.ofSeconds(1), timeout);
    }

    private CheckResult check(Duration timeout) throws IOException {
        return handler(timeout).check(null, projectDir, ProjectManifest.readFromProject(projectDir));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static String submission(String status, String verdict, Double score, String error) {
        return "{\"success\":true,\"data\":{\"id\":7,\"problemId\":101,\"status\":\"" + status + "\",\"verdict\":"
                + (verdict == null ? "null" : "\"" + verdict + "\"") + ",\"score\":" + score
                + ",\"passTestCases\":3,\"totalTestCases\":3,\"errorMessage\":"
                + (error == null ? "null" : "\"" + error + "\"") + "}}";
    }

    @Test
    void acceptedCheckSubmitsZipOfSourcesAndReportsPassed() throws Exception {
        server.enqueue(json(201, submission("PENDING", null, null, null)));
        server.enqueue(json(200, submission("JUDGING", null, null, null)));
        server.enqueue(json(200, submission("COMPLETED", "AC", 100.0, null)));

        CheckResult result = check(Duration.ofSeconds(60));

        assertEquals(CheckStatus.SUCCESS, result.status(), result.message());
        assertTrue(result.message().contains("PASSED"), result.message());
        assertTrue(result.message().contains("Next Stage"), result.message());

        RecordedRequest submit = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("/api/v1/problems/101/submit-multipart", submit.getPath());
        assertEquals("Bearer mlc_pat_test", submit.getHeader("Authorization"));
        byte[] body = submit.getBody().readByteArray();
        String text = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("name=\"language\"\r\n\r\nJAVA"));
        assertEquals(java.util.Set.of("Main.java", "bank/Bank.java"), new java.util.HashSet<>(zipEntries(body)));
        assertEquals("/api/v1/submissions/7", server.takeRequest(5, TimeUnit.SECONDS).getPath());
    }

    @Test
    void wrongAnswerShowsVerdictScoreAndFirstErrorLines() throws Exception {
        server.enqueue(json(201, submission("COMPLETED", "WA", 40.0, "SavingsAccount phải kế thừa Account")));

        CheckResult result = check(Duration.ofSeconds(60));

        assertEquals(CheckStatus.WARNING, result.status());
        assertTrue(result.message().contains("WA"));
        assertTrue(result.message().contains("40/100"));
        assertTrue(result.message().contains("SavingsAccount phải kế thừa Account"));
    }

    @Test
    void lockedStageAndRevokedTokenUseSpecMessages() throws Exception {
        server.enqueue(json(409, "{\"status\":409,\"code\":\"STAGE_LOCKED\",\"detail\":\"Complete stage 1\"}"));
        assertTrue(check(Duration.ofSeconds(60)).message().contains("chưa mở"));

        server.enqueue(json(401, "{\"status\":401,\"code\":\"PAT_INVALID\",\"detail\":\"revoked\"}"));
        CheckResult auth = check(Duration.ofSeconds(60));
        assertEquals(CheckStatus.ERROR, auth.status());
        assertTrue(auth.message().contains("Token hết hiệu lực"));
    }

    @Test
    void stillJudgingAfterTimeoutPointsToTheWeb() throws Exception {
        server.enqueue(json(201, submission("PENDING", null, null, null)));
        server.enqueue(json(200, submission("JUDGING", null, null, null)));
        server.enqueue(json(200, submission("JUDGING", null, null, null)));

        CheckResult result = check(Duration.ofSeconds(2)); // 2 lần poll

        assertEquals(CheckStatus.WARNING, result.status());
        assertTrue(result.message().contains("đang chấm"));
    }

    @Test
    void missingSourcesAreReportedWithoutCallingTheServer() throws Exception {
        Files.delete(projectDir.resolve("src/main/java/bank/Bank.java"));
        Files.delete(projectDir.resolve("src/main/java/bank"));
        Files.delete(projectDir.resolve("src/main/java/Main.java"));
        Files.delete(projectDir.resolve("src/main/java"));

        assertEquals(CheckStatus.WARNING, check(Duration.ofSeconds(60)).status());
        assertEquals(0, server.getRequestCount());
    }

    // ------------------------------------------------------------------ Next stage

    private NextStageAction nextStage() {
        return new NextStageAction(this::client, ProjectOpener.noOp(),
                (p, md) -> openedMarkdown.add(md), (p, t, c, type) -> notifications.add(type));
    }

    private static String plan(String stage2Status, boolean stage2Published) {
        return "{\"id\":1,\"slug\":\"oop-voi-java\",\"title\":\"OOP\",\"status\":\"PUBLISHED\",\"stageCount\":2,\"doneStages\":1,"
                + "\"items\":[{\"id\":5,\"position\":2,\"kind\":\"PROJECT\",\"title\":\"Mini Bank\",\"done\":false,\"stages\":["
                + "{\"stageNo\":1,\"problemId\":101,\"slug\":\"mini-bank-stage-1\",\"title\":\"S1\",\"published\":true,\"status\":\"DONE\"},"
                + "{\"stageNo\":2,\"problemId\":102,\"slug\":\"mini-bank-stage-2\",\"title\":\"S2\",\"published\":" + stage2Published
                + ",\"status\":\"" + stage2Status + "\"}]}]}";
    }

    @Test
    void nextStageAddsNewFilesKeepsStudentCodeAndMovesManifestLast() throws Exception {
        server.enqueue(json(200, plan("NEVER", true)));
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/zip")
                .setBody(new okio.Buffer().write(zip(Map.of("Main.java", "// starter", "SavingsAccount.java", "class SavingsAccount {}")))));
        server.enqueue(json(200, "{\"id\":102,\"title\":\"S2\",\"slug\":\"mini-bank-stage-2\",\"description\":\"# Stage 2\"}"));

        NextStageAction.Outcome outcome = nextStage().executeSync(null, projectDir);

        assertEquals(NotificationType.INFORMATION, outcome.type(), outcome.message());
        assertEquals("public class Main {}", Files.readString(projectDir.resolve("src/main/java/Main.java")));
        assertTrue(Files.exists(projectDir.resolve("src/main/java/SavingsAccount.java")));
        assertEquals("# Stage 2", Files.readString(projectDir.resolve("stages/stage-2.md")));
        ProjectManifest manifest = ProjectManifest.readFromProject(projectDir);
        assertEquals(2, manifest.currentStage());
        assertEquals("mini-bank-stage-2", manifest.problemSlug());
        assertEquals(List.of(projectDir.resolve("stages/stage-2.md")), openedMarkdown);
    }

    @Test
    void lockedOrMaintainedNextStageIsNotOpened() throws Exception {
        server.enqueue(json(200, plan("LOCKED", true)));
        assertEquals(NotificationType.WARNING, nextStage().executeSync(null, projectDir).type());

        server.enqueue(json(200, plan("NEVER", false)));
        NextStageAction.Outcome maintenance = nextStage().executeSync(null, projectDir);
        assertTrue(maintenance.message().contains("đang được cập nhật"));

        assertEquals(1, ProjectManifest.readFromProject(projectDir).currentStage());
        assertEquals(2, server.getRequestCount()); // chỉ gọi plan, không tải starter
        assertTrue(openedMarkdown.isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** Tên entry của ZIP nằm trong phần multipart {@code file}. */
    private static List<String> zipEntries(byte[] multipart) throws IOException {
        String s = new String(multipart, StandardCharsets.ISO_8859_1);
        int start = s.indexOf("PK\u0003\u0004");
        List<String> names = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(multipart, start, multipart.length - start))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        return names;
    }
}
