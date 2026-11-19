/**
 * Lá»›p trung gian giá»¯a UI vÃ  backend. Má»i component CHá»ˆ gá»i hÃ m á»Ÿ Ä‘Ã¢y.
 * - USE_MOCK=true  â†’ gá»i mock-server (dá»¯ liá»‡u giáº£)
 * - USE_MOCK=false â†’ gá»i REST API tháº­t. Endpoint Ä‘Ã¡nh dáº¥u [Äá»€ XUáº¤T] chÆ°a cÃ³ trong spec,
 *   cáº§n thá»‘ng nháº¥t vá»›i Ká»¹ sÆ° 1 (xem README_FRONTEND.md).
 */
import { api, ApiError, request, USE_MOCK } from "./api";
import { parseApiDate } from "./datetime";
import { setTokens } from "./auth";
import * as mock from "./mock-server";
import type { PollFetch } from "./polling";
import type {
  PlagiarismRow, ProctorReportRow, CaseResult, ContestInfo, Draft, Language, LeaderboardRow, Page, ProblemFull, ProblemLifecycle, ProblemScope, ProblemSummary, ProblemType,
  ProctoringEventType, ProctoringResult, Submission, SubmitBody, TestCase, Difficulty,
  AccessToken, CreatedAccessToken, PublishCheck, StudyPlanDetail, StudyPlanDraft, StudyPlanStatus, StudyPlanSummary,
  AttemptResult, AuthoringTopic, LearnerTopic, StepAnswer, TopicDraft, TopicSummary,
  GradingValidation, ProblemAuthoring, ReferenceRunResult, SubmissionFile,
  AdminContestDetail, AdminContestSummary, AdminParticipant, BulkParticipantsResult, ContestUpsert,
  AdminTag, RankingRow, SitePage, TagCategory, TagCount,
  AdminStats, AdminUserList, AdminUserRow, Role, UserStatus,
  Announcement, PlanProgress, PublicSettings, SiteSettings, TeachingDashboard,
} from "@/types/api";
import { TAG_CATEGORIES } from "@/types/api";

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
 * Backend tráº£ vá» Page theo dáº¡ng lá»“ng: `{ content: [...], page: { number, size, totalElements, totalPages } }`
 * (xem ProblemController tráº£ `ResponseEntity.ok(Page<ProblemResponse>)` vÃ  test
 * `jsonPath("$.page.totalElements")`). CÃ²n mock-server tráº£ dáº¡ng pháº³ng.
 * HÃ m nÃ y gá»™p cáº£ hai vá» má»™t shape pháº³ng duy nháº¥t Ä‘á»ƒ UI Ä‘á»c nháº¥t quÃ¡n.
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
  const qs = new URLSearchParams({ page: String(p.page - 1), size: String(p.size ?? 8) }); // backend Ä‘áº¿m trang tá»« 0
  if (p.q) qs.set("search", p.q);
  if (p.difficulty) qs.set("difficulty", p.difficulty);
  if (p.type) qs.set("type", p.type);
  // Backend lá»c chá»§ Ä‘á» á»Ÿ táº§ng SQL: bÃ i khá»›p náº¿u thuá»™c ÃT NHáº¤T Má»˜T tag Ä‘Æ°á»£c gá»­i lÃªn
  // (so khá»›p chÃ­nh xÃ¡c, khÃ´ng phÃ¢n biá»‡t hoa/thÆ°á»ng).
  if (p.tags.length) qs.set("tags", p.tags.join(","));
  // `progress` chá»‰ cÃ³ Ã½ nghÄ©a vá»›i ngÆ°á»i Ä‘Ã£ Ä‘Äƒng nháº­p; backend tá»± tÃ­nh tá»« submissions.
  if (p.progress) qs.set("progress", p.progress);
  const raw = await api<Raw>(`/api/v1/problems?${qs}`);
  const page = normPage<Raw>(raw);
  return { ...page, content: page.content.map(normProblem) };
}

/**
 * Äáº¿m sá»‘ bÃ i khá»›p bá»™ lá»c mÃ  khÃ´ng táº£i ná»™i dung: gá»i trang 1 vá»›i size=1 rá»“i Ä‘á»c totalElements.
 * DÃ¹ng cho "Your progress" vÃ¬ backend chÆ°a cÃ³ endpoint thá»‘ng kÃª riÃªng.
 */
export async function countProblems(p: Partial<Omit<ProblemQuery, "page" | "size">>): Promise<number> {
  const r = await fetchProblems({ page: 1, size: 1, q: p.q ?? "", difficulty: p.difficulty ?? "", progress: p.progress ?? "", tags: p.tags ?? [], type: p.type });
  return r.totalElements;
}

