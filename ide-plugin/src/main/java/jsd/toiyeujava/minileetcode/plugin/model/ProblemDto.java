package jsd.toiyeujava.minileetcode.plugin.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

/**
 * Data Transfer Object modeling the response of {@code GET /api/v1/problems/{idOrSlug}}.
 * Represents problem metadata, execution limits, topic tags, starter templates, and full problem description.
 * <p>
 * Implemented as a Java 21 record with defensive null-safety for Gson deserialization compatibility.
 * Distinct from {@link jsd.toiyeujava.minileetcode.plugin.client.ProblemDetail} which models RFC 7807 error responses.
 */
public record ProblemDto(
        Long id,
        String title,
        String slug,
        String description,
        Difficulty difficulty,
        @SerializedName(value = "type", alternate = {"problemType"})
        ProblemType type,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<String> tags,
        String starterTemplates,
        String status,
        Long createdById,
        String createdByUsername,
        String createdAt,
        String updatedAt,
        long testCaseCount,
        String progress
) {
    /**
     * Compact constructor enforcing null-safety and defensive defaults.
     */
    public ProblemDto {
        slug = Objects.requireNonNullElse(slug, "");
        title = Objects.requireNonNullElse(title, "");
        description = Objects.requireNonNullElse(description, "");
        difficulty = (difficulty != null) ? difficulty : Difficulty.EASY;
        type = (type != null) ? type : ProblemType.ALGORITHMIC;
        tags = (tags != null) ? tags.stream().filter(Objects::nonNull).toList() : List.of();
        starterTemplates = Objects.requireNonNullElse(starterTemplates, "");
        status = Objects.requireNonNullElse(status, "");
        createdByUsername = Objects.requireNonNullElse(createdByUsername, "");
        createdAt = Objects.requireNonNullElse(createdAt, "");
        updatedAt = Objects.requireNonNullElse(updatedAt, "");
        progress = Objects.requireNonNullElse(progress, "");
    }

    /**
     * Convenience constructor for basic problem identification and description.
     */
    public ProblemDto(
            Long id,
            String title,
            String slug,
            String description
    ) {
        this(id, title, slug, description, Difficulty.EASY, ProblemType.ALGORITHMIC,
                null, null, List.of(), null, "PUBLISHED",
                null, null, null, null, 0L, null);
    }

    /**
     * Convenience constructor for basic problem identification, description, difficulty, and type.
     */
    public ProblemDto(
            Long id,
            String title,
            String slug,
            String description,
            Difficulty difficulty,
            ProblemType type
    ) {
        this(id, title, slug, description, difficulty, type,
                null, null, List.of(), null, "PUBLISHED",
                null, null, null, null, 0L, null);
    }

    /**
     * Convenience constructor including resource limits, tags, and starter templates.
     */
    public ProblemDto(
            Long id,
            String title,
            String slug,
            String description,
            Difficulty difficulty,
            ProblemType type,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            List<String> tags,
            String starterTemplates
    ) {
        this(id, title, slug, description, difficulty, type,
                timeLimitMs, memoryLimitMb, tags, starterTemplates, "PUBLISHED",
                null, null, null, null, 0L, null);
    }

    /**
     * Null-safe accessor for tags, guarding against reflective/unsafe deserializers.
     */
    @Override
    public List<String> tags() {
        return tags != null ? tags : List.of();
    }

    // Domain query helpers
    public boolean isOopGrinding() {
        return type == ProblemType.OOP_GRINDING;
    }

    public boolean isAlgorithmic() {
        return type == ProblemType.ALGORITHMIC;
    }

    public boolean isPublished() {
        return "PUBLISHED".equalsIgnoreCase(status);
    }

    public boolean isDone() {
        return "DONE".equalsIgnoreCase(progress);
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equalsIgnoreCase(progress);
    }

    public boolean hasStarterTemplates() {
        return starterTemplates != null && !starterTemplates.isBlank();
    }

    public boolean hasTags() {
        return !tags().isEmpty();
    }

    public int effectiveTimeLimitMs() {
        return timeLimitMs != null ? timeLimitMs : 2000;
    }

    public int effectiveMemoryLimitMb() {
        return memoryLimitMb != null ? memoryLimitMb : 256;
    }

    // Java Bean getter aliases (matching StageDto / ItemDto conventions for Swing UI & binding)
    public Long getId() { return id(); }
    public String getTitle() { return title(); }
    public String getSlug() { return slug(); }
    public String getDescription() { return description(); }
    public Difficulty getDifficulty() { return difficulty(); }
    public ProblemType getType() { return type(); }
    public ProblemType getProblemType() { return type(); }
    public Integer getTimeLimitMs() { return timeLimitMs(); }
    public Integer getMemoryLimitMb() { return memoryLimitMb(); }
    public List<String> getTags() { return tags(); }
    public String getStarterTemplates() { return starterTemplates(); }
    public String getStatus() { return status(); }
    public Long getCreatedById() { return createdById(); }
    public String getCreatedByUsername() { return createdByUsername(); }
    public String getCreatedAt() { return createdAt(); }
    public String getUpdatedAt() { return updatedAt(); }
    public long getTestCaseCount() { return testCaseCount(); }
    public String getProgress() { return progress(); }
}
