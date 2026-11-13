// ===== Envelope & lỗi (RFC 7807) =====
export interface ApiResponse<T> {
  success: boolean;
  statusCode: number;
  message: string;
  data: T;
  timestamp?: string;
}
/** Phạm vi danh sách đề (chỉ staff gửi được; học viên luôn nhận STANDALONE). */
export type ProblemScope = "STANDALONE" | "COURSE" | "ALL";
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  invalid_params?: Record<string, string>;
  /** Lỗi theo đường dẫn JSON khi grading_config không hợp lệ (400). */
  errors?: string[];
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // trang hiện tại, đếm từ 0
  size: number;
}

// ===== Enum =====
export type Role = "USER" | "INSTRUCTOR" | "ADMIN";
export type Language = "JAVA" | "PYTHON";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type ProblemLifecycle = "DRAFT" | "REVIEWING" | "PUBLISHED" | "ARCHIVED";
export type UserProgress = "DONE" | "IN_PROGRESS" | "NEVER";
export type SubmissionStatus = "PENDING" | "JUDGING" | "COMPLETED" | "FAILED";
export type Verdict = "AC" | "WA" | "TLE" | "MLE" | "CE" | "RE" | "SV" | "FLAGGED_CHEATING";
export type ContestStatus = "SCHEDULED" | "RUNNING" | "COMPLETED";
export type ParticipantStatus = "REGISTERED" | "IN_PROGRESS" | "SUBMITTED" | "DISQUALIFIED";
export type ProctoringEventType = "WINDOW_BLUR" | "WINDOW_FOCUS" | "CLIPBOARD_PASTE";

export type ProblemType = "ALGORITHMIC" | "OOP_GRINDING";

// ===== Đề bài =====
export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  type?: ProblemType;
  /** Tiến độ của user hiện tại. `undefined` khi chưa đăng nhập. */
  progress?: UserProgress;
}
export interface ProblemFull extends ProblemSummary {
  description: string; // Markdown
  timeLimitMs: number;
  memoryLimitMb: number;
  status?: ProblemLifecycle; // vòng đời DRAFT/REVIEWING/PUBLISHED/ARCHIVED
  /** Số testcase của bài (mặc định 0). */
  testCaseCount?: number;
  createdByUsername?: string;
  updatedAt?: string;
  likes?: number;
  dislikes?: number;
  starterTemplates?: string | null;
}
export interface TestCase {
  id: number;
  input: string;
  expectedOutput: string;
  isHidden: boolean;
  orderIndex: number;
  explanation?: string;
  /** Server chỉ trả 8 KB đầu; true ⇒ nội dung hiển thị chưa đầy đủ, không được lưu ngược lại. */
  inputTruncated?: boolean;
  expectedOutputTruncated?: boolean;
}

// ===== Soạn bài (INSTRUCTOR/ADMIN) =====
export interface ProblemAuthoring {
  problemId: number;
  status: ProblemLifecycle;
  problemType: ProblemType;
  /** JSON {"rules": [...], "socketScenarios": [...]} hoặc null. */
  gradingConfig: string | null;
  referenceSolution: SubmissionFile[];
  /** Bài PUBLISHED: rule chấm và testcase bị khoá. */
  gradingFrozen: boolean;
  timeLimitMs: number;
  memoryLimitMb: number;
}
export interface GradingValidation {
  valid: boolean;
  errors: string[];
  ruleCount: number;
  socketScenarioCount: number;
  totalRuleWeight: number;
}
export type ReferenceCaseStatus =
  | "MATCH" | "DIFF" | "OK" | "TIME_LIMIT" | "MEMORY_LIMIT" | "RUNTIME_ERROR" | "OUTPUT_LIMIT";
export interface ReferenceCaseResult {
  testCaseId: number | null;
  orderIndex: number | null;
  hidden: boolean | null;
  status: ReferenceCaseStatus;
  stdout: string;
  stderr: string;
  exitCode: number | null;
  runtimeMs: number | null;
  applied: boolean;
}
export interface ReferenceRunResult {
  compiled: boolean;
  compileOutput: string | null;
  mainClass: string | null;
  /** Lỗi hạ tầng (sandbox không chạy được). */
  error: string | null;
  cases: ReferenceCaseResult[];
  appliedCount: number;
}

// ===== Bài nộp =====
export interface SubmissionFile {
  path: string;
  content: string;
}

export interface Submission {
  id: number;
  problemId: number;
  problemTitle?: string;
  contestId?: number | null;
  userId?: number;
  username?: string;
  language?: Language;
  status: SubmissionStatus;
  verdict: Verdict | null;
  executionTimeMs: number | null;
  memoryUsedKb: number | null;
  passTestCases: number;
  totalTestCases: number;
  score?: number | null;
  executionPhase?: string | null;
  scoreBreakdown?: string | null;
  retryCount?: number | null; // số lần bài nộp bị chấm lại (worker chết giữa chừng)
  errorMessage: string | null;
  memoryPercentile?: number; // backend CHƯA có
  createdAt?: string;
  updatedAt?: string;
}
export interface CaseResult {
  index: number;
  verdict: "AC" | "WA" | "TLE" | "MLE" | "RE" | "SKIPPED";
  hidden: boolean;
  input?: string;
  expected?: string;
  actual?: string;
  timeMs?: number;
}
export interface SubmitBody {
  language: Language;
  code?: string;
  files?: SubmissionFile[];
  contestId?: number;
}

// ===== Kỳ thi =====
export interface ContestProblem { id: number; slug: string; title: string; difficulty: Difficulty; label: string }
export interface ContestInfo {
  id: number;
  title: string;
  startTime: string;
  endTime: string;
  status: ContestStatus;
  scoringPolicy: "ACM_ICPC" | "PARTIAL_IOI";
  maxViolationsAllowed: number;
  problems: ContestProblem[];
  myStatus?: ParticipantStatus;
  myViolations?: number;
  /** Hạng của user ở kỳ thi đã kết thúc (null nếu không tham gia hoặc bị loại). */
  myRank?: number | null;
  /** Số bài user đã giải ở kỳ thi đã kết thúc. */
  mySolved?: number | null;
}
export interface ProctoringResult {
  contestId: number;
  userId: number;
  status: ParticipantStatus;
  violationCount: number;
  maxViolationsAllowed: number;
  isDisqualified: boolean;
  awayDurationMs: number | null;
  message: string;
}
export interface LeaderboardCell { solved: boolean; attempts: number; minute?: number }
export interface LeaderboardRow {
  rank: number | null;
  userId: number;
  username: string;
  solved: number;
  penaltyMinutes: number;
  disqualified?: boolean;
  cells: Record<number, LeaderboardCell>;
}
export interface Draft { value: string; savedAt: number }

// ===== Báo cáo giám sát (giảng viên / admin) =====
export interface ProctoringLogItem { at: number; type: ProctoringEventType; message: string; violationCount: number }
export interface ProctorReportRow {
  userId: number; username: string; status: ParticipantStatus; violationCount: number; lastEventAt?: string;
}
export interface PlagiarismRow { id: number; problemId: number; problemTitle: string; userA: string; userB: string; similarity: number }

// ===== Study Plan (specs/study-plan.spec.md) =====
/** Trạng thái một stage với người đang xem — server suy ra từ submissions. */
export type StageStatus = "LOCKED" | "NEVER" | "IN_PROGRESS" | "DONE";
export type StudyPlanStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type StudyPlanItemKind = "PROJECT" | "PROBLEM";
export interface StudyPlanSummary {
  id: number; slug: string; title: string; description?: string | null; status: StudyPlanStatus;
  itemCount: number; stageCount: number; doneStages: number;
  createdById?: number; createdByUsername?: string;
}
/** `published = false`: đề đang bảo trì, không mở được dù vẫn giữ trạng thái học. */
export interface StudyPlanStage {
  stageNo: number; problemId: number; slug: string | null; title: string | null; difficulty: Difficulty | null;
  problemType: ProblemType | null; published: boolean; status: StageStatus;
  /** Chủ đề học gắn vào stage (specs/topics.spec.md); có thể thiếu ở API cũ. */
  topics?: TopicRef[];
  /** Chỉ staff: trạng thái vòng đời của đề và số học viên đã nộp nhưng chưa AC (specs/course-builder.spec.md). */
  problemStatus?: ProblemLifecycle | null;
  learnersOnStage?: number | null;
}