/** Danh sÃ¡ch chá»§ Ä‘á» Ä‘ang cÃ³ trong catalogue, kÃ¨m sá»‘ bÃ i vÃ  nhÃ³m. `type` lá»c theo DSA/OOP. */
export async function fetchProblemTags(type?: ProblemType): Promise<TagCount[]> {
  if (USE_MOCK) return (await mock.listTags(type)).map((t) => ({ ...t, category: "OTHER" as const }));
  const raw = await api<Raw[]>(`/api/v1/problems/tags${type ? `?type=${type}` : ""}`);
  return (raw ?? []).map((t) => ({ tag: String(t.tag), count: Number(t.count), category: toTagCategory(t.category) }));
}

function toTagCategory(v: unknown): TagCategory {
  return TAG_CATEGORIES.some((c) => c.value === v) ? (v as TagCategory) : "OTHER";
}

// â”€â”€ NhÃ³m chá»§ Ä‘á» (chá»‰ ADMIN). Chá»§ Ä‘á» nháº­n diá»‡n báº±ng tÃªn. â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
export async function fetchAdminTags(): Promise<AdminTag[]> {
  const raw = await api<Raw[]>("/api/v1/admin/tags");
  return (raw ?? []).map((t) => ({ name: String(t.name), category: toTagCategory(t.category), problemCount: Number(t.problemCount) }));
}
export async function createTag(name: string, category: TagCategory): Promise<AdminTag> {
  return api<AdminTag>("/api/v1/admin/tags", { method: "POST", body: JSON.stringify({ name, category }) });
}
export async function updateTagCategory(name: string, category: TagCategory): Promise<AdminTag> {
  return api<AdminTag>(`/api/v1/admin/tags/${encodeURIComponent(name)}`, { method: "PUT", body: JSON.stringify({ category }) });
}
export async function deleteTag(name: string): Promise<void> {
  await api<void>(`/api/v1/admin/tags/${encodeURIComponent(name)}`, { method: "DELETE" });
}

// â”€â”€ Quáº£n trá»‹: tá»•ng quan vÃ  ngÆ°á»i dÃ¹ng (chá»‰ ADMIN) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
export async function fetchAdminStats(): Promise<AdminStats> {
  return api<AdminStats>("/api/v1/admin/stats");
}
export async function fetchAdminUsers(q: string, page = 0): Promise<AdminUserList> {
  return api<AdminUserList>(`/api/v1/admin/users?q=${encodeURIComponent(q)}&page=${page}`);
}

// ThÃ´ng bÃ¡o: má»i ngÆ°á»i Ä‘Ã£ Ä‘Äƒng nháº­p Ä‘á»c; giáº£ng viÃªn/ADMIN táº¡o vÃ  xoÃ¡.
export async function fetchAnnouncements(): Promise<Announcement[]> {
  if (USE_MOCK) return [];
  return (await api<Announcement[]>("/api/v1/announcements")) ?? [];
}
export async function createAnnouncement(body: { title: string; body: string }): Promise<Announcement> {
  return api<Announcement>("/api/v1/admin/announcements", { method: "POST", body: JSON.stringify(body) });
}
export async function deleteAnnouncement(id: number): Promise<void> {
  await api(`/api/v1/admin/announcements/${id}`, { method: "DELETE" });
}
export async function fetchPublicSettings(): Promise<PublicSettings | null> {
  if (USE_MOCK) return null;
  return api<PublicSettings>("/api/v1/settings/public");
}
export async function fetchSettings(): Promise<SiteSettings> {
  return api<SiteSettings>("/api/v1/admin/settings");
}
export async function saveSettings(changes: SiteSettings): Promise<SiteSettings> {
  return api<SiteSettings>("/api/v1/admin/settings", { method: "PUT", body: JSON.stringify(changes) });
}
export async function fetchTeachingDashboard(): Promise<TeachingDashboard> {
  return api<TeachingDashboard>("/api/v1/teaching/dashboard");
}
export async function fetchTeachingProgress(): Promise<PlanProgress[]> {
  return api<PlanProgress[]>("/api/v1/teaching/progress");
}
export async function updateAdminUser(id: number, change: { role?: Role; status?: UserStatus }): Promise<AdminUserRow> {
  return api<AdminUserRow>(`/api/v1/admin/users/${id}`, { method: "PATCH", body: JSON.stringify(change) });
}

