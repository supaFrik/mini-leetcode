package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Loại mục trong lộ trình.
 *
 * - PROJECT: project nhiều stage, mở khoá tuần tự; đề của nó bị ẩn khỏi danh mục /problems.
 * - PROBLEM: bài luyện lẻ, đúng một stage, vẫn hiện trong /problems.
 */
public enum StudyPlanItemKind {
    PROJECT,
    PROBLEM
}
