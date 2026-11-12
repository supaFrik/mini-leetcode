package jsd.toiyeujava.minileetcode.plugin.workspace;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Input request record for initializing a student workspace for a study plan stage.
 *
 * @param targetDirectory destination directory on the local filesystem (must not be null)
 * @param planSlug        slug of the study plan, e.g. "oop-with-java" (must not be blank)
 * @param itemSlugOrId    item identifier or position, e.g. "1" or "mini-bank" (must not be blank)
 * @param stageNumber     1-based stage number, e.g. 1 (must be >= 1)
 * @param problemSlug     backend problem slug corresponding to this stage, e.g. "mini-bank-stage-1" (must not be blank)
 * @param problemId       backend problem ID (optional, nullable)
 */
public record WorkspaceInitRequest(
        Path targetDirectory,
        String planSlug,
        String itemSlugOrId,
        int stageNumber,
        String problemSlug,
        Long problemId
) {
    public WorkspaceInitRequest {
        Objects.requireNonNull(targetDirectory, "targetDirectory must not be null");
        Objects.requireNonNull(planSlug, "planSlug must not be null");
        if (planSlug.isBlank()) {
            throw new IllegalArgumentException("planSlug must not be blank");
        }
        Objects.requireNonNull(itemSlugOrId, "itemSlugOrId must not be null");
        if (itemSlugOrId.isBlank()) {
            throw new IllegalArgumentException("itemSlugOrId must not be blank");
        }
        if (stageNumber < 1) {
            throw new IllegalArgumentException("stageNumber must be >= 1 (got " + stageNumber + ")");
        }
        Objects.requireNonNull(problemSlug, "problemSlug must not be null");
        if (problemSlug.isBlank()) {
            throw new IllegalArgumentException("problemSlug must not be blank");
        }
    }

    /**
     * Convenience constructor omitting optional problemId.
     */
    public WorkspaceInitRequest(Path targetDirectory, String planSlug, String itemSlugOrId, int stageNumber, String problemSlug) {
        this(targetDirectory, planSlug, itemSlugOrId, stageNumber, problemSlug, null);
    }
}
