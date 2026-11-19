-- ============================================================
-- V20: Tag categories become rows ADMIN can add, rename, recolour and delete.
-- tags.category (fixed ENUM) is replaced by tags.category_name, a FK to tag_categories.name:
-- renaming a category cascades to its tags, deleting one leaves its tags without a category
-- (shown as "Other").
-- ============================================================

CREATE TABLE tag_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#64748b',
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tag_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tag_categories (name, color, sort_order) VALUES
    ('DSA', '#2563eb', 10),
    ('OOP', '#d97706', 20),
    ('Java Core', '#dc2626', 30),
    ('Spring Boot', '#16a34a', 40),
    ('Database', '#0891b2', 50),
    ('Web', '#7c3aed', 60),
    ('Testing', '#db2777', 70),
    ('Design Patterns', '#ca8a04', 80),
    ('Other', '#64748b', 1000);

ALTER TABLE tags ADD COLUMN category_name VARCHAR(50) NULL;

UPDATE tags SET category_name = CASE category
    WHEN 'DSA' THEN 'DSA'
    WHEN 'OOP' THEN 'OOP'
    WHEN 'JAVA_CORE' THEN 'Java Core'
    WHEN 'SPRING_BOOT' THEN 'Spring Boot'
    ELSE 'Other'
END;

ALTER TABLE tags DROP COLUMN category;

ALTER TABLE tags ADD CONSTRAINT fk_tags_category FOREIGN KEY (category_name)
    REFERENCES tag_categories (name) ON UPDATE CASCADE ON DELETE SET NULL;
