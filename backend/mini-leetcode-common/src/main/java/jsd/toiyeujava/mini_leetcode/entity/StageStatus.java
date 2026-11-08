package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Trạng thái một stage với một học viên — luôn suy ra từ submissions (không lưu).
 *
 * - LOCKED: stage trước trong cùng mục chưa DONE.
 * - DONE: có bài nộp chính thức (không phải Run) được AC.
 * - IN_PROGRESS: có bài nộp chính thức nhưng chưa AC.
 * - NEVER: chưa nộp chính thức lần nào.
 */
public enum StageStatus {
    LOCKED,
    NEVER,
    IN_PROGRESS,
    DONE
}
