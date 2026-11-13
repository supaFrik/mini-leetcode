package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decoupled presenter interface for displaying IntelliJ balloon notifications.
 * Allows pure headless JUnit testing without IDE GUI or Application dependencies.
 */
@FunctionalInterface
public interface NotificationPresenter {

    String NOTIFICATION_GROUP_ID = "Mini LeetCode";

    void showNotification(
            @Nullable Project project,
            @NotNull String title,
            @NotNull String content,
            @NotNull NotificationType type
    );

    /**
     * Default production presenter dispatching to NotificationGroupManager.
     */
    NotificationPresenter DEFAULT = (project, title, content, type) -> {
        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isHeadlessEnvironment()) {
            try {
                NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
                if (group != null) {
                    group.createNotification(title, content, type).notify(project);
                    return;
                }
            } catch (Throwable ignored) {
            }

            // Fallback for older or customized environments
            try {
                Notification notification = new Notification(NOTIFICATION_GROUP_ID, title, content, type);
                Notifications.Bus.notify(notification, project);
            } catch (Throwable ignored) {
            }
        }
    };
}