// ===== Chủ đề học (specs/topics.spec.md) =====
export type TopicStepType = "THEORY" | "CHOICE" | "MATCHING" | "ORDERING" | "FILL_BLANK" | "CODE";
export type TopicProgress = "NEW" | "IN_PROGRESS" | "SKIPPED" | "DONE";
export interface TopicRef {
  id: number; slug: string; title: string; summary?: string | null; estMinutes: number;
  progress: TopicProgress; stepCount: number; doneSteps: number; status?: StudyPlanStatus;
}
export interface IdText { id: string; text: string }
/** Nội dung công khai từng loại step (đã bỏ đáp án, cột phải / các dòng đã xáo trộn). */
export interface TheoryContent { markdown: string }
export interface ChoiceContent { prompt: string; options: IdText[]; multiple?: boolean; hint?: string }
export interface MatchingContent { prompt: string; left: IdText[]; right: IdText[]; hint?: string }
export interface OrderingContent { prompt: string; items: IdText[]; hint?: string }
export interface FillBlankContent { prompt: string; code: string; blanks?: string[]; hint?: string }
export interface CodeContent {
  intro?: string; problemId?: number; problemSlug?: string; problemTitle?: string; difficulty?: Difficulty | null; published?: boolean;
}
export type StepContent = TheoryContent | ChoiceContent | MatchingContent | OrderingContent | FillBlankContent | CodeContent;
export interface LearnerStep {
  id: number; position: number; type: TopicStepType; title: string; content: StepContent; passed: boolean; attempts: number;
}
export interface LearnerTopic {
  id: number; slug: string; title: string; summary?: string | null; estMinutes: number; status: StudyPlanStatus;
  progress: TopicProgress; stepCount: number; doneSteps: number; steps: LearnerStep[];
  /** Đang bị khoá luyện tập tới thời điểm này (null = không khoá). */
  lockedUntil?: string | null;
  /** Phải đọc lại lý thuyết trước khi luyện tiếp. */
  mustReread?: boolean;
}
export interface AttemptResult {
  correct: boolean; message: string; explanation?: string | null; stepPassed: boolean; attempts: number;
  progress: TopicProgress; doneSteps: number; stepCount: number;
  wrongStreak?: number; lockedUntil?: string | null; mustReread?: boolean;
}
/** Bài gửi của từng loại step. */
export type StepAnswer =
  | Record<string, never>
  | { selected: string[] }
  | { pairs: Record<string, string> }
  | { order: string[] }
  | { values: Record<string, string> };

