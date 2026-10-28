export interface ApiResponse<T> {
  success: boolean; statusCode: number; message: string; data: T; timestamp?: string;
}
export interface ProblemDetail {            // lỗi chuẩn RFC 7807
  type?: string; title: string; status: number; detail: string;
  instance?: string; invalid_params?: Record<string, string>;
}

export type Language = "JAVA" | "PYTHON" | "CPP";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type SubmissionStatus = "PENDING" | "JUDGING" | "COMPLETED" | "FAILED";
export type Verdict = "AC" | "WA" | "TLE" | "MLE" | "CE" | "RE" | "SV" | "FLAGGED_CHEATING";

export interface Submission {
  id: number; problemId: number; problemTitle?: string; userId?: number; username?: string;
  language?: Language; status: SubmissionStatus; verdict: Verdict | null;
  executionTimeMs: number | null; memoryUsedKb: number | null;
  passTestCases: number; totalTestCases: number; errorMessage: string | null;
}

export type ProctoringEventType = "WINDOW_BLUR" | "WINDOW_FOCUS" | "CLIPBOARD_PASTE";
export interface ProctoringResult {
  contestId: number; userId: number; status: string;
  violationCount: number; maxViolationsAllowed: number;
  isDisqualified: boolean; awayDurationMs: number | null; message: string;
}

// ⚠ Chưa có trong spec – đây là GIẢ ĐỊNH, đối chiếu lại với Swagger
export interface ProblemSummary {
  id: number; slug: string; title: string; difficulty: Difficulty;
  tags: string[]; status?: "DONE" | "IN_PROGRESS" | "NEVER";
}
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }