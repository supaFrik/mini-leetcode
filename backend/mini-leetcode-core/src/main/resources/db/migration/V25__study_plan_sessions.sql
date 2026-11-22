-- ============================================================
-- V25: Study plan 2 tầng: plan → session → mục (LESSON = topic, PROBLEM = bài lẻ, STAGE = stage project).
-- study_plan_items giờ là session (kind = 'SESSION'). Dữ liệu cũ:
--   - mỗi stage của PROJECT thành 1 session "<tên project> · Stage N": các topic của stage (LESSON) rồi tới đề (STAGE);
--   - mỗi item PROBLEM thành 1 session cùng tên: các topic (LESSON) rồi tới đề (PROBLEM).
-- Tiến độ lưu theo problem / topic nên không mất. Hai bảng cũ được chép hết sang rồi xoá.
-- ============================================================

CREATE TABLE study_plan_entries (
    session_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    topic_id BIGINT NULL,
    problem_id BIGINT NULL,
    PRIMARY KEY (session_id, sort_order),
    CONSTRAINT fk_spe_session FOREIGN KEY (session_id) REFERENCES study_plan_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_spe_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_spe_problem FOREIGN KEY (problem_id) REFERENCES problems(id),
    UNIQUE KEY uq_spe_problem (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cột tạm: session mới lấy dữ liệu từ (item cũ, stage cũ) nào.
ALTER TABLE study_plan_items ADD COLUMN src_item_id BIGINT NULL;
ALTER TABLE study_plan_items ADD COLUMN src_stage_no INT NULL;
ALTER TABLE study_plan_items ADD COLUMN src_kind VARCHAR(20) NULL;

-- Chừa chỗ để chèn session của stage 2, 3... ngay sau project của nó (thứ tự chỉ cần tăng dần).
UPDATE study_plan_items SET sort_order = sort_order * 1000, src_item_id = id, src_stage_no = 0, src_kind = kind;

INSERT INTO study_plan_items (plan_id, sort_order, kind, title, description, src_item_id, src_stage_no, src_kind)
SELECT i.plan_id, i.sort_order + s.stage_no, 'SESSION', LEFT(CONCAT(i.title, ' · Stage ', s.stage_no + 1), 200),
       NULL, i.id, s.stage_no, 'PROJECT'
FROM study_plan_items i JOIN study_plan_item_stages s ON s.item_id = i.id
WHERE i.kind = 'PROJECT' AND s.stage_no > 0;

UPDATE study_plan_items SET title = LEFT(CONCAT(title, ' · Stage 1'), 200) WHERE kind = 'PROJECT';

-- Topic đứng trước đề trong session (sort_order của topic là duy nhất trong item cũ).
INSERT INTO study_plan_entries (session_id, sort_order, type, topic_id, problem_id)
SELECT n.id, t.sort_order, 'LESSON', t.topic_id, NULL
FROM study_plan_items n JOIN study_plan_stage_topics t ON t.item_id = n.src_item_id AND t.stage_no = n.src_stage_no;

INSERT INTO study_plan_entries (session_id, sort_order, type, topic_id, problem_id)
SELECT n.id, 1000000, CASE WHEN n.src_kind = 'PROJECT' THEN 'STAGE' ELSE 'PROBLEM' END, NULL, s.problem_id
FROM study_plan_items n JOIN study_plan_item_stages s ON s.item_id = n.src_item_id AND s.stage_no = n.src_stage_no;

UPDATE study_plan_items SET kind = 'SESSION';

ALTER TABLE study_plan_items DROP COLUMN src_item_id;
ALTER TABLE study_plan_items DROP COLUMN src_stage_no;
ALTER TABLE study_plan_items DROP COLUMN src_kind;

DROP TABLE study_plan_stage_topics;
DROP TABLE study_plan_item_stages;
