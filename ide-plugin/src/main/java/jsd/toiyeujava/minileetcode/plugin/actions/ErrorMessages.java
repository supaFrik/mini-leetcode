package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeConfigurable;
import org.jetbrains.annotations.Nullable;

/** Hành động chung khi gặp lỗi xác thực (spec Error Handling: 401 ⇒ mở Settings). */
final class ErrorMessages {

    private ErrorMessages() {
    }

    static void openSettings(@Nullable Project project) {
        var app = ApplicationManager.getApplication();
        if (app == null || app.isHeadlessEnvironment()) {
            return;
        }
        app.invokeLater(() -> ShowSettingsUtil.getInstance().showSettingsDialog(project, MiniLeetCodeConfigurable.class));
    }
}
