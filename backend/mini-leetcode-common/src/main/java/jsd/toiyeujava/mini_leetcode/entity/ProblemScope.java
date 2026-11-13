package jsd.toiyeujava.mini_leetcode.entity;

/**
 * Phạm vi danh sách đề. Đề "thuộc khoá học" = nằm trong một mục của study plan chưa ARCHIVED, hoặc là bài code của một
 * chủ đề chưa ARCHIVED. Trang Problems chỉ hiện đề lẻ (STANDALONE).
 */
public enum ProblemScope {
    STANDALONE,
    COURSE,
    ALL
}
