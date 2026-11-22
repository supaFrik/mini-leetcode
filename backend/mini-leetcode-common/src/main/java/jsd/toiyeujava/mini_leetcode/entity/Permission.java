package jsd.toiyeujava.mini_leetcode.entity;

/** Quyền trong khu quản trị; SecurityConfig và @PreAuthorize check theo tên enum (hasAuthority). */
public enum Permission {
    /** Xem cả bài chưa publish của giảng viên khác (chỉ xem). */
    PROBLEM_VIEW_ALL,
    /** Tạo và sửa bài, test case. */
    PROBLEM_EDIT,
    /** Chuyển trạng thái bài (gửi duyệt, publish, archive). */
    PROBLEM_PUBLISH,
    /** Xoá bài. */
    PROBLEM_DELETE,
    /** Tạo và sửa kỳ thi. */
    CONTEST_EDIT,
    /** Thêm/bớt thí sinh và giám thị. */
    CONTEST_PARTICIPANTS,
    /** Báo cáo giám sát, đạo văn, danh sách bài nộp. */
    CONTEST_REPORTS,
    /** Xoá kỳ thi. */
    CONTEST_DELETE,
    /** Tạo, sửa, publish study plan và topic. */
    COURSE_EDIT,
    /** Xoá study plan và topic. */
    COURSE_DELETE,
    /** Quản lý tag và nhóm tag. */
    TAG_MANAGE,
    /** Sửa trang chính sách. */
    PAGE_MANAGE,
    /** Đăng và xoá thông báo. */
    ANNOUNCEMENT_SEND,
    /** Tổng quan, người dùng, cài đặt: chỉ ADMIN, không cấp cho role khác. */
    SYSTEM_MANAGE
}
