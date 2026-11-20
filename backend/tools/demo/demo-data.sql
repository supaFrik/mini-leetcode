-- ============================================================
-- demo-data.sql  (DEV ONLY)
-- Demo accounts demo_student (USER) and demo_instructor (INSTRUCTOR), both with password Demo@123456.
-- Demo data for the contest pages (/exam):
--   * RUNNING "Demo Contest (7 days)" (slug dev-demo-contest-7d), started 2 hours ago, ends in 7 days.
--     4 problems: A two-sum (BEGINNER), B valid-parentheses (EASY), C coin-change (MEDIUM), D course-schedule (HARD).
--     demo_student: A accepted after 1 wrong attempt, B accepted, C 2 wrong attempts, D not tried.
--   * COMPLETED "Demo Past Contest 1" (slug dev-demo-past-1), ended 2 days ago, 5 participants.
--     demo_student finishes #2 with 2/3 solved.
--   * COMPLETED "Demo Past Contest 2" (slug dev-demo-past-2), ended 6 days ago, 4 participants.
--     demo_student finishes #1 with 3/3 solved.
--   * 4 rival accounts (quanghuy03, thaovy.nt, ducanh_hanu, baotran2k5; cannot log in) fill the leaderboards.
--   * 7 ACTIVE practice accounts (votinhyeuem ... khoa.ngu.java; cannot log in) fill the site ranking (/ranking).
--     Solved (published problems): 9, 7, 6, 4, 3, 2, 1. tuanbug99 ties demo_student on solved
--     but has fewer submissions, so it ranks above.
-- Submissions are inserted already judged (no judge run, no stored code). Rank and solved count are
-- computed by the backend from these submissions.
--
-- Run (PowerShell, repo root). Copy the file into the container, then run mysql inside it:
--   docker cp backend\tools\demo\demo-data.sql mini_leetcode_mysql:/tmp/demo-data.sql
--   docker exec mini_leetcode_mysql sh -c "mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode < /tmp/demo-data.sql"
-- setup-demo.ps1 (repo root) runs it as its last step.
-- Safe to run again: it deletes the previous demo contests (and their submissions) and recreates them
-- relative to the current time.
-- Needs tools/seed-problems.sql (the 4 problems) and an ADMIN account (the bootstrap admin).
-- ============================================================

SET @admin := (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1);

-- Student account (password Demo@123456).
INSERT INTO users (username, email, password_hash, role, status) VALUES
  ('demo_student', 'demo_student@dev.local', '$2a$12$aNURimmCmJAwIM43GgReYeDH.X86jqZy2091CF3dgM8EIC05xRiBK', 'USER', 'ACTIVE')
  ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 'ACTIVE';
SET @student := (SELECT id FROM users WHERE username = 'demo_student');

-- Seed accounts, old names (demo_coder_N / demo_rival_N) and current names: drop them with
-- everything that references them, so a re-run never leaves stale or duplicate accounts.
CREATE TEMPORARY TABLE seed_users AS SELECT id FROM users WHERE username IN ('demo_coder_1', 'demo_coder_2', 'demo_coder_3', 'demo_coder_4', 'demo_coder_5', 'demo_coder_6', 'demo_coder_7', 'demo_rival_1', 'demo_rival_2', 'demo_rival_3', 'demo_rival_4', 'votinhyeuem', 'minhcode2k5', 'lanhuong.dev', 'tuanbug99', 'hoangnam_fit', 'ngoclinh204', 'khoa.ngu.java', 'quanghuy03', 'thaovy.nt', 'ducanh_hanu', 'baotran2k5');
DELETE FROM submissions WHERE user_id IN (SELECT id FROM seed_users);
DELETE FROM contest_proctoring_events WHERE user_id IN (SELECT id FROM seed_users);
DELETE FROM contest_participants WHERE user_id IN (SELECT id FROM seed_users);
DELETE FROM users WHERE id IN (SELECT id FROM seed_users);
DROP TEMPORARY TABLE seed_users;

-- Instructor account (password Demo@123456) owning one study plan and two of its problems.
INSERT INTO users (username, email, password_hash, role, status) VALUES
  ('demo_instructor', 'demo_instructor@dev.local', '$2a$12$aNURimmCmJAwIM43GgReYeDH.X86jqZy2091CF3dgM8EIC05xRiBK', 'INSTRUCTOR', 'ACTIVE')
  ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = 'INSTRUCTOR', status = 'ACTIVE';
