package jsd.toiyeujava.minileetcode.plugin.workspace;

import java.nio.file.Path;

/**
 * Functional interface abstracting the platform-level action of opening/importing an initialized project directory.
 * <p>
 * This boundary decouples IntelliJ Platform SDK GUI / Event Dispatch Thread (EDT) / ProjectManager interactions
 * from the filesystem and network orchestration pipeline in {@link WorkspaceService}.
 * <p>
 * <b>Headless Safety Rationale:</b>
 * In headless unit tests (e.g., Gradle test tasks), {@code com.intellij.openapi.application.ApplicationManager.getApplication()}
 * is null. Directly invoking {@code ProjectUtil.openOrImport} triggers {@link NullPointerException} or EDT assertions.
 * By decoupling via this interface, tests can supply a lightweight recording mock, allowing full verification
 * of the 7-stage workspace pipeline without platform crashes or heavy IDE test fixture bootstrapping.
 */
@FunctionalInterface
public interface ProjectOpener {

    /**
     * Opens or imports the specified project directory into the IDE.
     *
     * @param projectDirectory the absolute path to the local project root
     * @throws Exception if project opening or platform import fails
     */
    void openProject(Path projectDirectory) throws Exception;

    /**
     * Default production implementation delegating directly to IntelliJ's {@code ProjectUtil.openOrImport}.
     */
    ProjectOpener DEFAULT = projectDirectory -> {
        com.intellij.ide.impl.ProjectUtil.openOrImport(projectDirectory);
    };

    /**
     * Factory method returning the production IntelliJ ProjectOpener implementation.
     *
     * @return production ProjectOpener
     */
    static ProjectOpener intelliJ() {
        return DEFAULT;
    }

    /**
     * No-op implementation useful for testing, dry runs, or batch processing.
     *
     * @return no-op ProjectOpener
     */
    static ProjectOpener noOp() {
        return projectDirectory -> {};
    }
}
