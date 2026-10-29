"use client";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";
import { fetchPlagiarism, fetchProctoringReport, unlockParticipant } from "@/lib/services";
import { toast } from "@/lib/toast";
import type { ParticipantStatus, PlagiarismRow, ProctorReportRow, Role } from "@/types/api";

const STATUS: Record<ParticipantStatus, { label: string; cls: string }> = {
  REGISTERED: { label: "Chưa làm", cls: "bg-secondary text-muted-foreground" },
  IN_PROGRESS: { label: "Đang làm", cls: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" },
  SUBMITTED: { label: "Đã nộp", cls: "bg-blue-500/10 text-blue-600 dark:text-blue-400" },
  DISQUALIFIED: { label: "Bị loại", cls: "bg-destructive/15 text-destructive" },
};
const THRESHOLD = 0.8; // BRD: độ tương đồng >= 80% đưa vào danh sách "Cần phúc khảo"

// Báo cáo dành cho INSTRUCTOR / ADMIN (RBAC: "Xem Báo cáo Gian lận / Mất Focus", ADMIN được mở khoá).
export function ContestReport({ contestId, role }: { contestId: number; role: Role }) {
  const [rows, setRows] = useState<ProctorReportRow[] | null>(null);
  const [plag, setPlag] = useState<PlagiarismRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [plagError, setPlagError] = useState<string | null>(null);

  const load = useCallback(() => {
    fetchProctoringReport(contestId).then((r) => { setRows(r); setError(null); }).catch((e) => setError(errorMessage(e)));
    fetchPlagiarism(contestId).then((r) => { setPlag(r); setPlagError(null); }).catch((e) => setPlagError(errorMessage(e)));
  }, [contestId]);
  useEffect(load, [load]);

  const unlock = async (r: ProctorReportRow) => {
    if (!window.confirm(`Mở khoá cho ${r.username}? Số lần vi phạm sẽ được đặt lại.`)) return;
    try { await unlockParticipant(contestId, r.userId); toast(`Đã mở khoá ${r.username}.`, "success"); load(); }
    catch (e) { toast(errorMessage(e), "error"); }
  };

  const flagged = rows?.filter((r) => r.violationCount > 0).length ?? 0;
  return (
    <section className="space-y-3" aria-label="Báo cáo giám sát">
      <h2 className="text-sm font-semibold">Báo cáo dành cho giảng viên</h2>
      <Tabs defaultValue="violations">
        <TabsList>
          <TabsTrigger value="violations">Vi phạm{rows ? ` (${flagged})` : ""}</TabsTrigger>
          <TabsTrigger value="plagiarism">Cần phúc khảo{plag ? ` (${plag.filter((p) => p.similarity >= THRESHOLD).length})` : ""}</TabsTrigger>
        </TabsList>

        <TabsContent value="violations" className="overflow-x-auto rounded-lg border bg-card">
          {error ? <p role="alert" className="p-4 text-sm text-destructive">{error}</p> : !rows ? <p className="p-4 text-sm text-muted-foreground">Đang tải…</p> : (
            <table className="w-full min-w-[520px] text-sm">
              <thead><tr className="border-b text-left font-mono text-xs text-muted-foreground"><th className="px-4 py-2">Thí sinh</th><th className="px-2">Trạng thái</th><th className="px-2 text-center">Vi phạm</th><th className="px-2">Lần gần nhất</th><th /></tr></thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.userId} className="border-b last:border-b-0">
                    <td className="px-4 py-2 font-medium">{r.username}</td>
                    <td className="px-2"><span className={cn("rounded px-2 py-0.5 text-xs", STATUS[r.status].cls)}>{STATUS[r.status].label}</span></td>
                    <td className="px-2 text-center font-mono">{r.violationCount}</td>
                    <td className="px-2 text-xs text-muted-foreground">{r.lastEventAt ? fmtDateTime(r.lastEventAt) : "—"}</td>
                    <td className="px-2 py-1 text-right">{role === "ADMIN" && r.status === "DISQUALIFIED" && <Button size="sm" variant="outline" onClick={() => void unlock(r)}>Mở khoá</Button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TabsContent>

        <TabsContent value="plagiarism" className="overflow-x-auto rounded-lg border bg-card">
          {plagError ? <p role="alert" className="p-4 text-sm text-destructive">{plagError}</p> : !plag ? <p className="p-4 text-sm text-muted-foreground">Đang tải…</p> : plag.length === 0 ? <p className="p-4 text-sm text-muted-foreground">Chưa phát hiện cặp bài nộp nào đáng ngờ.</p> : (
            <table className="w-full min-w-[520px] text-sm">
              <thead><tr className="border-b text-left font-mono text-xs text-muted-foreground"><th className="px-4 py-2">Đề bài</th><th className="px-2">Cặp bài nộp</th><th className="px-2">Độ tương đồng</th></tr></thead>
              <tbody>
                {plag.map((p) => (
                  <tr key={p.id} className="border-b last:border-b-0">
                    <td className="px-4 py-2">{p.problemTitle}</td>
                    <td className="px-2">{p.userA} và {p.userB}</td>
                    <td className="px-2"><span className={cn("font-mono", p.similarity >= THRESHOLD && "font-semibold text-destructive")}>{Math.round(p.similarity * 100)}%</span>{p.similarity >= THRESHOLD && <span className="ml-2 rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive">Cần phúc khảo</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TabsContent>
      </Tabs>
    </section>
  );
}
