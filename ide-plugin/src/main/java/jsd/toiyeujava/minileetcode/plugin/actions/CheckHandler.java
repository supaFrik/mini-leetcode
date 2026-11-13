package jsd.toiyeujava.minileetcode.plugin.actions;

import com.intellij.openapi.project.Project;
import jsd.toiyeujava.minileetcode.plugin.workspace.ProjectManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Strategy interface for checking a Mini LeetCode project stage.
 * In Slice 4, validates local project integrity and displays current stage status.
 * In Slice 5, handles archive packaging, server submission, and polling.
 */
public interface CheckHandler {

    /**
     * Executes check using IntelliJ Project context and parsed manifest.
     *
     * @param project  IntelliJ project
     * @param manifest parsed .mlc/project.yaml manifest
     * @return check execution result
     */
    CheckResult check(@Nullable Project project, @NotNull ProjectManifest manifest);

    /**
     * Overloaded check method accepting explicit project directory path.
     * Ideal for headless testing where Project.getBasePath() may be null.
     *
     * @param project    IntelliJ project (nullable in headless tests)
     * @param projectDir root directory containing .mlc/
     * @param manifest   parsed ProjectManifest
     * @return check execution result
     */
    default CheckResult check(@Nullable Project project, @NotNull Path projectDir, @NotNull ProjectManifest manifest) {
        return check(project, manifest);
    }
}
