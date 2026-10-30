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
  REGISTERED: { label: "Not Started", cls: "bg-hairline-soft text-body" },
  IN_PROGRESS: { label: "In Progress", cls: "bg-success/10 text-success" },
  SUBMITTED: { label: "Submitted", cls: "bg-link-soft text-link-deep" },
  DISQUALIFIED: { label: "Disqualified", cls: "bg-destructive/10 text-destructive" },
};
const THRESHOLD = 0.8;

export function ContestReport({ contestId, role }: { contestId: number; role: Role }) {
  const [rows, setRows] = useState<ProctorReportRow[] | null>(null);
  const [plag, setPlag] = useState<PlagiarismRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [plagError, setPlagError] = useState<string | null>(null);

  const load = useCallback(() => {
    fetchProctoringReport(contestId)
      .then((r) => {
        setRows(r);
        setError(null);
      })
      .catch((e) => setError(errorMessage(e)));
    fetchPlagiarism(contestId)
      .then((r) => {
        setPlag(r);
        setPlagError(null);
      })
      .catch((e) => setPlagError(errorMessage(e)));
  }, [contestId]);
  useEffect(load, [load]);

  const unlock = async (r: ProctorReportRow) => {
    if (!window.confirm(`Unlock ${r.username}? Their violation count will be reset.`)) return;
    try {
      await unlockParticipant(contestId, r.userId);
      toast(`Unlocked ${r.username}.`, "success");
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const flagged = rows?.filter((r) => r.violationCount > 0).length ?? 0;
  return (
    <section className="space-y-4" aria-label="Proctoring report">
      <h2 className="text-heading-md text-ink">Instructor proctoring report</h2>
      <Tabs defaultValue="violations">
        <TabsList>
          <TabsTrigger value="violations">Violations{rows ? ` (${flagged})` : ""}</TabsTrigger>
          <TabsTrigger value="plagiarism">
            Review Required{plag ? ` (${plag.filter((p) => p.similarity >= THRESHOLD).length})` : ""}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="violations" className="overflow-x-auto rounded-xl border border-border bg-card">
          {error ? (
            <p role="alert" className="p-5 text-body-md text-destructive">
              {error}
            </p>
          ) : !rows ? (
            <p className="p-5 text-body-md text-mute">Loading…</p>
          ) : (
            <table className="w-full min-w-[520px] text-body-md">
              <thead>
                <tr className="border-b border-border text-left text-[11px] font-medium uppercase tracking-wider text-mute">
                  <th className="px-5 py-2.5">Candidate</th>
                  <th className="px-2">Status</th>
                  <th className="px-2 text-center">Violations</th>
                  <th className="px-2">Last incident</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.userId} className="border-b border-border last:border-b-0">
                    <td className="px-5 py-2.5 font-medium text-ink">{r.username}</td>
                    <td className="px-2">
                      <span className={cn("rounded-full px-2 py-0.5 text-body-sm", STATUS[r.status].cls)}>
                        {STATUS[r.status].label}
                      </span>
                    </td>
                    <td className="px-2 text-center font-mono">{r.violationCount}</td>
                    <td className="px-2 text-body-sm text-mute">{r.lastEventAt ? fmtDateTime(r.lastEventAt) : "—"}</td>
                    <td className="px-2 py-1 text-right">
                      {role === "ADMIN" && r.status === "DISQUALIFIED" && (
                        <Button size="sm" variant="outline" onClick={() => void unlock(r)}>
                          Unlock
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </TabsContent>

        <TabsContent value="plagiarism" className="overflow-x-auto rounded-xl border border-border bg-card">
          {plagError ? (
            <p role="alert" className="p-5 text-body-md text-destructive">
              {plagError}
            </p>
          ) : !plag ? (
            <p className="p-5 text-body-md text-mute">Loading…</p>
          ) : plag.length === 0 ? (
            <p className="p-5 text-body-md text-mute">No suspicious submission pairs detected.</p>
          ) : (
            <table className="w-full min-w-[520px] text-body-md">
              <thead>
                <tr className="border-b border-border text-left text-[11px] font-medium uppercase tracking-wider text-mute">
                  <th className="px-5 py-2.5">Problem</th>
                  <th className="px-2">Candidate pair</th>
                  <th className="px-2">Similarity</th>
                </tr>
              </thead>
              <tbody>
                {plag.map((p) => (
                  <tr key={p.id} className="border-b border-border last:border-b-0">
                    <td className="px-5 py-2.5 text-ink">{p.problemTitle}</td>
                    <td className="px-2 text-body">
                      {p.userA} and {p.userB}
                    </td>
                    <td className="px-2">
                      <span className={cn("font-mono", p.similarity >= THRESHOLD && "font-medium text-destructive")}>
                        {Math.round(p.similarity * 100)}%
                      </span>
                      {p.similarity >= THRESHOLD && (
                        <span className="ml-2 rounded-full bg-destructive/10 px-2 py-0.5 text-body-sm text-destructive">
                          Review required
                        </span>
                      )}
                    </td>
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
