package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import jsd.toiyeujava.minileetcode.plugin.runner.SampleInputResolver;
import jsd.toiyeujava.minileetcode.plugin.runner.SanityRunResult;
import jsd.toiyeujava.minileetcode.plugin.runner.SanityRunner;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Editor and Toolbar Action executing local sanity run for main() with sample input (Feature 15).
 * Validates presence of .mlc/project.yaml, runs asynchronously via Task.Backgroundable without blocking EDT,
 * and displays an IntelliJ balloon notification summarizing the run outcome.
 */
public class SanityRunAction extends DumbAwareAction {

    private final SanityRunner sanityRunner;
    private final NotificationPresenter notificationPresenter;

    public SanityRunAction() {
        this(new SanityRunner(), null);
    }

    public SanityRunAction(@Nullable SanityRunner sanityRunner) {
        this(sanityRunner, null);
    }

    public SanityRunAction(@Nullable SanityRunner sanityRunner, @Nullable NotificationPresenter notificationPresenter) {
        super("Run Local Sanity Check", "Run project main() with sample input before submission", AllIcons.Actions.Execute);
        this.sanityRunner = (sanityRunner != null) ? sanityRunner : new SanityRunner();
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
        if (isMlc) {
            e.getPresentation().setText("Run Local Sanity Check");
            e.getPresentation().setDescription("Execute main() with sample input for sanity checking");
        }
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
                    "Not a Mini LeetCode Project",
                    "Missing .mlc/project.yaml in project root or parent directories.",
                    NotificationType.WARNING
            );
            return;
        }

        Path projectRoot = mlcRootOpt.get();
        executeSanityRunAsync(project, projectRoot);
    }

    /**
     * Executes sanity run asynchronously on background thread.
     */
    public void executeSanityRunAsync(@NotNull Project project, @NotNull Path projectRoot) {
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Running Local Sanity Check", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("Compiling and executing main()...");
                    executeSanityRunSync(project, projectRoot);
                }
            });
        } else {
            executeSanityRunSync(project, projectRoot);
        }
    }

    /**
     * Synchronous sanity run execution. Directly callable by headless unit tests.
     */
    public SanityRunResult executeSanityRunSync(@Nullable Project project, @NotNull Path projectRoot) {
        ProjectManifest manifest;
        try {
            manifest = ProjectManifest.readFromProject(projectRoot);
        } catch (Exception ex) {
            notificationPresenter.showNotification(
                    project,
                    "Manifest Error",
                    "Failed to parse .mlc/project.yaml: " + ex.getMessage(),
                    NotificationType.ERROR
            );
            return SanityRunResult.error(1, "Failed to parse manifest: " + ex.getMessage());
        }

        String sampleInput = SampleInputResolver.resolveSampleInput(projectRoot, manifest.currentStage());
        SanityRunResult result = sanityRunner.run(projectRoot, sampleInput);
        handleResult(project, result, manifest);
        return result;
    }

    public void handleResult(@Nullable Project project, @NotNull SanityRunResult result, @NotNull ProjectManifest manifest) {
        if (result.timedOut()) {
            notificationPresenter.showNotification(
                    project,
                    "Execution Timed Out",
                    "Execution of main() exceeded " + sanityRunner.getTimeout().toSeconds() + "s limit.\n" +
                            "Check for infinite loops or waiting indefinitely on Scanner input.",
                    NotificationType.WARNING
            );
        } else if (result.exitCode() != 0) {
            String details = truncate(result.stderr().isBlank() ? result.stdout() : result.stderr(), 500);
            notificationPresenter.showNotification(
                    project,
                    "Execution Failed (Exit Code " + result.exitCode() + ")",
                    details.isBlank() ? "Process terminated with exit code " + result.exitCode() : details,
                    NotificationType.ERROR
            );
        } else {
            String preview = truncate(result.stdout(), 300);
            String msg = "Stage " + manifest.currentStage() + " (" + manifest.problemSlug() + ") finished with exit code 0.\n" +
                    (preview.isBlank() ? "(No output)" : "Output:\n" + preview);
            notificationPresenter.showNotification(
                    project,
                    "Sanity Run Passed",
                    msg,
                    NotificationType.INFORMATION
            );
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String trimmed = text.trim();
        return (trimmed.length() <= maxLen) ? trimmed : trimmed.substring(0, maxLen) + "...";
    }
}
