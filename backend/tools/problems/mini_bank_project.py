r"""Project "Mini Bank Console" 5 stage + plan mẫu "OOP với Java" (specs/study-plan.spec.md, slice 2).

Nguồn: backend/tools/problems/projects/mini-bank/ (project.json, grading.json, stages/*.md, starters/stage-N/)
Lời giải tham chiếu dùng chung cho cả 5 stage: backend/tools/problems/oop-starters/mini-bank-console/
(output của mọi lệnh giữ nguyên từ stage 1 tới stage 5 nên một lời giải sinh được output kỳ vọng cho mọi stage).

  python backend\tools\problems\mini_bank_project.py check          # biên dịch bộ khung từng stage (cần javac)
  python backend\tools\problems\mini_bank_project.py sql            # -> backend\tools\seed-mini-bank-project.sql
  python backend\tools\problems\mini_bank_project.py cases          # -> backend\tools\mini-bank-stage-N\cases\ (cần Docker)
  python backend\tools\problems\mini_bank_project.py cases --local  # chạy bằng java trên máy, CHỈ để thử nhanh

Testcase cộng dồn: stage N gồm mọi case có stage <= N. Case đầu tiên của chính stage N là ví dụ công khai.
Output kỳ vọng phải sinh trong đúng image sandbox của judge (eclipse-temurin:17-jdk) để mã hoá ký tự /
định dạng số khớp tuyệt đối; --local chỉ dùng kiểm tra logic, đừng nạp kết quả của nó lên server.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = HERE / "projects" / "mini-bank"
TOOLS = HERE.parent
IMAGE = "eclipse-temurin:17-jdk"


def load():
    meta = json.loads((PROJECT / "project.json").read_text(encoding="utf-8"))
    rules = json.loads((PROJECT / "grading.json").read_text(encoding="utf-8"))["rules"]
    return meta, rules


def starter_files(stage_no):
    """Bản mới nhất của mỗi file khung tính tới stage_no (stage sau thay bản của stage trước)."""
    files = {}
    for n in range(1, stage_no + 1):
        folder = PROJECT / "starters" / f"stage-{n}"
        if folder.is_dir():
            for f in sorted(folder.glob("*.java")):
                files[f.name] = f.read_text(encoding="utf-8")
    order = sorted(files, key=lambda name: (name != "Main.java", name))
    return [{"path": name, "content": files[name]} for name in order]


def stage_rules(rules, stage_no):
    return [{k: v for k, v in r.items() if k != "fromStage"} for r in rules if r["fromStage"] <= stage_no]


def stage_cases(cases, stage_no):
    """[(file_stem, stdin)] cộng dồn; case đầu tiên của stage_no là công khai, còn lại ẩn."""
    picked = [c for c in cases if c["stage"] <= stage_no]
    public = next(c for c in picked if c["stage"] == stage_no)
    ordered = [public] + [c for c in picked if c is not public]
    return [(f"{i + 1:02d}-s{c['stage']}-{c['name']}" + ("" if c is public else "-hidden"), c["stdin"])
            for i, c in enumerate(ordered)]


# ------------------------------------------------------------------ check

def cmd_check():
    meta, _ = load()
    for n in range(1, len(meta["stages"]) + 1):
        with tempfile.TemporaryDirectory(prefix=f"mb-starter-{n}-") as tmp:
            for f in starter_files(n):
                (Path(tmp) / f["path"]).write_text(f["content"], encoding="utf-8")
            sources = [str(p) for p in Path(tmp).glob("*.java")]
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, *sources], check=True)
            print(f"stage {n}: starter compiles ({len(sources)} files)")


# ------------------------------------------------------------------ sql

def sql_str(value):
    # Nạp qua mysql CLI (sql_mode mặc định): backslash là ký tự escape.
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def cmd_sql():
    meta, rules = load()
    out = [
        "-- seed-mini-bank-project.sql — sinh bởi backend/tools/problems/mini_bank_project.py (DEV). Chạy lại được.",
        "-- Cần: API đã chạy một lần (Flyway V8). Nên nạp seed-problems.sql trước để có 2 bài luyện lẻ của plan.",
        "SET NAMES utf8mb4;",
        "INSERT INTO users (username, email, password_hash, role, status)",
        "VALUES ('seed_author', 'seed_author@dev.local', '!seed-account-cannot-login', 'INSTRUCTOR', 'BLOCKED')",
        "ON DUPLICATE KEY UPDATE username = username;",
        "SET @seed_author := (SELECT id FROM users WHERE username = 'seed_author');",
        "",
        "-- 1. Năm đề stage (OOP_GRINDING). Chỉ cập nhật đề do seed_author tạo.",
    ]
    for n, stage in enumerate(meta["stages"], start=1):
        description = (PROJECT / "stages" / f"stage-{n}.md").read_text(encoding="utf-8").removesuffix("\n")
        starters = json.dumps(starter_files(n), ensure_ascii=False)
        grading = json.dumps({"rules": stage_rules(rules, n)}, ensure_ascii=False)
        out += [
            "INSERT INTO problems (title, slug, description, difficulty, time_limit_ms, memory_limit_mb, status,"
            " created_by, problem_type, starter_templates, grading_config, created_at, updated_at) VALUES ("
            f"{sql_str(stage['title'])}, {sql_str(stage['slug'])}, {sql_str(description)}, {sql_str(stage['difficulty'])},"
            f" 3000, 256, 'PUBLISHED', @seed_author, 'OOP_GRINDING', {sql_str(starters)}, {sql_str(grading)}, NOW(), NOW())"
            " ON DUPLICATE KEY UPDATE"
            " title = IF(created_by = @seed_author, VALUES(title), title),"
            " description = IF(created_by = @seed_author, VALUES(description), description),"
            " difficulty = IF(created_by = @seed_author, VALUES(difficulty), difficulty),"
            " starter_templates = IF(created_by = @seed_author, VALUES(starter_templates), starter_templates),"
            " grading_config = IF(created_by = @seed_author, VALUES(grading_config), grading_config),"
            " updated_at = NOW();",
            f"SET @pid := (SELECT id FROM problems WHERE slug = {sql_str(stage['slug'])} AND created_by = @seed_author);",
            "DELETE FROM problem_tags WHERE problem_id = @pid;",
            "INSERT INTO problem_tags (problem_id, sort_order, tag) SELECT @pid, t.sort_order, t.tag FROM ("
            + " UNION ALL ".join(f"SELECT {i} AS sort_order, {sql_str(tag)} AS tag" for i, tag in enumerate(stage["tags"]))
            + ") t WHERE @pid IS NOT NULL;",
        ]

    plan = meta["plan"]
    out += [
        "",
        "-- 2. Plan mẫu. Xoá rồi tạo lại các mục (stage xoá theo ON DELETE CASCADE).",
        "INSERT INTO study_plans (slug, title, description, status, created_by, created_at, updated_at) VALUES ("
        f"{sql_str(plan['slug'])}, {sql_str(plan['title'])}, {sql_str(plan['description'])}, 'PUBLISHED', @seed_author, NOW(), NOW())"
        " ON DUPLICATE KEY UPDATE title = VALUES(title), description = VALUES(description), status = 'PUBLISHED', updated_at = NOW();",
        f"SET @plan := (SELECT id FROM study_plans WHERE slug = {sql_str(plan['slug'])});",
        "DELETE FROM study_plan_items WHERE plan_id = @plan;",
    ]
    for pos, item in enumerate(plan["items"]):
        slugs = ", ".join(sql_str(s) for s in item["problems"])
        out += [
            f"-- mục {pos}: {item['title']} (bỏ qua nếu thiếu đề)",
            "INSERT INTO study_plan_items (plan_id, sort_order, kind, title, description)"
            f" SELECT @plan, {pos}, {sql_str(item['kind'])}, {sql_str(item['title'])}, {sql_str(item.get('description', ''))}"
            f" FROM DUAL WHERE (SELECT COUNT(*) FROM problems WHERE slug IN ({slugs})) = {len(item['problems'])};",
            "SET @item := IF(ROW_COUNT() > 0, LAST_INSERT_ID(), NULL);",
        ]
        out += [
            f"INSERT INTO study_plan_item_stages (item_id, stage_no, problem_id)"
            f" SELECT @item, {k}, id FROM problems WHERE slug = {sql_str(slug)} AND @item IS NOT NULL;"
            for k, slug in enumerate(item["problems"])
        ]
    out += [
        "",
        "-- 3. Kiểm tra nhanh: mục nào bị bỏ qua sẽ không có dòng ở đây.",
        "SELECT i.sort_order, i.kind, i.title, COUNT(s.problem_id) AS stages FROM study_plan_items i"
        " LEFT JOIN study_plan_item_stages s ON s.item_id = i.id WHERE i.plan_id = @plan"
        " GROUP BY i.id, i.sort_order, i.kind, i.title ORDER BY i.sort_order;",
    ]
    target = TOOLS / "seed-mini-bank-project.sql"
    target.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {target}")


# ------------------------------------------------------------------ cases

def cmd_cases(local):
    meta, _ = load()
    reference = HERE / meta["reference"]
    work = Path(tempfile.mkdtemp(prefix="mb-ref-"))
    try:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), *map(str, reference.glob("*.java"))], check=True)
        env = dict(os.environ, MSYS_NO_PATHCONV="1")
        if local:
            print("WARNING: --local dùng java trên máy; output có thể khác image của judge. Không nạp lên server.")
            runner = ["java", "-Dfile.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US", "-cp", str(work), "Main"]
        else:
            runner = ["docker", "run", "-i", "--rm", "--network", "none", "-v", f"{work}:/w:ro", IMAGE,
                      "java", "-cp", "/w", "Main"]
        outputs = {}
        for case in meta["cases"]:
            run = subprocess.run(runner, input=case["stdin"].encode("utf-8"), capture_output=True, env=env, timeout=60)
            if run.returncode != 0:
                raise SystemExit(f"{case['name']}: reference exited {run.returncode}: "
                                 f"{run.stderr.decode('utf-8', 'replace')}")
            outputs[case["name"]] = run.stdout
        for n, stage in enumerate(meta["stages"], start=1):
            out_dir = TOOLS / stage["slug"] / "cases"
            if out_dir.exists():
                shutil.rmtree(out_dir)
            out_dir.mkdir(parents=True)
            for stem, stdin in stage_cases(meta["cases"], n):
                name = stem.split("-", 2)[2].removesuffix("-hidden")
                (out_dir / f"{stem}.in").write_bytes(stdin.encode("utf-8"))
                (out_dir / f"{stem}.out").write_bytes(outputs[name])
            print(f"{stage['slug']}: {len(list(out_dir.glob('*.in')))} cases -> {out_dir}")
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # console Windows (cp1252) không in được tiếng Việt
    if len(argv) < 2 or argv[1] not in ("check", "sql", "cases"):
        print(__doc__)
        raise SystemExit(2)
    if argv[1] == "check":
        cmd_check()
    elif argv[1] == "sql":
        cmd_sql()
    else:
        cmd_cases("--local" in argv[2:])


if __name__ == "__main__":
    main(sys.argv)
