package jsd.toiyeujava.minileetcode.plugin.model;

/**
 * Vòng đời của lộ trình học tập trên server.
 */
public enum StudyPlanStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
