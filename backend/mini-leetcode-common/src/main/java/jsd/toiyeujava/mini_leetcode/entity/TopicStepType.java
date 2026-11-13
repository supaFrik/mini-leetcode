package jsd.toiyeujava.mini_leetcode.entity;

/** Loại step trong một chủ đề học (specs/topics.spec.md). */
public enum TopicStepType {
    /** Lý thuyết markdown, section tách theo "## ". */
    THEORY,
    /** Trắc nghiệm một hoặc nhiều đáp án. */
    CHOICE,
    /** Ghép cặp cột trái với cột phải. */
    MATCHING,
    /** Sắp xếp các dòng theo đúng thứ tự. */
    ORDERING,
    /** Điền chỗ trống trong đoạn code. */
    FILL_BLANK,
    /** Bài code: trỏ tới một đề, xong khi đề được AC. */
    CODE
}
