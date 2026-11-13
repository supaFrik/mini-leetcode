package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.ItemDto;
import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.model.StageDto;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectOpener;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceInitRequest;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceResult;
import jsd.toiyeujava.minileetcode.plugin.workspace.WorkspaceService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * PL-6 (specs/study-plan.spec.md): sau khi stage hiện tại AC, tải stage tiếp theo vào CÙNG thư mục project.
 * WorkspaceService chỉ tạo file chưa có (không ghi đè code học viên) và ghi .mlc/project.yaml sau cùng.
 */
public class NextStageAction extends DumbAwareAction {

    /** Kết quả cho UI / test: {@code stageMarkdown} null khi không mở stage nào. */
    public record Outcome(@NotNull NotificationType type, @NotNull String message, @Nullable Path stageMarkdown) {}

    private final SubmitCheckHandler.ClientSupplier clientSupplier;
    private final ProjectOpener projectOpener;
    private final MarkdownOpener markdownOpener;
    private final NotificationPresenter notificationPresenter;

    public NextStageAction() {
        this(null, ProjectOpener.noOp(), MarkdownOpener.DEFAULT, NotificationPresenter.DEFAULT);
    }

    public NextStageAction(@Nullable SubmitCheckHandler.ClientSupplier clientSupplier, @NotNull ProjectOpener projectOpener,
                           @NotNull MarkdownOpener markdownOpener, @NotNull NotificationPresenter notificationPresenter) {
        super("Next Stage", "Download the next stage of this project (keeps your code)", null);
        this.clientSupplier = clientSupplier;
        this.projectOpener = projectOpener;
        this.markdownOpener = markdownOpener;
        this.notificationPresenter = notificationPresenter;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enabled = project != null && !project.isDisposed()
                && CheckAction.findMlcRoot(project, e.getData(CommonDataKeys.VIRTUAL_FILE)).isPresent();
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        Optional<Path> root = CheckAction.findMlcRoot(project, e.getData(CommonDataKeys.VIRTUAL_FILE));
        if (root.isEmpty()) {
            return;
        }
        var app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Mini LeetCode: Next Stage", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Downloading next stage...");
                    executeSync(project, root.get());
                }
            });
        } else {
            executeSync(project, root.get());
        }
    }

    /** Đồng bộ, gọi trực tiếp được trong test. Luôn hiện một thông báo và trả về kết quả đó. */
    public Outcome executeSync(@Nullable Project project, @NotNull Path projectDir) {
        Outcome outcome = run(project, projectDir);
        notificationPresenter.showNotification(project, "Mini LeetCode — Next Stage", outcome.message(), outcome.type());
        if (outcome.stageMarkdown() != null) {
            markdownOpener.openMarkdown(project, outcome.stageMarkdown());
        }
        return outcome;
    }

    private Outcome run(@Nullable Project project, @NotNull Path projectDir) {
        try {
            ProjectManifest manifest = ProjectManifest.readFromProject(projectDir);
            ApiClient client = clientSupplier != null ? clientSupplier.get() : SubmitCheckHandler.clientFromSettings();
            PlanDetail plan = client.getStudyPlan(manifest.plan());
            int position = manifest.itemAsInt();
            ItemDto item = plan.items().stream().filter(i -> i.position() == position).findFirst().orElse(null);
            if (item == null) {
                return new Outcome(NotificationType.WARNING, "Không còn tìm thấy mục này trong plan, bấm Refresh.", null);
            }
            int nextNo = manifest.currentStage() + 1;
            StageDto next = item.stages().stream().filter(s -> s.stageNo() == nextNo).findFirst().orElse(null);
            if (next == null) {
                return new Outcome(NotificationType.INFORMATION,
                        "Đã ở stage cuối của \"" + item.title() + "\". Hoàn thành project khi stage này AC.", null);
            }
            if (next.status() == StageStatus.LOCKED) {
                return new Outcome(NotificationType.WARNING,
                        "Stage " + nextNo + " chưa mở: bấm Check để stage " + manifest.currentStage() + " được AC trước.", null);
            }
            if (!next.published()) {
                return new Outcome(NotificationType.WARNING, "Stage " + nextNo + " đang được cập nhật, thử lại sau.", null);
            }
            WorkspaceResult result = new WorkspaceService(client, projectOpener).initializeProject(new WorkspaceInitRequest(
                    projectDir, manifest.plan(), manifest.item(), nextNo, next.slug(), next.problemId()));
            return new Outcome(NotificationType.INFORMATION,
                    "Đã mở stage " + nextNo + ": " + next.title() + " (code của bạn được giữ nguyên).", result.stageMarkdownPath());
        } catch (Exception e) {
            if (OpenStageAction.findCause(e, AuthenticationException.class) != null) {
                ErrorMessages.openSettings(project);
                return new Outcome(NotificationType.ERROR,
                        "Token hết hiệu lực, tạo token mới trên web rồi dán vào Settings › Tools › Mini LeetCode.", null);
            }
            MiniLeetCodeApiException api = OpenStageAction.findCause(e, MiniLeetCodeApiException.class);
            if (api != null && api.getStatusCode() == 404) {
                return new Outcome(NotificationType.WARNING, "Plan hoặc stage không còn công khai, bấm Refresh.", null);
            }
            return new Outcome(NotificationType.ERROR,
                    "Không mở được stage tiếp theo: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), null);
        }
    }
}
