-- ============================================================
-- V14: Difficulty goes from 3 to 5 levels: BEGINNER, EASY, MEDIUM, HARD, EXPERT.
-- Only widens the ENUM; existing rows keep their EASY / MEDIUM / HARD value.
-- ============================================================

ALTER TABLE problems MODIFY COLUMN difficulty ENUM('BEGINNER', 'EASY', 'MEDIUM', 'HARD', 'EXPERT') NOT NULL;
