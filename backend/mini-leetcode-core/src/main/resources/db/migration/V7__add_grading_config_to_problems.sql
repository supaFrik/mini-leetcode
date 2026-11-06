-- Cấu hình chấm bài OOP do giảng viên định nghĩa, CHỈ judge đọc (không trả ra API công khai).
-- Dạng: {"rules": [OopConstraintRule...], "socketScenarios": [SocketScenarioConfig...]}
-- Testcase stdin/stdout của bài OOP dùng bảng test_cases như bài thuật toán.
ALTER TABLE problems ADD COLUMN grading_config JSON NULL;