// Soạn chủ đề (INSTRUCTOR / ADMIN)
export interface TopicSummary {
  id: number; slug: string; title: string; summary?: string | null; estMinutes: number; status: StudyPlanStatus;
  stepCount: number; createdById: number; createdByUsername: string; updatedAt?: string | null;
}
/** `answer`: đáp án — chỉ có ở API soạn. */
export interface AuthoringStep {
  id?: number | null; type: TopicStepType; title: string;
  content: Record<string, unknown>; answer?: Record<string, unknown> | null;
  problemId?: number | null; problemSlug?: string | null;
}
export interface AuthoringTopic {
  id: number; slug: string; title: string; summary?: string | null; estMinutes: number; status: StudyPlanStatus;
  steps: AuthoringStep[]; createdById: number; createdByUsername: string;
}
export interface TopicDraft {
  slug: string; title: string; summary?: string; estMinutes: number; steps: AuthoringStep[];
}
export interface StudyPlanItem {
  id: number; position: number; kind: StudyPlanItemKind; title: string; description?: string | null;
  done: boolean; stages: StudyPlanStage[];
}
export interface StudyPlanDetail {
  id: number; slug: string; title: string; description?: string | null; status: StudyPlanStatus;
  stageCount: number; doneStages: number; items: StudyPlanItem[];
  createdById?: number; createdByUsername?: string;
  /** Gửi lại làm `expectedUpdatedAt` khi lưu để phát hiện sửa ở tab khác. */
  updatedAt?: string | null;
  /** Chỉ staff: số học viên có bài nộp chính thức trên đề của plan. */
  learnerCount?: number | null;
}
/** Một mục checklist publish; `node` chỉ tới nút trên outline của Course Builder. */
export interface PublishIssue {
  node: "plan" | "item" | "stage" | "topic"; itemIndex?: number | null; problemId?: number | null; topicId?: number | null; message: string;
}
export interface PublishCheck {
  ok: boolean; errors: PublishIssue[]; warnings: PublishIssue[]; problemsToPublish: number[]; topicsToPublish: number[];
}
/** Body của POST / PUT /api/v1/study-plans (thay toàn bộ nội dung plan). */
export interface StudyPlanDraft {
  slug: string; title: string; description?: string;
  expectedUpdatedAt?: string | null;
  /** `stageTopics[k]`: id chủ đề học của stage k (cùng thứ tự với problemIds). */
  items: { kind: StudyPlanItemKind; title: string; description?: string; problemIds: number[]; stageTopics?: number[][] }[];
}

// ===== Personal Access Token cho plugin IntelliJ =====
export interface AccessToken {
  id: number; name: string; createdAt: string; expiresAt: string; lastUsedAt?: string | null; revoked: boolean;
}
/** `token` chỉ có đúng một lần, ngay khi tạo. */
export interface CreatedAccessToken { token: string; info: AccessToken }

// ---------- Admin: quản lý kỳ thi (/api/v1/admin/contests) ----------
export type RegistrationMode = "OPEN" | "INVITE_ONLY";
export type ScoringPolicy = "ACM_ICPC" | "PARTIAL_IOI";

export interface AdminContestSummary {
  id: number;
  title: string;
  slug: string;
  status: ContestStatus;
  startTime: string;
  endTime: string;
  registrationMode: RegistrationMode;
  problemCount: number;
  participantCount: number;
}
export interface AdminContestProblem {
  problemId: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  problemStatus?: ProblemLifecycle;
  label: string;
  orderIndex: number;
}
export interface AdminContestDetail {
  id: number;
  title: string;
  slug: string;
  status: ContestStatus;
  startTime: string;
  endTime: string;
  scoringPolicy: ScoringPolicy;
  maxViolationsAllowed: number;
  registrationMode: RegistrationMode;
  createdBy: string | null;
  version: number;
  hasSubmissions: boolean;
  problems: AdminContestProblem[];
}
/** Body tạo/sửa kỳ thi. Mốc thời gian là ISO UTC (`toISOString()`). */
export interface ContestUpsert {
  title: string;
  startTime: string;
  endTime: string;
  scoringPolicy: ScoringPolicy;
  maxViolationsAllowed: number;
  registrationMode: RegistrationMode;
  problems: { problemId: number; label: string; orderIndex: number }[];
  version?: number;
}
export interface AdminParticipant {
  userId: number;
  username: string;
  email: string;
  status: ParticipantStatus;
  violationCount: number;
  solved: number;
  penaltyMinutes: number;
  rank: number | null;
  hasSubmissions: boolean;
}
export type BulkOutcome = "ADDED" | "ALREADY_REGISTERED" | "NOT_FOUND" | "BLOCKED";
export interface BulkParticipantsResult {
  lines: { input: string; outcome: BulkOutcome; username: string | null }[];
  added: number;
}
