package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Tiến độ của một người dùng với một bài tập.
 *
 * - DONE: đã có ít nhất một bài nộp được chấm AC.
 * - IN_PROGRESS: đã thử (có bài nộp) nhưng chưa có bài nộp nào AC.
 * - NEVER: chưa nộp bài nào.
 */
public enum UserProgress {
    DONE,
    IN_PROGRESS,
    NEVER
}
