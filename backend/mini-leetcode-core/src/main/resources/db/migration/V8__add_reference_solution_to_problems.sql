-- Lời giải mẫu của giảng viên (JSON [{path, content}]), dùng để sinh output kỳ vọng của testcase
-- và chạy thử trong console. CHỈ API soạn bài (INSTRUCTOR/ADMIN) đọc; không trả ra API công khai.
ALTER TABLE problems ADD COLUMN reference_solution JSON NULL;
