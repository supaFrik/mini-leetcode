-- Thay 5 quyền cũ (V23) bằng quyền chi tiết; quyền mặc định của INSTRUCTOR giữ đúng hành vi cũ.
DELETE FROM role_permissions WHERE role = 'INSTRUCTOR';
INSERT INTO role_permissions (role, permission) VALUES
    ('INSTRUCTOR', 'PROBLEM_EDIT'),
    ('INSTRUCTOR', 'PROBLEM_PUBLISH'),
    ('INSTRUCTOR', 'CONTEST_EDIT'),
    ('INSTRUCTOR', 'CONTEST_PARTICIPANTS'),
    ('INSTRUCTOR', 'CONTEST_REPORTS'),
    ('INSTRUCTOR', 'COURSE_EDIT'),
    ('INSTRUCTOR', 'COURSE_DELETE'),
    ('INSTRUCTOR', 'ANNOUNCEMENT_SEND');
