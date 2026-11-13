-- ============================================================
-- V12: Chủ đề học (lý thuyết, quiz, ghép cặp, sắp xếp, điền chỗ trống, bài code) gắn vào stage project
-- Đặc tả: specs/topics.spec.md
-- ============================================================

CREATE TABLE topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(200) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    est_minutes INT NOT NULL DEFAULT 10,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- content: JSON công khai; answer: JSON đáp án, KHÔNG trả cho học viên. problem_id chỉ dùng cho CODE.
CREATE TABLE topic_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    answer TEXT NULL,
    problem_id BIGINT NULL,
    CONSTRAINT fk_ts_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_ts_problem FOREIGN KEY (problem_id) REFERENCES problems(id),
    INDEX idx_ts_topic (topic_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chủ đề gắn vào stage của một mục plan; xoá theo mục (plan được thay cả khi sửa).
CREATE TABLE study_plan_stage_topics (
    item_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    stage_no INT NOT NULL,
    topic_id BIGINT NOT NULL,
    PRIMARY KEY (item_id, sort_order),
    CONSTRAINT fk_spst_item FOREIGN KEY (item_id) REFERENCES study_plan_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_spst_topic FOREIGN KEY (topic_id) REFERENCES topics(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE topic_step_progress (
    user_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, step_id),
    CONSTRAINT fk_tsp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tsp_step FOREIGN KEY (step_id) REFERENCES topic_steps(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE topic_skips (
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, topic_id),
    CONSTRAINT fk_tsk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tsk_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
