/**
 * Lớp trung gian giữa UI và backend. Mọi component CHỈ gọi hàm ở đây.
 * - USE_MOCK=true  → gọi mock-server (dữ liệu giả)
 * - USE_MOCK=false → gọi REST API thật. Endpoint đánh dấu [ĐỀ XUẤT] chưa có trong spec,
 *   cần thống nhất với Kỹ sư 1 (xem README_FRONTEND.md).
 */
import { api, ApiError, request, USE_MOCK } from "./api";
import { setTokens } from "./auth";
import * as mock from "./mock-server";
import type { PollFetch } from "./polling";
import type {
  PlagiarismRow, ProctorReportRow, CaseResult, ContestInfo, Draft, Language, LeaderboardRow, Page, ProblemFull, ProblemSummary, ProctoringEventType,
  ProctoringResult, Submission, SubmitBody, TestCase,
} from "@/types/api";

export const RUN_ENABLED = USE_MOCK || process.env.NEXT_PUBLIC_ENABLE_RUN === "true";
export const DRAFT_SYNC = USE_MOCK || process.env.NEXT_PUBLIC_ENABLE_DRAFT_SYNC === "true";

/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type Raw = any;

// ---------- Auth ----------
export async function login(username: string, password: string) {
  if (USE_MOCK) { const t = mock.login(username); setTokens(t.accessToken, t.refreshToken); return; }
  const d = await api<Raw>("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ username, password }) });
  const access = d?.accessToken ?? d?.token;
  if (!access) throw new Error("Phản hồi đăng nhập không có accessToken");
  setTokens(access, d.refreshToken);
}
export async function register(username: string, email: string, password: string) {
  if (!USE_MOCK) await api("/api/v1/auth/register", { method: "POST", body: JSON.stringify({ username, email, password }) });
  await login(username, password);
}
export function ensureMockSession() { const t = mock.login("student"); setTokens(t.accessToken, t.refreshToken); }

// ---------- Problems ----------
const normProblem = (r: Raw): ProblemFull => ({
  id: r.id, slug: r.slug ?? String(r.id), title: r.title, difficulty: r.difficulty, tags: r.tags ?? [], progress: r.progress,
  description: r.description ?? "", timeLimitMs: r.timeLimitMs ?? 1000, memoryLimitMb: r.memoryLimitMb ?? 256,
  status: r.status, likes: r.likes, dislikes: r.dislikes,
});

export interface ProblemQuery { page: number; q: string; difficulty: string; progress: string; tags: string[] }
export async function fetchProblems(p: ProblemQuery): Promise<Page<ProblemSummary>> {
  if (USE_MOCK) return mock.listProblems(p);
  const qs = new URLSearchParams({ page: String(p.page - 1), size: "8" }); // backend đếm trang từ 0
  if (p.q) qs.set("search", p.q);
  if (p.difficulty) qs.set("difficulty", p.difficulty);
  const page = await api<Page<Raw>>(`/api/v1/problems?${qs}`);
  return { ...page, content: page.content.map(normProblem) };
}
export async function fetchProblem(idOrSlug: string): Promise<ProblemFull> {
  if (USE_MOCK) return mock.getProblem(idOrSlug);
  return normProblem(await api<Raw>(`/api/v1/problems/${encodeURIComponent(idOrSlug)}`));
}
export async function updateProblem(id: number, patch: Partial<ProblemFull>): Promise<ProblemFull> {
  if (USE_MOCK) return mock.updateProblem(id, patch);
  return normProblem(await api<Raw>(`/api/v1/problems/${id}`, { method: "PUT", body: JSON.stringify(patch) }));
}

const normCase = (r: Raw): TestCase => ({
  id: r.id, input: r.input ?? r.inputContent ?? r.inputPreview ?? "", expectedOutput: r.expectedOutput ?? r.expectedOutputContent ?? r.outputPreview ?? "",
  isHidden: !!(r.isHidden ?? r.hidden), orderIndex: r.orderIndex ?? 0, explanation: r.explanation,
});
export async function fetchTestCases(problemId: number): Promise<TestCase[]> {
  if (USE_MOCK) return mock.getTestCases(problemId);
  return (await api<Raw[]>(`/api/v1/problems/${problemId}/test-cases`)).map(normCase);
}
export async function addTestCase(problemId: number, c: { input: string; expectedOutput: string; isHidden: boolean }): Promise<TestCase> {
  if (USE_MOCK) return mock.addTestCase(problemId, c);
  // Backend nhận multipart 2 file. TÊN FIELD dưới đây là đoán, hãy đối chiếu Swagger.
  const fd = new FormData();
  fd.append("inputFile", new Blob([c.input], { type: "text/plain" }), "input.txt");
  fd.append("outputFile", new Blob([c.expectedOutput], { type: "text/plain" }), "output.txt");
  fd.append("isHidden", String(c.isHidden));
  return normCase(await api<Raw>(`/api/v1/problems/${problemId}/test-cases`, { method: "POST", body: fd }));
}

// ---------- Submissions ----------
export async function submitCode(problemId: number, body: SubmitBody, run = false): Promise<Submission> {
  if (USE_MOCK) return mock.submit(problemId, body, run);
  return api<Submission>(`/api/v1/problems/${problemId}/${run ? "run" : "submit"}`, { method: "POST", body: JSON.stringify(body) }); // /run: [ĐỀ XUẤT]
}
export async function pollSubmission(id: number, signal: AbortSignal): Promise<PollFetch> {
  if (USE_MOCK) return { data: await mock.getSubmission(id), retryAfterMs: 0 };
  const { data, headers } = await request<Submission>(`/api/v1/submissions/${id}`, { signal });
  return { data, retryAfterMs: Number(headers.get("Retry-After") ?? 0) * 1000 };
}
export async function fetchSubmission(id: number): Promise<Submission> {
  if (USE_MOCK) return mock.getSubmission(id);
  return api<Submission>(`/api/v1/submissions/${id}`);
}
export async function fetchMySubmissions(page: number, size = 15): Promise<Page<Submission>> {
  if (USE_MOCK) return mock.mySubmissions(page - 1, size);
  return api<Page<Submission>>(`/api/v1/submissions/my?page=${page - 1}&size=${size}`);
}
export async function fetchSubmissionCode(id: number): Promise<string> {
  if (USE_MOCK) return mock.submissionCode(id);
  return api<string>(`/api/v1/submissions/${id}/code`);
}
// [ĐỀ XUẤT] GET /api/v1/submissions/{id}/results – chi tiết từng testcase công khai. Chưa có → trả null.
export async function fetchSubmissionResults(id: number): Promise<CaseResult[] | null> {
  if (USE_MOCK) return mock.submissionResults(id);
  try { return await api<CaseResult[]>(`/api/v1/submissions/${id}/results`); }
  catch (e) { if (e instanceof ApiError && [404, 405, 501].includes(e.status)) return null; throw e; }
}

// ---------- Contests ----------
// [ĐỀ XUẤT] GET /api/v1/contests, /contests/{id}, /contests/{id}/leaderboard
export async function fetchContests(): Promise<ContestInfo[]> {
  if (USE_MOCK) return mock.listContests();
  const d = await api<Raw>("/api/v1/contests");
  return Array.isArray(d) ? d : d.content;
}
export async function fetchContest(id: number): Promise<ContestInfo> {
  if (USE_MOCK) return mock.getContest(id);
  return api<ContestInfo>(`/api/v1/contests/${id}`);
}
export async function fetchLeaderboard(id: number): Promise<LeaderboardRow[]> {
  if (USE_MOCK) return mock.leaderboard(id);
  return api<LeaderboardRow[]>(`/api/v1/contests/${id}/leaderboard`);
}
export async function postProctoringEvent(contestId: number, eventType: ProctoringEventType): Promise<ProctoringResult> {
  if (USE_MOCK) return mock.proctoringEvent(contestId, eventType);
  return api<ProctoringResult>(`/api/v1/contests/${contestId}/proctoring/events`, {
    method: "POST",
    keepalive: true, // vẫn gửi được khi tab đang bị ẩn
    body: JSON.stringify({ eventType, clientTimestamp: Date.now() }),
  });
}
// [ĐỀ XUẤT] POST /api/v1/contests/{id}/register
export async function registerContest(id: number): Promise<void> {
  if (USE_MOCK) return mock.registerContest(id);
  await api(`/api/v1/contests/${id}/register`, { method: "POST" });
}
// [ĐỀ XUẤT] GET /api/v1/contests/{id}/proctoring/report (INSTRUCTOR, ADMIN): báo cáo vi phạm của thí sinh
export async function fetchProctoringReport(id: number): Promise<ProctorReportRow[]> {
  if (USE_MOCK) return mock.proctoringReport(id);
  return api<ProctorReportRow[]>(`/api/v1/contests/${id}/proctoring/report`);
}
// [ĐỀ XUẤT] POST /api/v1/contests/{id}/participants/{userId}/unlock (ADMIN): mở khoá thí sinh bị loại
export async function unlockParticipant(contestId: number, userId: number): Promise<void> {
  if (USE_MOCK) return mock.unlockParticipant(contestId, userId);
  await api(`/api/v1/contests/${contestId}/participants/${userId}/unlock`, { method: "POST" });
}
// [ĐỀ XUẤT] GET /api/v1/contests/{id}/plagiarism: kết quả đối soát đạo văn (Jaccard) của Kỹ sư 2
export async function fetchPlagiarism(id: number): Promise<PlagiarismRow[]> {
  if (USE_MOCK) return mock.plagiarism(id);
  return api<PlagiarismRow[]>(`/api/v1/contests/${id}/plagiarism`);
}
export function resetMockExam(contestId: number) { mock.resetExam(contestId); }

// ---------- Draft (chống rớt mạng) ----------
// [ĐỀ XUẤT] GET/PUT /api/v1/contests/{cid}/problems/{pid}/draft?language=
export async function loadDraft(cid: number, pid: number, lang: Language): Promise<Draft | null> {
  if (USE_MOCK) return mock.loadDraft(cid, pid, lang);
  const d = await api<Raw>(`/api/v1/contests/${cid}/problems/${pid}/draft?language=${lang}`);
  return d?.code != null ? { value: d.code, savedAt: d.updatedAt ? Date.parse(d.updatedAt) : 0 } : null;
}
export async function saveDraft(cid: number, pid: number, lang: Language, value: string): Promise<void> {
  if (USE_MOCK) return mock.saveDraft(cid, pid, lang, value);
  await api(`/api/v1/contests/${cid}/problems/${pid}/draft`, { method: "PUT", body: JSON.stringify({ language: lang, code: value }) });
}
