/**
 * Lớp trung gian giữa UI và backend. Mọi component CHỈ gọi hàm ở đây.
 * - USE_MOCK=true  → gọi mock-server (dữ liệu giả)
 * - USE_MOCK=false → gọi REST API thật. Endpoint đánh dấu [ĐỀ XUẤT] chưa có trong spec,
 *   cần thống nhất với Kỹ sư 1 (xem README_FRONTEND.md).
 */
import { api, ApiError, request, USE_MOCK } from "./api";
import { parseApiDate } from "./datetime";
import { setTokens } from "./auth";
import * as mock from "./mock-server";
import type { PollFetch } from "./polling";
import type {
  PlagiarismRow, ProctorReportRow, CaseResult, ContestInfo, Draft, Language, LeaderboardRow, Page, ProblemFull, ProblemLifecycle, ProblemSummary, ProblemType,
  ProctoringEventType, ProctoringResult, Submission, SubmitBody, TestCase, Difficulty,
  AccessToken, CreatedAccessToken, StudyPlanDetail, StudyPlanDraft, StudyPlanStatus, StudyPlanSummary,
  AttemptResult, AuthoringTopic, LearnerTopic, StepAnswer, TopicDraft, TopicSummary,
  GradingValidation, ProblemAuthoring, ReferenceRunResult, SubmissionFile,
  AdminContestDetail, AdminContestSummary, AdminParticipant, BulkParticipantsResult, ContestUpsert,
} from "@/types/api";

export const RUN_ENABLED = USE_MOCK || process.env.NEXT_PUBLIC_ENABLE_RUN === "true";
export const DRAFT_SYNC = USE_MOCK || process.env.NEXT_PUBLIC_ENABLE_DRAFT_SYNC === "true";

/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type Raw = any;

// ---------- Auth ----------
export async function login(username: string, password: string) {
  if (USE_MOCK) { const t = mock.login(username); setTokens(t.accessToken, t.refreshToken); return; }
  const d = await api<Raw>("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ username, usernameOrEmail: username, password }) });
  const access = d?.accessToken ?? d?.token;
  if (!access) throw new Error("Login response missing accessToken");
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
  status: r.status, testCaseCount: Number(r.testCaseCount ?? 0), createdByUsername: r.createdByUsername, updatedAt: r.updatedAt,
  likes: r.likes, dislikes: r.dislikes,
  type: r.type ?? "ALGORITHMIC",
  starterTemplates: r.starterTemplates ?? null,
});

/**
 * Backend trả về Page theo dạng lồng: `{ content: [...], page: { number, size, totalElements, totalPages } }`
 * (xem ProblemController trả `ResponseEntity.ok(Page<ProblemResponse>)` và test
 * `jsonPath("$.page.totalElements")`). Còn mock-server trả dạng phẳng.
 * Hàm này gộp cả hai về một shape phẳng duy nhất để UI đọc nhất quán.
 */
const normPage = <T,>(r: Raw): Page<T> => {
  const content: T[] = Array.isArray(r?.content) ? r.content : [];
  const meta = r?.page ?? r ?? {};
  const size = Number(meta.size ?? content.length ?? 0);
  const number = Number(meta.number ?? 0);
  const totalElements = Number(meta.totalElements ?? content.length);
  const totalPages = Number(meta.totalPages ?? (size > 0 ? Math.ceil(totalElements / size) : 1));
  return { content, totalElements, totalPages, number, size };
};

export interface ProblemQuery { page: number; q: string; difficulty: string; progress: string; tags: string[]; type?: ProblemType; size?: number }
export async function fetchProblems(p: ProblemQuery): Promise<Page<ProblemSummary>> {
  if (USE_MOCK) return mock.listProblems(p);
  const qs = new URLSearchParams({ page: String(p.page - 1), size: String(p.size ?? 8) }); // backend đếm trang từ 0
  if (p.q) qs.set("search", p.q);
  if (p.difficulty) qs.set("difficulty", p.difficulty);
  if (p.type) qs.set("type", p.type);
  // Backend lọc chủ đề ở tầng SQL: bài khớp nếu thuộc ÍT NHẤT MỘT tag được gửi lên
  // (so khớp chính xác, không phân biệt hoa/thường).
  if (p.tags.length) qs.set("tags", p.tags.join(","));
  // `progress` chỉ có ý nghĩa với người đã đăng nhập; backend tự tính từ submissions.
  if (p.progress) qs.set("progress", p.progress);
  const raw = await api<Raw>(`/api/v1/problems?${qs}`);
  const page = normPage<Raw>(raw);
  return { ...page, content: page.content.map(normProblem) };
}

