-- ============================================================
-- V11: Nhãn testcase cho thông báo lỗi (vd. "stage 1 · two-accounts").
-- Với testcase ẩn, judge chỉ hiện nhãn + số dòng lệch, không hiện dữ liệu.
-- ============================================================
ALTER TABLE test_cases ADD COLUMN label VARCHAR(100) NULL;
