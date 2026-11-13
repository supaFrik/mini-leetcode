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
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Editor and toolbar action for checking current Mini LeetCode stage status.
 * <p>
 * Key behaviors:
 * <ul>
 *     <li>Registered in {@code EditorPopupMenu}, {@code EditorTabPopupMenu}, and {@code MainToolBar}.</li>
 *     <li>Overrides {@link #getActionUpdateThread()} returning {@link ActionUpdateThread#BGT}.</li>
 *     <li>Visible and enabled ONLY when an active {@code .mlc/project.yaml} exists in project root or parents.</li>
 *     <li>Reads and validates {@link ProjectManifest} metadata.</li>
 *     <li>Displays balloon notification via {@link NotificationPresenter} with current stage status.</li>
 *     <li>Pluggable {@link CheckHandler} seam for Slice 5 submission logic integration.</li>
 * </ul>
 */
public class CheckAction extends DumbAwareAction {

    private final CheckHandler checkHandler;
    private final NotificationPresenter notificationPresenter;

    public CheckAction() {
        this(null, null);
    }

    public CheckAction(@Nullable CheckHandler checkHandler) {
        this(checkHandler, null);
    }

    public CheckAction(@Nullable CheckHandler checkHandler, @Nullable NotificationPresenter notificationPresenter) {
        super("Check Stage", "Check current stage status and validate project files", AllIcons.Actions.Execute);
        this.checkHandler = (checkHandler != null) ? checkHandler : CheckHandlerRegistry.getHandler();
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
        boolean isMlc = findMlcRoot(project, file).isPresent();
        e.getPresentation().setEnabledAndVisible(isMlc);
        if (isMlc) {
            e.getPresentation().setText("Check Stage");
            e.getPresentation().setDescription("Check Mini LeetCode stage status and validate files");
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Optional<Path> mlcRootOpt = findMlcRoot(project, file);
        if (mlcRootOpt.isEmpty()) {
            notificationPresenter.showNotification(
                    project,
                    "Mini LeetCode — Stage Status",
                    "Not a Mini LeetCode project. Could not locate .mlc/project.yaml in project root or parents.",
                    NotificationType.WARNING
            );
            return;
        }

        Path projectDir = mlcRootOpt.get();
        executeCheckAsync(project, projectDir);
    }

    /**
     * Executes check asynchronously on background thread.
     */
    public void executeCheckAsync(@NotNull Project project, @NotNull Path projectDir) {
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Mini LeetCode: Checking Stage", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Validating stage files and metadata...");
                    executeCheckSync(project, projectDir);
                }
            });
        } else {
            executeCheckSync(project, projectDir);
        }
    }

    /**
     * Synchronous check execution. Directly callable by headless unit tests.
     */
    public CheckResult executeCheckSync(@Nullable Project project, @NotNull Path projectDir) {
        Objects.requireNonNull(projectDir, "projectDir must not be null");

        // 1. Read ProjectManifest
        ProjectManifest manifest;
        try {
            manifest = ProjectManifest.readFromProject(projectDir);
        } catch (NoSuchFileException e) {
            CheckResult errorResult = new CheckResult(
                    CheckStatus.ERROR,
                    "Mini LeetCode — Stage Status",
                    "Missing metadata file: .mlc/project.yaml not found at " + projectDir,
                    null,
                    List.of(".mlc/project.yaml"),
                    List.of("File not found: " + e.getMessage())
            );
            notificationPresenter.showNotification(project, errorResult.title(), errorResult.message(), NotificationType.ERROR);
            return errorResult;
        } catch (Exception e) {
            CheckResult errorResult = new CheckResult(
                    CheckStatus.ERROR,
                    "Mini LeetCode — Stage Status",
                    "Corrupted .mlc/project.yaml: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    null,
                    List.of(".mlc/project.yaml"),
                    List.of(e.getMessage() != null ? e.getMessage() : "")
            );
            notificationPresenter.showNotification(project, errorResult.title(), errorResult.message(), NotificationType.ERROR);
            return errorResult;
        }

        // 2. Delegate to CheckHandler seam
        CheckResult result = checkHandler.check(project, projectDir, manifest);

        // 3. Dispatch notification balloon
        NotificationType notifType = switch (result.status()) {
            case SUCCESS -> NotificationType.INFORMATION;
            case WARNING -> NotificationType.WARNING;
            case ERROR, NOT_MLC_PROJECT -> NotificationType.ERROR;
        };
        notificationPresenter.showNotification(project, result.title(), result.message(), notifType);

        return result;
    }

    /**
     * Resolves the Mini LeetCode project root by traversing upwards from active file or project base path.
     *
     * @param project    IntelliJ project
     * @param activeFile currently focused file or null
     * @return Optional containing project root path if .mlc/project.yaml exists
     */
    public static Optional<Path> findMlcRoot(@Nullable Project project, @Nullable VirtualFile activeFile) {
        if (activeFile != null) {
            try {
                if (activeFile.isInLocalFileSystem()) {
                    Path p = activeFile.toNioPath();
                    Path current = Files.isDirectory(p) ? p : p.getParent();
                    while (current != null) {
                        if (ProjectManifest.existsIn(current)) {
                            return Optional.of(current);
                        }
                        current = current.getParent();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (project != null && project.getBasePath() != null) {
            try {
                Path current = Path.of(project.getBasePath());
                while (current != null) {
                    if (ProjectManifest.existsIn(current)) {
                        return Optional.of(current);
                    }
                    current = current.getParent();
                }
            } catch (Exception ignored) {
            }
        }

        return Optional.empty();
    }
}
