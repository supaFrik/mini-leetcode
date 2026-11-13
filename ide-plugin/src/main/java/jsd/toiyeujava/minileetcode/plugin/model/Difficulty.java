package jsd.toiyeujava.minileetcode.plugin.model;

/**
 * Độ khó của bài toán/stage.
 */
public enum Difficulty {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    private final String displayName;

    Difficulty(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
