-- ============================================================
-- V23: Quyền của từng role, ADMIN bật/tắt ở /admin/users.
-- ADMIN luôn đủ quyền (code), USER không có quyền quản trị nào; chỉ lưu INSTRUCTOR.
-- ============================================================
CREATE TABLE role_permissions (
    role VARCHAR(20) NOT NULL,
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (role, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO role_permissions (role, permission) VALUES
    ('INSTRUCTOR', 'PROBLEM_EDIT'),
    ('INSTRUCTOR', 'CONTENT_MANAGE');
