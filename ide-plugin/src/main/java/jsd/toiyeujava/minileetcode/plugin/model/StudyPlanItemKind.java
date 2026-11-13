package jsd.toiyeujava.minileetcode.plugin.model;

/**
 * Loại mục trong lộ trình học tập:
 * - PROJECT: Dự án thực tế nhiều stage, làm tuần tự.
 * - PROBLEM: Bài luyện tập đơn lẻ (1 stage duy nhất).
 */
public enum StudyPlanItemKind {
    PROJECT,
    PROBLEM;

    public boolean isProject() {
        return this == PROJECT;
    }

    public boolean isProblem() {
        return this == PROBLEM;
    }
}
