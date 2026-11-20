-- ============================================================
-- V15: Tag taxonomy. Each topic tag belongs to one group shown on /problems.
-- problem_tags keeps storing the tag name per problem; this table only adds the group.
-- A tag used by a problem but missing here is shown as OTHER.
-- ============================================================

CREATE TABLE tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    category ENUM('DSA', 'JAVA_CORE', 'SPRING_BOOT', 'OOP', 'OTHER') NOT NULL DEFAULT 'OTHER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tags_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every tag already attached to a problem, grouped by name.
INSERT INTO tags (name, category)
SELECT d.tag,
       CASE
           WHEN d.tag IN ('Array', 'Hash Table', 'Hash Map', 'String', 'Two Pointers', 'Math', 'Dynamic Programming',
                          'Sorting', 'Binary Search', 'Graph', 'Heap', 'Linked List', 'Matrix', 'Sliding Window',
                          'Stack', 'Queue', 'Topological Sort', 'Tree', 'Greedy', 'Backtracking', 'Recursion',
                          'Breadth-First Search', 'Depth-First Search', 'Divide and Conquer', 'Bit Manipulation')
               THEN 'DSA'
           WHEN d.tag IN ('OOP', 'Encapsulation', 'Inheritance', 'Polymorphism', 'Abstraction', 'Interface',
                          'Interfaces', 'Class', 'Constructor', 'Override', 'Static', 'Design', 'Design Pattern',
                          'Design Patterns')
               THEN 'OOP'
           WHEN d.tag IN ('Collections', 'Exceptions', 'Streams', 'Scanner', 'Multithreading', 'Socket', 'Generics')
               THEN 'JAVA_CORE'
           WHEN d.tag IN ('Spring Boot', 'Spring', 'REST API', 'JPA')
               THEN 'SPRING_BOOT'
           ELSE 'OTHER'
       END
FROM (SELECT DISTINCT tag FROM problem_tags) d;
