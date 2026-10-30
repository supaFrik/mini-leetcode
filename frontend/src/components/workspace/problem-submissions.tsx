"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { VerdictBadge } from "@/components/shared/verdict-badge";
import { errorMessage } from "@/lib/api";
import { fmtDateTime, fmtMemory } from "@/lib/format";
import { fetchMySubmissions } from "@/lib/services";
import { LANGUAGES } from "@/lib/templates";
import type { Submission } from "@/types/api";

/**
 * Số lần bài nộp bị chấm lại (bài nộp bị chấm lại khi judge worker chết giữa
 * chừng và message trong Redis Stream được XCLAIM lại).
 */
const attemptsLabel = (retryCount?: number | null) => (retryCount != null && retryCount > 0 ? String(retryCount + 1) : "1");
const attemptsTitle = (retryCount?: number | null) =>
  retryCount != null && retryCount > 0
    ? `Bài nộp được chấm lại ${retryCount} lần do worker bị gián đoạn`
    : "Chấm lần đầu";

export function ProblemSubmissions({ problemId, contestId, refreshKey }: { problemId: number; contestId?: number; refreshKey: string }) {
  const [rows, setRows] = useState<Submission[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let off = false;
    fetchMySubmissions(1, 50)
      .then((p) => {
        if (!off) {
          setRows(p.content.filter((s) => s.problemId === problemId && (s.contestId ?? undefined) === contestId));
          setError(null);
        }
      })
      .catch((e) => {
        if (!off) setError(errorMessage(e));
      });
    return () => {
      off = true;
    };
  }, [problemId, contestId, refreshKey]);

  if (error)
    return (
      <p role="alert" className="p-4 text-body-md text-destructive">
        {error}
      </p>
    );
  if (!rows) return <p className="p-4 text-body-md text-mute">Loading…</p>;
  if (rows.length === 0)
    return (
      <p className="p-4 text-body-md text-mute">You haven&apos;t submitted any solutions for this problem yet.</p>
    );
  return (
    <table className="w-full text-body-md">
      <thead>
        <tr className="border-b border-border text-left text-[11px] font-medium uppercase tracking-wider text-mute">
          <th className="px-4 py-2.5">Time</th>
          <th className="px-2">Language</th>
          <th className="px-2">Status</th>
          <th className="px-2">Runtime</th>
          <th className="px-2">Peak memory</th>
          <th className="px-2">Attempts</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((s) => (
          <tr key={s.id} className="border-b border-border last:border-b-0 transition-colors hover:bg-hairline-soft">
            <td className="px-4 py-2.5">
              <Link href={`/submissions/${s.id}`} className="text-ink underline-offset-2 hover:underline">
                {fmtDateTime(s.createdAt)}
              </Link>
            </td>
            <td className="px-2 text-body">{LANGUAGES.find((l) => l.value === s.language)?.label ?? "—"}</td>
            <td className="px-2">
              <VerdictBadge verdict={s.verdict} status={s.status} />
            </td>
            <td className="px-2 font-mono text-ink">{s.executionTimeMs != null ? `${s.executionTimeMs} ms` : "—"}</td>
            <td className="px-2 font-mono text-ink">{fmtMemory(s.memoryUsedKb)}</td>
            <td className="px-2 font-mono text-ink" title={attemptsTitle(s.retryCount)}>
              {attemptsLabel(s.retryCount)}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
