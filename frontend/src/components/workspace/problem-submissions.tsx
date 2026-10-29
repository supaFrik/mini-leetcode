"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { VerdictBadge } from "@/components/shared/verdict-badge";
import { errorMessage } from "@/lib/api";
import { fmtDateTime, fmtMemory } from "@/lib/format";
import { fetchMySubmissions } from "@/lib/services";
import { LANGUAGES } from "@/lib/templates";
import type { Submission } from "@/types/api";

// Lịch sử nộp bài của riêng đề này (và đúng chế độ Practice / kỳ thi hiện tại).
export function ProblemSubmissions({ problemId, contestId, refreshKey }: { problemId: number; contestId?: number; refreshKey: string }) {
  const [rows, setRows] = useState<Submission[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let off = false;
    fetchMySubmissions(1, 50)
      .then((p) => { if (!off) { setRows(p.content.filter((s) => s.problemId === problemId && (s.contestId ?? undefined) === contestId)); setError(null); } })
      .catch((e) => { if (!off) setError(errorMessage(e)); });
    return () => { off = true; };
  }, [problemId, contestId, refreshKey]);

  if (error) return <p role="alert" className="p-4 text-sm text-destructive">{error}</p>;
  if (!rows) return <p className="p-4 text-sm text-muted-foreground">Đang tải…</p>;
  if (rows.length === 0) return <p className="p-4 text-sm text-muted-foreground">Bạn chưa nộp bài nào cho đề này.</p>;
  return (
    <table className="w-full text-sm">
      <thead><tr className="border-b text-left font-mono text-xs text-muted-foreground"><th className="px-4 py-2">Thời gian</th><th className="px-2">Ngôn ngữ</th><th className="px-2">Kết quả</th><th className="px-2">Runtime</th><th className="px-2">Memory</th></tr></thead>
      <tbody>
        {rows.map((s) => (
          <tr key={s.id} className="border-b last:border-b-0 hover:bg-accent/40">
            <td className="px-4 py-2"><Link href={`/submissions/${s.id}`} className="hover:underline">{fmtDateTime(s.createdAt)}</Link></td>
            <td className="px-2">{LANGUAGES.find((l) => l.value === s.language)?.label ?? "—"}</td>
            <td className="px-2"><VerdictBadge verdict={s.verdict} status={s.status} /></td>
            <td className="px-2 font-mono">{s.executionTimeMs != null ? `${s.executionTimeMs} ms` : "—"}</td>
            <td className="px-2 font-mono">{fmtMemory(s.memoryUsedKb)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
