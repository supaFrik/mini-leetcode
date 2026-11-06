"""Sinh backend/tools/seed-mini-bank-console.sql: bài OOP dùng Scanner để test console live.

Chạy lại được nhiều lần (cập nhật theo slug). Nạp:
  python backend/tools/problems/seed_oop_console.py
  Get-Content backend/tools/seed-mini-bank-console.sql -Raw | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
SLUG = "mini-bank-console"
FILES = ["Main.java", "Account.java", "SavingsAccount.java", "CheckingAccount.java",
         "InsufficientFundsException.java", "Bank.java"]
TAGS = ["OOP", "Inheritance", "Polymorphism", "Scanner"]


def sql_str(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    starters = [{"path": name, "content": (HERE / "oop-starters" / SLUG / name).read_text(encoding="utf-8")}
                for name in FILES]
    description = (HERE / "statements" / f"{SLUG}.md").read_text(encoding="utf-8")
    # Rule chấm cấu trúc (chỉ judge đọc). Testcase stdin/stdout nạp riêng qua API (xem oop_console_cases.py).
    grading = json.dumps(json.loads((HERE / "grading" / f"{SLUG}.json").read_text(encoding="utf-8")), ensure_ascii=False)
    sql = [
        "SET NAMES utf8mb4;",
        "INSERT INTO problems (title, slug, description, difficulty, time_limit_ms, memory_limit_mb, status,"
        " created_by, problem_type, starter_templates, grading_config, created_at, updated_at) VALUES ("
        f"'Mini Bank Console', {sql_str(SLUG)}, {sql_str(description)}, 'MEDIUM', 3000, 256, 'PUBLISHED',"
        f" (SELECT MIN(id) FROM users), 'OOP_GRINDING', {sql_str(json.dumps(starters, ensure_ascii=False))}, {sql_str(grading)}, NOW(), NOW())"
        " ON DUPLICATE KEY UPDATE description = VALUES(description), starter_templates = VALUES(starter_templates),"
        " grading_config = VALUES(grading_config),"
        " status = 'PUBLISHED', updated_at = NOW();",
        f"SET @pid = (SELECT id FROM problems WHERE slug = {sql_str(SLUG)});",
        "DELETE FROM problem_tags WHERE problem_id = @pid;",
        "INSERT INTO problem_tags (problem_id, sort_order, tag) VALUES "
        + ", ".join(f"(@pid, {i}, {sql_str(t)})" for i, t in enumerate(TAGS)) + ";",
        "SELECT id, slug, status FROM problems WHERE id = @pid;",
    ]
    out = HERE.parent / f"seed-{SLUG}.sql"
    out.write_text("\n".join(sql) + "\n", encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
