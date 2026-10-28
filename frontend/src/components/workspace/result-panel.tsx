"use client";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { VERDICT } from "@/lib/verdict";
import type { Submission } from "@/types/api";

export function ResultPanel({ result, judging }: { result: Submission | null; judging: boolean }) {
  if (judging) return (
    <div className="flex items-center gap-2 p-4 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" />
      {result?.status === "JUDGING" ? "Đang chấm bài…" : "Đang xếp hàng chờ chấm…"}
    </div>
  );
  if (!result?.verdict) return <p className="p-4 text-sm text-muted-foreground">Chạy hoặc nộp bài để xem kết quả.</p>;

  const v = VERDICT[result.verdict];
  const Icon = v.ok ? CheckCircle2 : XCircle;
  return (
    <div className="space-y-3 p-4">
      <div className="flex items-center gap-4 rounded-lg bg-secondary px-4 py-3">
        <span className={cn("flex items-center gap-2 text-lg font-semibold", v.ok ? "text-primary" : "text-destructive")}>
          <Icon className="h-5 w-5" />{v.label}
        </span>
        <span className="font-mono text-xs text-muted-foreground">
          Runtime: {result.executionTimeMs ?? "—"} ms
          <span className="mx-3">|</span>
          Memory: {result.memoryUsedKb != null ? (result.memoryUsedKb / 1024).toFixed(1) : "—"} MB
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-lg bg-secondary p-3">
          <p className="text-xs text-muted-foreground">Status</p>
          <p className={cn("font-semibold", v.ok ? "text-primary" : "text-destructive")}>
            {v.ok ? "Passed" : "Failed"} ({result.passTestCases}/{result.totalTestCases})
          </p>
        </div>
        {/* "Memory Percentile" backend chưa có → chưa render. */}
      </div>

      {/* CE / RE: errorMessage chứa log lỗi biên dịch / stacktrace */}
      {result.errorMessage && (
        <pre className="max-h-40 overflow-auto rounded-lg bg-black p-3 font-mono text-xs text-red-300">{result.errorMessage}</pre>
      )}
      {/* Chip Case 1/2/3 + Input/Output/Expected: cần endpoint chi tiết từng testcase (xem cuối bài) */}
    </div>
  );
}