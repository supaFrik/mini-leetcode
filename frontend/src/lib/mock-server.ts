/**
 * "Backend giả" chạy trong trình duyệt, dùng khi NEXT_PUBLIC_USE_MOCK=true.
 * Mục đích: cho phép làm và demo toàn bộ UI mà không cần backend.
 * Dữ liệu lưu ở localStorage (khoá bắt đầu bằng "mock:").
 *
 * Quy tắc của "judge giả" (xem judgeOutcome):
 *   - code còn y nguyên template ........ WA
 *   - có `while(true)` / `while True` .... TLE
 *   - có `throw new` / `raise ` .......... RE
 *   - có chữ "syntax error" .............. CE
 *   - còn lại ............................ AC
 */
import { ApiError } from "./api";
import { decodeUser, getToken } from "./auth";
import { TEMPLATES } from "./templates";
import { MOCK_STAGE_TOPICS, stageTopicRefs } from "./mock-topics";
export { attemptTopicStep, deleteTopic, getTopic, getTopicForAuthoring, listTopics, saveTopic, setTopicSkipped, transitionTopic } from "./mock-topics";
import type {
  PlagiarismRow, ProctorReportRow, CaseResult, ContestInfo, ContestProblem, Draft, Language, LeaderboardCell, LeaderboardRow, Page,
  ProblemFull, ProblemLifecycle, ProblemSummary, ProblemType, ProctoringEventType, ProctoringResult, Role, Submission, SubmitBody, TestCase, Verdict,
  AccessToken, CreatedAccessToken, StudyPlanDetail, StudyPlanDraft, StudyPlanStage, StudyPlanSummary,
  GradingValidation, ProblemAuthoring, ReferenceRunResult, SubmissionFile,
} from "@/types/api";

const sleep = (ms = 150) => new Promise<void>((r) => setTimeout(r, ms));
const store = {
  get<T>(k: string, fb: T): T { try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : fb; } catch { return fb; } },
  set(k: string, v: unknown) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* bỏ qua */ } },
  del(k: string) { try { localStorage.removeItem(k); } catch { /* bỏ qua */ } },
};
const forbidden = (detail: string) => new ApiError(403, { title: "Forbidden", status: 403, detail });
const notFound = (detail: string) => new ApiError(404, { title: "Resource Not Found", status: 404, detail });
const ensureOnline = () => { if (typeof navigator !== "undefined" && !navigator.onLine) throw new TypeError("Failed to fetch"); };
const me = () => decodeUser(getToken()) ?? { id: 1, username: "student", role: "USER" as Role };