// â”€â”€ Báº£ng xáº¿p háº¡ng vÃ  trang chÃ­nh sÃ¡ch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
export async function fetchRanking(): Promise<RankingRow[]> {
  return (await api<RankingRow[]>("/api/v1/ranking")) ?? [];
}
export async function fetchSitePages(): Promise<SitePage[]> {
  return (await api<SitePage[]>("/api/v1/pages")) ?? [];
}
export async function fetchSitePage(slug: string): Promise<SitePage> {
  return api<SitePage>(`/api/v1/pages/${encodeURIComponent(slug)}`);
}
export async function updateSitePage(slug: string, title: string, contentMd: string): Promise<SitePage> {
  return api<SitePage>(`/api/v1/admin/pages/${encodeURIComponent(slug)}`, { method: "PUT", body: JSON.stringify({ title, contentMd }) });
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
 * Chuyá»ƒn tráº¡ng thÃ¡i vÃ²ng Ä‘á»i cá»§a bÃ i (DRAFT â†’ REVIEWING â†’ PUBLISHED â†’ ARCHIVED).
 * TÃ¡ch riÃªng khá»i `updateProblem` vÃ¬ server kiá»ƒm tra cáº¡nh tráº¡ng thÃ¡i há»£p lá»‡.
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
  /** Tráº¡ng thÃ¡i vÃ²ng Ä‘á»i; rá»—ng = khÃ´ng lá»c. */
  status: string;
  tags: string[];
  type?: ProblemType;
  /** STANDALONE (máº·c Ä‘á»‹nh cá»§a server) = Ä‘á» láº»; COURSE = Ä‘á» náº±m trong study plan / chá»§ Ä‘á»; ALL = táº¥t cáº£. */
  scope?: ProblemScope;
}

/** Danh sÃ¡ch bÃ i cho trang quáº£n trá»‹ â€” staff tháº¥y cáº£ bÃ i chÆ°a publish. */
export async function fetchAdminProblems(q: AdminProblemQuery): Promise<Page<ProblemFull>> {
  if (USE_MOCK) return mock.listAdminProblems(q);
  const qs = new URLSearchParams({ page: String(q.page - 1), size: "20" });
  if (q.q) qs.set("search", q.q);
  if (q.difficulty) qs.set("difficulty", q.difficulty);
  if (q.status) qs.set("status", q.status);
  if (q.type) qs.set("type", q.type);
  if (q.tags.length) qs.set("tags", q.tags.join(","));
  if (q.scope) qs.set("scope", q.scope);
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

/** Táº¡o bÃ i má»›i. LuÃ´n báº¯t Ä‘áº§u á»Ÿ tráº¡ng thÃ¡i DRAFT. */
export async function createProblem(draft: ProblemDraft): Promise<ProblemFull> {
  if (USE_MOCK) return mock.createProblem(draft);
  return normProblem(await api<Raw>(`/api/v1/problems`, { method: "POST", body: JSON.stringify(draft) }));
}

/** Cáº­p nháº­t metadata cá»§a bÃ i (khÃ´ng Ä‘á»•i tráº¡ng thÃ¡i vÃ²ng Ä‘á»i). */
export async function saveProblem(id: number, draft: ProblemDraft): Promise<ProblemFull> {
  if (USE_MOCK) return mock.updateProblem(id, draft as Partial<ProblemFull>);
  return normProblem(await api<Raw>(`/api/v1/problems/${id}`, { method: "PUT", body: JSON.stringify(draft) }));
}

/** XoÃ¡ cá»©ng bÃ i. Server tráº£ 409 náº¿u bÃ i Ä‘Ã£ cÃ³ lá»‹ch sá»­ bÃ i ná»™p. */
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
  // Backend nháº­n multipart 2 file. TÃŠN FIELD dÆ°á»›i Ä‘Ã¢y lÃ  Ä‘oÃ¡n, hÃ£y Ä‘á»‘i chiáº¿u Swagger.
  const fd = new FormData();
  fd.append("inputFile", new Blob([c.input], { type: "text/plain" }), "input.txt");
  fd.append("outputFile", new Blob([c.expectedOutput], { type: "text/plain" }), "output.txt");
  fd.append("isHidden", String(c.isHidden));
  return normCase(await api<Raw>(`/api/v1/problems/${problemId}/test-cases`, { method: "POST", body: fd }));
}

/** Cáº­p nháº­t testcase. TrÆ°á»ng nÃ o khÃ´ng truyá»n thÃ¬ giá»¯ nguyÃªn (file Ä‘Æ°á»£c gá»­i dáº¡ng multipart). */
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

