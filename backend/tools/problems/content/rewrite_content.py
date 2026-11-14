"""Apply the DSA + OOP content (dsa.py, oop.py) to a running Mini LeetCode through the admin API.

For every problem: unlock if PUBLISHED -> update statement/metadata -> (OOP) output-only grading ->
save the Java reference -> replace or keep test cases (outputs computed by running the reference
locally with javac/java) -> cross-check every case with the server's reference run -> publish.

    python backend/tools/problems/content/rewrite_content.py --user admin --password '...'
    python backend/tools/problems/content/rewrite_content.py --only 1 101 --dry-run   # local runs only

Needs JDK 17 on PATH. Idempotent: re-running rewrites the same content.
"""
import argparse, json, pathlib, subprocess, sys, tempfile, urllib.error, urllib.request, uuid

HERE = pathlib.Path(__file__).resolve().parent
TOOLS = HERE.parent  # backend/tools/problems
sys.path.insert(0, str(HERE))
import dsa  # noqa: E402
import oop  # noqa: E402


class Api:
    def __init__(self, base, user, password):
        self.base, self.token = base.rstrip("/"), None
        self.token = self.call("POST", "/auth/login", {"username": user, "usernameOrEmail": user, "password": password})["accessToken"]

    def call(self, method, path, body=None, raw=None, ctype="application/json"):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        headers = {"Content-Type": ctype, **({"Authorization": "Bearer " + self.token} if self.token else {})}
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                text = r.read().decode("utf-8")
                return json.loads(text) if text else None
        except urllib.error.HTTPError as e:
            raise SystemExit(f"{method} {path} -> {e.code}: {e.read().decode('utf-8', 'replace')[:500]}")

    def multipart(self, method, path, files, fields):
        b = uuid.uuid4().hex
        out = b""
        for k, v in fields.items():
            out += f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
        for k, content in files.items():
            out += (f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{k}.txt\"\r\n"
                    "Content-Type: text/plain\r\n\r\n").encode() + content.encode("utf-8") + b"\r\n"
        out += f"--{b}--\r\n".encode()
        return self.call(method, path, raw=out, ctype=f"multipart/form-data; boundary={b}")


def reference_files(spec):
    """Inline {path: source} or a folder of .java files relative to backend/tools/problems."""
    if isinstance(spec, dict):
        return spec
    if "public class Solution" in spec:  # DSA: one inline Solution.java
        return {"Solution.java": spec}
    folder = TOOLS / spec
    return {p.name: p.read_text(encoding="utf-8") for p in sorted(folder.glob("*.java"))}


def run_local(files, inputs):
    """Compile the reference once and run it on every input; returns stdout per input."""
    with tempfile.TemporaryDirectory() as tmp:
        src = pathlib.Path(tmp)
        for name, code in files.items():
            (src / name).write_text(code, encoding="utf-8")
        javac = subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, *[str(src / n) for n in files]],
                               capture_output=True, text=True)
        if javac.returncode:
            raise SystemExit("reference does not compile:\n" + javac.stderr)
        main = "Solution" if "Solution.java" in files else "Main"
        outs = []
        for stdin in inputs:
            run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, main], input=stdin.encode("utf-8"),
                                 capture_output=True, timeout=60)
            if run.returncode not in (0,):
                raise SystemExit(f"reference exited {run.returncode}: {run.stderr.decode('utf-8', 'replace')[:300]}")
            outs.append(run.stdout.decode("utf-8").replace("\r\n", "\n"))
        return outs


def statement(p, examples):
    """Fixed template: Problem · Input · Output · Examples · Constraints. Examples come from real public cases."""
    parts = ["### Problem", p["problem"], "### Input", p["input"], "### Output", p["output"], "### Examples"]
    for i, (stdin, stdout) in enumerate(examples):
        parts.append(f"**Example {i + 1}**\n\nInput:\n\n```\n{stdin.rstrip()}\n```\n\nOutput:\n\n```\n{stdout.rstrip() or '(nothing)'}\n```")
        if p.get("explain", {}).get(i):
            parts.append("Explanation: " + p["explain"][i])
    parts += ["### Constraints", "\n".join(f"- {c}" for c in p["constraints"])]
    return "\n\n".join(parts) + "\n"


