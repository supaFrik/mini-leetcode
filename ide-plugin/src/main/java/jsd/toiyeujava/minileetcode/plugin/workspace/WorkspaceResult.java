package jsd.toiyeujava.minileetcode.plugin.workspace;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result record encapsulating the artifacts generated during workspace initialization.
 *
 * @param projectDir        the local directory of the project
 * @param manifestPath      absolute path to the generated {@code .mlc/project.yaml}
 * @param stageMarkdownPath absolute path to the generated {@code stages/stage-N.md}
 * @param extractionResult  summary of archive extraction (extracted count, skipped count, written paths)
 */
public record WorkspaceResult(
        Path projectDir,
        Path manifestPath,
        Path stageMarkdownPath,
        ArchiveExtractor.ExtractionResult extractionResult
) {
    public WorkspaceResult {
        Objects.requireNonNull(projectDir, "projectDir must not be null");
        Objects.requireNonNull(manifestPath, "manifestPath must not be null");
        Objects.requireNonNull(stageMarkdownPath, "stageMarkdownPath must not be null");
        Objects.requireNonNull(extractionResult, "extractionResult must not be null");
    }
}
