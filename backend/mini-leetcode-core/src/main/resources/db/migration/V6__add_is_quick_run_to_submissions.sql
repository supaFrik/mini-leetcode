-- ============================================================
-- V6: Mark quick runs (Run button) so they are not counted as real submissions
-- ============================================================

ALTER TABLE submissions ADD COLUMN is_quick_run BOOLEAN NOT NULL DEFAULT FALSE;
