-- ============================================================
-- V2: Contest features (đề thi trong kỳ thi, nhật ký giám sát)
-- ============================================================

-- 1. Bài toán thuộc kỳ thi (nhãn A, B, C... theo thứ tự hiển thị)
CREATE TABLE contest_problems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    label VARCHAR(10) NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cpro_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpro_problem FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE,
    UNIQUE KEY uq_contest_problem (contest_id, problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Nhật ký sự kiện giám sát (rời tab, copy/paste) -> "sự cố gần nhất" cho giảng viên
CREATE TABLE contest_proctoring_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_type ENUM('WINDOW_BLUR', 'WINDOW_FOCUS', 'CLIPBOARD_PASTE') NOT NULL,
    violation_count INT NOT NULL DEFAULT 0,
    away_duration_ms BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cpe_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE,
    CONSTRAINT fk_cpe_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_cpe_contest_user (contest_id, user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
