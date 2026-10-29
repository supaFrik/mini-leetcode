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
import type {
  PlagiarismRow, ProctorReportRow, CaseResult, ContestInfo, ContestProblem, Draft, Language, LeaderboardCell, LeaderboardRow, Page,
  ProblemFull, ProblemSummary, ProctoringEventType, ProctoringResult, Role, Submission, SubmitBody, TestCase, Verdict,
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
  "Cho hai số nguyên `a` và `b`. Hãy in ra tổng `a + b`.", "",
  "### Input", "Một dòng chứa hai số nguyên `a`, `b` cách nhau bởi dấu cách.", "",
  "### Output", "In ra một số nguyên là `a + b`.", "",
  "### Ràng buộc", "- `-10^9 ≤ a, b ≤ 10^9`", "",
  "### Ví dụ", "```", "Input:  1 2", "Output: 3", "```",
].join("\n");

const PROBLEMS: ProblemFull[] = Array.from({ length: 36 }, (_, i) => {
  const id = i + 1;
  const title = i < TITLES.length ? TITLES[i] : `Placeholder #${id}`;
  return {
    id, title, slug: i < TITLES.length ? slugify(title) : `placeholder-${id}`,
    difficulty: DIFFS[i % 3], tags: TAGSETS[i % TAGSETS.length], progress: PROGRESS[i % 3],
    description: i === 0 ? TWO_SUM_MD : `Đề bài **${title}** (dữ liệu mẫu).\n\nĐọc hai số nguyên \`a\`, \`b\` từ input và in ra \`a + b\`.`,
    timeLimitMs: 1000, memoryLimitMb: 256, status: "PUBLISHED", likes: 40 + ((id * 7) % 60), dislikes: 5 + ((id * 3) % 30),
  };
});
const withEdits = (p: ProblemFull): ProblemFull => ({ ...p, ...store.get<Record<number, Partial<ProblemFull>>>("mock:problemEdits", {})[p.id] });

export async function listProblems(q: { page: number; q: string; difficulty: string; progress: string; tags: string[] }): Promise<Page<ProblemSummary>> {
  await sleep(); ensureOnline();
  const done = new Set(store.get<SubRec[]>("mock:subs", []).filter((s) => s.verdict === "AC" && !s.run).map((s) => s.problemId));
  const size = 8;
  const all = PROBLEMS.map(withEdits).map((p) => ({ ...p, progress: done.has(p.id) ? ("DONE" as const) : p.progress }))
    .filter((p) =>
      (!q.q || p.title.toLowerCase().includes(q.q.toLowerCase()) || p.tags.join(" ").toLowerCase().includes(q.q.toLowerCase())) &&
      (!q.difficulty || p.difficulty === q.difficulty) && (!q.progress || p.progress === q.progress) &&
      q.tags.every((t) => p.tags.includes(t)));
  const start = (q.page - 1) * size;
  return {
    content: all.slice(start, start + size).map(({ id, slug, title, difficulty, tags, progress }) => ({ id, slug, title, difficulty, tags, progress })),
    totalElements: all.length, totalPages: Math.max(1, Math.ceil(all.length / size)), number: q.page - 1, size,
  };
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

const BASE_CASES: [string, string][] = [["1 2", "3"], ["5 7", "12"], ["-3 3", "0"], ["100 200", "300"], ["999 1", "1000"]];
function casesFor(problemId: number): TestCase[] {
  const base = BASE_CASES.map(([input, expectedOutput], idx) => ({
    id: problemId * 100 + idx + 1, input, expectedOutput, isHidden: idx >= 3, orderIndex: idx,
    explanation: idx === 0 ? "Hai số 1 và 2 có tổng bằng 3." : undefined,
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
  if (!body.code.trim()) throw new ApiError(400, { title: "Bad Request", status: 400, detail: "Validation failed", invalid_params: { code: "Code must not be blank" } });
  if (body.code.length > 64 * 1024) throw new ApiError(400, { title: "Bad Request", status: 400, detail: "Validation failed", invalid_params: { code: "Code must not exceed 64KB" } });
  if (body.contestId != null) {
    const c = await getContest(body.contestId);
    if (c.status !== "RUNNING") throw forbidden("Contest is not currently active");
    if (!c.myStatus) throw forbidden(`User is not registered for contest id: ${body.contestId}`);
    if (readProctor(body.contestId).dq) throw forbidden("User has been disqualified from this contest due to proctoring violations");
  }
  const u = me();
  const total = casesFor(problemId).filter((c) => !run || !c.isHidden).length;
  const out = judgeOutcome(body.code, body.language, total);
  const subs = store.get<SubRec[]>("mock:subs", []);
  const rec: SubRec = {
    id: 500 + subs.length + 1, userId: u.id, username: u.username, problemId, contestId: body.contestId ?? null, language: body.language,
    code: body.code, run, createdAt: Date.now(), total, ...out,
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
export async function mySubmissions(page: number, size: number): Promise<Page<Submission>> {
  await sleep(); ensureOnline();
  const u = me();
  const mine = store.get<SubRec[]>("mock:subs", []).filter((s) => s.userId === u.id && !s.run).sort((a, b) => b.createdAt - a.createdAt);
  return {
    content: mine.slice(page * size, page * size + size).map((r) => toSubmission(r, Date.now() - r.createdAt)),
    totalElements: mine.length, totalPages: Math.max(1, Math.ceil(mine.length / size)), number: page, size,
  };
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
const TITLES_C: Record<number, string> = { 1: "Midterm Exam – Cấu trúc dữ liệu", 2: "Weekly Contest #12", 3: "Weekly Contest #11" };

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
