package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import jsd.toiyeujava.minileetcode.plugin.model.StageStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;

/**
 * Central repository of icons used throughout the Mini LeetCode plugin.
 * Provides public static final constants for all 4 StageStatus values, ToolWindow, and Refresh actions.
 *
 * Implements a headless-safe fallback mechanism so unit tests running in a headless JVM
 * (without full IntelliJ GUI runtime) do not crash with ExceptionInInitializerError.
 */
public final class MiniLeetCodeIcons {

    private MiniLeetCodeIcons() {
        // Prevent instantiation
    }

    public static final Icon LOCKED = loadSafe("/icons/locked.svg");
    public static final Icon NEVER = loadSafe("/icons/never.svg");
    public static final Icon IN_PROGRESS = loadSafe("/icons/in_progress.svg");
    public static final Icon DONE = loadSafe("/icons/done.svg");
    public static final Icon TOOL_WINDOW = loadSafe("/icons/toolWindow.svg");
    public static final Icon REFRESH = loadSafe("/icons/refresh.svg", loadAllIconsRefresh());

    /**
     * Maps a {@link StageStatus} to its corresponding 16x16 status icon.
     * Defaults to {@link #NEVER} if status is null.
     */
    @NotNull
    public static Icon getStatusIcon(@Nullable StageStatus status) {
        if (status == null) {
            return NEVER;
        }
        return switch (status) {
            case LOCKED -> LOCKED;
            case NEVER -> NEVER;
            case IN_PROGRESS -> IN_PROGRESS;
            case DONE -> DONE;
        };
    }

    /**
     * Safely loads an icon from the classpath. If IconLoader fails or throws any Throwable
     * (e.g. in headless testing), returns the specified default fallback or a {@link HeadlessSafeIcon}.
     */
    @NotNull
    public static Icon loadSafe(@NotNull String path) {
        return loadSafe(path, null);
    }

    @NotNull
    public static Icon loadSafe(@NotNull String path, @Nullable Icon defaultFallback) {
        try {
            return IconLoader.getIcon(path, MiniLeetCodeIcons.class.getClassLoader());
        } catch (Throwable t) {
            if (defaultFallback != null) {
                return defaultFallback;
            }
            return new HeadlessSafeIcon(16, 16, path);
        }
    }

    private static Icon loadAllIconsRefresh() {
        try {
            return AllIcons.Actions.Refresh;
        } catch (Throwable ignored) {
            return new HeadlessSafeIcon(16, 16, "AllIcons.Actions.Refresh");
        }
    }

    /**
     * Headless-safe dummy icon providing 16x16 dimensions and a no-op paint implementation.
     */
    public static class HeadlessSafeIcon implements Icon {
        private final int width;
        private final int height;
        private final String description;

        public HeadlessSafeIcon(int width, int height, @NotNull String description) {
            this.width = width;
            this.height = height;
            this.description = description;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            // No-op in headless environment
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @NotNull
        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "HeadlessSafeIcon[" + description + " (" + width + "x" + height + ")]";
        }
    }
}
