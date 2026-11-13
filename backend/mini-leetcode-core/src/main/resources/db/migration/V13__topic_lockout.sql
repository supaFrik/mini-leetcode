-- ============================================================
-- V13: Sai 3 lần liên tiếp ở một câu luyện tập ⇒ khoá phần luyện tập của chủ đề 5 phút và bắt đọc lại lý thuyết
-- Đặc tả: specs/topics.spec.md (Khoá khi trả lời sai)
-- ============================================================

ALTER TABLE topic_step_progress ADD COLUMN wrong_streak INT NOT NULL DEFAULT 0;

CREATE TABLE topic_locks (
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    locked_until TIMESTAMP NOT NULL,
    must_reread BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, topic_id),
    CONSTRAINT fk_tl_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tl_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
