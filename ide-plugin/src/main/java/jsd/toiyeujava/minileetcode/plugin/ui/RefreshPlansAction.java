package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.PlanDetail;
import jsd.toiyeujava.minileetcode.plugin.model.PlanSummary;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Toolbar and action-bar action that refreshes Study Plans and learning progress from the server.
 * Extends DumbAwareAction to allow triggering during project indexing.
 *
 * Workflow:
 * 1. Sets tree model to loading state (guarded by atomic in-flight tracking).
 * 2. Fetches study plan summaries on a background thread.
 * 3. Fetches detailed plan structures (items and stages) for each published plan.
 * 4. Dispatches tree updates cleanly to the EDT (or synchronized on tree root in headless mode).
 * 5. Handles authentication and API exceptions, reporting errors to both tree model and system notifications.
 */
public class RefreshPlansAction extends DumbAwareAction {

    public static final String NOTIFICATION_GROUP_ID = "Mini LeetCode";

    @FunctionalInterface
    public interface ApiClientFactory {
        ApiClient create(String serverUrl, String pat);
    }

    private StudyPlanTreeModel treeModel;
    private ApiClientFactory apiClientFactory;

    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final AtomicInteger inFlightRefreshes = new AtomicInteger(0);

    public RefreshPlansAction() {
        this(null, null);
    }

    public RefreshPlansAction(@Nullable StudyPlanTreeModel treeModel) {
        this(treeModel, null);
    }

    public RefreshPlansAction(@Nullable StudyPlanTreeModel treeModel, @Nullable ApiClientFactory apiClientFactory) {
        super("Refresh Study Plans", "Reload study plans and progress from server", AllIcons.Actions.Refresh);
        this.treeModel = treeModel;
        this.apiClientFactory = apiClientFactory != null
                ? apiClientFactory
                : (url, pat) -> new ApiClient(url, pat);
    }

    public void setTreeModel(@Nullable StudyPlanTreeModel treeModel) {
        this.treeModel = treeModel;
    }

    @Nullable
    public StudyPlanTreeModel getTreeModel() {
        return treeModel;
    }

    public void setApiClientFactory(@NotNull ApiClientFactory factory) {
        this.apiClientFactory = factory;
    }

    public boolean isRefreshing() {
        return isRefreshing.get();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        performRefresh(e.getProject());
    }

    /**
     * Executes the refresh workflow asynchronously on a background thread.
     */
    public void performRefresh(@Nullable Project project) {
        inFlightRefreshes.incrementAndGet();

        // Atomically transition to loading state only once when no refresh is currently active,
        // avoiding UI thrashing and caller thread collisions during concurrent invocations.
        if (isRefreshing.compareAndSet(false, true)) {
            if (treeModel != null) {
                try {
                    runOnEdt(treeModel::setLoading);
                } catch (Throwable ignored) {
                    // Defensive guard: never let UI dispatch fail caller threads
                }
            }
        }

        Application app = ApplicationManager.getApplication();
        if (app != null) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Refreshing Study Plans", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        indicator.setIndeterminate(true);
                        indicator.setText("Connecting to Mini LeetCode server...");
                        refreshPlansSync(indicator, project);
                    } finally {
                        if (inFlightRefreshes.decrementAndGet() <= 0) {
                            isRefreshing.set(false);
                        }
                    }
                }
            });
        } else {
            // Headless / non-IDEA fallback execution
            Thread bgThread = new Thread(() -> {
                try {
                    refreshPlansSync(null, project);
                } finally {
                    if (inFlightRefreshes.decrementAndGet() <= 0) {
                        isRefreshing.set(false);
                    }
                }
            }, "MiniLeetCode-RefreshPlans");
            bgThread.setDaemon(true);
            bgThread.start();
        }
    }

    /**
     * Synchronous implementation of the plan refresh workflow.
     * Can be invoked directly in unit test suites for deterministic verification.
     */
    public void refreshPlansSync(@Nullable ProgressIndicator indicator, @Nullable Project project) {
        isRefreshing.set(true);
        try {
            MiniLeetCodeSettingsState settings = MiniLeetCodeSettingsState.getInstance();
            String serverUrl = settings.getNormalizedServerUrl();
            String pat = MiniLeetCodePasswordSafe.getInstance().getPassword();

            if (serverUrl == null || serverUrl.isBlank()) {
                reportError(project, "Server URL is not configured. Please open Settings › Tools › Mini LeetCode.");
                return;
            }

            if (indicator != null) {
                indicator.setText("Fetching study plan list...");
            }

            ApiClient client = apiClientFactory.create(serverUrl, pat);
            List<PlanSummary> summaries = client.getStudyPlans();

            if (summaries == null || summaries.isEmpty()) {
                runOnEdt(() -> {
                    if (treeModel != null) {
                        treeModel.setPlans(List.of());
                    }
                });
                return;
            }

            List<PlanDetail> details = new ArrayList<>(summaries.size());
            int total = summaries.size();
            for (int i = 0; i < total; i++) {
                PlanSummary summary = summaries.get(i);
                if (indicator != null) {
                    indicator.setText("Loading study plan: " + summary.title());
                    indicator.setFraction((double) (i + 1) / total);
                }

                try {
                    PlanDetail detail = client.getStudyPlan(summary.slug());
                    if (detail != null) {
                        details.add(detail);
                    }
                } catch (AuthenticationException authEx) {
                    throw authEx; // token hết hạn giữa chừng: báo lỗi xác thực, không hiện plan rỗng
                } catch (MiniLeetCodeApiException apiEx) {
                    if (apiEx.getStatusCode() == 404) {
                        continue; // plan vừa bị gỡ / chuyển về DRAFT giữa hai lần gọi: bỏ khỏi cây
                    }
                    details.add(fallbackDetail(summary));
                } catch (Exception detailEx) {
                    // Lỗi mạng tạm thời: giữ plan với thông tin tóm tắt
                    details.add(fallbackDetail(summary));
                }
            }

            runOnEdt(() -> {
                if (treeModel != null) {
                    treeModel.setPlans(details);
                }
            });

        } catch (AuthenticationException e) {
            reportError(project, "Token hết hiệu lực, tạo token mới trên web rồi dán vào Settings › Tools › Mini LeetCode.");
            openSettings(project);
        } catch (MiniLeetCodeApiException e) {
            String msg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "API error (HTTP " + e.getStatusCode() + ")";
            reportError(project, "Failed to load study plans: " + msg);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            reportError(project, "Network error: " + msg);
        } finally {
            if (inFlightRefreshes.get() <= 0) {
                isRefreshing.set(false);
            }
        }
    }

    private static PlanDetail fallbackDetail(PlanSummary summary) {
        return new PlanDetail(summary.id(), summary.slug(), summary.title(), summary.description(), summary.status(),
                summary.stageCount(), summary.doneStages(), List.of());
    }

    private static void openSettings(@Nullable Project project) {
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            app.invokeLater(() -> com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeConfigurable.class));
        }
    }

    private void reportError(@Nullable Project project, @NotNull String errorMessage) {
        runOnEdt(() -> {
            if (treeModel != null) {
                treeModel.setError(errorMessage);
            }
        });

        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            Notification notification = new Notification(
                    NOTIFICATION_GROUP_ID,
                    "Mini LeetCode",
                    errorMessage,
                    NotificationType.ERROR
            );
            Notifications.Bus.notify(notification, project);
        }
    }

    private void runOnEdt(@NotNull Runnable runnable) {
        Application app = ApplicationManager.getApplication();
        if (app != null) {
            app.invokeLater(runnable);
        } else if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            runnable.run();
        }
    }
}
