package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Functional interface for folder selection, decoupling modal file choosers for headless testability.
 */
@FunctionalInterface
public interface DirectoryChooser {

    @Nullable
    Path chooseDirectory(@Nullable Project project, @Nullable Path defaultDirectory);

    default @Nullable Path chooseDirectory(@Nullable Project project, @Nullable String title) {
        return chooseDirectory(project, (Path) null);
    }

    DirectoryChooser DEFAULT = (project, defaultDirectory) -> {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Project Destination Directory")
                .withDescription("Choose a local folder to initialize the study plan workspace");

        VirtualFile toSelect = defaultDirectory != null
                ? VfsUtil.findFileByIoFile(defaultDirectory.toFile(), true)
                : null;

        VirtualFile chosen = FileChooser.chooseFile(descriptor, project, toSelect);
        return chosen != null ? chosen.toNioPath() : null;
    };
}
