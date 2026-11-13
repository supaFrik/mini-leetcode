// Live test of every admin/instructor API on the running stack. Creates a throwaway problem + student, cleans up.
const API = process.env.API ?? "http://localhost:8080";
const WS = API.replace(/^http/, "ws") + "/api/v1/console/ws";
const ADMIN = { usernameOrEmail: "devadmin", password: process.env.ADMIN_PW };
const ts = Date.now().toString(36);
const results = [];
let section = "";
const check = (name, ok, detail = "") => {
  results.push({ section, name, ok: !!ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  [${section}] ${name}${!ok && detail ? `  -> ${detail}` : ""}`);
};

async function call(method, path, { token, json, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (json !== undefined) { headers["Content-Type"] = "application/json"; body = JSON.stringify(json); }
  if (form) body = form;
  const res = await fetch(API + path, { method, headers, body });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: res.status, data };
}
const caseForm = (input, output, hidden) => {
  const fd = new FormData();
  if (input !== undefined) fd.append("inputFile", new Blob([input], { type: "text/plain" }), "in.txt");
  if (output !== undefined) fd.append("outputFile", new Blob([output], { type: "text/plain" }), "out.txt");
  if (hidden !== undefined) fd.append("isHidden", String(hidden));
  return fd;
};

const MAIN = `import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.print("Enter n: ");
        System.out.flush();
        int n = in.nextInt();
        System.out.println(n * 2);
    }
}
`;
const GOOD_RULES = JSON.stringify({ rules: [{ targetClassName: "Main", expectedType: "CLASS", weight: 1,
  methods: [{ name: "main", returnType: "void", parameterTypes: ["String[]"], requiredModifiers: ["public", "static"] }] }] });

function consoleRun(token, problemId, files, stdin) {
  return new Promise((resolve) => {
    const ws = new WebSocket(WS);
    let out = "", exit = null, error = null, sent = false;
    const done = () => { try { ws.close(); } catch {} resolve({ out, exit, error }); };
    const timer = setTimeout(() => { error = error ?? "timeout"; done(); }, 60000);
    ws.onopen = () => ws.send(JSON.stringify({ type: "start", token, problemId, language: "JAVA", files }));
    ws.onmessage = (e) => {
      const f = JSON.parse(e.data);
      if (f.type === "stdout") {
        out += f.data;
        if (!sent && out.includes("Enter n:")) { sent = true; ws.send(JSON.stringify({ type: "stdin", data: stdin })); }
      } else if (f.type === "stderr") out += f.data;
      else if (f.type === "exit") { exit = f; clearTimeout(timer); done(); }
      else if (f.type === "error") { error = f.message; clearTimeout(timer); done(); }
    };
    ws.onerror = () => { error = error ?? "socket error"; };
    ws.onclose = () => { clearTimeout(timer); resolve({ out, exit, error }); };
  });
}

let admin, student, studentId, pid, contestId;
try {
  section = "Auth";
  const login = await call("POST", "/api/v1/auth/login", { json: ADMIN });
  admin = login.data?.accessToken;
  check("admin login", login.status === 200 && admin, `status ${login.status}`);
  const reg = await call("POST", "/api/v1/auth/register", { json: { username: `smoke_stu_${ts}`, email: `smoke_stu_${ts}@example.com`, password: "Password123!" } });
  student = reg.data?.accessToken;
  studentId = reg.data?.user?.id ?? reg.data?.userId;
  check("throwaway student registered", reg.status === 201 && student, `status ${reg.status}`);

  section = "Problem CRUD";
  const created = await call("POST", "/api/v1/problems", { token: admin, json: {
    title: `ZZ Admin Smoke ${ts}`, description: "Read n, print 2n.", difficulty: "EASY", timeLimitMs: 3000, memoryLimitMb: 256,
    tags: ["Smoke Test", "  math "], type: "OOP_GRINDING", starterTemplates: JSON.stringify([{ path: "Main.java", content: "public class Main {}\n" }]),
  } });
  pid = created.data?.id;
  check("create problem (201, DRAFT)", created.status === 201 && created.data.status === "DRAFT", JSON.stringify(created.data).slice(0, 200));
  check("type OOP_GRINDING persisted", created.data?.type === "OOP_GRINDING", created.data?.type);
  check("starter template persisted", (created.data?.starterTemplates ?? "").includes("Main.java"));
  check("slug generated", /^zz-admin-smoke/.test(created.data?.slug ?? ""), created.data?.slug);
  check("tags normalized", JSON.stringify(created.data?.tags) === JSON.stringify(["Smoke Test", "math"]) || (created.data?.tags ?? []).length === 2, JSON.stringify(created.data?.tags));
  const stuCreate = await call("POST", "/api/v1/problems", { token: student, json: { title: "x", description: "x", difficulty: "EASY" } });
  check("student cannot create (403)", stuCreate.status === 403, stuCreate.status);
  const anonCreate = await call("POST", "/api/v1/problems", { json: { title: "x", description: "x", difficulty: "EASY" } });
  check("anonymous cannot create (401)", anonCreate.status === 401, anonCreate.status);
  const badCreate = await call("POST", "/api/v1/problems", { token: admin, json: { title: "", description: "", difficulty: "EASY" } });
  check("validation: blank title rejected (400)", badCreate.status === 400, badCreate.status);

  const upd = await call("PUT", `/api/v1/problems/${pid}`, { token: admin, json: { title: `ZZ Admin Smoke ${ts} v2`, timeLimitMs: 4000, tags: ["Smoke Test"] } });
  check("update metadata", upd.status === 200 && upd.data.title.endsWith("v2") && upd.data.timeLimitMs === 4000, upd.status);
  check("update keeps type when omitted", upd.data?.type === "OOP_GRINDING", upd.data?.type);
  const stuUpd = await call("PUT", `/api/v1/problems/${pid}`, { token: student, json: { title: "hack" } });
  check("student cannot update (403)", stuUpd.status === 403, stuUpd.status);

  section = "Admin list & filters";
  const listDraft = await call("GET", `/api/v1/problems?status=DRAFT&search=${encodeURIComponent("ZZ Admin Smoke " + ts)}&size=20`, { token: admin });
  const items = listDraft.data?.content ?? [];
  check("staff sees DRAFT in list (search+status)", items.some((p) => p.id === pid), `found ${items.length}`);
  const listType = await call("GET", `/api/v1/problems?type=OOP_GRINDING&difficulty=EASY&search=${encodeURIComponent("ZZ Admin Smoke " + ts)}`, { token: admin });
  check("filter by type+difficulty", (listType.data?.content ?? []).some((p) => p.id === pid));
  const listStu = await call("GET", `/api/v1/problems?search=${encodeURIComponent("ZZ Admin Smoke " + ts)}`, { token: student });
  check("student does not see DRAFT", !(listStu.data?.content ?? []).some((p) => p.id === pid));
  const stuGet = await call("GET", `/api/v1/problems/${pid}`, { token: student });
  check("student GET draft -> 404", stuGet.status === 404, stuGet.status);

  section = "Test cases";
  const c1 = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: admin, form: caseForm("21\n", "42\n", false) });
  const c2 = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: admin, form: caseForm("5\n", "11\n", true) });
  const c3 = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: admin, form: caseForm("7\n", "14\n", true) });
  check("add 3 cases (201)", [c1, c2, c3].every((c) => c.status === 201), [c1, c2, c3].map((c) => c.status).join(","));
  const emptyCase = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: admin, form: caseForm("", "x", true) });
  check("empty input rejected (400)", emptyCase.status === 400, emptyCase.status);
  const stuCase = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: student, form: caseForm("1", "2", true) });
  check("student cannot add case (403)", stuCase.status === 403, stuCase.status);
  let cases = await call("GET", `/api/v1/problems/${pid}/test-cases`, { token: admin });
  check("staff lists all 3 incl. hidden with content", cases.data?.length === 3 && cases.data[1].expectedOutput === "11\n", JSON.stringify(cases.data?.map((c) => c.expectedOutput)));
  check("truncation flags present (false)", cases.data?.every((c) => c.inputTruncated === false && c.expectedOutputTruncated === false));
  const upd3 = await call("PUT", `/api/v1/problems/${pid}/test-cases/${c3.data.id}`, { token: admin, form: caseForm(undefined, undefined, false) });
  check("update case visibility", upd3.status === 200 && upd3.data.isHidden === false, upd3.status);
  const del3 = await call("DELETE", `/api/v1/problems/${pid}/test-cases/${c3.data.id}`, { token: admin });
  check("delete case", del3.status === 204 || del3.status === 200, del3.status);
  const big = "9\n".repeat(5000);
  const cBig = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: admin, form: caseForm(big, "x\n", true) });
  cases = await call("GET", `/api/v1/problems/${pid}/test-cases`, { token: admin });
  const bigRow = cases.data?.find((c) => c.id === cBig.data?.id);
  check(">8KB input flagged inputTruncated", bigRow?.inputTruncated === true && bigRow.input.length === 8192, `${bigRow?.inputTruncated} ${bigRow?.input?.length}`);
  await call("DELETE", `/api/v1/problems/${pid}/test-cases/${cBig.data.id}`, { token: admin });

  section = "Authoring: grading_config";
  const anonAuth = await call("GET", `/api/v1/admin/problems/${pid}/authoring`);
  check("anonymous -> 401", anonAuth.status === 401, anonAuth.status);
  const stuAuth = await call("GET", `/api/v1/admin/problems/${pid}/authoring`, { token: student });
  check("student -> 403", stuAuth.status === 403, stuAuth.status);
  const auth0 = await call("GET", `/api/v1/admin/problems/${pid}/authoring`, { token: admin });
  check("admin GET authoring (empty)", auth0.status === 200 && auth0.data.gradingConfig === null && auth0.data.referenceSolution.length === 0, auth0.status);
  const v1 = await call("POST", `/api/v1/admin/problems/${pid}/grading-config/validate`, { token: admin, json: { gradingConfig: '{"rules":[{"targetClassName":"A","methods":[{"name":"m","retunType":"int"}]}],"rule":[]}' } });
  check("validate reports unknown keys by path", v1.data?.valid === false && v1.data.errors.includes("rules[0].methods[0].retunType: unknown key") && v1.data.errors.some((e) => e.startsWith("rule:")), JSON.stringify(v1.data?.errors));
  const v2 = await call("POST", `/api/v1/admin/problems/${pid}/grading-config/validate`, { token: admin, json: { gradingConfig: "{bad json" } });
  check("validate reports invalid JSON", v2.data?.valid === false && v2.data.errors[0].startsWith("Not valid JSON"), JSON.stringify(v2.data?.errors));
  const sBad = await call("PUT", `/api/v1/admin/problems/${pid}/grading-config`, { token: admin, json: { gradingConfig: '{"rules":[{"expectedType":"KLASS"}]}' } });
  check("save invalid -> 400 with errors[]", sBad.status === 400 && Array.isArray(sBad.data?.errors) && sBad.data.errors.length > 0, `${sBad.status} ${JSON.stringify(sBad.data?.errors)}`);
  const sGood = await call("PUT", `/api/v1/admin/problems/${pid}/grading-config`, { token: admin, json: { gradingConfig: GOOD_RULES } });
  check("save valid rules", sGood.status === 200 && sGood.data.gradingConfig?.includes("Main"), sGood.status);
  const pub = await call("GET", `/api/v1/problems/${pid}`, { token: admin });
  check("problem DTO never leaks grading/reference", !JSON.stringify(pub.data).includes("gradingConfig") && !JSON.stringify(pub.data).includes("referenceSolution"));

  section = "Authoring: reference solution & run";
  const noRef = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: {} });
  check("run without reference -> 400", noRef.status === 400, noRef.status);
  const badPath = await call("PUT", `/api/v1/admin/problems/${pid}/reference-solution`, { token: admin, json: { files: [{ path: "../evil.java", content: "x" }] } });
  check("path traversal rejected (400)", badPath.status === 400, badPath.status);
  const ref = await call("PUT", `/api/v1/admin/problems/${pid}/reference-solution`, { token: admin, json: { files: [{ path: "Main.java", content: MAIN }] } });
  check("save reference solution", ref.status === 200 && ref.data.referenceSolution[0].path === "Main.java", ref.status);
  const adhoc = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: { inputs: ["100\n", "abc\n"] } });
  check("ad-hoc run in sandbox: 100 -> 200", adhoc.status === 200 && adhoc.data.cases[0].status === "OK" && adhoc.data.cases[0].stdout.includes("200"), JSON.stringify(adhoc.data).slice(0, 300));
  check("ad-hoc bad input -> RUNTIME_ERROR", adhoc.data?.cases?.[1]?.status === "RUNTIME_ERROR", adhoc.data?.cases?.[1]?.status);
  const check1 = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: {} });
  const st = check1.data?.cases?.map((c) => c.status);
  // Lời giải in "Enter n: " trước ⇒ cả 2 case đều DIFF so với output thuần số; đó là hành vi đúng.
  check("check against cases returns per-case status", check1.status === 200 && st?.length === 2, JSON.stringify(st));
  const applied = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: { apply: true } });
  check("apply overwrites differing outputs", applied.status === 200 && applied.data.appliedCount === 2, JSON.stringify({ s: applied.status, n: applied.data?.appliedCount }));
  cases = await call("GET", `/api/v1/problems/${pid}/test-cases`, { token: admin });
  check("expected outputs now from reference", cases.data?.[0]?.expectedOutput === "Enter n: 42\n" && cases.data?.[1]?.expectedOutput === "Enter n: 10\n", JSON.stringify(cases.data?.map((c) => c.expectedOutput)));
  const again = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: {} });
  check("re-check: all MATCH", again.data?.cases?.every((c) => c.status === "MATCH"), JSON.stringify(again.data?.cases?.map((c) => c.status)));
  const adhocApply = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: { inputs: ["1\n"], apply: true } });
  check("ad-hoc + apply rejected (400)", adhocApply.status === 400, adhocApply.status);
  const compileErr = await call("PUT", `/api/v1/admin/problems/${pid}/reference-solution`, { token: admin, json: { files: [{ path: "Main.java", content: "public class Main { int x = ; }" }] } });
  const ce = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: { inputs: ["1\n"] } });
  check("compile error reported", compileErr.status === 200 && ce.data?.compiled === false && (ce.data.compileOutput ?? "").includes("Main.java"), JSON.stringify(ce.data).slice(0, 200));
  await call("PUT", `/api/v1/admin/problems/${pid}/reference-solution`, { token: admin, json: { files: [{ path: "Main.java", content: MAIN }] } });

  section = "Console live";
  const run = await consoleRun(admin, pid, [{ path: "Main.java", content: MAIN }], "21\n");
  check("console: prompt, stdin 21 -> 42, exit 0", run.out.includes("Enter n:") && run.out.includes("42") && run.exit?.code === 0, JSON.stringify(run));
  const runBad = await consoleRun("not-a-token", pid, [{ path: "Main.java", content: MAIN }], "1\n");
  check("console rejects invalid token", runBad.error && /log in/i.test(runBad.error), JSON.stringify(runBad));

  section = "Lifecycle";
  const bad = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: admin, json: { status: "PUBLISHED" } });
  check("DRAFT -> PUBLISHED rejected (409)", bad.status === 409, bad.status);
  const stuTr = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: student, json: { status: "REVIEWING" } });
  check("student cannot transition (403)", stuTr.status === 403, stuTr.status);
  const toRev = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: admin, json: { status: "REVIEWING" } });
  check("DRAFT -> REVIEWING", toRev.status === 200 && toRev.data.status === "REVIEWING", toRev.status);
  const toPub = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: admin, json: { status: "PUBLISHED" } });
  check("REVIEWING -> PUBLISHED (has public case)", toPub.status === 200 && toPub.data.status === "PUBLISHED", `${toPub.status} ${JSON.stringify(toPub.data?.detail ?? "")}`);
  const stuSees = await call("GET", `/api/v1/problems/${pid}`, { token: student });
  check("student sees published problem", stuSees.status === 200);
  const stuCases = await call("GET", `/api/v1/problems/${pid}/test-cases`, { token: student });
  check("student sees only public cases", stuCases.data?.length === 1 && stuCases.data.every((c) => !c.isHidden), JSON.stringify(stuCases.data?.map((c) => c.isHidden)));

  section = "Frozen when PUBLISHED";
  const fz = await call("GET", `/api/v1/admin/problems/${pid}/authoring`, { token: admin });
  check("authoring.gradingFrozen = true", fz.data?.gradingFrozen === true);
  const fg = await call("PUT", `/api/v1/admin/problems/${pid}/grading-config`, { token: admin, json: { gradingConfig: GOOD_RULES } });
  check("grading save -> 409", fg.status === 409, fg.status);
  const fa = await call("POST", `/api/v1/problems/${pid}/test-cases`, { token: admin, form: caseForm("1\n", "2\n", true) });
  check("add case -> 409", fa.status === 409, fa.status);
  const fApply = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: { apply: true } });
  check("apply -> 409", fApply.status === 409, fApply.status);
  const fRun = await call("POST", `/api/v1/admin/problems/${pid}/reference-run`, { token: admin, json: {} });
  check("read-only check still allowed", fRun.status === 200, fRun.status);
  const fRef = await call("PUT", `/api/v1/admin/problems/${pid}/reference-solution`, { token: admin, json: { files: [{ path: "Main.java", content: MAIN }] } });
  check("reference solution still editable", fRef.status === 200, fRef.status);
  const back = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: admin, json: { status: "DRAFT" } });
  check("PUBLISHED -> DRAFT rejected (409)", back.status === 409, back.status);
  const arch = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: admin, json: { status: "ARCHIVED" } });
  check("PUBLISHED -> ARCHIVED", arch.status === 200 && arch.data.status === "ARCHIVED", arch.status);
  const dr = await call("POST", `/api/v1/problems/${pid}/transitions`, { token: admin, json: { status: "DRAFT" } });
  check("ARCHIVED -> DRAFT", dr.status === 200 && dr.data.status === "DRAFT", dr.status);

  section = "Contests (staff)";
  const contests = await call("GET", "/api/v1/contests", { token: admin });
  check("list contests", contests.status === 200 && Array.isArray(contests.data), contests.status);
  const cid = contests.data?.[0]?.id;
  if (cid) {
    const rep = await call("GET", `/api/v1/contests/${cid}/proctoring/report`, { token: admin });
    check(`proctoring report (contest ${cid})`, rep.status === 200 && Array.isArray(rep.data), rep.status);
    const pl = await call("GET", `/api/v1/contests/${cid}/plagiarism`, { token: admin });
    check(`plagiarism report (contest ${cid})`, pl.status === 200 && Array.isArray(pl.data), pl.status);
    const lb = await call("GET", `/api/v1/contests/${cid}/leaderboard`, { token: admin });
    check("leaderboard", lb.status === 200, lb.status);
    const sRep = await call("GET", `/api/v1/contests/${cid}/proctoring/report`, { token: student });
    check("student cannot read proctoring (403)", sRep.status === 403, sRep.status);
    const sPl = await call("GET", `/api/v1/contests/${cid}/plagiarism`, { token: student });
    check("student cannot read plagiarism (403)", sPl.status === 403, sPl.status);
    const sUn = await call("POST", `/api/v1/contests/${cid}/participants/1/unlock`, { token: student });
    check("student cannot unlock (403)", sUn.status === 403, sUn.status);
    const aUn = await call("POST", `/api/v1/contests/${cid}/participants/987654321/unlock`, { token: admin });
    check("admin unlock unknown participant -> 404", aUn.status === 404, aUn.status);
  } else check("has a contest to test", false, "no contests");

  section = "Contest admin";
  {
  const sList = await call("GET", "/api/v1/admin/contests", { token: student });
  check("student cannot list admin contests (403)", sList.status === 403, sList.status);
  const pubList = await call("GET", "/api/v1/problems?status=PUBLISHED&size=1", { token: admin });
  const pubId = pubList.data?.content?.[0]?.id;
  check("found a published problem", !!pubId, pubList.status);
  const start = new Date(Date.now() + 24 * 3600_000);
  const mk = await call("POST", "/api/v1/admin/contests", { token: admin, json: {
    title: `ZZ Live Contest ${ts}`, startTime: start.toISOString(), endTime: new Date(start.getTime() + 2 * 3600_000).toISOString(),
    scoringPolicy: "ACM_ICPC", maxViolationsAllowed: 3, registrationMode: "INVITE_ONLY", problems: [{ problemId: pubId }] } });
  contestId = mk.data?.id;
  check("create invite-only contest", mk.status === 201 && mk.data.status === "SCHEDULED" && mk.data.problems?.[0]?.label === "A",
    `${mk.status} ${JSON.stringify(mk.data)}`);
  const bad = await call("POST", "/api/v1/admin/contests", { token: admin, json: {
    title: "bad", startTime: start.toISOString(), endTime: start.toISOString(), problems: [] } });
  check("end <= start rejected (400)", bad.status === 400, bad.status);
  const studentName = `smoke_stu_${ts}`;
  const add = await call("POST", `/api/v1/admin/contests/${contestId}/participants`, { token: admin,
    json: { entries: `${studentName}, khongtontai_${ts}` } });
  check("bulk add: added + not found", add.status === 200 && add.data.lines?.[0]?.outcome === "ADDED"
    && add.data.lines?.[1]?.outcome === "NOT_FOUND", `${add.status} ${JSON.stringify(add.data)}`);
  const reg2 = await call("POST", "/api/v1/auth/register", { json: { username: `smoke_out_${ts}`, email: `smoke_out_${ts}@example.com`, password: "Password123!" } });
  const outsider = reg2.data?.accessToken;
  const denied = await call("POST", `/api/v1/contests/${contestId}/register`, { token: outsider });
  check("outsider register -> 403 invite-only", denied.status === 403 && denied.data?.detail === "This contest is invite-only",
    `${denied.status} ${JSON.stringify(denied.data)}`);
  const invited = await call("POST", `/api/v1/contests/${contestId}/register`, { token: student });
  check("invited student register -> 200", invited.status === 200, invited.status);
  const archive = await call("POST", `/api/v1/problems/${pubId}/transitions`, { token: admin, json: { status: "ARCHIVED" } });
  check("archive problem used by contest -> 409", archive.status === 409 && String(archive.data?.detail).includes("ZZ Live Contest"),
    `${archive.status} ${JSON.stringify(archive.data)}`);
  const detail = await call("GET", `/api/v1/admin/contests/${contestId}`, { token: admin });
  const edit = { ...detail.data, title: `ZZ Live Contest ${ts} v2`, problems: null };
  const ok = await call("PUT", `/api/v1/admin/contests/${contestId}`, { token: admin, json: edit });
  check("edit scheduled contest", ok.status === 200 && ok.data.version > detail.data.version, ok.status);
  const stale = await call("PUT", `/api/v1/admin/contests/${contestId}`, { token: admin, json: edit });
  check("stale version -> 409", stale.status === 409, stale.status);
  const parts = await call("GET", `/api/v1/admin/contests/${contestId}/participants`, { token: admin });
  check("participants list", parts.status === 200 && parts.data.length === 1, parts.status);
  const rm = await call("DELETE", `/api/v1/admin/contests/${contestId}/participants/${parts.data?.[0]?.userId}`, { token: admin });
  check("remove participant without submissions", rm.status === 204, rm.status);
  }
} catch (e) {
  check("script error", false, e.stack);
} finally {
  section = "Cleanup";
  if (contestId) {
    const del = await call("DELETE", `/api/v1/admin/contests/${contestId}`, { token: admin });
    check("admin deletes throwaway contest", del.status === 204, `${del.status} ${JSON.stringify(del.data)}`);
    const gone = await call("GET", `/api/v1/admin/contests/${contestId}`, { token: admin });
    check("deleted contest -> 404", gone.status === 404, gone.status);
  }
  if (pid) {
    const del = await call("DELETE", `/api/v1/problems/${pid}`, { token: admin });
    check("delete throwaway problem", del.status === 204 || del.status === 200, `${del.status} ${JSON.stringify(del.data)}`);
    const gone = await call("GET", `/api/v1/problems/${pid}`, { token: admin });
    check("deleted problem -> 404", gone.status === 404, gone.status);
  }
  const failed = results.filter((r) => !r.ok);
  console.log(`\nTOTAL ${results.length}  PASS ${results.length - failed.length}  FAIL ${failed.length}`);
  console.log(`STUDENT=smoke_stu_${ts}`);
}
