package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.SubmissionDto;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PL-5 (specs/study-plan.spec.md): Check = nén src/main/java/**.java ⇒ nộp chính thức ⇒ poll kết quả ⇒ hiển thị.
 * Chỉ verdict của server được tính; plugin không chạy test trên máy.
 */
public class SubmitCheckHandler implements CheckHandler {

    static final String TITLE = "Mini LeetCode — Check";
    static final int MAX_FILES = 100; // cùng giới hạn số file nộp của backend

    /** Server URL + PAT hiện tại; tách ra để test không cần IntelliJ services. */
    public interface ClientSupplier {
        ApiClient get();
    }

    /** Chờ giữa hai lần poll; test truyền vào bản không ngủ. */
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private final DefaultCheckHandler fileCheck;
    private final ClientSupplier clientSupplier;
    private final Sleeper sleeper;
    private final Duration pollInterval;
    private final Duration pollTimeout;

    public SubmitCheckHandler() {
        this(new DefaultCheckHandler(), SubmitCheckHandler::clientFromSettings,
                d -> Thread.sleep(d.toMillis()), Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    public SubmitCheckHandler(@NotNull DefaultCheckHandler fileCheck, @NotNull ClientSupplier clientSupplier,
                              @NotNull Sleeper sleeper, @NotNull Duration pollInterval, @NotNull Duration pollTimeout) {
        this.fileCheck = fileCheck;
        this.clientSupplier = clientSupplier;
        this.sleeper = sleeper;
        this.pollInterval = pollInterval;
        this.pollTimeout = pollTimeout;
    }

    @Override
    public CheckResult check(@Nullable Project project, @NotNull ProjectManifest manifest) {
        Path dir = project != null && project.getBasePath() != null ? Path.of(project.getBasePath()) : Path.of(".");
        return check(project, dir, manifest);
    }

    @Override
    public CheckResult check(@Nullable Project project, @NotNull Path projectDir, @NotNull ProjectManifest manifest) {
        CheckResult files = fileCheck.check(project, projectDir, manifest);
        if (files.status() != CheckStatus.SUCCESS) {
            return files; // thiếu pom.xml / đề / src/main/java: báo trước khi nộp
        }
        int stage = manifest.currentStage();
        try {
            byte[] zip = zipSources(projectDir.resolve("src/main/java"));
            ApiClient client = clientSupplier.get();
            Long problemId = manifest.problemId() != null ? manifest.problemId()
                    : client.getProblem(manifest.problemSlug()).id();
            SubmissionDto submitted = client.submitStage(problemId, zip);
            SubmissionDto result = poll(client, submitted);
            return toResult(manifest, result);
        } catch (AuthenticationException e) {
            ErrorMessages.openSettings(project);
            return result(CheckStatus.ERROR, manifest,
                    "Personal Access Token is invalid or expired. Please generate a new token on the web and configure it in Settings › Tools › Mini LeetCode. (Token hết hiệu lực)");
        } catch (MiniLeetCodeApiException e) {
            if (e.getStatusCode() == 409 && "STAGE_LOCKED".equals(e.getCode())) {
                return result(CheckStatus.WARNING, manifest,
                        "Stage " + stage + " is locked: please complete stage " + (stage - 1) + " first. (chưa mở)");
            }
            if (e.getStatusCode() == 403) {
                return result(CheckStatus.ERROR, manifest, "This operation can only be performed on the web.");
            }
            if (e.getStatusCode() == 404) {
                return result(CheckStatus.WARNING, manifest, "This stage is being updated. Please try again later.");
            }
            return result(CheckStatus.ERROR, manifest, "Submission failed: " + e.getMessage());
        } catch (IOException | IllegalArgumentException e) {
            return result(CheckStatus.ERROR, manifest, "Cannot read source code: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(CheckStatus.WARNING, manifest, "Waiting for results cancelled. Check results on the web.");
        }
    }

    private SubmissionDto poll(ApiClient client, SubmissionDto submitted) throws InterruptedException {
        SubmissionDto current = submitted;
        long waitedMs = 0;
        while (!current.isFinished() && waitedMs < pollTimeout.toMillis()) {
            sleeper.sleep(pollInterval);
            waitedMs += pollInterval.toMillis();
            current = client.getSubmission(submitted.id());
        }
        return current;
    }

    private CheckResult toResult(ProjectManifest manifest, SubmissionDto s) {
        int stage = manifest.currentStage();
        if (!s.isFinished()) {
            return result(CheckStatus.WARNING, manifest,
                    "Stage " + stage + ": grading in progress (submission #" + s.id() + "). Check results on the web. (đang chấm)");
        }
        String score = s.score() != null ? String.format(" — %.0f/100", s.score()) : "";
        String tests = s.totalTestCases() != null && s.totalTestCases() > 0
                ? " — testcase " + s.passTestCases() + "/" + s.totalTestCases() : "";
        if (s.isAccepted()) {
            return result(CheckStatus.SUCCESS, manifest, "Stage " + stage + ": PASSED (AC" + score + ")" + tests
                    + ". Select Mini LeetCode › Next Stage to proceed to the next stage.");
        }
        String verdict = s.verdict() != null ? s.verdict() : s.status();
        String detail = s.errorMessage() != null && !s.errorMessage().isBlank()
                ? "\n" + firstLines(s.errorMessage(), 8) : "";
        return result(CheckStatus.WARNING, manifest, "Stage " + stage + ": " + verdict + score + tests + detail);
    }

    /** ZIP các file .java dưới src/main/java, giữ đường dẫn tương đối (package). */
    public static byte[] zipSources(Path sourceRoot) throws IOException {
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            sources = walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("src/main/java does not contain any .java files");
        }
        if (sources.size() > MAX_FILES) {
            throw new IllegalArgumentException("Exceeded maximum limit of " + MAX_FILES + " .java files in src/main/java");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path p : sources) {
                zip.putNextEntry(new ZipEntry(sourceRoot.relativize(p).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(p));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String firstLines(String text, int max) {
        String[] lines = text.split("\\R");
        return String.join("\n", java.util.Arrays.copyOf(lines, Math.min(lines.length, max)))
                + (lines.length > max ? "\n…" : "");
    }

    private static CheckResult result(CheckStatus status, ProjectManifest manifest, String message) {
        return new CheckResult(status, TITLE, message, manifest, List.of(), List.of());
    }

    static ApiClient clientFromSettings() {
        String url = MiniLeetCodeSettingsState.getInstance().getNormalizedServerUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Server URL is not configured. Please open Settings › Tools › Mini LeetCode.");
        }
        return new ApiClient(url, MiniLeetCodePasswordSafe.getInstance().getPassword());
    }
}
