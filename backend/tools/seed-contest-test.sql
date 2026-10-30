-- ============================================================
-- seed-contest-test.sql
-- Tạo 1 kỳ thi đang chạy để test thủ công (phòng thi + giám sát + leaderboard).
--
-- Run:
--   Get-Content backend\tools\seed-contest-test.sql -Raw | docker exec -i mini_leetcode_mysql mysql -u leetcode -pleetcode_password mini_leetcode
-- ============================================================

-- 1. Kỳ thi mới: bắt đầu 30 phút trước, kết thúc sau 90 phút nữa (đủ thời gian test).
INSERT INTO contests (id, title, slug, start_time, end_time, scoring_policy, max_violations_allowed, status, created_by, created_at) VALUES
(100, 'Test Exam - Algorithms Sprint', 'test-exam-algorithms-sprint',
 DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 MINUTE), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 90 MINUTE),
 'ACM_ICPC', 3, 'RUNNING', 1, NOW())
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  scoring_policy = VALUES(scoring_policy),
  max_violations_allowed = VALUES(max_violations_allowed),
  status = VALUES(status);

-- 2. Gán 3 bài toán (nhãn A, B, C) — chọn bài có testcase để chấm được thật.
DELETE FROM contest_problems WHERE contest_id = 100;
INSERT INTO contest_problems (contest_id, problem_id, label, order_index) VALUES
(100, 1, 'A', 0),   -- Two Sum              (4 testcase)
(100, 3, 'B', 1),   -- Palindrome Number    (4 testcase)
(100, 4, 'C', 2);   -- FizzBuzz             (2 testcase)

-- 3. Đăng ký thí sinh mẫu (student, id=21) để leaderboard có dữ liệu.
INSERT INTO contest_participants (contest_id, user_id, status, violation_count, score, penalty_time_minutes, created_at) VALUES
(100, 21, 'REGISTERED', 0, 0, 0, NOW())
ON DUPLICATE KEY UPDATE status = 'REGISTERED', violation_count = 0;

SELECT id, title, slug, status, start_time, end_time FROM contests WHERE id = 100;
SELECT contest_id, problem_id, label, order_index FROM contest_problems WHERE contest_id = 100 ORDER BY order_index;
