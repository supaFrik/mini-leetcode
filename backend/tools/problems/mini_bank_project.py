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


OUTPUT_POLICY = json.loads((PROJECT / "grading.json").read_text(encoding="utf-8")).get("output")


def load():
    meta = json.loads((PROJECT / "project.json").read_text(encoding="utf-8"))
    rules = json.loads((PROJECT / "grading.json").read_text(encoding="utf-8"))["rules"]
    return meta, rules


def load_lessons(meta):
    """Bài học lý thuyết + bài tập (lessons/<slug>/): lesson.json, lesson.md, starter/, solution/."""
    lessons = []
    for slug in meta.get("lessons", []):
        folder = PROJECT / "lessons" / slug
        lesson = json.loads((folder / "lesson.json").read_text(encoding="utf-8"))
        if lesson["slug"] != slug:
            raise SystemExit(f"lessons/{slug}/lesson.json: slug mismatch ({lesson['slug']})")
        lesson["folder"] = folder
        lessons.append(lesson)
    return lessons


def java_files(folder):
    """[{path, content}] của mọi .java trong folder, Main.java đứng đầu."""
    files = sorted(folder.glob("*.java"), key=lambda f: (f.name != "Main.java", f.name))
    return [{"path": f.name, "content": f.read_text(encoding="utf-8")} for f in files]


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

def compile_files(files, label):
    with tempfile.TemporaryDirectory(prefix="mb-check-") as tmp:
        for f in files:
            (Path(tmp) / f["path"]).write_text(f["content"], encoding="utf-8")
        sources = [str(p) for p in Path(tmp).glob("*.java")]
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, *sources], check=True)
        print(f"{label}: compiles ({len(sources)} files)")


def cmd_check():
    meta, _ = load()
    for n in range(1, len(meta["stages"]) + 1):
        compile_files(starter_files(n), f"stage {n} starter")
    for lesson in load_lessons(meta):
        compile_files(java_files(lesson["folder"] / "starter"), f"{lesson['slug']} starter")
        compile_files(java_files(lesson["folder"] / "solution"), f"{lesson['slug']} solution")


# ------------------------------------------------------------------ sql

def sql_str(value):
    # Nạp qua mysql CLI (sql_mode mặc định): backslash là ký tự escape.
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def problem_sql(slug, title, difficulty, tags, description, starters, rule_list):
    """INSERT ... ON DUPLICATE KEY UPDATE một đề OOP_GRINDING do seed_author tạo (chạy lại được)."""
    config = {"rules": rule_list}
    if OUTPUT_POLICY:
        config["output"] = OUTPUT_POLICY  # cách so stdout (OutputPolicy ở judge)
    grading = json.dumps(config, ensure_ascii=False)
    starters_json = json.dumps(starters, ensure_ascii=False)
    return [
        "INSERT INTO problems (title, slug, description, difficulty, time_limit_ms, memory_limit_mb, status,"
        " created_by, problem_type, starter_templates, grading_config, created_at, updated_at) VALUES ("
        f"{sql_str(title)}, {sql_str(slug)}, {sql_str(description)}, {sql_str(difficulty)},"
        f" 3000, 256, 'PUBLISHED', @seed_author, 'OOP_GRINDING', {sql_str(starters_json)}, {sql_str(grading)}, NOW(), NOW())"
        " ON DUPLICATE KEY UPDATE"
        " title = IF(created_by = @seed_author, VALUES(title), title),"
        " description = IF(created_by = @seed_author, VALUES(description), description),"
        " difficulty = IF(created_by = @seed_author, VALUES(difficulty), difficulty),"
        " starter_templates = IF(created_by = @seed_author, VALUES(starter_templates), starter_templates),"
        " grading_config = IF(created_by = @seed_author, VALUES(grading_config), grading_config),"
        " updated_at = NOW();",
        f"SET @pid := (SELECT id FROM problems WHERE slug = {sql_str(slug)} AND created_by = @seed_author);",
        "DELETE FROM problem_tags WHERE problem_id = @pid;",
        "INSERT INTO problem_tags (problem_id, sort_order, tag) SELECT @pid, t.sort_order, t.tag FROM ("
        + " UNION ALL ".join(f"SELECT {i} AS sort_order, {sql_str(tag)} AS tag" for i, tag in enumerate(tags))
        + ") t WHERE @pid IS NOT NULL;",
    ]