def apply(api, p, kind, dry):
    pid = p["id"]
    files = reference_files(p["reference"])
    current = api.call("GET", f"/problems/{pid}")
    tests = api.call("GET", f"/problems/{pid}/test-cases")

    if p["cases"] is None:  # keep generated cases, outputs already correct
        cases = [(t.get("label") or f"case {i + 1}", t["input"], t["expectedOutput"], i < 2) for i, t in enumerate(tests)]
    else:
        src = p["cases"]
        if isinstance(src, str) and src.startswith("copy:"):  # same cases as another problem
            other = api.call("GET", f"/problems/{src[5:]}/test-cases")
            src = [(t.get("label") or f"case {i + 1}", t["input"], i < 2) for i, t in enumerate(other)]
        outs = run_local(files, [c[1] for c in src])
        cases = [(label, stdin, out, public) for (label, stdin, public), out in zip(src, outs)]
    examples = [(c[1], c[2]) for c in cases if c[3]][:2]
    print(f"[{pid}] {p['title']}: {len(cases)} cases, {sum(c[3] for c in cases)} public")
    if dry:
        return

    if current["status"] == "PUBLISHED":
        api.call("POST", f"/admin/problems/{pid}/unlock")
    body = {"title": p["title"], "description": statement(p, examples), "tags": p.get("tags", current["tags"]),
            "difficulty": p.get("difficulty", current["difficulty"]), "timeLimitMs": p.get("timeLimitMs", current["timeLimitMs"]),
            "problemType": "OOP_GRINDING" if kind == "oop" else "ALGORITHMIC"}
    if "starter" in p:
        body["starterTemplates"] = json.dumps([{"path": k, "content": v} for k, v in p["starter"].items()])
    api.call("PUT", f"/problems/{pid}", body)
    if kind == "oop":
        api.call("PUT", f"/admin/problems/{pid}/grading-config", {"gradingConfig": oop.OUTPUT_ONLY_CONFIG})
    api.call("PUT", f"/admin/problems/{pid}/reference-solution", {"files": [{"path": k, "content": v} for k, v in files.items()]})

    if p["cases"] is None:
        for i, t in enumerate(tests):
            if bool(t.get("isHidden")) == (i < 2):  # make the first two public, the rest hidden
                api.multipart("PUT", f"/problems/{pid}/test-cases/{t['id']}?isHidden={str(i >= 2).lower()}", {}, {})
    else:
        for t in tests:
            api.call("DELETE", f"/problems/{pid}/test-cases/{t['id']}")
        for i, (label, stdin, out, public) in enumerate(cases):
            api.multipart("POST", f"/problems/{pid}/test-cases?isHidden={str(not public).lower()}&orderIndex={i}"
                          f"&label={urllib.request.quote(label)}", {"inputFile": stdin, "outputFile": out}, {})

    run = api.call("POST", f"/admin/problems/{pid}/reference-run", {})
    # ponytail: the first sandbox runs after a judge restart can time out while the JVM warms up; retry once.
    if run["compiled"] and run["cases"] and all(c["status"] in ("MATCH", "TIME_LIMIT") for c in run["cases"]) \
            and any(c["status"] == "TIME_LIMIT" for c in run["cases"]):
        run = api.call("POST", f"/admin/problems/{pid}/reference-run", {})
    if not run["compiled"]:
        raise SystemExit(f"[{pid}] server could not compile the reference: {run['compileOutput'] or run['error']}")
    bad = [c for c in run["cases"] if c["status"] != "MATCH"]
    if bad:
        raise SystemExit(f"[{pid}] server reference run disagrees on {len(bad)} case(s): "
                         + ", ".join(f"#{c['orderIndex']} {c['status']}" for c in bad))
    status = api.call("GET", f"/problems/{pid}")["status"]
    for target in {"DRAFT": ["REVIEWING", "PUBLISHED"], "REVIEWING": ["PUBLISHED"]}.get(status, []):
        api.call("POST", f"/problems/{pid}/transitions", {"status": target})
    print(f"[{pid}] ok: {len(run['cases'])}/{len(run['cases'])} cases match the server run, published")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8088/api/v1")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", required=True)
    ap.add_argument("--only", nargs="*", type=int)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    api = Api(a.base, a.user, a.password)
    for kind, problems in (("dsa", dsa.PROBLEMS), ("oop", oop.PROBLEMS)):
        for p in problems:
            if not a.only or p["id"] in a.only:
                apply(api, p, kind, a.dry_run)


if __name__ == "__main__":
    main()