// ===================== AUTH =====================
function b64url(o: object) {
  const bytes = new TextEncoder().encode(JSON.stringify(o));
  let s = "";
  bytes.forEach((b) => { s += String.fromCharCode(b); });
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
export function login(username: string) {
  const u = username.trim().toLowerCase() || "student";
  const role: Role = u.startsWith("admin") ? "ADMIN" : /^(teacher|instructor)/.test(u) ? "INSTRUCTOR" : "USER";
  const id = 1 + (u.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 900);
  return { accessToken: `${b64url({ alg: "none" })}.${b64url({ userId: id, username: u, role })}.mock`, refreshToken: "mock-refresh" };
}

// ===================== PROBLEMS =====================
const TITLES = ["Two Sum", "Valid Parentheses", "Merge Two Sorted Lists", "Best Time to Buy and Sell Stock",
  "Longest Substring Without Repeating Characters", "Binary Search", "Climbing Stairs", "Number of Islands",
  "LRU Cache", "Course Schedule", "Maximum Subarray", "Word Ladder"];
const TAGSETS = [["Array", "Hash Table"], ["Two Pointers", "Sorting"], ["Math", "Dynamic Programming"],
  ["Design", "Doubly-Linked List"], ["Array", "DFS / BFS"], ["Graph"]];
const DIFFS = ["EASY", "MEDIUM", "HARD"] as const;
const PROGRESS = ["DONE", "IN_PROGRESS", "NEVER"] as const;
const slugify = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");

const TWO_SUM_MD = [
  "Given two integers `a` and `b`, print their sum `a + b`.", "",
  "### Input", "A single line containing two space-separated integers `a` and `b`.", "",
  "### Output", "Print a single integer representing `a + b`.", "",
  "### Constraints", "- `-10^9 ≤ a, b ≤ 10^9`", "",
  "### Example", "```", "Input:  1 2", "Output: 3", "```",
].join("\n");

const PROBLEMS: ProblemFull[] = Array.from({ length: 36 }, (_, i) => {
  const id = i + 1;
  const title = i < TITLES.length ? TITLES[i] : `Placeholder #${id}`;
  return {
    id, title, slug: i < TITLES.length ? slugify(title) : `placeholder-${id}`,
    difficulty: DIFFS[i % 3], tags: TAGSETS[i % TAGSETS.length], progress: PROGRESS[i % 3],
    description: i === 0 ? TWO_SUM_MD : `Problem **${title}** (sample data).\n\nRead two integers \`a\`, \`b\` from input and output \`a + b\`.`,
    timeLimitMs: 1000, memoryLimitMb: 256, status: "PUBLISHED", likes: 40 + ((id * 7) % 60), dislikes: 5 + ((id * 3) % 30),
  };
});
const withEdits = (p: ProblemFull): ProblemFull => ({ ...p, ...store.get<Record<number, Partial<ProblemFull>>>("mock:problemEdits", {})[p.id] });

export async function listProblems(q: { page: number; q: string; difficulty: string; progress: string; tags: string[]; type?: ProblemType; size?: number }): Promise<Page<ProblemSummary>> {
  await sleep(); ensureOnline();
  const done = new Set(store.get<SubRec[]>("mock:subs", []).filter((s) => s.verdict === "AC" && !s.run).map((s) => s.problemId));
  const size = q.size ?? 8;
  const qText = q.q.trim();
  // Bộ lọc chủ đề dùng ngữ nghĩa OR + so khớp chính xác không phân biệt hoa/thường
  // (giống hệt backend: bài chỉ cần thuộc ÍT NHẤT MỘT chủ đề được chọn).
  const wanted = q.tags.map((t) => t.trim().toLowerCase()).filter((t) => t.length > 0);
  const all = PROBLEMS.map(withEdits).map((p) => ({ ...p, progress: done.has(p.id) ? ("DONE" as const) : p.progress }))
    .filter((p) =>
      (!qText || p.title.toLowerCase().includes(qText.toLowerCase()) || p.tags.join(" ").toLowerCase().includes(qText.toLowerCase()) || (/^[0-9]+$/.test(qText) && p.id === Number(qText))) &&
      (!q.difficulty || p.difficulty === q.difficulty) && (!q.progress || p.progress === q.progress) &&
      (!q.type || (p.type ?? "ALGORITHMIC") === q.type) &&
      (wanted.length === 0 || p.tags.some((t) => wanted.includes(t.toLowerCase().trim()))));
  const start = (q.page - 1) * size;
  return {
    content: all.slice(start, start + size).map(({ id, slug, title, difficulty, tags, progress, type }) => ({ id, slug, title, difficulty, tags, progress, type: type ?? "ALGORITHMIC" })),
    totalElements: all.length, totalPages: Math.max(1, Math.ceil(all.length / size)), number: q.page - 1, size,
  };
}
/** Danh sách chủ đề đang có trong catalogue, kèm số bài tương ứng. */
export async function listTags(type?: ProblemType): Promise<{ tag: string; count: number }[]> {
  await sleep(); ensureOnline();
  const counts = new Map<string, number>();
  for (const p of PROBLEMS) {
    if (type && (p.type ?? "ALGORITHMIC") !== type) continue;
    for (const t of p.tags) counts.set(t, (counts.get(t) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count || a.tag.localeCompare(b.tag));
}

export async function getProblem(idOrSlug: string): Promise<ProblemFull> {
  await sleep(); ensureOnline();
  const p = PROBLEMS.find((x) => String(x.id) === idOrSlug || x.slug === idOrSlug);
  if (!p) throw notFound(`Problem not found: ${idOrSlug}`);
  return withEdits(p);
}
export async function updateProblem(id: number, patch: Partial<ProblemFull>): Promise<ProblemFull> {
  await sleep(); ensureOnline();
  const edits = store.get<Record<number, Partial<ProblemFull>>>("mock:problemEdits", {});
  edits[id] = { ...edits[id], ...patch };
  store.set("mock:problemEdits", edits);
  return getProblem(String(id));
}

/**
 * Chuyển trạng thái vòng đời — giữ đúng luật của backend:
 * - chỉ đi theo cạnh hợp lệ (DRAFT → REVIEWING → PUBLISHED → ARCHIVED),
 * - PUBLISHED đòi hỏi ít nhất một testcase công khai,
 * - chuyển sai trạng thái trả về 409 Conflict.
 */
const LIFECYCLE_EDGES: Record<string, string[]> = {
  DRAFT: ["REVIEWING", "ARCHIVED"],
  REVIEWING: ["DRAFT", "PUBLISHED"],
  PUBLISHED: ["ARCHIVED"],
  ARCHIVED: ["DRAFT"],
};

/** Các trạng thái hợp lệ có thể chuyển tới từ trạng thái hiện tại. */
export function allowedTransitions(from: string): string[] {
  return LIFECYCLE_EDGES[from] ?? [];
}

export async function transitionProblem(id: number, target: ProblemLifecycle): Promise<ProblemFull> {
  await sleep(); ensureOnline();
  const p = PROBLEMS.find((x) => x.id === id);
  if (!p) throw notFound(`Problem not found: ${id}`);
  const current = (withEdits(p).status ?? "DRAFT") as ProblemLifecycle;
  if (current === target) {
    throw new ApiError(409, { title: "Resource Conflict", status: 409, detail: `Problem is already in status ${target}` });
  }
  if (!(LIFECYCLE_EDGES[current] ?? []).includes(target)) {
    throw new ApiError(409, {
      title: "Resource Conflict", status: 409,
      detail: `Cannot transition problem from ${current} to ${target}. Allowed targets: ${(LIFECYCLE_EDGES[current] ?? []).join(", ")}`,
    });
  }
  if (target === "PUBLISHED" && !casesFor(id).some((c) => !c.isHidden)) {
    throw new ApiError(409, {
      title: "Resource Conflict", status: 409,
      detail: `Cannot publish problem ${id}: at least one public test case is required`,
    });
  }
  return updateProblem(id, { status: target });
}

/* -------------------- ADMIN -------------------- */

/**
 * Danh sách bài cho trang quản trị. Khác với `listProblems`, staff thấy được
 * cả bài chưa publish, và lọc theo trạng thái vòng đời.
 */
export async function listAdminProblems(q: { page: number; q: string; difficulty: string; status: string; tags: string[]; type?: ProblemType }): Promise<Page<ProblemFull>> {
  await sleep(); ensureOnline();
  const size = 20;
  const wanted = q.tags.map((t) => t.trim().toLowerCase()).filter((t) => t.length > 0);
  const all = PROBLEMS.map(withEdits).filter((p) =>
    (!q.q || p.title.toLowerCase().includes(q.q.toLowerCase()) || p.description.toLowerCase().includes(q.q.toLowerCase())) &&
    (!q.difficulty || p.difficulty === q.difficulty) &&
    (!q.status || (p.status ?? "PUBLISHED") === q.status) &&
    (!q.type || (p.type ?? "ALGORITHMIC") === q.type) &&
    (wanted.length === 0 || p.tags.some((t) => wanted.includes(t.toLowerCase().trim())))
  );
  const start = (q.page - 1) * size;
  return {
    content: all.slice(start, start + size),
    totalElements: all.length, totalPages: Math.max(1, Math.ceil(all.length / size)), number: q.page - 1, size,
  };
}

export async function createProblem(draft: {
  title: string; description: string; difficulty: string; timeLimitMs: number; memoryLimitMb: number; tags: string[];
  type?: ProblemType; starterTemplates?: string | null;
}): Promise<ProblemFull> {
  await sleep(); ensureOnline();
  const id = Math.max(0, ...PROBLEMS.map((p) => p.id)) + 1;
  const created: ProblemFull = {
    id, title: draft.title, slug: slugify(draft.title), difficulty: draft.difficulty as ProblemFull["difficulty"],
    tags: draft.tags, progress: "NEVER", description: draft.description,
    timeLimitMs: draft.timeLimitMs, memoryLimitMb: draft.memoryLimitMb,
    status: "DRAFT", likes: 0, dislikes: 0,
    type: draft.type ?? "ALGORITHMIC",
    starterTemplates: draft.starterTemplates ?? null,
  };
  PROBLEMS.push(created);
  return created;
}

export async function deleteProblem(id: number): Promise<void> {
  await sleep(); ensureOnline();
  const idx = PROBLEMS.findIndex((p) => p.id === id);
  if (idx === -1) throw notFound(`Problem not found: ${id}`);
  const hasSubs = store.get<SubRec[]>("mock:subs", []).some((s) => s.problemId === id);
  if (hasSubs) {
    throw new ApiError(409, {
      title: "Resource Conflict", status: 409,
      detail: `Cannot hard delete problem ${id}: it has submission history. Archive it instead.`,
    });
  }
  PROBLEMS.splice(idx, 1);
}

const BASE_CASES: [string, string][] = [["1 2", "3"], ["5 7", "12"], ["-3 3", "0"], ["100 200", "300"], ["999 1", "1000"]];
function casesFor(problemId: number): TestCase[] {
  const base = BASE_CASES.map(([input, expectedOutput], idx) => ({
    id: problemId * 100 + idx + 1, input, expectedOutput, isHidden: idx >= 3, orderIndex: idx,
    explanation: idx === 0 ? "The numbers 1 and 2 sum up to 3." : undefined,
  }));
  return [...base, ...store.get<Record<number, TestCase[]>>("mock:extraCases", {})[problemId] ?? []];
}
export async function getTestCases(problemId: number): Promise<TestCase[]> {
  await sleep(); ensureOnline();
  return casesFor(problemId).filter((c) => me().role !== "USER" || !c.isHidden);
}
export async function addTestCase(problemId: number, c: { input: string; expectedOutput: string; isHidden: boolean }): Promise<TestCase> {
  await sleep(); ensureOnline();
  const all = store.get<Record<number, TestCase[]>>("mock:extraCases", {});
  const list = all[problemId] ?? [];
  const created: TestCase = { id: problemId * 100 + 50 + list.length, ...c, orderIndex: casesFor(problemId).length };
  all[problemId] = [...list, created];
  store.set("mock:extraCases", all);
  return created;
}

function editExtraCase(problemId: number, id: number, edit: (list: TestCase[], idx: number) => TestCase[]) {
  const all = store.get<Record<number, TestCase[]>>("mock:extraCases", {});
  const list = all[problemId] ?? [];
  const idx = list.findIndex((c) => c.id === id);
  if (idx < 0) throw new ApiError(409, { title: "Conflict", status: 409, detail: "Built-in mock test cases are read-only." });
  all[problemId] = edit(list, idx);
  store.set("mock:extraCases", all);
  return all[problemId];
}
export async function updateTestCase(problemId: number, id: number, c: { input?: string; expectedOutput?: string; isHidden?: boolean }): Promise<TestCase> {
  await sleep(); ensureOnline();
  const list = editExtraCase(problemId, id, (l, i) => l.map((x, j) => (j === i ? { ...x, ...c } : x)));
  return list.find((x) => x.id === id)!;
}
export async function deleteTestCase(problemId: number, id: number): Promise<void> {
  await sleep(); ensureOnline();
  editExtraCase(problemId, id, (l, i) => l.filter((_, j) => j !== i));
}

// ===================== AUTHORING (mock: lưu localStorage, "chạy" lời giải mẫu = echo stdin) =====================
interface MockAuthoring { gradingConfig: string | null; referenceSolution: SubmissionFile[] }
function authoringOf(problemId: number): MockAuthoring {
  return store.get<Record<number, MockAuthoring>>("mock:authoring", {})[problemId] ?? { gradingConfig: null, referenceSolution: [] };
}
async function toAuthoring(problemId: number): Promise<ProblemAuthoring> {
  const p = await getProblem(String(problemId));
  return {
    problemId, status: p.status ?? "DRAFT", problemType: p.type ?? "ALGORITHMIC", ...authoringOf(problemId),
    gradingFrozen: p.status === "PUBLISHED", timeLimitMs: p.timeLimitMs, memoryLimitMb: p.memoryLimitMb,
  };
}
function putAuthoring(problemId: number, patch: Partial<MockAuthoring>) {
  const all = store.get<Record<number, MockAuthoring>>("mock:authoring", {});
  all[problemId] = { ...authoringOf(problemId), ...patch };
  store.set("mock:authoring", all);
}
export async function getAuthoring(problemId: number): Promise<ProblemAuthoring> {
  await sleep(); ensureOnline();
  return toAuthoring(problemId);
}
export async function validateGradingConfig(raw: string): Promise<GradingValidation> {
  await sleep();
  try {
    const parsed = JSON.parse(raw);
    const rules = Array.isArray(parsed?.rules) ? parsed.rules : [];
    const scenarios = Array.isArray(parsed?.socketScenarios) ? parsed.socketScenarios : [];
    const errors = rules.flatMap((r: { targetClassName?: string }, i: number) =>
      r?.targetClassName ? [] : [`rules[${i}].targetClassName: required`]);
    return { valid: errors.length === 0, errors, ruleCount: rules.length, socketScenarioCount: scenarios.length,
      totalRuleWeight: rules.reduce((sum: number, r: { weight?: number }) => sum + (r?.weight ?? 1), 0) };
  } catch (e) {
    return { valid: false, errors: [`Not valid JSON: ${(e as Error).message}`], ruleCount: 0, socketScenarioCount: 0, totalRuleWeight: 0 };
  }
}
export async function saveGradingConfig(problemId: number, raw: string | null): Promise<ProblemAuthoring> {
  if (raw && raw.trim()) {
    const v = await validateGradingConfig(raw);
    if (!v.valid) throw new ApiError(400, { title: "Invalid Grading Configuration", status: 400, detail: "Grading configuration is invalid", errors: v.errors });
  }
  putAuthoring(problemId, { gradingConfig: raw && raw.trim() ? raw.trim() : null });
  return toAuthoring(problemId);
}
export async function saveReferenceSolution(problemId: number, files: SubmissionFile[]): Promise<ProblemAuthoring> {
  await sleep();
  putAuthoring(problemId, { referenceSolution: files });
  return toAuthoring(problemId);
}
export async function runReference(problemId: number, body: { inputs?: string[]; testCaseIds?: number[]; apply?: boolean }): Promise<ReferenceRunResult> {
  await sleep(400); ensureOnline();
  if (authoringOf(problemId).referenceSolution.length === 0) {
    throw new ApiError(400, { title: "Bad Request", status: 400, detail: "Save a reference solution before running it" });
  }
  const run = (stdin: string) => ({ stdout: stdin, stderr: "", exitCode: 0, runtimeMs: 120 });
  if (body.inputs?.length) {
    return { compiled: true, compileOutput: null, mainClass: "Main", error: null, appliedCount: 0,
      cases: body.inputs.map((i) => ({ testCaseId: null, orderIndex: null, hidden: null, status: "OK" as const, applied: false, ...run(i) })) };
  }
  const cases = casesFor(problemId).filter((c) => !body.testCaseIds?.length || body.testCaseIds.includes(c.id));
  let applied = 0;
  const results = await Promise.all(cases.map(async (c) => {
    const r = run(c.input);
    const same = r.stdout.trimEnd() === c.expectedOutput.trimEnd();
    let didApply = false;
    if (body.apply && !same) {
      try { await updateTestCase(problemId, c.id, { expectedOutput: r.stdout }); didApply = true; applied++; } catch { /* built-in case */ }
    }
    return { testCaseId: c.id, orderIndex: c.orderIndex, hidden: c.isHidden, status: same ? "MATCH" as const : "DIFF" as const, applied: didApply, ...r };
  }));
  return { compiled: true, compileOutput: null, mainClass: "Main", error: null, cases: results, appliedCount: applied };
}

// ===================== SUBMISSIONS =====================
interface SubRec {
  id: number; userId: number; username: string; problemId: number; contestId: number | null; language: Language; code: string;
  run: boolean; createdAt: number; verdict: Verdict; passed: number; total: number; timeMs: number; memKb: number; error: string | null;
}
const JUDGING_AT = 1500, DONE_AT = 3200; // ms kể từ lúc nộp

function judgeOutcome(code: string, language: Language, total: number) {
  const base = { timeMs: 40 + (code.length % 80), memKb: 30000 + (code.length % 9000), error: null as string | null };
  if (/syntax error/i.test(code)) return { ...base, verdict: "CE" as Verdict, passed: 0, error: "Solution.java:3: error: ';' expected\n        int x = 5\n                 ^\n1 error" };
  if (/while\s*\(\s*true\s*\)|while True/.test(code)) return { ...base, verdict: "TLE" as Verdict, passed: Math.min(1, total), timeMs: 1000 };
  if (/throw new|raise /.test(code)) return { ...base, verdict: "RE" as Verdict, passed: Math.min(1, total), error: "Exception in thread \"main\" java.lang.RuntimeException\n\tat Solution.main(Solution.java:4)" };
  if (code.trim() === TEMPLATES[language].trim()) return { ...base, verdict: "WA" as Verdict, passed: Math.min(1, total) };
  return { ...base, verdict: "AC" as Verdict, passed: total };
}

export async function submit(problemId: number, body: SubmitBody, run: boolean): Promise<Submission> {
  await sleep(250); ensureOnline();
  const effectiveCode = body.code ?? (body.files && body.files.length > 0 ? body.files.map((f) => `// === FILE: ${f.path} ===\n${f.content}`).join("\n\n") : "");
  if (!effectiveCode.trim()) throw new ApiError(400, { title: "Bad Request", status: 400, detail: "Validation failed", invalid_params: { code: "Code must not be blank" } });
  if (effectiveCode.length > 64 * 1024) throw new ApiError(400, { title: "Bad Request", status: 400, detail: "Validation failed", invalid_params: { code: "Code must not exceed 64KB" } });
  if (body.contestId != null) {
    const c = await getContest(body.contestId);
    if (c.status !== "RUNNING") throw forbidden("Contest is not currently active");
    if (!c.myStatus) throw forbidden(`User is not registered for contest id: ${body.contestId}`);
    if (readProctor(body.contestId).dq) throw forbidden("User has been disqualified from this contest due to proctoring violations");
  }
  const u = me();
  const total = casesFor(problemId).filter((c) => !run || !c.isHidden).length;
  const out = judgeOutcome(effectiveCode, body.language, total);
  const subs = store.get<SubRec[]>("mock:subs", []);
  const rec: SubRec = {
    id: 500 + subs.length + 1, userId: u.id, username: u.username, problemId, contestId: body.contestId ?? null, language: body.language,
    code: effectiveCode, run, createdAt: Date.now(), total, ...out,
  };
  store.set("mock:subs", [...subs, rec]);
  return toSubmission(rec, 0);
}

function toSubmission(r: SubRec, elapsed: number): Submission {
  const p = PROBLEMS.find((x) => x.id === r.problemId);
  const base = {
    id: r.id, problemId: r.problemId, problemTitle: p?.title, contestId: r.contestId, userId: r.userId, username: r.username,
    language: r.language, createdAt: new Date(r.createdAt).toISOString(), updatedAt: new Date(r.createdAt + elapsed).toISOString(),
  };
  if (elapsed < JUDGING_AT) return { ...base, status: "PENDING", verdict: null, executionTimeMs: null, memoryUsedKb: null, passTestCases: 0, totalTestCases: 0, errorMessage: null };
  if (elapsed < DONE_AT) return { ...base, status: "JUDGING", verdict: null, executionTimeMs: null, memoryUsedKb: null, passTestCases: 0, totalTestCases: 0, errorMessage: null };
  return {
    ...base, status: "COMPLETED", verdict: r.verdict, executionTimeMs: r.timeMs, memoryUsedKb: r.memKb, passTestCases: r.passed,
    totalTestCases: r.total, errorMessage: r.error, memoryPercentile: 40 + ((r.id * 7) % 55),
  };
}
const findSub = (id: number) => {
  const r = store.get<SubRec[]>("mock:subs", []).find((s) => s.id === id);
  if (!r) throw notFound(`Submission not found with id: ${id}`);
  return r;
};
export async function getSubmission(id: number): Promise<Submission> {
  await sleep(100); ensureOnline();
  const r = findSub(id);
  return toSubmission(r, Date.now() - r.createdAt);
}
/** Khoá verdict như backend: bài FAILED chưa có verdict là RE, chưa chấm xong là PENDING. */
const verdictKey = (s: Submission) => s.verdict ?? (s.status === "FAILED" ? "RE" : "PENDING");

export async function mySubmissions(page: number, size: number, filter: { verdict?: string; language?: string } = {}): Promise<Page<Submission>> {
  await sleep(); ensureOnline();
  const u = me();
  const mine = store.get<SubRec[]>("mock:subs", []).filter((s) => s.userId === u.id && !s.run).sort((a, b) => b.createdAt - a.createdAt)
    .map((r) => toSubmission(r, Date.now() - r.createdAt))
    .filter((s) => (!filter.verdict || verdictKey(s) === filter.verdict) && (!filter.language || s.language === filter.language));
  return {
    content: mine.slice(page * size, page * size + size),
    totalElements: mine.length, totalPages: Math.max(1, Math.ceil(mine.length / size)), number: page, size,
  };
}
/** Như /submissions/my/summary: đếm theo verdict (chưa chấm xong là PENDING), bỏ Run. */
export async function mySubmissionSummary(): Promise<Record<string, number>> {
  await sleep(); ensureOnline();
  const u = me();
  const counts: Record<string, number> = {};
  for (const r of store.get<SubRec[]>("mock:subs", []).filter((s) => s.userId === u.id && !s.run)) {
    const key = verdictKey(toSubmission(r, Date.now() - r.createdAt));
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return counts;
}
export async function submissionCode(id: number): Promise<string> { await sleep(100); return findSub(id).code; }

const wrong = (s: string) => (s.trim() !== "" && !Number.isNaN(Number(s)) ? String(Number(s) + 1) : "wrong");
export async function submissionResults(id: number): Promise<CaseResult[]> {
  await sleep(150); ensureOnline();
  const r = findSub(id);
  if (r.verdict === "CE") return [];
  return casesFor(r.problemId).filter((c) => !r.run || !c.isHidden).map((c, i): CaseResult => {
    const failing = r.verdict !== "AC" && i === r.passed;
    const verdict: CaseResult["verdict"] = i < r.passed ? "AC" : failing ? (["WA", "TLE", "MLE", "RE"].includes(r.verdict) ? (r.verdict as "WA") : "RE") : "SKIPPED";
    const shown = !c.isHidden;
    return {
      index: i, verdict, hidden: c.isHidden, timeMs: verdict === "SKIPPED" ? undefined : 20 + ((i * 13 + r.id) % 60),
      input: shown ? c.input : undefined, expected: shown ? c.expectedOutput : undefined,
      actual: !shown || verdict === "SKIPPED" ? undefined : verdict === "AC" ? c.expectedOutput : verdict === "WA" ? wrong(c.expectedOutput) : "",
    };
  });
}

// ===================== CONTESTS =====================
const PIDS = [1, 2, 3];
const contestProblems = (): ContestProblem[] =>
  PIDS.map((id, i) => { const p = PROBLEMS[id - 1]; return { id, slug: p.slug, title: p.title, difficulty: p.difficulty, label: String.fromCharCode(65 + i) }; });

interface ProctorState { count: number; away: number | null; dq: boolean }
const readProctor = (cid: number) => store.get<ProctorState>(`mock:proctor:${cid}`, { count: 0, away: null, dq: false });

function contestWindow(id: number) {
  const now = Date.now();
  if (id === 1) {
    let end = store.get<number>("mock:c1:end", 0);
    if (!end) { end = now + 45 * 60_000; store.set("mock:c1:end", end); }
    return { start: end - 90 * 60_000, end };
  }
  if (id === 2) return { start: now + 2 * 86_400_000, end: now + 2 * 86_400_000 + 2 * 3_600_000 };
  if (id === 3) return { start: now - 86_400_000 - 2 * 3_600_000, end: now - 86_400_000 };
  throw notFound(`Contest not found with id: ${id}`);
}
const registered = (id: number) => store.get<number[]>("mock:reg", [1, 3]).includes(id); // kỳ thi 1 và 3 mặc định đã đăng ký
const TITLES_C: Record<number, string> = { 1: "Midterm Exam – Data Structures", 2: "Weekly Contest #12", 3: "Weekly Contest #11" };

export async function getContest(id: number): Promise<ContestInfo> {
  await sleep(120); ensureOnline();
  const w = contestWindow(id);
  const now = Date.now();
  const ps = readProctor(id);
  const status = now < w.start ? "SCHEDULED" : now >= w.end ? "COMPLETED" : "RUNNING";
  return {
    id, title: TITLES_C[id], startTime: new Date(w.start).toISOString(), endTime: new Date(w.end).toISOString(), status,
    scoringPolicy: "ACM_ICPC", maxViolationsAllowed: 3, problems: contestProblems(),
    myStatus: !registered(id) ? undefined : ps.dq ? "DISQUALIFIED" : ps.count > 0 ? "IN_PROGRESS" : "REGISTERED", myViolations: ps.count,
  };
}
export async function listContests(): Promise<ContestInfo[]> { return Promise.all([1, 2, 3].map(getContest)); }

export async function proctoringEvent(contestId: number, type: ProctoringEventType): Promise<ProctoringResult> {
  await sleep(80); ensureOnline();
  const c = await getContest(contestId);
  if (c.status !== "RUNNING") throw forbidden("Contest is not currently active");
  if (!c.myStatus) throw forbidden(`User is not registered for contest id: ${contestId}`);
  const max = c.maxViolationsAllowed;
  const s = readProctor(contestId);
  const u = me();
  let awayDurationMs: number | null = null;
  let message = "Event processed";
  const out = (): ProctoringResult => {
    store.set(`mock:proctor:${contestId}`, s);
    return { contestId, userId: u.id, status: s.dq ? "DISQUALIFIED" : "IN_PROGRESS", violationCount: s.count, maxViolationsAllowed: max, isDisqualified: s.dq, awayDurationMs, message };
  };
  if (s.dq) { message = "Already disqualified"; return out(); }
  const punish = (ok: string, dq: string) => { s.count += 1; if (s.count >= max) { s.dq = true; message = `${dq} (${s.count}/${max})`; } else message = `${ok} (${s.count}/${max})`; };

  if (type === "WINDOW_BLUR") { s.away = Date.now(); message = "Window blur recorded"; }
  else if (type === "WINDOW_FOCUS") {
    if (s.away == null) { message = "No pending blur"; return out(); }
    awayDurationMs = Date.now() - s.away; s.away = null; // Server tự tính ΔT, không tin số liệu từ client
    if (awayDurationMs < 1500) message = `Within grace period (${awayDurationMs}ms), no violation`;
    else punish(`Violation recorded: away for ${awayDurationMs}ms`, "Disqualified: exceeded maximum permitted window switches");
  } else punish("Violation recorded: unauthorized clipboard paste detected", "Disqualified: unauthorized clipboard paste detected");
  return out();
}

const NAMES = ["alice", "bob", "charlie", "david", "eve", "frank", "grace", "heidi"];
export async function leaderboard(contestId: number): Promise<LeaderboardRow[]> {
  await sleep(150); ensureOnline();
  const w = contestWindow(contestId);
  const rows: Omit<LeaderboardRow, "rank">[] = NAMES.map((username, i) => {
    const cells: Record<number, LeaderboardCell> = {};
    PIDS.forEach((pid, k) => { cells[pid] = i < 6 - k * 2 ? { solved: true, attempts: (i + k) % 3, minute: 8 + i * 6 + k * 20 } : { solved: false, attempts: (i + k) % 2 }; });
    return { userId: 100 + i, username, cells, ...tally(cells) };
  });
  // Dòng của chính bạn, tính từ các bài nộp trong kỳ thi này
  const u = me();
  const mine = store.get<SubRec[]>("mock:subs", []).filter((s) => s.userId === u.id && s.contestId === contestId && !s.run && Date.now() - s.createdAt >= DONE_AT)
    .sort((a, b) => a.createdAt - b.createdAt);
  const cells: Record<number, LeaderboardCell> = {};
  PIDS.forEach((pid) => {
    const list = mine.filter((s) => s.problemId === pid);
    const firstAc = list.findIndex((s) => s.verdict === "AC");
    if (firstAc >= 0) cells[pid] = { solved: true, attempts: firstAc, minute: Math.max(0, Math.floor((list[firstAc].createdAt - w.start) / 60_000)) };
    else if (list.length) cells[pid] = { solved: false, attempts: list.length };
  });
  rows.push({ userId: u.id, username: u.username, cells, disqualified: readProctor(contestId).dq, ...tally(cells) });

  const ok = rows.filter((r) => !r.disqualified).sort((a, b) => b.solved - a.solved || a.penaltyMinutes - b.penaltyMinutes);
  return [...ok.map((r, i) => ({ ...r, rank: i + 1 })), ...rows.filter((r) => r.disqualified).map((r) => ({ ...r, rank: null }))];
}
// Penalty = Σ (T_AC + 20 × N_WA) chỉ tính trên bài đã AC (luật ACM/ICPC)
function tally(cells: Record<number, LeaderboardCell>) {
  const solved = Object.values(cells).filter((c) => c.solved);
  return { solved: solved.length, penaltyMinutes: solved.reduce((a, c) => a + (c.minute ?? 0) + 20 * c.attempts, 0) };
}

export function resetExam(contestId: number) {
  store.del(`mock:proctor:${contestId}`);
  if (contestId === 1) store.del("mock:c1:end");
  store.set("mock:subs", store.get<SubRec[]>("mock:subs", []).filter((s) => s.contestId !== contestId));
  try { Object.keys(localStorage).filter((k) => k.startsWith(`mock:draft:${contestId}:`) || k.startsWith(`draft:${contestId}:`)).forEach((k) => localStorage.removeItem(k)); } catch { /* bỏ qua */ }
}

// ===================== DRAFT (giả lập "server") =====================
const draftKey = (c: number, p: number, l: Language) => `mock:draft:${c}:${p}:${l}`;
export async function loadDraft(c: number, p: number, l: Language): Promise<Draft | null> { await sleep(100); ensureOnline(); return store.get<Draft | null>(draftKey(c, p, l), null); }
export async function saveDraft(c: number, p: number, l: Language, value: string): Promise<void> { await sleep(150); ensureOnline(); store.set(draftKey(c, p, l), { value, savedAt: Date.now() }); }

// ===================== ĐĂNG KÝ / BÁO CÁO GIÁM SÁT / ĐẠO VĂN =====================
export async function registerContest(id: number): Promise<void> {
  await sleep(); ensureOnline(); contestWindow(id);
  store.set("mock:reg", Array.from(new Set([...store.get<number[]>("mock:reg", [1, 3]), id])));
}
export async function proctoringReport(contestId: number): Promise<ProctorReportRow[]> {
  await sleep(); ensureOnline(); contestWindow(contestId);
  const unlocked = store.get<number[]>(`mock:unlocked:${contestId}`, []);
  const counts = [0, 1, 0, 2, 3, 0, 1, 0];
  const status = (n: number) => (n >= 3 ? "DISQUALIFIED" : n > 0 ? "IN_PROGRESS" : "REGISTERED") as ProctorReportRow["status"];
  const rows: ProctorReportRow[] = NAMES.map((username, i) => {
    const n = unlocked.includes(100 + i) ? 0 : counts[i];
    return { userId: 100 + i, username, violationCount: n, status: status(n), lastEventAt: n ? new Date(Date.now() - (i + 1) * 420_000).toISOString() : undefined };
  });
  if (registered(contestId)) { const s = readProctor(contestId), u = me(); rows.push({ userId: u.id, username: u.username, violationCount: s.count, status: s.dq ? "DISQUALIFIED" : status(s.count) }); }
  return rows;
}
export async function unlockParticipant(contestId: number, userId: number): Promise<void> {
  await sleep(); ensureOnline();
  if (userId === me().id) { store.del(`mock:proctor:${contestId}`); return; }
  store.set(`mock:unlocked:${contestId}`, [...store.get<number[]>(`mock:unlocked:${contestId}`, []), userId]);
}
export async function plagiarism(contestId: number): Promise<PlagiarismRow[]> {
  await sleep(); ensureOnline(); contestWindow(contestId);
  return [
    { id: 1, problemId: 1, problemTitle: "Two Sum", userA: "alice", userB: "bob", similarity: 0.93 },
    { id: 2, problemId: 2, problemTitle: "Valid Parentheses", userA: "charlie", userB: "david", similarity: 0.84 },
  ];
}

// ---------- Study plans (giống seed "OOP với Java" ở backend/tools/problems/projects/mini-bank) ----------
const bankStage = (n: number, title: string, status: StudyPlanStage["status"]): StudyPlanStage => ({
  stageNo: n, problemId: 100 + n, slug: `mini-bank-stage-${n}`, title: `Mini Bank ${n}/5 — ${title}`,
  difficulty: n <= 2 ? "EASY" : "MEDIUM", problemType: "OOP_GRINDING", published: true, status,
});
const MOCK_PLAN: StudyPlanDetail = {
  id: 1, slug: "oop-voi-java", title: "OOP với Java", status: "PUBLISHED", stageCount: 7, doneStages: 2,
  description: "Từ lớp và đóng gói tới kế thừa, đa hình, exception: hai bài luyện ngắn rồi xây một ngân hàng mini chạy trên console qua 5 stage.",
  items: [
    { id: 1, position: 0, kind: "PROBLEM", title: "Luyện: lớp & đóng gói", done: true, stages: [
      { stageNo: 1, problemId: 13, slug: "bank-account-class", title: "Bank Account Class", difficulty: "EASY", problemType: "ALGORITHMIC", published: true, status: "DONE" }] },
    { id: 2, position: 1, kind: "PROBLEM", title: "Luyện: kế thừa & đa hình", done: false, stages: [
      { stageNo: 1, problemId: 14, slug: "shape-area-hierarchy", title: "Shape Area Hierarchy", difficulty: "EASY", problemType: "ALGORITHMIC", published: true, status: "IN_PROGRESS" }] },
    { id: 3, position: 2, kind: "PROJECT", title: "Project: Mini Bank Console", done: false,
      description: "Ngân hàng mini đọc lệnh bằng Scanner. Mỗi stage thêm tính năng; code của stage trước vẫn phải chạy đúng.",
      stages: [bankStage(1, "Tài khoản & đóng gói", "DONE"), bankStage(2, "Kế thừa & đa hình", "NEVER"),
        bankStage(3, "Exception & chuyển khoản", "LOCKED"), bankStage(4, "Lớp Bank & mở tài khoản", "LOCKED"),
        bankStage(5, "Hoàn thiện CLI", "LOCKED")] },
  ],
};
MOCK_PLAN.createdById = 2;
MOCK_PLAN.createdByUsername = "instructor";
/** Plan trong bộ nhớ (mất khi tải lại trang) — đủ để thử màn hình soạn plan ở chế độ mock. */
const mockPlans: StudyPlanDetail[] = [MOCK_PLAN];
const findPlan = (slug: string) => {
  const plan = mockPlans.find((p) => p.slug === slug);
  if (!plan) throw notFound(`Study plan not found: ${slug}`);
  return plan;
};
export async function listStudyPlans(): Promise<StudyPlanSummary[]> {
  await sleep(); ensureOnline();
  return mockPlans.map(({ items, ...rest }) => ({ ...rest, itemCount: items.length }));
}
/** id chủ đề theo (id mục, stageNo) của các plan mock; plan mẫu gắn sẵn chủ đề vào project Mini Bank. */
const mockStageTopics = new Map<string, number[]>(
  Object.entries(MOCK_STAGE_TOPICS).map(([stageNo, ids]) => [`3:${stageNo}`, ids]),
);
export async function getStudyPlan(slug: string): Promise<StudyPlanDetail> {
  await sleep(); ensureOnline();
  const plan = findPlan(slug);
  return { ...plan, items: plan.items.map((item) => ({ ...item, stages: item.stages.map((st) => ({
    ...st, topics: stageTopicRefs(mockStageTopics.get(`${item.id}:${st.stageNo}`) ?? []) })) })) };
}
export async function saveStudyPlan(slug: string | null, draft: StudyPlanDraft): Promise<StudyPlanDetail> {
  await sleep(); ensureOnline();
  const me = decodeUser(getToken());
  const old = slug ? findPlan(slug) : null;
  const plan: StudyPlanDetail = {
    id: old?.id ?? Date.now(), slug: draft.slug, title: draft.title, description: draft.description ?? null,
    status: old?.status ?? "DRAFT", createdById: old?.createdById ?? me?.id, createdByUsername: old?.createdByUsername ?? me?.username,
    stageCount: draft.items.reduce((n, i) => n + i.problemIds.length, 0), doneStages: 0,
    items: draft.items.map((it, position) => ({
      id: position + 1, position, kind: it.kind, title: it.title, description: it.description ?? null, done: false,
      stages: it.problemIds.map((problemId, k) => ({ stageNo: k + 1, problemId, slug: `problem-${problemId}`, title: `Problem #${problemId}`,
        difficulty: "EASY", problemType: "ALGORITHMIC", published: true, status: k === 0 ? "NEVER" : "LOCKED" })),
    })),
  };
  draft.items.forEach((it, position) => (it.stageTopics ?? []).forEach((ids, k) => mockStageTopics.set(`${position + 1}:${k + 1}`, ids)));
  if (old) mockPlans.splice(mockPlans.indexOf(old), 1, plan); else mockPlans.push(plan);
  return getStudyPlan(plan.slug);
}
export async function transitionStudyPlan(slug: string, status: StudyPlanDetail["status"]): Promise<StudyPlanDetail> {
  await sleep(); ensureOnline();
  const plan = findPlan(slug);
  plan.status = status;
  return plan;
}
export async function deleteStudyPlan(slug: string): Promise<void> {
  await sleep(); ensureOnline();
  mockPlans.splice(mockPlans.indexOf(findPlan(slug)), 1);
}

// ---------- Personal access tokens ----------
const TOKENS_KEY = "mock:tokens";
export async function listTokens(): Promise<AccessToken[]> { await sleep(); ensureOnline(); return store.get<AccessToken[]>(TOKENS_KEY, []); }
export async function createToken(name: string, expiresInDays: number): Promise<CreatedAccessToken> {
  await sleep(); ensureOnline();
  const all = store.get<AccessToken[]>(TOKENS_KEY, []);
  if (all.filter((t) => !t.revoked).length >= 5) throw new ApiError(409, { title: "Resource Conflict", status: 409, detail: "At most 5 active tokens per user; revoke one first" });
  const now = new Date();
  const info: AccessToken = { id: Date.now(), name, createdAt: now.toISOString(), revoked: false, lastUsedAt: null,
    expiresAt: new Date(now.getTime() + expiresInDays * 86400000).toISOString() };
  store.set(TOKENS_KEY, [info, ...all]);
  return { token: `mlc_pat_mock${Math.random().toString(36).slice(2, 14)}`, info };
}
export async function revokeToken(id: number): Promise<void> {
  await sleep(); ensureOnline();
  store.set(TOKENS_KEY, store.get<AccessToken[]>(TOKENS_KEY, []).map((t) => (t.id === id ? { ...t, revoked: true } : t)));
}