SET @instructor := (SELECT id FROM users WHERE username = 'demo_instructor');
UPDATE study_plans SET created_by = @instructor WHERE slug = 'oop-voi-java';
UPDATE problems SET created_by = @instructor WHERE slug IN ('mini-bank-stage-1', 'mini-bank-stage-2');

-- Rival accounts: same pattern as seed_author (unusable password, blocked).
INSERT IGNORE INTO users (username, email, password_hash, role, status) VALUES
  ('quanghuy03', 'quanghuy03@dev.local', '!seed-account-cannot-login', 'USER', 'BLOCKED'),
  ('thaovy.nt', 'thaovy.nt@dev.local', '!seed-account-cannot-login', 'USER', 'BLOCKED'),
  ('ducanh_hanu', 'ducanh_hanu@dev.local', '!seed-account-cannot-login', 'USER', 'BLOCKED'),
  ('baotran2k5', 'baotran2k5@dev.local', '!seed-account-cannot-login', 'USER', 'BLOCKED');
SET @r1 := (SELECT id FROM users WHERE username = 'quanghuy03');
SET @r2 := (SELECT id FROM users WHERE username = 'thaovy.nt');
SET @r3 := (SELECT id FROM users WHERE username = 'ducanh_hanu');
SET @r4 := (SELECT id FROM users WHERE username = 'baotran2k5');

SET @pA := (SELECT id FROM problems WHERE slug = 'two-sum');
SET @pB := (SELECT id FROM problems WHERE slug = 'valid-parentheses');
SET @pC := (SELECT id FROM problems WHERE slug = 'coin-change');
SET @pD := (SELECT id FROM problems WHERE slug = 'course-schedule');

-- Remove the previous run (submissions have no ON DELETE CASCADE).
DELETE s FROM submissions s JOIN contests c ON c.id = s.contest_id
  WHERE c.slug IN ('dev-demo-contest-7d', 'dev-demo-past-1', 'dev-demo-past-2');
DELETE FROM contests WHERE slug IN ('dev-demo-contest-7d', 'dev-demo-past-1', 'dev-demo-past-2');

-- ------------------------------------------------------------
-- 1. RUNNING: started 2 hours ago, ends in 7 days. Times are stored in UTC.
-- ------------------------------------------------------------
SET @start := UTC_TIMESTAMP() - INTERVAL 2 HOUR;
INSERT INTO contests (title, slug, start_time, end_time, scoring_policy, max_violations_allowed, status, created_by, registration_mode)
VALUES ('Demo Contest (7 days)', 'dev-demo-contest-7d', @start, UTC_TIMESTAMP() + INTERVAL 7 DAY, 'ACM_ICPC', 3, 'RUNNING', @admin, 'OPEN');
SET @c := LAST_INSERT_ID();

INSERT INTO contest_problems (contest_id, problem_id, label, order_index) VALUES
  (@c, @pA, 'A', 0), (@c, @pB, 'B', 1), (@c, @pC, 'C', 2), (@c, @pD, 'D', 3);

INSERT INTO contest_participants (contest_id, user_id, status) VALUES
  (@c, @student, 'IN_PROGRESS'), (@c, @r1, 'IN_PROGRESS'), (@c, @r2, 'IN_PROGRESS'),
  (@c, @r3, 'IN_PROGRESS'), (@c, @r4, 'IN_PROGRESS');

-- (user, problem, verdict, minutes after start).
INSERT INTO submissions (user_id, problem_id, contest_id, language, code_storage_uri, status, verdict,
                         execution_time_ms, memory_used_kb, pass_test_cases, total_test_cases, created_at)
SELECT s.u, s.p, @c, 'JAVA', '', 'COMPLETED', s.v, 42, 38000,
       IF(s.v = 'AC', 10, 6), 10, @start + INTERVAL s.m MINUTE