def load_topics(meta):
    """Chủ đề học của từng bài (lessons/<slug>/topic.json); step THEORY lấy markdown từ theoryFile."""
    topics = []
    for lesson in load_lessons(meta):
        path = lesson["folder"] / "topic.json"
        if not path.exists():
            continue
        topic = json.loads(path.read_text(encoding="utf-8"))
        for step in topic["steps"]:
            if step["type"] == "THEORY" and "theoryFile" in step:
                step["content"] = {"markdown": (lesson["folder"] / step.pop("theoryFile")).read_text(encoding="utf-8")}
        topics.append(topic)
    return topics


def topic_sql(topic):
    """Chủ đề PUBLISHED của seed_author; step xoá rồi tạo lại. CODE trỏ tới đề theo slug (`problem`)."""
    slug = sql_str(topic["slug"])
    out = [
        "INSERT INTO topics (slug, title, summary, est_minutes, status, created_by, created_at, updated_at) VALUES ("
        f"{slug}, {sql_str(topic['title'])}, {sql_str(topic.get('summary', ''))}, {int(topic['estMinutes'])},"
        " 'PUBLISHED', @seed_author, NOW(), NOW())"
        " ON DUPLICATE KEY UPDATE"
        " title = IF(created_by = @seed_author, VALUES(title), title),"
        " summary = IF(created_by = @seed_author, VALUES(summary), summary),"
        " est_minutes = IF(created_by = @seed_author, VALUES(est_minutes), est_minutes), updated_at = NOW();",
        f"SET @tid := (SELECT id FROM topics WHERE slug = {slug} AND created_by = @seed_author);",
        "DELETE FROM topic_steps WHERE topic_id = @tid;",
    ]
    for i, step in enumerate(topic["steps"]):
        content = sql_str(json.dumps(step.get("content", {}), ensure_ascii=False))
        answer = sql_str(json.dumps(step["answer"], ensure_ascii=False)) if step.get("answer") else "NULL"
        problem = f"(SELECT id FROM problems WHERE slug = {sql_str(step['problem'])})" if step.get("problem") else "NULL"
        out.append("INSERT INTO topic_steps (topic_id, sort_order, type, title, content, answer, problem_id)"
                   f" SELECT @tid, {i}, {sql_str(step['type'])}, {sql_str(step['title'])}, {content}, {answer}, {problem}"
                   " FROM DUAL WHERE @tid IS NOT NULL;")
    return out


