package jsd.toiyeujava.minileetcode.plugin.workspace;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import jsd.toiyeujava.minileetcode.plugin.actions.NotificationPresenter;
import com.intellij.notification.NotificationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mở project của stage trong <b>cửa sổ IntelliJ mới</b>, sẵn sàng để code (lệnh "Open in IntelliJ" từ web):
 * project đã mở thì chỉ đưa cửa sổ lên trước; chưa có JDK thì chọn JDK ≥ 17 mới nhất đã cài; mở file .java đầu tiên.
 */
public final class NewWindowProjectOpener implements ProjectOpener {

    /** pom.xml mẫu biên dịch với release 17. */
    static final int MIN_JAVA = 17;
    private static final Pattern MAJOR = Pattern.compile("(?:^|\\D)(\\d{1,2})(?:\\.\\d+)*(?:\\D|$)");

    @Override
    public void openProject(@NotNull Path projectDirectory) {
        Project open = findOpenProject(projectDirectory);
        Project project = open != null ? open : ProjectUtil.openOrImport(projectDirectory, null, true);
        if (project == null) {
            throw new IllegalStateException("IntelliJ did not open " + projectDirectory);
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            ensureProjectSdk(project);
            openFirstSource(project, projectDirectory);
            ProjectUtil.focusProjectWindow(project, true);
        });
    }

    private static @Nullable Project findOpenProject(Path dir) {
        Path wanted = dir.toAbsolutePath().normalize();
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (!p.isDisposed() && p.getBasePath() != null && Path.of(p.getBasePath()).toAbsolutePath().normalize().equals(wanted)) {
                return p;
            }
        }
        return null;
    }

    private static void ensureProjectSdk(Project project) {
        ProjectRootManager roots = ProjectRootManager.getInstance(project);
        if (roots.getProjectSdk() != null) return;
        Optional<Sdk> jdk = Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
                .filter(s -> "JavaSDK".equals(s.getSdkType().getName()))
                .filter(s -> javaMajor(s.getVersionString()) >= MIN_JAVA)
                .max(Comparator.comparingInt(s -> javaMajor(s.getVersionString())));
        if (jdk.isPresent()) {
            WriteAction.run(() -> roots.setProjectSdk(jdk.get()));
        } else {
            NotificationPresenter.DEFAULT.showNotification(project, "Mini LeetCode",
                    "No JDK " + MIN_JAVA + "+ found. Open File › Project Structure › SDKs and download one to run the code.",
                    NotificationType.WARNING);
        }
    }

    /** "Oracle OpenJDK 21.0.2" ⇒ 21; "1.8.0_392" ⇒ 8; không đọc được ⇒ 0. */
    static int javaMajor(@Nullable String version) {
        if (version == null) return 0;
        String v = version.replaceFirst("(?<!\\d)1\\.(\\d)(?!\\d)", "$1");
        Matcher m = MAJOR.matcher(v);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static void openFirstSource(Project project, Path dir) {
        Path src = dir.resolve("src/main/java");
        if (!Files.isDirectory(src)) return;
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(f -> f.toString().endsWith(".java")).sorted().findFirst().ifPresent(f -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f);
                if (vf != null) FileEditorManager.getInstance(project).openFile(vf, false);
            });
        } catch (IOException ignored) {
            // không mở được file nguồn thì vẫn để người học tự mở
        }
    }
}