// ---------- Soáº¡n bÃ i: grading_config, lá»i giáº£i máº«u, sinh output (INSTRUCTOR/ADMIN) ----------
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
/** LÆ°u grading_config (rá»—ng â‡’ xoÃ¡). Lá»—i 400 mang `errors` trong ApiError.body. */
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
 * Cháº¡y lá»i giáº£i máº«u Ä‘Ã£ lÆ°u trong sandbox cá»§a judge.
 * `inputs`: stdin tuá»³ Ã½ (soáº¡n testcase má»›i). KhÃ´ng cÃ³ `inputs`: cháº¡y trÃªn testcase cá»§a bÃ i, `apply` ghi output má»›i.
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
  return api<Submission>(`/api/v1/problems/${problemId}/${run ? "run" : "submit"}`, { method: "POST", body: JSON.stringify(body) }); // /run: [Äá»€ XUáº¤T]
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
/** `verdict` lÃ  tÃªn verdict hoáº·c PENDING; server lá»c vÃ  phÃ¢n trang. Bá» trá»‘ng lÃ  khÃ´ng lá»c. */
export async function fetchMySubmissions(page: number, size = 15, filter: { verdict?: string; language?: string } = {}): Promise<Page<Submission>> {
  if (USE_MOCK) return mock.mySubmissions(page - 1, size, filter);
  const qs = new URLSearchParams({ page: String(page - 1), size: String(size) });
  if (filter.verdict) qs.set("verdict", filter.verdict);
  if (filter.language) qs.set("language", filter.language);
  return normPage<Submission>(await api<Raw>(`/api/v1/submissions/my?${qs}`));
}
/** Sá»‘ bÃ i ná»™p theo verdict (thÃªm PENDING) trÃªn toÃ n bá»™ bÃ i ná»™p cá»§a user. */
export async function fetchMySubmissionSummary(): Promise<Record<string, number>> {
  if (USE_MOCK) return mock.mySubmissionSummary();
  return (await api<Record<string, number>>("/api/v1/submissions/my/summary")) ?? {};
}
export async function fetchSubmissionCode(id: number): Promise<string> {
  if (USE_MOCK) return mock.submissionCode(id);
  return api<string>(`/api/v1/submissions/${id}/code`);
}
// [Äá»€ XUáº¤T] GET /api/v1/submissions/{id}/results â€“ chi tiáº¿t tá»«ng testcase cÃ´ng khai. ChÆ°a cÃ³ â†’ tráº£ null.
export async function fetchSubmissionResults(id: number): Promise<CaseResult[] | null> {
  if (USE_MOCK) return mock.submissionResults(id);
  // Backend chÆ°a cÃ³ endpoint nÃ y (404 â†’ null, UI áº©n chi tiáº¿t tá»«ng testcase); giá»¯ lá»i gá»i Ä‘á»ƒ tá»± hiá»‡n khi backend bá»• sung.
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
    keepalive: true, // váº«n gá»­i Ä‘Æ°á»£c khi tab Ä‘ang bá»‹ áº©n
    body: JSON.stringify({ eventType, clientTimestamp: Date.now() }),
  });
}
// POST /api/v1/contests/{id}/register
export async function registerContest(id: number): Promise<void> {
  if (USE_MOCK) return mock.registerContest(id);
  await api(`/api/v1/contests/${id}/register`, { method: "POST" });
}
// POST /api/v1/contests/{id}/finalize: chá»‘t bÃ i thi, khoÃ¡ ná»™p bÃ i tiáº¿p theo.
export async function finalizeContestSubmission(id: number): Promise<ContestInfo | null> {
  if (USE_MOCK) return mock.getContest(id);
  try {
    return await api<ContestInfo>(`/api/v1/contests/${id}/finalize`, { method: "POST" });
  } catch {
    // Chá»‘t bÃ i lÃ  hÃ nh Ä‘á»™ng "best effort": lá»—i khÃ´ng Ä‘Æ°á»£c cháº·n luá»“ng hiá»ƒn thá»‹ phÃ­a client.
    return null;
  }
}
// GET /api/v1/contests/{id}/proctoring/report (INSTRUCTOR, ADMIN): bÃ¡o cÃ¡o vi pháº¡m cá»§a thÃ­ sinh
export async function fetchProctoringReport(id: number): Promise<ProctorReportRow[]> {
  if (USE_MOCK) return mock.proctoringReport(id);
  return api<ProctorReportRow[]>(`/api/v1/contests/${id}/proctoring/report`);
}
// POST /api/v1/contests/{id}/participants/{userId}/unlock (ADMIN): má»Ÿ khoÃ¡ thÃ­ sinh bá»‹ loáº¡i
export async function unlockParticipant(contestId: number, userId: number): Promise<void> {
  if (USE_MOCK) return mock.unlockParticipant(contestId, userId);
  await api(`/api/v1/contests/${contestId}/participants/${userId}/unlock`, { method: "POST" });
}
// GET /api/v1/contests/{id}/plagiarism: káº¿t quáº£ Ä‘á»‘i soÃ¡t Ä‘áº¡o vÄƒn (Jaccard) cá»§a Ká»¹ sÆ° 2
export async function fetchPlagiarism(id: number): Promise<PlagiarismRow[]> {
  if (USE_MOCK) return mock.plagiarism(id);
  return api<PlagiarismRow[]>(`/api/v1/contests/${id}/plagiarism`);
}
export function resetMockExam(contestId: number) { mock.resetExam(contestId); }