def cmd_sql():
    meta, rules = load()
    out = [
        "-- seed-mini-bank-project.sql — sinh bởi backend/tools/problems/mini_bank_project.py (DEV). Chạy lại được.",
        "-- Cần: API đã chạy một lần (Flyway V10). Nên nạp seed-problems.sql trước để có 2 bài luyện lẻ của plan.",
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
        out += problem_sql(stage["slug"], stage["title"], stage["difficulty"], stage["tags"], description,
                           starter_files(n), stage_rules(rules, n))

    out += ["", "-- 1b. Bài học: lý thuyết (đề) + bài tập code, chấm bằng rule OOP + testcase."]
    for lesson in load_lessons(meta):
        description = (lesson["folder"] / "lesson.md").read_text(encoding="utf-8").removesuffix("\n")
        out += problem_sql(lesson["slug"], lesson["title"], lesson["difficulty"], lesson["tags"], description,
                           java_files(lesson["folder"] / "starter"), lesson["rules"])

    out += ["", "-- 1c. Chủ đề học (specs/topics.spec.md): lý thuyết + quiz / ghép cặp / sắp xếp / điền chỗ trống + bài code.",
            "--     Nạp lại xoá rồi tạo lại các step ⇒ tiến độ chủ đề của học viên trên các step này bị đặt lại."]
    for topic in load_topics(meta):
        out += topic_sql(topic)

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
        order = 0
        for k, topic_slugs in enumerate(item.get("stageTopics", [])):
            for topic_slug in topic_slugs:
                out.append(f"INSERT INTO study_plan_stage_topics (item_id, sort_order, stage_no, topic_id)"
                           f" SELECT @item, {order}, {k}, id FROM topics WHERE slug = {sql_str(topic_slug)} AND @item IS NOT NULL;")
                order += 1
    out += [
        "",
        "-- 3. Kiểm tra nhanh: mục nào bị bỏ qua sẽ không có dòng ở đây.",
        "SELECT i.sort_order, i.kind, i.title, COUNT(DISTINCT s.problem_id) AS stages, COUNT(DISTINCT t.topic_id) AS topics"
        " FROM study_plan_items i LEFT JOIN study_plan_item_stages s ON s.item_id = i.id"
        " LEFT JOIN study_plan_stage_topics t ON t.item_id = i.id WHERE i.plan_id = @plan"
        " GROUP BY i.id, i.sort_order, i.kind, i.title ORDER BY i.sort_order;",
    ]
    target = TOOLS / "seed-mini-bank-project.sql"
    target.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {target}")


# ------------------------------------------------------------------ cases

def run_reference(source_dir, cases, local):
    """Biên dịch lời giải trong source_dir, chạy từng case; trả {tên case: stdout bytes}."""
    work = Path(tempfile.mkdtemp(prefix="mb-ref-"))
    try:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), *map(str, source_dir.glob("*.java"))], check=True)
        env = dict(os.environ, MSYS_NO_PATHCONV="1")
        if local:
            runner = ["java", "-Dfile.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US", "-cp", str(work), "Main"]
        else:
            runner = ["docker", "run", "-i", "--rm", "--network", "none", "-v", f"{work}:/w:ro", IMAGE,
                      "java", "-cp", "/w", "Main"]
        outputs = {}
        for case in cases:
            run = subprocess.run(runner, input=case["stdin"].encode("utf-8"), capture_output=True, env=env, timeout=60)
            if run.returncode != 0:
                raise SystemExit(f"{source_dir.name}/{case['name']}: reference exited {run.returncode}: "
                                 f"{run.stderr.decode('utf-8', 'replace')}")
            outputs[case["name"]] = run.stdout
        return outputs
    finally:
        shutil.rmtree(work, ignore_errors=True)


def write_lesson_cases(lesson, outputs):
    """Case đầu tiên là ví dụ công khai, còn lại ẩn. Tên file thành nhãn testcase (seed-two-sum-testcases.ps1)."""
    out_dir = TOOLS / lesson["slug"] / "cases"
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    for i, case in enumerate(lesson["cases"]):
        stem = f"{i + 1:02d}-{case['name']}" + ("" if i == 0 else "-hidden")
        (out_dir / f"{stem}.in").write_bytes(case["stdin"].encode("utf-8"))
        (out_dir / f"{stem}.out").write_bytes(outputs[case["name"]])
    print(f"{lesson['slug']}: {len(lesson['cases'])} cases -> {out_dir}")


def cmd_cases(local):
    meta, _ = load()
    if local:
        print("WARNING: --local dùng java trên máy; output có thể khác image của judge. Không nạp lên server.")
    for lesson in load_lessons(meta):
        write_lesson_cases(lesson, run_reference(lesson["folder"] / "solution", lesson["cases"], local))
    reference = HERE / meta["reference"]
    outputs = run_reference(reference, meta["cases"], local)
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
