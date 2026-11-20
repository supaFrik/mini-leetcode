-- ============================================================
-- V22: Drop the "Design" tag seeded by V17. No problem uses it and it duplicates "Design Patterns".
-- Kept if a problem was tagged with it in the meantime.
-- ============================================================

DELETE FROM tags
WHERE name = 'Design'
  AND NOT EXISTS (SELECT 1 FROM problem_tags WHERE tag = 'Design');
