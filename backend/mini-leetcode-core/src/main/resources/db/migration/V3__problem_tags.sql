-- ============================================================
-- V3: Problem tags (chủ đề) — taxonomy cho bộ lọc của trang /problems
-- ============================================================

-- 1. Bảng nối bài tập ↔ chủ đề.
--    Dùng bảng nối thay vì cột CSV để lọc theo chủ đề được đẩy xuống SQL
--    (index theo tag) thay vì lọc ở tầng ứng dụng.
CREATE TABLE problem_tags (
    problem_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    tag VARCHAR(50) NOT NULL,
    CONSTRAINT fk_pt_problem FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE,
    PRIMARY KEY (problem_id, sort_order),
    UNIQUE KEY uq_pt_problem_tag (problem_id, tag),
    INDEX idx_pt_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Gán chủ đề cho 10 bài tập đã seed sẵn.
--    sort_order giữ thứ tự hiển thị ổn định (không phụ thuộc thứ tự chèn).
INSERT INTO problem_tags (problem_id, sort_order, tag)
SELECT p.id, t.sort_order, t.tag
FROM problems p
JOIN (
    SELECT 'two-sum'           AS slug, 0 AS sort_order, 'Array'        AS tag UNION ALL
    SELECT 'two-sum',           1,                   'Hash Table'   UNION ALL
    SELECT 'reverse-string',    0,                   'String'       UNION ALL
    SELECT 'reverse-string',    1,                   'Two Pointers' UNION ALL
    SELECT 'palindrome-number', 0,                   'Math'         UNION ALL
    SELECT 'fizz-buzz',         0,                   'String'       UNION ALL
    SELECT 'fizz-buzz',         1,                   'Math'         UNION ALL
    SELECT 'fibonacci-number',  0,                   'Math'         UNION ALL
    SELECT 'fibonacci-number',  1,                   'Dynamic Programming' UNION ALL
    SELECT 'sum-array',         0,                   'Array'        UNION ALL
    SELECT 'find-max',          0,                   'Array'        UNION ALL
    SELECT 'find-max',          1,                   'Sorting'      UNION ALL
    SELECT 'count-vowels',      0,                   'String'       UNION ALL
    SELECT 'binary-search',     0,                   'Array'        UNION ALL
    SELECT 'binary-search',     1,                   'Binary Search' UNION ALL
    SELECT 'binary-search',     2,                   'Sorting'      UNION ALL
    SELECT 'bubble-sort',       0,                   'Array'        UNION ALL
    SELECT 'bubble-sort',       1,                   'Sorting'      UNION ALL
    SELECT 'bubble-sort',       2,                   'Design'
) t ON t.slug = p.slug;
