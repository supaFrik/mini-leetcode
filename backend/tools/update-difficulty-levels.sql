-- ============================================================
-- update-difficulty-levels.sql  (DEV ONLY)
-- Re-levels the seeded problems to the five difficulty levels (needs Flyway V14 applied).
-- Only changes the difficulty column. Nothing is deleted.
-- Only problems created by `seed_author` are touched, so a real problem with the same slug keeps its level.
--
-- Run (PowerShell, repo root):
--   Get-Content backend\tools\update-difficulty-levels.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode
-- Safe to run again.
-- ============================================================

SET @seed_author = (SELECT id FROM users WHERE username = 'seed_author');

UPDATE problems
SET difficulty = CASE slug
        WHEN 'two-sum' THEN 'BEGINNER'
        WHEN 'binary-search' THEN 'BEGINNER'
        WHEN 'bank-account-class' THEN 'BEGINNER'
        WHEN 'shape-area-hierarchy' THEN 'BEGINNER'
        WHEN 'library-management-system' THEN 'EASY'
        WHEN 'course-schedule' THEN 'HARD'
        WHEN 'trapping-rain-water' THEN 'EXPERT'
        WHEN 'word-ladder' THEN 'EXPERT'
        WHEN 'tcp-chat-room' THEN 'EXPERT'
        WHEN 'mini-bank-stage-1' THEN 'BEGINNER'
        WHEN 'mini-bank-stage-5' THEN 'HARD'
        WHEN 'oop-1-lop-va-doi-tuong' THEN 'BEGINNER'
    END,
    updated_at = NOW()
WHERE created_by = @seed_author
  AND slug IN ('two-sum', 'binary-search', 'bank-account-class', 'shape-area-hierarchy',
               'library-management-system', 'course-schedule', 'trapping-rain-water', 'word-ladder',
               'tcp-chat-room', 'mini-bank-stage-1', 'mini-bank-stage-5', 'oop-1-lop-va-doi-tuong');

SELECT ROW_COUNT() AS updated_problems;

SELECT difficulty, COUNT(*) AS problems
FROM problems WHERE created_by = @seed_author
GROUP BY difficulty ORDER BY FIELD(difficulty, 'BEGINNER', 'EASY', 'MEDIUM', 'HARD', 'EXPERT');
