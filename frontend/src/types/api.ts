// ===== Envelope & lỗi (RFC 7807) =====
export interface ApiResponse<T> {
  success: boolean;
  statusCode: number;
  message: string;
  data: T;
  timestamp?: string;
}
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  invalid_params?: Record<string, string>;
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
export type Language = "JAVA" | "PYTHON" | "CPP";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type ProblemLifecycle = "DRAFT" | "REVIEWING" | "PUBLISHED" | "ARCHIVED";
export type UserProgress = "DONE" | "IN_PROGRESS" | "NEVER";
export type SubmissionStatus = "PENDING" | "JUDGING" | "COMPLETED" | "FAILED";
export type Verdict = "AC" | "WA" | "TLE" | "MLE" | "CE" | "RE" | "SV" | "FLAGGED_CHEATING";
export type ContestStatus = "SCHEDULED" | "RUNNING" | "COMPLETED";
export type ParticipantStatus = "REGISTERED" | "IN_PROGRESS" | "SUBMITTED" | "DISQUALIFIED";
export type ProctoringEventType = "WINDOW_BLUR" | "WINDOW_FOCUS" | "CLIPBOARD_PASTE";

// ===== Đề bài =====
export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  progress?: UserProgress; // tiến độ của user: backend CHƯA có
}
export interface ProblemFull extends ProblemSummary {
  description: string; // Markdown
  timeLimitMs: number;
  memoryLimitMb: number;
  status?: ProblemLifecycle; // vòng đời DRAFT/REVIEWING/PUBLISHED/ARCHIVED
  likes?: number;
  dislikes?: number;
}
export interface TestCase {
  id: number;
  input: string;
  expectedOutput: string;
  isHidden: boolean;
  orderIndex: number;
  explanation?: string;
}

// ===== Bài nộp =====
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
  code: string;
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
