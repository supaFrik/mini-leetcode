-- ============================================================
-- V4: OOP Grinding Infrastructure Fields
-- ============================================================

ALTER TABLE problems ADD COLUMN problem_type VARCHAR(30) NOT NULL DEFAULT 'ALGORITHMIC';

ALTER TABLE submissions ADD COLUMN score DOUBLE DEFAULT NULL;
ALTER TABLE submissions ADD COLUMN execution_phase VARCHAR(30) DEFAULT NULL;
ALTER TABLE submissions ADD COLUMN score_breakdown JSON DEFAULT NULL;
