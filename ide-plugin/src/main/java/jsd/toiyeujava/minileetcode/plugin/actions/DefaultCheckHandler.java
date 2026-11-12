package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link CheckHandler} for Slice 4.
 * Validates local file presence (pom.xml, stages/stage-N.md, src/main/java)
 * and formats the stage status notification.
 */
public class DefaultCheckHandler implements CheckHandler {

    private final Path projectDirOverride;

    public DefaultCheckHandler() {
        this(null);
    }

    public DefaultCheckHandler(@Nullable Path projectDirOverride) {
        this.projectDirOverride = projectDirOverride;
    }

    @Override
    public CheckResult check(@Nullable Project project, @NotNull ProjectManifest manifest) {
        Path projectDir = resolveProjectDir(project);
        return check(project, projectDir, manifest);
    }

    @Override
    public CheckResult check(@Nullable Project project, @NotNull Path projectDir, @NotNull ProjectManifest manifest) {
        List<String> missingFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Validate pom.xml
        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.isRegularFile(pomFile)) {
            missingFiles.add("pom.xml");
        }

        // 2. Validate stages/stage-N.md
        int stage = manifest.currentStage();
        Path stageMd = projectDir.resolve("stages").resolve("stage-" + stage + ".md");
        if (!Files.isRegularFile(stageMd)) {
            missingFiles.add("stages/stage-" + stage + ".md");
        }

        // 3. Validate src/main/java
        Path srcMainJava = projectDir.resolve("src/main/java");
        if (!Files.isDirectory(srcMainJava)) {
            missingFiles.add("src/main/java/");
        }

        // Title specified in requirements
        String title = "Mini LeetCode — Stage Status";

        // If files are missing, produce a WARNING result
        if (!missingFiles.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Stage ").append(stage).append(" (").append(manifest.problemSlug()).append(") has missing files:\n");
            for (String file : missingFiles) {
                sb.append(" • ").append(file).append("\n");
            }
            sb.append("Please verify project layout before submission.");

            return new CheckResult(
                    CheckStatus.WARNING,
                    title,
                    sb.toString().trim(),
                    manifest,
                    missingFiles,
                    warnings
            );
        }

        // All expected files present -> produce SUCCESS result
        String content = "Stage " + stage + " (" + manifest.problemSlug() + "): Ready for local check/submission\n"
                + "Plan: " + manifest.plan() + " | Item: " + manifest.item();

        return new CheckResult(
                CheckStatus.SUCCESS,
                title,
                content,
                manifest,
                List.of(),
                List.of()
        );
    }

    private Path resolveProjectDir(@Nullable Project project) {
        if (projectDirOverride != null) {
            return projectDirOverride;
        }
        if (project != null && project.getBasePath() != null) {
            return Path.of(project.getBasePath());
        }
        return Path.of(".");
    }
}
