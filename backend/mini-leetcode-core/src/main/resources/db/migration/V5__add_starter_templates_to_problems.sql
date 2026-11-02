-- ============================================================
-- V5: Add starter_templates JSON to problems table
-- ============================================================

ALTER TABLE problems ADD COLUMN starter_templates JSON DEFAULT NULL;