// ---------- Draft (chá»‘ng rá»›t máº¡ng) ----------
// [Äá»€ XUáº¤T] GET/PUT /api/v1/contests/{cid}/problems/{pid}/draft?language=
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
// Backend tráº£ tháº³ng JSON (khÃ´ng bá»c ApiResponse); `api()` xá»­ lÃ½ Ä‘Æ°á»£c cáº£ hai dáº¡ng.
export async function fetchStudyPlans(): Promise<StudyPlanSummary[]> {
  if (USE_MOCK) return mock.listStudyPlans();
  return (await api<StudyPlanSummary[]>("/api/v1/study-plans")) ?? [];
}
export async function fetchStudyPlan(slug: string): Promise<StudyPlanDetail> {
  if (USE_MOCK) return mock.getStudyPlan(slug);
  return api<StudyPlanDetail>(`/api/v1/study-plans/${encodeURIComponent(slug)}`);
}

// Soáº¡n plan (INSTRUCTOR / ADMIN). Server kiá»ƒm tra quyá»n tÃ¡c giáº£, Ä‘á» PUBLISHED, Ä‘á» khÃ´ng thuá»™c plan khÃ¡c.
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

// ---------- Course Builder (specs/course-builder.spec.md) ----------
export async function fetchPublishCheck(slug: string): Promise<PublishCheck> {
  if (USE_MOCK) return mock.publishCheck(slug);
  return api<PublishCheck>(`/api/v1/study-plans/${encodeURIComponent(slug)}/publish-check`);
}
/** Publish plan cÃ¹ng má»i Ä‘á» / chá»§ Ä‘á» chÆ°a PUBLISHED bÃªn trong (má»™t transaction á»Ÿ server). */
export async function publishStudyPlan(slug: string): Promise<StudyPlanDetail> {
  if (USE_MOCK) return mock.publishStudyPlan(slug);
  return api<StudyPlanDetail>(`/api/v1/study-plans/${encodeURIComponent(slug)}/publish`, { method: "POST" });
}
/** Äá» DRAFT má»›i chÃ©p Ä‘á» bÃ i, grading, starter, lá»i giáº£i máº«u vÃ  má»i testcase cá»§a `problemId`. */
export async function cloneProblem(problemId: number, title: string): Promise<ProblemFull> {
  if (USE_MOCK) return mock.cloneProblem(problemId, title);
  return normProblem(await api<Raw>(`/api/v1/admin/problems/${problemId}/clone`, { method: "POST", body: JSON.stringify({ title }) }));
}
/** PUBLISHED â†’ DRAFT Ä‘á»ƒ sá»­a testcase / grading; há»c viÃªn khÃ´ng ná»™p Ä‘Æ°á»£c stage nÃ y cho tá»›i khi publish láº¡i. */
export async function unlockProblem(problemId: number): Promise<ProblemFull> {
  if (USE_MOCK) return mock.unlockProblem(problemId);
  return normProblem(await api<Raw>(`/api/v1/admin/problems/${problemId}/unlock`, { method: "POST" }));
}

// ---------- Chá»§ Ä‘á» há»c (specs/topics.spec.md) ----------
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
// Soáº¡n (INSTRUCTOR / ADMIN)
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

/** File ZIP cá»§a plugin; `npm run plugin:copy` chÃ©p báº£n build vÃ o public/downloads. */
export const PLUGIN_DOWNLOAD_URL = process.env.NEXT_PUBLIC_PLUGIN_DOWNLOAD_URL || "/downloads/mini-leetcode-intellij.zip";
// ---------- Admin: ká»³ thi (/api/v1/admin/contests, INSTRUCTOR + ADMIN; xoÃ¡ chá»‰ ADMIN) ----------
// KhÃ´ng cÃ³ báº£n mock: trang nÃ y luÃ´n nÃ³i chuyá»‡n vá»›i API tháº­t (e2e cháº·n request báº±ng page.route).
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
