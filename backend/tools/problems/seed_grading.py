r"""In ra SQL cập nhật problems.grading_config từ mọi file problems/grading/<slug>.json.

  python backend\tools\problems\seed_grading.py | docker exec -i mini_leetcode_mysql mysql --default-character-set=utf8mb4 -u leetcode -pleetcode_password mini_leetcode
"""
import json
import sys
from pathlib import Path

GRADING = Path(__file__).resolve().parent / "grading"


def sql_str(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    out = ["SET NAMES utf8mb4;"]
    for path in sorted(GRADING.glob("*.json")):
        config = json.dumps(json.loads(path.read_text(encoding="utf-8")), ensure_ascii=False)
        out.append(f"UPDATE problems SET grading_config = {sql_str(config)} WHERE slug = {sql_str(path.stem)};")
    out.append("SELECT slug, JSON_LENGTH(grading_config, '$.rules') AS rules FROM problems"
               " WHERE grading_config IS NOT NULL ORDER BY id;")
    sys.stdout.buffer.write(("\n".join(out) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
