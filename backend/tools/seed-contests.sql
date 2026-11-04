-- ============================================================
-- seed-contests.sql  (CHỈ DÙNG CHO DEV)
-- Dữ liệu xem thử cho trang /exam: 1 kỳ đang diễn ra, 1 kỳ sắp tới, 1 kỳ đã kết thúc.
-- Thời gian tính theo NOW() (MySQL dev chạy UTC). Chạy lại không bị trùng: id cố định + ON DUPLICATE KEY / INSERT IGNORE.
-- Không tạo bài nộp, để không ảnh hưởng tiến độ ở /problems và /submissions.
--
-- Run:
--   Get-Content backend\tools\seed-contests.sql -Raw | docker exec -i mini_leetcode_mysql mysql -u leetcode -pleetcode_password mini_leetcode
-- ============================================================

SET @author = (SELECT id FROM users WHERE username = 'seed_author');
SET @me     = (SELECT id FROM users WHERE username = 'tri1205');

INSERT INTO contests (id, title, slug, start_time, end_time, scoring_policy, max_violations_allowed, status, created_by, created_at)
SELECT * FROM (
  SELECT 201 AS id, 'Weekly Contest 12' AS title, 'dev-weekly-contest-12' AS slug,
         DATE_SUB(NOW(), INTERVAL 30 MINUTE) AS start_time, DATE_ADD(NOW(), INTERVAL 60 MINUTE) AS end_time,
         'ACM_ICPC' AS scoring_policy, 3 AS max_violations_allowed, 'RUNNING' AS status, @author AS created_by, NOW() AS created_at
  UNION ALL
  SELECT 202, 'Weekly Contest 13', 'dev-weekly-contest-13',
         DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 7 DAY), INTERVAL 90 MINUTE),
         'ACM_ICPC', 3, 'SCHEDULED', @author, NOW()
  UNION ALL
  SELECT 203, 'Weekly Contest 11', 'dev-weekly-contest-11',
         DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 7 DAY), INTERVAL 90 MINUTE),
         'ACM_ICPC', 3, 'COMPLETED', @author, NOW()
) AS seed
WHERE @author IS NOT NULL
ON DUPLICATE KEY UPDATE
  title = VALUES(title), start_time = VALUES(start_time), end_time = VALUES(end_time), status = VALUES(status);

-- Mỗi kỳ 3 bài đã publish (id 1..20 có sẵn từ seed-problems.sql).
INSERT IGNORE INTO contest_problems (contest_id, problem_id, label, order_index, created_at)
SELECT c.id, p.id, CHAR(64 + p.rn), p.rn - 1, NOW()
FROM (SELECT 201 AS id UNION ALL SELECT 202 UNION ALL SELECT 203) c
JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM problems WHERE status = 'PUBLISHED') p ON p.rn <= 3
WHERE EXISTS (SELECT 1 FROM contests WHERE contests.id = c.id);

-- tri1205 đã đăng ký kỳ sắp tới (nút hiện "Registered") và kỳ đã qua (Your record có số kỳ tham gia).
INSERT IGNORE INTO contest_participants (contest_id, user_id, status, violation_count, score, penalty_time_minutes, created_at)
SELECT c.id, @me, 'REGISTERED', 0, 0, 0, NOW()
FROM (SELECT 202 AS id UNION ALL SELECT 203) c
WHERE @me IS NOT NULL AND EXISTS (SELECT 1 FROM contests WHERE contests.id = c.id);

SELECT id, title, status, start_time, end_time FROM contests WHERE id IN (201, 202, 203) ORDER BY id;
