-- ============================================================
-- V10 (V8, V9 thuộc nhánh admin-console-authoring, merge trước): Study Plan (lộ trình học theo project nhiều stage) + Personal Access Token cho plugin IDE
-- Đặc tả: specs/study-plan.spec.md
-- ============================================================

-- 1. Lộ trình học do INSTRUCTOR / ADMIN soạn
CREATE TABLE study_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(200) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sp_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Mục trong lộ trình: PROJECT (nhiều stage) hoặc PROBLEM (bài lẻ, đúng 1 stage)
CREATE TABLE study_plan_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    CONSTRAINT fk_spi_plan FOREIGN KEY (plan_id) REFERENCES study_plans(id) ON DELETE CASCADE,
    INDEX idx_spi_plan (plan_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Stage của một mục; stage_no bắt đầu từ 0 (stage hiển thị = stage_no + 1).
--    UNIQUE(problem_id): một đề chỉ thuộc một mục ⇒ khoá stage và ẩn khỏi /problems có nghĩa rõ ràng.
CREATE TABLE study_plan_item_stages (
    item_id BIGINT NOT NULL,
    stage_no INT NOT NULL,
    problem_id BIGINT NOT NULL,
    PRIMARY KEY (item_id, stage_no),
    CONSTRAINT fk_spis_item FOREIGN KEY (item_id) REFERENCES study_plan_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_spis_problem FOREIGN KEY (problem_id) REFERENCES problems(id),
    UNIQUE KEY uq_spis_problem (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Personal Access Token: chỉ lưu SHA-256 (hex) của token, token gốc hiện cho người dùng đúng một lần
CREATE TABLE personal_access_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_pat_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
