package jsd.toiyeujava.minileetcode.plugin.actions;

import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of a stage check operation.
 *
 * @param status       overall result status
 * @param title        title for notification display
 * @param message      detailed notification content
 * @param manifest     parsed ProjectManifest, or null if manifest was unreadable
 * @param missingFiles list of expected project files that were absent on disk
 * @param warnings     list of diagnostic warnings or error details
 */
public record CheckResult(
        @NotNull CheckStatus status,
        @NotNull String title,
        @NotNull String message,
        @Nullable ProjectManifest manifest,
        @NotNull List<String> missingFiles,
        @NotNull List<String> warnings
) {
    public CheckResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(message, "message must not be null");
        missingFiles = (missingFiles == null) ? List.of() : List.copyOf(missingFiles);
        warnings = (warnings == null) ? List.of() : List.copyOf(warnings);
    }

    public boolean isSuccess() {
        return status == CheckStatus.SUCCESS;
    }

    public boolean hasWarnings() {
        return status == CheckStatus.WARNING || !warnings.isEmpty() || !missingFiles.isEmpty();
    }
}
