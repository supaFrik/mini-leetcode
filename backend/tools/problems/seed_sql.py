"""Generate backend/tools/seed-problems.sql from problems.json and statements/<slug>.md.

Usage (repo root): python backend\\tools\\problems\\seed_sql.py
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent / "seed-problems.sql"

HEADER = r"""-- ============================================================
-- seed-problems.sql  (CHỈ DÙNG CHO MÔI TRƯỜNG DEV)
-- 20 bài PUBLISHED (12 DSA + 8 OOP, đủ EASY/MEDIUM/HARD) kèm chủ đề,
-- để có dữ liệu xem trang /problems.
--
-- Run (PowerShell, đứng ở thư mục gốc repo):
--   Get-Content backend\tools\seed-problems.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode
--
-- Chạy lại bao nhiêu lần cũng được:
--   * Bài nhận diện theo slug (UNIQUE). Bài đã có sẽ được cập nhật, không nhân đôi.
--   * Chỉ bài do tài khoản `seed_author` tạo mới bị cập nhật / gán lại chủ đề.
--     Nếu DB đã có bài thật trùng slug, bài đó được giữ nguyên.
--
-- LƯU Ý: các bài này CHƯA CÓ TESTCASE nên chưa nộp và chấm được.
-- Muốn chấm thật thì thêm testcase qua trang Admin hoặc API
-- POST /api/v1/problems/{id}/test-cases.
-- ============================================================

-- 1. Tác giả của dữ liệu seed. password_hash không phải bcrypt nên tài khoản này không đăng nhập được.
INSERT INTO users (username, email, password_hash, role, status)
VALUES ('seed_author', 'seed_author@dev.local', '!seed-account-cannot-login', 'INSTRUCTOR', 'BLOCKED')
ON DUPLICATE KEY UPDATE username = username;

SET @seed_author := (SELECT id FROM users WHERE username = 'seed_author');

-- 2. Bài tập. seq quyết định thứ tự hiển thị (trang /problems sắp mới nhất trước,
--    nên created_at = NOW() - seq phút để bài seq 1 nằm trên cùng).
DROP TEMPORARY TABLE IF EXISTS seed_problem_rows;
CREATE TEMPORARY TABLE seed_problem_rows (
    seq INT NOT NULL,
    slug VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    problem_type VARCHAR(30) NOT NULL,
    description TEXT NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO seed_problem_rows (seq, slug, title, difficulty, problem_type, description) VALUES
"""

UPSERT = r"""
INSERT INTO problems (title, slug, description, difficulty, time_limit_ms, memory_limit_mb, status,
                      created_by, created_at, problem_type)
SELECT r.title, r.slug, r.description, r.difficulty, 1000, 256, 'PUBLISHED',
       @seed_author, NOW() - INTERVAL r.seq MINUTE, r.problem_type
FROM seed_problem_rows r
ORDER BY r.seq
ON DUPLICATE KEY UPDATE
    title        = IF(problems.created_by = @seed_author, VALUES(title), problems.title),
    description  = IF(problems.created_by = @seed_author, VALUES(description), problems.description),
    difficulty   = IF(problems.created_by = @seed_author, VALUES(difficulty), problems.difficulty),
    problem_type = IF(problems.created_by = @seed_author, VALUES(problem_type), problems.problem_type),
    status       = IF(problems.created_by = @seed_author, 'PUBLISHED', problems.status);

-- Ba bài socket chưa chấm tự động được (trình chấm OOP không đọc testcase của bài), nên ẩn khỏi người dùng.
UPDATE problems SET status = 'ARCHIVED'
WHERE created_by = @seed_author
  AND slug IN ({archived});

-- 3. Chủ đề. Xoá rồi gán lại cho đúng các bài của seed_author, nên chạy lại không bị trùng.
DROP TEMPORARY TABLE IF EXISTS seed_tag_rows;
CREATE TEMPORARY TABLE seed_tag_rows (
    slug VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL,
    tag VARCHAR(50) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO seed_tag_rows (slug, sort_order, tag) VALUES
"""

FOOTER = r"""
DELETE pt FROM problem_tags pt
JOIN problems p ON p.id = pt.problem_id
WHERE p.created_by = @seed_author;

INSERT INTO problem_tags (problem_id, sort_order, tag)
SELECT p.id, t.sort_order, t.tag
FROM seed_tag_rows t
JOIN problems p ON p.slug = t.slug AND p.created_by = @seed_author;

DROP TEMPORARY TABLE IF EXISTS seed_problem_rows;
DROP TEMPORARY TABLE IF EXISTS seed_tag_rows;

-- 4. Kiểm tra nhanh.
SELECT p.problem_type, p.difficulty, COUNT(*) AS problems
FROM problems p WHERE p.created_by = @seed_author
GROUP BY p.problem_type, p.difficulty ORDER BY p.problem_type, FIELD(p.difficulty, 'EASY', 'MEDIUM', 'HARD');

SELECT COUNT(*) AS seeded_problems_without_testcases
FROM problems p
WHERE p.created_by = @seed_author
  AND NOT EXISTS (SELECT 1 FROM test_cases tc WHERE tc.problem_id = p.id);
"""


def sql(text):
    # Seed is piped to the mysql CLI (default sql_mode), where backslash is an escape character.
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


def tag_lines(problems):
    for p in problems:
        tuples = [f"({sql(p['slug'])}, {i}, {sql(tag)})" for i, tag in enumerate(p["tags"])]
        line = ", ".join(tuples)
        yield from ([line] if len(line) <= 150 else tuples)


def build():
    problems = json.loads((HERE / "problems.json").read_text(encoding="utf-8"))
    rows = []
    for p in problems:
        # Universal newlines: statements read the same with LF or CRLF checkouts.
        desc = (HERE / "statements" / f"{p['slug']}.md").read_text(encoding="utf-8").removesuffix("\n")
        section = f"-- ---------- {p['section']} ----------\n" if "section" in p else ""
        meta = ", ".join(sql(p[k]) for k in ("slug", "title", "difficulty", "problemType"))
        rows.append(f"{section}({p['seq']:2d}, {meta},\n {sql(desc)})")
    archived = ", ".join(sql(p["slug"]) for p in problems if p.get("archived"))
    return (HEADER + ",\n".join(rows) + ";\n"
            + UPSERT.format(archived=archived)
            + ",\n".join(tag_lines(problems)) + ";\n"
            + FOOTER)


if __name__ == "__main__":
    OUT.write_text(build(), encoding="utf-8", newline="\n")
    print(f"wrote {OUT}")