/**
 * Đếm số bài khớp bộ lọc mà không tải nội dung: gọi trang 1 với size=1 rồi đọc totalElements.
 * Dùng cho "Your progress" vì backend chưa có endpoint thống kê riêng.
 */
export async function countProblems(p: Partial<Omit<ProblemQuery, "page" | "size">>): Promise<number> {
  const r = await fetchProblems({ page: 1, size: 1, q: p.q ?? "", difficulty: p.difficulty ?? "", progress: p.progress ?? "", tags: p.tags ?? [], type: p.type });
  return r.totalElements;
}

/** Danh sách chủ đề đang có trong catalogue, kèm số bài. `type` lọc theo DSA/OOP. */
export async function fetchProblemTags(type?: ProblemType): Promise<{ tag: string; count: number }[]> {
  if (USE_MOCK) return mock.listTags(type);
  const raw = await api<Raw[]>(`/api/v1/problems/tags${type ? `?type=${type}` : ""}`);
  return (raw ?? []).map((t) => ({ tag: String(t.tag), count: Number(t.count) }));
}
export async function fetchProblem(idOrSlug: string): Promise<ProblemFull> {
  if (USE_MOCK) return mock.getProblem(idOrSlug);
  return normProblem(await api<Raw>(`/api/v1/problems/${encodeURIComponent(idOrSlug)}`));
}
export async function updateProblem(id: number, patch: Partial<ProblemFull>): Promise<ProblemFull> {
  if (USE_MOCK) return mock.updateProblem(id, patch);
  return normProblem(await api<Raw>(`/api/v1/problems/${id}`, { method: "PUT", body: JSON.stringify(patch) }));
}

/**
 * Chuyển trạng thái vòng đời của bài (DRAFT → REVIEWING → PUBLISHED → ARCHIVED).
 * Tách riêng khỏi `updateProblem` vì server kiểm tra cạnh trạng thái hợp lệ.
 */
export async function transitionProblem(id: number, status: ProblemLifecycle): Promise<ProblemFull> {
  if (USE_MOCK) return mock.transitionProblem(id, status);
  return normProblem(await api<Raw>(`/api/v1/problems/${id}/transitions`, { method: "POST", body: JSON.stringify({ status }) }));
}

// ---------- Admin ----------
export interface AdminProblemQuery {
  page: number;
  q: string;
  difficulty: string;
  /** Trạng thái vòng đời; rỗng = không lọc. */
  status: string;
  tags: string[];
  type?: ProblemType;
}

/** Danh sách bài cho trang quản trị — staff thấy cả bài chưa publish. */
export async function fetchAdminProblems(q: AdminProblemQuery): Promise<Page<ProblemFull>> {
  if (USE_MOCK) return mock.listAdminProblems(q);
  const qs = new URLSearchParams({ page: String(q.page - 1), size: "20" });
  if (q.q) qs.set("search", q.q);
  if (q.difficulty) qs.set("difficulty", q.difficulty);
  if (q.status) qs.set("status", q.status);
  if (q.type) qs.set("type", q.type);
  if (q.tags.length) qs.set("tags", q.tags.join(","));
  const raw = await api<Raw>(`/api/v1/problems?${qs}`);
  const page = normPage<Raw>(raw);
  return { ...page, content: page.content.map(normProblem) };
}

export interface ProblemDraft {
  title: string;
  description: string;
  difficulty: Difficulty;
  timeLimitMs: number;
  memoryLimitMb: number;
  tags: string[];
  type?: ProblemType;
  starterTemplates?: string | null;
}

/** Tạo bài mới. Luôn bắt đầu ở trạng thái DRAFT. */
export async function createProblem(draft: ProblemDraft): Promise<ProblemFull> {
  if (USE_MOCK) return mock.createProblem(draft);
  return normProblem(await api<Raw>(`/api/v1/problems`, { method: "POST", body: JSON.stringify(draft) }));
}

