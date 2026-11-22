package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.SubmissionDto;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Editor and toolbar action for running public sample test cases (FR-RN-01).
 * Shortcut: Ctrl+Alt+R.
 */
public class RunStageAction extends DumbAwareAction {

    private final SubmitCheckHandler.ClientSupplier clientSupplier;
    private final NotificationPresenter notificationPresenter;

    public RunStageAction() {
        this(null, null);
    }

    public RunStageAction(@Nullable SubmitCheckHandler.ClientSupplier clientSupplier,
                          @Nullable NotificationPresenter notificationPresenter) {
        super("Run Samples", "Run public test cases against sample inputs", AllIcons.Actions.Execute);
        this.clientSupplier = clientSupplier;
        this.notificationPresenter = (notificationPresenter != null) ? notificationPresenter : NotificationPresenter.DEFAULT;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.isDisposed()) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean isMlc = CheckAction.findMlcRoot(project, file).isPresent();
        e.getPresentation().setEnabledAndVisible(isMlc);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Optional<Path> mlcRootOpt = CheckAction.findMlcRoot(project, file);
        if (mlcRootOpt.isEmpty()) {
            notificationPresenter.showNotification(
                    project,
                    "Mini LeetCode — Run",
                    "Not a Mini LeetCode project. Could not locate .mlc/project.yaml.",
                    NotificationType.WARNING
            );
            return;
        }

        Path projectDir = mlcRootOpt.get();
        executeRunAsync(project, projectDir);
    }

    public void executeRunAsync(@NotNull Project project, @NotNull Path projectDir) {
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Mini LeetCode: Running Samples", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Evaluating sample test cases...");
                    executeRunSync(project, projectDir);
                }
            });
        } else {
            executeRunSync(project, projectDir);
        }
    }

    public SubmissionDto executeRunSync(@Nullable Project project, @NotNull Path projectDir) {
        try {
            // 1. Save all open documents
            Application app = ApplicationManager.getApplication();
            if (app != null) {
                app.invokeAndWait(FileDocumentManager.getInstance()::saveAllDocuments);
            }

            // 2. Validate and read manifest
            ProjectManifest manifest = ProjectManifest.readFromProject(projectDir);

            // 3. Zip sources
            Path srcMainJava = projectDir.resolve("src/main/java");
            if (!Files.isDirectory(srcMainJava)) {
                notificationPresenter.showNotification(project, "Mini LeetCode — Run",
                        "src/main/java does not exist.", NotificationType.ERROR);
                return null;
            }

            byte[] zipBytes = SubmitCheckHandler.zipSources(srcMainJava);

            // 4. Client
            ApiClient client = (clientSupplier != null) ? clientSupplier.get() : SubmitCheckHandler.clientFromSettings();
            Long problemId = manifest.problemId() != null ? manifest.problemId()
                    : client.getProblem(manifest.problemSlug()).id();

            // 5. Submit run request
            SubmissionDto submitted = client.runStage(problemId, zipBytes);

            // 6. Poll for completion
            SubmissionDto current = submitted;
            long waited = 0;
            while (!current.isFinished() && waited < 60_000) {
                Thread.sleep(1000);
                waited += 1000;
                current = client.getSubmission(submitted.id());
            }

            // 7. Present notification
            boolean ac = current.isAccepted();
            String verdict = current.verdict() != null ? current.verdict() : current.status();
            String passInfo = (current.totalTestCases() != null && current.totalTestCases() > 0)
                    ? " (" + (current.passTestCases() != null ? current.passTestCases() : 0) + "/" + current.totalTestCases() + " passed)"
                    : "";

            NotificationType type = ac ? NotificationType.INFORMATION : NotificationType.WARNING;
            String msg = "Stage " + manifest.currentStage() + " [Run Samples]: " + verdict + passInfo;
            if (!ac && current.errorMessage() != null && !current.errorMessage().isBlank()) {
                msg += "\n" + current.errorMessage();
            }

            notificationPresenter.showNotification(project, "Mini LeetCode — Run", msg, type);
            return current;

        } catch (AuthenticationException ae) {
            notificationPresenter.showNotification(project, "Mini LeetCode — Run",
                    "Session expired. Please update token in Settings.", NotificationType.ERROR);
            ErrorMessages.openSettings(project);
            return null;
        } catch (MiniLeetCodeApiException apiEx) {
            notificationPresenter.showNotification(project, "Mini LeetCode — Run",
                    "API error: " + apiEx.getMessage(), NotificationType.ERROR);
            return null;
        } catch (IOException | IllegalArgumentException ex) {
            notificationPresenter.showNotification(project, "Mini LeetCode — Run",
                    "Source error: " + ex.getMessage(), NotificationType.ERROR);
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            notificationPresenter.showNotification(project, "Mini LeetCode — Run",
                    "Run cancelled.", NotificationType.WARNING);
            return null;
        }
    }
}
