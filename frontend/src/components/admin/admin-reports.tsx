"use client";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { useLoad } from "@/hooks/use-load";
import { fetchAdminContests, fetchPlagiarism, fetchProctoringReport } from "@/lib/services";
import { formatWhen } from "./admin-overview";

const selectClass = "h-10 min-w-72 rounded-sm border border-border bg-card px-2 text-body-md text-ink";
const th = "px-4 py-2 font-medium";
const td = "px-4 py-2";

/** /admin/reports: báo cáo giám sát (proctoring) và đạo văn của một kỳ thi. */
export function AdminReportsView() {
  const contests = useLoad(() => fetchAdminContests(), []);
  const [picked, setPicked] = useState<number | null>(null);
  const list = contests.state === "ok" ? contests.data : [];
  const contestId = picked ?? list[0]?.id ?? null;

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        {list.length > 0 && (
          <select value={contestId ?? ""} onChange={(e) => setPicked(Number(e.target.value))} aria-label="Contest" className={selectClass}>
            {list.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
          </select>
        )}
      </div>

      {contests.state === "loading" && <p className="text-body-md text-mute">Loading contests…</p>}
      {contests.state === "error" && <p className="text-body-md text-destructive" role="alert">{contests.message}</p>}
      {contests.state === "ok" && list.length === 0 && <p className="text-body-md text-mute">No contests yet.</p>}
      {contestId != null && <ContestReports key={contestId} contestId={contestId} />}
    </div>
  );
}

function ContestReports({ contestId }: { contestId: number }) {
  const proctor = useLoad(() => fetchProctoringReport(contestId), [contestId]);
  const plagiarism = useLoad(() => fetchPlagiarism(contestId), [contestId]);
  return (
    <>
      <section className="space-y-2">
        <h2 className="text-heading-sm text-ink">Proctoring</h2>
        <div className="overflow-x-auto rounded-sm border border-border bg-card" role="region" aria-label="Proctoring report">
          {proctor.state === "loading" && <p className="p-6 text-body-md text-mute">Loading…</p>}
          {proctor.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{proctor.message}</p>}
          {proctor.state === "ok" && proctor.data.length === 0 && <p className="p-6 text-body-md text-mute">No participants yet.</p>}
          {proctor.state === "ok" && proctor.data.length > 0 && (
            <table className="w-full text-left text-body-md">
              <thead className="border-b border-border text-body-sm text-mute">
                <tr><th className={th}>Participant</th><th className={th}>Status</th><th className={th}>Violations</th><th className={th}>Last event</th></tr>
              </thead>
              <tbody>
                {[...proctor.data].sort((a, b) => b.violationCount - a.violationCount).map((r) => (
                  <tr key={r.userId} className="border-b border-border last:border-0">
                    <td className={`${td} font-medium text-ink`}>{r.username}</td>
                    <td className={td}><Badge variant={r.status === "DISQUALIFIED" ? "destructive" : "secondary"}>{r.status}</Badge></td>
                    <td className={`${td} ${r.violationCount > 0 ? "font-medium text-destructive" : "text-mute"}`}>{r.violationCount}</td>
                    <td className={`${td} text-body-sm text-mute`}>{r.lastEventAt ? formatWhen(r.lastEventAt) : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <section className="space-y-2">
        <h2 className="text-heading-sm text-ink">Plagiarism</h2>
        <div className="overflow-x-auto rounded-sm border border-border bg-card" role="region" aria-label="Plagiarism report">
          {plagiarism.state === "loading" && <p className="p-6 text-body-md text-mute">Loading…</p>}
          {plagiarism.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{plagiarism.message}</p>}
          {plagiarism.state === "ok" && plagiarism.data.length === 0 && <p className="p-6 text-body-md text-mute">No similar solutions found.</p>}
          {plagiarism.state === "ok" && plagiarism.data.length > 0 && (
            <table className="w-full text-left text-body-md">
              <thead className="border-b border-border text-body-sm text-mute">
                <tr><th className={th}>Problem</th><th className={th}>Participants</th><th className={th}>Similarity</th></tr>
              </thead>
              <tbody>
                {[...plagiarism.data].sort((a, b) => b.similarity - a.similarity).map((r) => (
                  <tr key={r.id} className="border-b border-border last:border-0">
                    <td className={`${td} text-ink`}>{r.problemTitle}</td>
                    <td className={`${td} text-ink`}>{r.userA} · {r.userB}</td>
                    <td className={`${td} font-medium`}>{Math.round(r.similarity * 100)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </>
  );
}
