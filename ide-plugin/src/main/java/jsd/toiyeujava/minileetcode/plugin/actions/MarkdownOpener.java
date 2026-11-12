package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * Functional interface for opening problem markdown files with IntelliJ native split preview (PL-4).
 */
@FunctionalInterface
public interface MarkdownOpener {

    void openMarkdown(@Nullable Project project, @NotNull Path stageMarkdownPath);

    MarkdownOpener DEFAULT = (project, stageMarkdownPath) -> {
        Runnable openTask = () -> {
            if (project == null || project.isDisposed()) {
                return;
            }
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(stageMarkdownPath.toString());
            if (vf == null) {
                return;
            }
            FileEditor[] editors = FileEditorManager.getInstance(project).openFile(vf, true);
            for (FileEditor editor : editors) {
                if (editor instanceof TextEditorWithPreview textEditorWithPreview) {
                    textEditorWithPreview.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW);
                }
            }
        };

        Application app = ApplicationManager.getApplication();
        if (app != null) {
            app.invokeLater(openTask);
        } else if (SwingUtilities.isEventDispatchThread()) {
            openTask.run();
        } else {
            openTask.run();
        }
    };
}
