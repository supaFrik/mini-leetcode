-- ============================================================
-- V17: Pre-register every tag used by the seed data (backend/tools) with its group.
-- V15 only copied tags that already existed in problem_tags. On a fresh database the problems
-- are seeded after Flyway runs, so their tags were missing from `tags` and showed up as OTHER.
-- Existing rows are left untouched: an admin may already have changed a tag's group.
-- ============================================================

INSERT INTO tags (name, category)
SELECT c.name, c.category
FROM (
    SELECT 'Array' AS name, 'DSA' AS category
    UNION ALL SELECT 'Binary Search', 'DSA'
    UNION ALL SELECT 'Dynamic Programming', 'DSA'
    UNION ALL SELECT 'Graph', 'DSA'
    UNION ALL SELECT 'Hash Map', 'DSA'
    UNION ALL SELECT 'Hash Table', 'DSA'
    UNION ALL SELECT 'Heap', 'DSA'
    UNION ALL SELECT 'Linked List', 'DSA'
    UNION ALL SELECT 'Math', 'DSA'
    UNION ALL SELECT 'Matrix', 'DSA'
    UNION ALL SELECT 'Sliding Window', 'DSA'
    UNION ALL SELECT 'Sorting', 'DSA'
    UNION ALL SELECT 'Stack', 'DSA'
    UNION ALL SELECT 'String', 'DSA'
    UNION ALL SELECT 'Topological Sort', 'DSA'
    UNION ALL SELECT 'Two Pointers', 'DSA'
    UNION ALL SELECT 'OOP', 'OOP'
    UNION ALL SELECT 'Abstraction', 'OOP'
    UNION ALL SELECT 'Class', 'OOP'
    UNION ALL SELECT 'Constructor', 'OOP'
    UNION ALL SELECT 'Design', 'OOP'
    UNION ALL SELECT 'Design Patterns', 'OOP'
    UNION ALL SELECT 'Encapsulation', 'OOP'
    UNION ALL SELECT 'Inheritance', 'OOP'
    UNION ALL SELECT 'Interfaces', 'OOP'
    UNION ALL SELECT 'Override', 'OOP'
    UNION ALL SELECT 'Polymorphism', 'OOP'
    UNION ALL SELECT 'Static', 'OOP'
    UNION ALL SELECT 'Collections', 'JAVA_CORE'
    UNION ALL SELECT 'Exceptions', 'JAVA_CORE'
    UNION ALL SELECT 'Multithreading', 'JAVA_CORE'
    UNION ALL SELECT 'Networking', 'JAVA_CORE'
    UNION ALL SELECT 'Scanner', 'JAVA_CORE'
    UNION ALL SELECT 'Socket', 'JAVA_CORE'
    UNION ALL SELECT 'Streams', 'JAVA_CORE'
    UNION ALL SELECT 'Spring Boot', 'SPRING_BOOT'
    UNION ALL SELECT 'REST API', 'SPRING_BOOT'
    UNION ALL SELECT 'JPA', 'SPRING_BOOT'
) c
WHERE NOT EXISTS (SELECT 1 FROM tags t WHERE t.name = c.name);