FROM (
  SELECT @student AS u, @pA AS p, 'WA' AS v, 9 AS m
  UNION ALL SELECT @student, @pA, 'AC', 14
  UNION ALL SELECT @student, @pB, 'AC', 31
  UNION ALL SELECT @student, @pC, 'WA', 52
  UNION ALL SELECT @student, @pC, 'TLE', 68
  UNION ALL SELECT @r1, @pA, 'AC', 6
  UNION ALL SELECT @r1, @pB, 'AC', 18
  UNION ALL SELECT @r1, @pC, 'AC', 47
  UNION ALL SELECT @r2, @pA, 'AC', 8
  UNION ALL SELECT @r2, @pB, 'WA', 25
  UNION ALL SELECT @r2, @pB, 'AC', 33
  UNION ALL SELECT @r3, @pA, 'AC', 21
  UNION ALL SELECT @r4, @pA, 'WA', 15
) s;
SET @running := @c;

-- ------------------------------------------------------------
-- 2. COMPLETED: 90 minutes, ended 2 days ago. 5 participants.
--    Expected ranking: r1 3/3, demo_student 2/3 (#2), r2 2/3, r3 1/3, r4 0/3.
-- ------------------------------------------------------------
SET @start := UTC_TIMESTAMP() - INTERVAL 2 DAY - INTERVAL 90 MINUTE;
INSERT INTO contests (title, slug, start_time, end_time, scoring_policy, max_violations_allowed, status, created_by, registration_mode)
VALUES ('Demo Past Contest 1', 'dev-demo-past-1', @start, @start + INTERVAL 90 MINUTE, 'ACM_ICPC', 3, 'COMPLETED', @admin, 'OPEN');
SET @c := LAST_INSERT_ID();

INSERT INTO contest_problems (contest_id, problem_id, label, order_index) VALUES
  (@c, @pA, 'A', 0), (@c, @pB, 'B', 1), (@c, @pC, 'C', 2);

INSERT INTO contest_participants (contest_id, user_id, status) VALUES
  (@c, @student, 'SUBMITTED'), (@c, @r1, 'SUBMITTED'), (@c, @r2, 'SUBMITTED'),
  (@c, @r3, 'SUBMITTED'), (@c, @r4, 'SUBMITTED');

INSERT INTO submissions (user_id, problem_id, contest_id, language, code_storage_uri, status, verdict,
                         execution_time_ms, memory_used_kb, pass_test_cases, total_test_cases, created_at)
SELECT s.u, s.p, @c, 'JAVA', '', 'COMPLETED', s.v, 42, 38000,
       IF(s.v = 'AC', 10, 6), 10, @start + INTERVAL s.m MINUTE
FROM (
  SELECT @student AS u, @pA AS p, 'AC' AS v, 7 AS m
  UNION ALL SELECT @student, @pB, 'WA', 20
  UNION ALL SELECT @student, @pB, 'AC', 26
  UNION ALL SELECT @student, @pC, 'WA', 70
  UNION ALL SELECT @r1, @pA, 'AC', 5
  UNION ALL SELECT @r1, @pB, 'AC', 15
  UNION ALL SELECT @r1, @pC, 'AC', 40
  UNION ALL SELECT @r2, @pA, 'AC', 12
  UNION ALL SELECT @r2, @pB, 'WA', 30
  UNION ALL SELECT @r2, @pB, 'AC', 44
  UNION ALL SELECT @r3, @pA, 'WA', 10
  UNION ALL SELECT @r3, @pA, 'AC', 18
  UNION ALL SELECT @r4, @pA, 'WA', 25
) s;
SET @past1 := @c;

-- ------------------------------------------------------------
-- 3. COMPLETED: 90 minutes, ended 6 days ago. 4 participants.
--    Expected ranking: demo_student 3/3 (#1), r1 2/3, r3 2/3, r2 1/3.
-- ------------------------------------------------------------
SET @start := UTC_TIMESTAMP() - INTERVAL 6 DAY - INTERVAL 90 MINUTE;
INSERT INTO contests (title, slug, start_time, end_time, scoring_policy, max_violations_allowed, status, created_by, registration_mode)
VALUES ('Demo Past Contest 2', 'dev-demo-past-2', @start, @start + INTERVAL 90 MINUTE, 'ACM_ICPC', 3, 'COMPLETED', @admin, 'OPEN');
SET @c := LAST_INSERT_ID();

INSERT INTO contest_problems (contest_id, problem_id, label, order_index) VALUES
  (@c, @pA, 'A', 0), (@c, @pC, 'B', 1), (@c, @pD, 'C', 2);

INSERT INTO contest_participants (contest_id, user_id, status) VALUES
  (@c, @student, 'SUBMITTED'), (@c, @r1, 'SUBMITTED'), (@c, @r2, 'SUBMITTED'), (@c, @r3, 'SUBMITTED');

INSERT INTO submissions (user_id, problem_id, contest_id, language, code_storage_uri, status, verdict,
                         execution_time_ms, memory_used_kb, pass_test_cases, total_test_cases, created_at)
SELECT s.u, s.p, @c, 'JAVA', '', 'COMPLETED', s.v, 42, 38000,
       IF(s.v = 'AC', 10, 6), 10, @start + INTERVAL s.m MINUTE
FROM (
  SELECT @student AS u, @pA AS p, 'AC' AS v, 6 AS m
  UNION ALL SELECT @student, @pC, 'WA', 25
  UNION ALL SELECT @student, @pC, 'AC', 34
  UNION ALL SELECT @student, @pD, 'AC', 61
  UNION ALL SELECT @r1, @pA, 'AC', 9
  UNION ALL SELECT @r1, @pC, 'AC', 41
  UNION ALL SELECT @r1, @pD, 'TLE', 80
  UNION ALL SELECT @r2, @pA, 'WA', 11
  UNION ALL SELECT @r2, @pA, 'AC', 17
  UNION ALL SELECT @r3, @pA, 'AC', 14
  UNION ALL SELECT @r3, @pC, 'WA', 50
  UNION ALL SELECT @r3, @pC, 'AC', 58
) s;
SET @past2 := @c;

-- ------------------------------------------------------------
-- 4. Site ranking: practice submissions (no contest) for ACTIVE USER accounts.
--    BLOCKED rivals are not ranked, so these accounts are ACTIVE with an unusable password.
--    Each coder gets AC on the first `solved` published problems (by id) and WA on the first `wrong`.
-- ------------------------------------------------------------
INSERT IGNORE INTO users (username, email, password_hash, role, status) VALUES
  ('votinhyeuem', 'votinhyeuem@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE'),
  ('minhcode2k5', 'minhcode2k5@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE'),
  ('lanhuong.dev', 'lanhuong.dev@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE'),
  ('tuanbug99', 'tuanbug99@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE'),
  ('hoangnam_fit', 'hoangnam_fit@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE'),
  ('ngoclinh204', 'ngoclinh204@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE'),
  ('khoa.ngu.java', 'khoa.ngu.java@dev.local', '!seed-account-cannot-login', 'USER', 'ACTIVE');

DELETE s FROM submissions s JOIN users u ON u.id = s.user_id
  WHERE u.username IN ('votinhyeuem', 'minhcode2k5', 'lanhuong.dev', 'tuanbug99', 'hoangnam_fit', 'ngoclinh204', 'khoa.ngu.java') AND s.contest_id IS NULL;

INSERT INTO submissions (user_id, problem_id, contest_id, language, code_storage_uri, status, verdict,
                         execution_time_ms, memory_used_kb, pass_test_cases, total_test_cases, created_at)
SELECT u.id, p.id, NULL, 'JAVA', '', 'COMPLETED', v.verdict, 42, 38000,
       IF(v.verdict = 'AC', 10, 6), 10, UTC_TIMESTAMP() - INTERVAL p.rn HOUR
FROM (
  SELECT 'votinhyeuem' AS username, 9 AS solved, 1 AS wrong
  UNION ALL SELECT 'minhcode2k5', 7, 3
  UNION ALL SELECT 'lanhuong.dev', 6, 2
  UNION ALL SELECT 'tuanbug99', 4, 2
  UNION ALL SELECT 'hoangnam_fit', 3, 4
  UNION ALL SELECT 'ngoclinh204', 2, 1
  UNION ALL SELECT 'khoa.ngu.java', 1, 3
) t
JOIN users u ON u.username = t.username
JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM problems WHERE status = 'PUBLISHED') p
JOIN (SELECT 'AC' AS verdict UNION ALL SELECT 'WA') v
  ON (v.verdict = 'AC' AND p.rn <= t.solved) OR (v.verdict = 'WA' AND p.rn <= t.wrong);

SELECT @running AS running_contest_id, @past1 AS past_contest_1_id, @past2 AS past_contest_2_id;