/** Cập nhật metadata của bài (không đổi trạng thái vòng đời). */
export async function saveProblem(id: number, draft: ProblemDraft): Promise<ProblemFull> {
  if (USE_MOCK) return mock.updateProblem(id, draft as Partial<ProblemFull>);
  return normProblem(await api<Raw>(`/api/v1/problems/${id}`, { method: "PUT", body: JSON.stringify(draft) }));
}

/** Xoá cứng bài. Server trả 409 nếu bài đã có lịch sử bài nộp. */
export async function deleteProblem(id: number): Promise<void> {
  if (USE_MOCK) return mock.deleteProblem(id);
  await api<Raw>(`/api/v1/problems/${id}`, { method: "DELETE" });
}

const normCase = (r: Raw): TestCase => ({
  id: r.id, input: r.input ?? r.inputContent ?? r.inputPreview ?? "", expectedOutput: r.expectedOutput ?? r.expectedOutputContent ?? r.outputPreview ?? "",
  isHidden: !!(r.isHidden ?? r.hidden), orderIndex: r.orderIndex ?? 0, explanation: r.explanation,
  inputTruncated: !!r.inputTruncated, expectedOutputTruncated: !!r.expectedOutputTruncated,
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

/** Cập nhật testcase. Trường nào không truyền thì giữ nguyên (file được gửi dạng multipart). */
export async function updateTestCase(
  problemId: number,
  testCaseId: number,
  c: { input?: string; expectedOutput?: string; isHidden?: boolean },
): Promise<TestCase> {
  if (USE_MOCK) return mock.updateTestCase(problemId, testCaseId, c);
  const fd = new FormData();
  if (c.input !== undefined) fd.append("inputFile", new Blob([c.input], { type: "text/plain" }), "input.txt");
  if (c.expectedOutput !== undefined) fd.append("outputFile", new Blob([c.expectedOutput], { type: "text/plain" }), "output.txt");
  if (c.isHidden !== undefined) fd.append("isHidden", String(c.isHidden));
  return normCase(await api<Raw>(`/api/v1/problems/${problemId}/test-cases/${testCaseId}`, { method: "PUT", body: fd }));
}
export async function deleteTestCase(problemId: number, testCaseId: number): Promise<void> {
  if (USE_MOCK) return mock.deleteTestCase(problemId, testCaseId);
  await api<Raw>(`/api/v1/problems/${problemId}/test-cases/${testCaseId}`, { method: "DELETE" });
}

// ---------- Soạn bài: grading_config, lời giải mẫu, sinh output (INSTRUCTOR/ADMIN) ----------
const authoringBase = (id: number) => `/api/v1/admin/problems/${id}`;
const normAuthoring = (r: Raw): ProblemAuthoring => ({
  problemId: r.problemId, status: r.status, problemType: r.problemType ?? "ALGORITHMIC",
  gradingConfig: r.gradingConfig ?? null, referenceSolution: r.referenceSolution ?? [],
  gradingFrozen: !!r.gradingFrozen, timeLimitMs: r.timeLimitMs ?? 1000, memoryLimitMb: r.memoryLimitMb ?? 256,
});
export async function fetchAuthoring(problemId: number): Promise<ProblemAuthoring> {
  if (USE_MOCK) return mock.getAuthoring(problemId);
  return normAuthoring(await api<Raw>(`${authoringBase(problemId)}/authoring`));
}
export async function validateGradingConfig(problemId: number, gradingConfig: string): Promise<GradingValidation> {
  if (USE_MOCK) return mock.validateGradingConfig(gradingConfig);
  return api<GradingValidation>(`${authoringBase(problemId)}/grading-config/validate`, {
    method: "POST", body: JSON.stringify({ gradingConfig }),
  });
}
/** Lưu grading_config (rỗng ⇒ xoá). Lỗi 400 mang `errors` trong ApiError.body. */
export async function saveGradingConfig(problemId: number, gradingConfig: string | null): Promise<ProblemAuthoring> {
  if (USE_MOCK) return mock.saveGradingConfig(problemId, gradingConfig);
  return normAuthoring(await api<Raw>(`${authoringBase(problemId)}/grading-config`, {
    method: "PUT", body: JSON.stringify({ gradingConfig }),
  }));
}
export async function saveReferenceSolution(problemId: number, files: SubmissionFile[]): Promise<ProblemAuthoring> {
  if (USE_MOCK) return mock.saveReferenceSolution(problemId, files);
  return normAuthoring(await api<Raw>(`${authoringBase(problemId)}/reference-solution`, {
    method: "PUT", body: JSON.stringify({ files }),
  }));
}
/**
 * Chạy lời giải mẫu đã lưu trong sandbox của judge.
 * `inputs`: stdin tuỳ ý (soạn testcase mới). Không có `inputs`: chạy trên testcase của bài, `apply` ghi output mới.
 */
export async function runReference(
  problemId: number,
  body: { inputs?: string[]; testCaseIds?: number[]; apply?: boolean },
): Promise<ReferenceRunResult> {
  if (USE_MOCK) return mock.runReference(problemId, body);
  return api<ReferenceRunResult>(`${authoringBase(problemId)}/reference-run`, { method: "POST", body: JSON.stringify(body) });
}

// ---------- Submissions ----------
export async function submitCode(problemId: number, body: SubmitBody, run = false): Promise<Submission> {
  if (USE_MOCK) return mock.submit(problemId, body, run);
  return api<Submission>(`/api/v1/problems/${problemId}/${run ? "run" : "submit"}`, { method: "POST", body: JSON.stringify(body) }); // /run: [ĐỀ XUẤT]
}

export async function submitCodeMultipart(problemId: number, formData: FormData, run = false): Promise<Submission> {
  if (!formData.has("problemId")) formData.append("problemId", String(problemId));
  if (!formData.has("isQuickRun")) formData.append("isQuickRun", String(run));
  return api<Submission>("/api/v1/submissions", {
    method: "POST",
    body: formData,
  });
}

export async function downloadStarterZip(problemId: number, slug?: string): Promise<void> {
  const res = await fetch(`/api/v1/problems/${problemId}/starter`);
  if (!res.ok) {
    throw new Error(`Failed to download starter scaffolding: ${res.statusText}`);
  }
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${slug ?? `problem-${problemId}`}-starter.zip`;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
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
/** `verdict` là tên verdict hoặc PENDING; server lọc và phân trang. Bỏ trống là không lọc. */
export async function fetchMySubmissions(page: number, size = 15, filter: { verdict?: string; language?: string } = {}): Promise<Page<Submission>> {
  if (USE_MOCK) return mock.mySubmissions(page - 1, size, filter);
  const qs = new URLSearchParams({ page: String(page - 1), size: String(size) });
  if (filter.verdict) qs.set("verdict", filter.verdict);
  if (filter.language) qs.set("language", filter.language);
  return normPage<Submission>(await api<Raw>(`/api/v1/submissions/my?${qs}`));
}
/** Số bài nộp theo verdict (thêm PENDING) trên toàn bộ bài nộp của user. */
export async function fetchMySubmissionSummary(): Promise<Record<string, number>> {
  if (USE_MOCK) return mock.mySubmissionSummary();
  return (await api<Record<string, number>>("/api/v1/submissions/my/summary")) ?? {};
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
// GET /api/v1/contests, /contests/{id}, /contests/{id}/leaderboard
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
// POST /api/v1/contests/{id}/register
export async function registerContest(id: number): Promise<void> {
  if (USE_MOCK) return mock.registerContest(id);
  await api(`/api/v1/contests/${id}/register`, { method: "POST" });
}
// POST /api/v1/contests/{id}/finalize: chốt bài thi, khoá nộp bài tiếp theo.
export async function finalizeContestSubmission(id: number): Promise<ContestInfo | null> {
  if (USE_MOCK) return mock.getContest(id);
  try {
    return await api<ContestInfo>(`/api/v1/contests/${id}/finalize`, { method: "POST" });
  } catch {
    // Chốt bài là hành động "best effort": lỗi không được chặn luồng hiển thị phía client.
    return null;
  }
}
// GET /api/v1/contests/{id}/proctoring/report (INSTRUCTOR, ADMIN): báo cáo vi phạm của thí sinh
export async function fetchProctoringReport(id: number): Promise<ProctorReportRow[]> {
  if (USE_MOCK) return mock.proctoringReport(id);
  return api<ProctorReportRow[]>(`/api/v1/contests/${id}/proctoring/report`);
}
// POST /api/v1/contests/{id}/participants/{userId}/unlock (ADMIN): mở khoá thí sinh bị loại
export async function unlockParticipant(contestId: number, userId: number): Promise<void> {
  if (USE_MOCK) return mock.unlockParticipant(contestId, userId);
  await api(`/api/v1/contests/${contestId}/participants/${userId}/unlock`, { method: "POST" });
}
// GET /api/v1/contests/{id}/plagiarism: kết quả đối soát đạo văn (Jaccard) của Kỹ sư 2
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
  return d?.code != null ? { value: d.code, savedAt: d.updatedAt ? parseApiDate(d.updatedAt) : 0 } : null;
}
export async function saveDraft(cid: number, pid: number, lang: Language, value: string): Promise<void> {
  if (USE_MOCK) return mock.saveDraft(cid, pid, lang, value);
  await api(`/api/v1/contests/${cid}/problems/${pid}/draft`, { method: "PUT", body: JSON.stringify({ language: lang, code: value }) });
}

// ---------- Study plans (specs/study-plan.spec.md) ----------
// Backend trả thẳng JSON (không bọc ApiResponse); `api()` xử lý được cả hai dạng.
export async function fetchStudyPlans(): Promise<StudyPlanSummary[]> {
  if (USE_MOCK) return mock.listStudyPlans();
  return (await api<StudyPlanSummary[]>("/api/v1/study-plans")) ?? [];
}
export async function fetchStudyPlan(slug: string): Promise<StudyPlanDetail> {
  if (USE_MOCK) return mock.getStudyPlan(slug);
  return api<StudyPlanDetail>(`/api/v1/study-plans/${encodeURIComponent(slug)}`);
}

// Soạn plan (INSTRUCTOR / ADMIN). Server kiểm tra quyền tác giả, đề PUBLISHED, đề không thuộc plan khác.
export async function createStudyPlan(draft: StudyPlanDraft): Promise<StudyPlanDetail> {
  if (USE_MOCK) return mock.saveStudyPlan(null, draft);
  return api<StudyPlanDetail>("/api/v1/study-plans", { method: "POST", body: JSON.stringify(draft) });
}
export async function updateStudyPlan(slug: string, draft: StudyPlanDraft): Promise<StudyPlanDetail> {
  if (USE_MOCK) return mock.saveStudyPlan(slug, draft);
  return api<StudyPlanDetail>(`/api/v1/study-plans/${encodeURIComponent(slug)}`, { method: "PUT", body: JSON.stringify(draft) });
}
export async function transitionStudyPlan(slug: string, status: StudyPlanStatus): Promise<StudyPlanDetail> {
  if (USE_MOCK) return mock.transitionStudyPlan(slug, status);
  return api<StudyPlanDetail>(`/api/v1/study-plans/${encodeURIComponent(slug)}/transitions`, { method: "POST", body: JSON.stringify({ status }) });
}
export async function deleteStudyPlan(slug: string): Promise<void> {
  if (USE_MOCK) return mock.deleteStudyPlan(slug);
  await api(`/api/v1/study-plans/${encodeURIComponent(slug)}`, { method: "DELETE" });
}

// ---------- Chủ đề học (specs/topics.spec.md) ----------
const topicPath = (slug: string) => `/api/v1/topics/${encodeURIComponent(slug)}`;
export async function fetchTopic(slug: string): Promise<LearnerTopic> {
  if (USE_MOCK) return mock.getTopic(slug);
  return api<LearnerTopic>(topicPath(slug));
}
export async function attemptTopicStep(slug: string, stepId: number, answer: StepAnswer): Promise<AttemptResult> {
  if (USE_MOCK) return mock.attemptTopicStep(slug, stepId, answer);
  return api<AttemptResult>(`${topicPath(slug)}/steps/${stepId}/attempts`, { method: "POST", body: JSON.stringify({ answer }) });
}
export async function setTopicSkipped(slug: string, skipped: boolean): Promise<void> {
  if (USE_MOCK) return mock.setTopicSkipped(slug, skipped);
  await api(`${topicPath(slug)}/skip`, { method: skipped ? "POST" : "DELETE" });
}
// Soạn (INSTRUCTOR / ADMIN)
export async function fetchTopics(search = ""): Promise<TopicSummary[]> {
  if (USE_MOCK) return mock.listTopics(search);
  return (await api<TopicSummary[]>(`/api/v1/topics${search ? `?search=${encodeURIComponent(search)}` : ""}`)) ?? [];
}
export async function fetchTopicForAuthoring(slug: string): Promise<AuthoringTopic> {
  if (USE_MOCK) return mock.getTopicForAuthoring(slug);
  return api<AuthoringTopic>(`${topicPath(slug)}/authoring`);
}
export async function saveTopic(slug: string | null, draft: TopicDraft): Promise<AuthoringTopic> {
  if (USE_MOCK) return mock.saveTopic(slug, draft);
  return slug
    ? api<AuthoringTopic>(topicPath(slug), { method: "PUT", body: JSON.stringify(draft) })
    : api<AuthoringTopic>("/api/v1/topics", { method: "POST", body: JSON.stringify(draft) });
}
export async function transitionTopic(slug: string, status: StudyPlanStatus): Promise<AuthoringTopic> {
  if (USE_MOCK) return mock.transitionTopic(slug, status);
  return api<AuthoringTopic>(`${topicPath(slug)}/transitions`, { method: "POST", body: JSON.stringify({ status }) });
}
export async function deleteTopic(slug: string): Promise<void> {
  if (USE_MOCK) return mock.deleteTopic(slug);
  await api(topicPath(slug), { method: "DELETE" });
}

// ---------- Personal access tokens (plugin IntelliJ) ----------
export async function fetchAccessTokens(): Promise<AccessToken[]> {
  if (USE_MOCK) return mock.listTokens();
  return (await api<AccessToken[]>("/api/v1/me/tokens")) ?? [];
}
export async function createAccessToken(name: string, expiresInDays: number): Promise<CreatedAccessToken> {
  if (USE_MOCK) return mock.createToken(name, expiresInDays);
  return api<CreatedAccessToken>("/api/v1/me/tokens", { method: "POST", body: JSON.stringify({ name, expiresInDays }) });
}
export async function revokeAccessToken(id: number): Promise<void> {
  if (USE_MOCK) return mock.revokeToken(id);
  await api(`/api/v1/me/tokens/${id}`, { method: "DELETE" });
}

/** File ZIP của plugin; `npm run plugin:copy` chép bản build vào public/downloads. */
export const PLUGIN_DOWNLOAD_URL = process.env.NEXT_PUBLIC_PLUGIN_DOWNLOAD_URL || "/downloads/mini-leetcode-intellij.zip";
// ---------- Admin: kỳ thi (/api/v1/admin/contests, INSTRUCTOR + ADMIN; xoá chỉ ADMIN) ----------
// Không có bản mock: trang này luôn nói chuyện với API thật (e2e chặn request bằng page.route).
export async function fetchAdminContests(q: { status?: string; search?: string } = {}): Promise<AdminContestSummary[]> {
  const qs = new URLSearchParams();
  if (q.status) qs.set("status", q.status);
  if (q.search) qs.set("search", q.search);
  const s = qs.toString();
  return api<AdminContestSummary[]>(`/api/v1/admin/contests${s ? `?${s}` : ""}`);
}
export async function fetchAdminContest(id: number): Promise<AdminContestDetail> {
  return api<AdminContestDetail>(`/api/v1/admin/contests/${id}`);
}
export async function createContest(body: ContestUpsert): Promise<AdminContestDetail> {
  return api<AdminContestDetail>("/api/v1/admin/contests", { method: "POST", body: JSON.stringify(body) });
}
export async function updateContest(id: number, body: ContestUpsert): Promise<AdminContestDetail> {
  return api<AdminContestDetail>(`/api/v1/admin/contests/${id}`, { method: "PUT", body: JSON.stringify(body) });
}
export async function deleteContest(id: number): Promise<void> {
  await api(`/api/v1/admin/contests/${id}`, { method: "DELETE" });
}
export async function fetchContestParticipants(id: number): Promise<AdminParticipant[]> {
  return api<AdminParticipant[]>(`/api/v1/admin/contests/${id}/participants`);
}
export async function addContestParticipants(id: number, entries: string): Promise<BulkParticipantsResult> {
  return api<BulkParticipantsResult>(`/api/v1/admin/contests/${id}/participants`, {
    method: "POST",
    body: JSON.stringify({ entries }),
  });
}
export async function removeContestParticipant(id: number, userId: number): Promise<void> {
  await api(`/api/v1/admin/contests/${id}/participants/${userId}`, { method: "DELETE" });
}
