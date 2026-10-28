package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Ngôn ngữ lập trình được hỗ trợ trong hệ thống Online Judge.
 */
public enum SubmissionLanguage {
    JAVA("Java 17"),
    PYTHON("Python 3"),
    CPP("C++ 20");

    private final String displayName;

    SubmissionLanguage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
