-- Quản lý kỳ thi trên giao diện admin:
-- registration_mode: OPEN (tự đăng ký) | INVITE_ONLY (chỉ người do giảng viên thêm)
-- version: khoá lạc quan khi nhiều người cùng sửa một kỳ thi
ALTER TABLE contests ADD COLUMN registration_mode VARCHAR(20) NOT NULL DEFAULT 'OPEN';
ALTER TABLE contests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
