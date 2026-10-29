"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { VERDICT } from "@/lib/verdict";
import { fmtMemory } from "@/lib/format";
import { USE_MOCK } from "@/lib/api";
import { DiffView } from "@/components/shared/diff-view";
import type { CaseResult, Submission } from "@/types/api";

interface Props {
  result: Submission | null;
  cases: CaseResult[] | null;
  judging: boolean;
  timedOut: boolean;
  error: { message: string } | null;
  runMode?: boolean;
}

const Spinner = () => <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />;
const Box = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div>
    <p className="mb-1 font-mono text-[11px] uppercase text-muted-foreground">{label}</p>
    <pre className="overflow-auto whitespace-pre-wrap rounded-md border bg-black/90 px-3 py-2 font-mono text-xs text-white">{children}</pre>
  </div>
);

export function ResultPanel({ result, cases, judging, timedOut, error, runMode }: Props) {
  const [sel, setSel] = useState(0);
  // Tự chọn testcase lỗi đầu tiên (fail-fast nên chỉ có tối đa 1 case lỗi)
  useEffect(() => { if (cases) setSel(Math.max(0, cases.findIndex((c) => c.verdict !== "AC" && c.verdict !== "SKIPPED"))); }, [cases]);

  if (error) return <div role="alert" className="m-4 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">{error.message}</div>;

  if (judging) {
    return (
      <div className="flex items-center gap-2 p-4 text-sm text-muted-foreground" aria-live="polite">
        <Spinner />{result?.status === "JUDGING" ? "Đang chấm bài…" : "Đã nhận bài, đang xếp hàng chờ chấm…"}
      </div>
    );
  }
  if (timedOut) {
    return (
      <p className="p-4 text-sm text-amber-600 dark:text-amber-400" role="status">
        Chấm bài lâu hơn dự kiến. Bài của bạn vẫn được lưu, hãy xem kết quả trong <Link href="/submissions" className="underline">Bài đã nộp</Link>.
      </p>
    );
  }
  if (!result?.verdict) {
    return (
      <div className="space-y-2 p-4 text-sm text-muted-foreground">
        <p>Chạy hoặc nộp bài để xem kết quả.</p>
        {USE_MOCK && <p className="text-xs">Judge giả: giữ nguyên template là WA, có while(true) là TLE, có throw new là RE, có syntax error là CE, còn lại là AC.</p>}
      </div>
    );
  }

  const v = VERDICT[result.verdict];
  const c = cases?.[sel];
  return (
    <div className="space-y-3 p-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg bg-secondary px-4 py-3">
        <span className={cn("flex items-center gap-2 text-lg font-semibold", v.ok ? "text-primary" : "text-destructive")}>
          <span className={cn("grid h-5 w-5 place-items-center rounded-full text-white", v.ok ? "bg-primary" : "bg-destructive")}>
            {v.ok ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
          </span>
          {v.label}
        </span>
        <span className="font-mono text-xs text-muted-foreground">
          Runtime: {result.executionTimeMs ?? "—"} ms <span className="mx-2">|</span> Memory: {fmtMemory(result.memoryUsedKb)}
        </span>
        {runMode && <span className="ml-auto rounded bg-background px-2 py-0.5 text-xs text-muted-foreground">Run: chỉ testcase công khai</span>}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-lg bg-secondary p-3">
          <p className="text-xs text-muted-foreground">Status</p>
          <p className={cn("font-semibold", v.ok ? "text-primary" : "text-destructive")}>{v.ok ? "Passed" : "Failed"} ({result.passTestCases}/{result.totalTestCases})</p>
        </div>
        {result.memoryPercentile != null && (
          <div className="rounded-lg bg-secondary p-3">
            <p className="text-xs text-muted-foreground">Memory Percentile</p>
            <p className="font-semibold">Beats {result.memoryPercentile} %</p>
          </div>
        )}
      </div>

      {result.errorMessage && <pre className="max-h-40 overflow-auto rounded-lg bg-black p-3 font-mono text-xs text-red-300">{result.errorMessage}</pre>}

      {cases && cases.length > 0 && (
        <>
          <div className="flex flex-wrap gap-2" role="tablist" aria-label="Chọn testcase">
            {cases.map((k, i) => (
              <button key={i} role="tab" aria-selected={sel === i} onClick={() => setSel(i)}
                className={cn("flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs", sel === i ? "bg-accent" : "bg-card hover:bg-accent/60")}>
                <span className={cn("h-1.5 w-1.5 rounded-full", k.verdict === "AC" ? "bg-primary" : k.verdict === "SKIPPED" ? "bg-muted-foreground" : "bg-destructive")} />
                Case {i + 1}{k.hidden && <span className="text-muted-foreground">(ẩn)</span>}
              </button>
            ))}
          </div>
          {c && (
            <div className="space-y-2 rounded-lg border p-3">
              {c.hidden ? (
                <p className="text-sm text-muted-foreground">Testcase ẩn: chỉ hiển thị kết quả ({c.verdict === "SKIPPED" ? "chưa chạy" : VERDICT[c.verdict as keyof typeof VERDICT]?.label ?? c.verdict}), không hiển thị dữ liệu.</p>
              ) : c.verdict === "SKIPPED" ? (
                <p className="text-sm text-muted-foreground">Chưa chạy: hệ thống dừng ở testcase lỗi đầu tiên (Fail-Fast).</p>
              ) : (
                <>
                  <Box label="Input">{c.input}</Box>
                  {c.verdict === "TLE" || c.verdict === "MLE" || c.verdict === "RE"
                    ? <p className="text-sm text-destructive">{VERDICT[c.verdict].label} tại testcase này.</p>
                    : <DiffView expected={c.expected ?? ""} actual={c.actual ?? ""} />}
                </>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
