package jsd.toiyeujava.minileetcode.plugin.workspace;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result record encapsulating the artifacts generated during workspace initialization.
 *
 * @param projectDir        the local directory of the project
 * @param manifestPath      absolute path to the generated {@code .mlc/project.yaml}
 * @param stageMarkdownPath optional path to {@code stages/stage-N.md} (null for in-IDE problem view)
 * @param extractionResult  summary of archive extraction (extracted count, skipped count, written paths)
 */
public record WorkspaceResult(
        Path projectDir,
        Path manifestPath,
        @Nullable Path stageMarkdownPath,
        ArchiveExtractor.ExtractionResult extractionResult
) {
    public WorkspaceResult {
        Objects.requireNonNull(projectDir, "projectDir must not be null");
        Objects.requireNonNull(manifestPath, "manifestPath must not be null");
        Objects.requireNonNull(extractionResult, "extractionResult must not be null");
    }
}
