"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { fetchAdminSubmissions } from "@/lib/services";
import { VERDICT } from "@/lib/verdict";
import type { AdminSubmissionList, Verdict } from "@/types/api";
import { formatWhen, Outcome } from "./admin-overview";

const VERDICTS = Object.keys(VERDICT) as Verdict[];
const selectClass = "h-10 rounded-sm border border-border bg-card px-2 text-body-md text-ink";

/** /admin/submissions: mọi bài nộp (không tính Run), lọc theo user, bài, verdict; 50 dòng/trang. */
export function AdminSubmissionsView() {
  const [user, setUser] = useState("");
  const [problem, setProblem] = useState("");
  const [verdict, setVerdict] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminSubmissionList | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Gõ lọc: chờ 300ms rồi mới gọi API.
    const t = setTimeout(() => {
      fetchAdminSubmissions({ user: user.trim(), problem: problem.trim(), verdict }, page)
        .then((d) => { setData(d); setError(null); })
        .catch((e) => setError(errorMessage(e)));
    }, 300);
    return () => clearTimeout(t);
  }, [user, problem, verdict, page]);

  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;
  const filter = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0); };

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <div>
        <h1 className="text-heading-md text-ink">Submissions</h1>
        <p className="text-body-md text-mute">Every submission across problems and contests, newest first. Run attempts are not listed.</p>
      </div>

      <div className="flex flex-wrap gap-3">
        <Input value={user} onChange={(e) => filter(setUser)(e.target.value)} placeholder="Username"
          aria-label="Filter by username" className="h-10 w-56 bg-card" />
        <Input value={problem} onChange={(e) => filter(setProblem)(e.target.value)} placeholder="Problem title or slug"
          aria-label="Filter by problem" className="h-10 w-64 bg-card" />
        <select value={verdict} onChange={(e) => filter(setVerdict)(e.target.value)} aria-label="Filter by verdict" className={selectClass}>
          <option value="">All verdicts</option>
          {VERDICTS.map((v) => <option key={v} value={v}>{VERDICT[v].label}</option>)}
          <option value="PENDING">No verdict (judging or failed)</option>
        </select>
      </div>

      <div className="overflow-x-auto rounded-sm border border-border bg-card" role="region" aria-label="Submissions">
        {error && <p className="p-6 text-body-md text-destructive" role="alert">{error}</p>}
        {!error && !data && <p className="p-6 text-body-md text-mute">Loading submissions…</p>}
        {!error && data?.items.length === 0 && <p className="p-6 text-body-md text-mute">No submissions match these filters.</p>}
        {!error && data && data.items.length > 0 && (
          <table className="w-full text-left text-body-md">
            <thead className="border-b border-border text-body-sm text-mute">
              <tr>
                <th className="px-4 py-2 font-medium">ID</th>
                <th className="px-4 py-2 font-medium">User</th>
                <th className="px-4 py-2 font-medium">Problem</th>
                <th className="px-4 py-2 font-medium">Language</th>
                <th className="px-4 py-2 font-medium">Verdict</th>
                <th className="px-4 py-2 font-medium">Score</th>
                <th className="px-4 py-2 font-medium">Time</th>
                <th className="px-4 py-2 font-medium">Submitted</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((s) => (
                <tr key={s.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-2 font-mono text-body-sm text-mute">#{s.id}</td>
                  <td className="px-4 py-2 font-medium text-ink">{s.username}</td>
                  <td className="px-4 py-2">
                    <Link href={`/problems/${s.problemSlug}`} className="text-ink hover:underline">{s.problemTitle}</Link>
                    {s.contestId != null && <span className="ml-2 text-body-sm text-mute">Contest #{s.contestId}</span>}
                  </td>
                  <td className="px-4 py-2 text-mute">{s.language}</td>
                  <td className="px-4 py-2"><Outcome s={s} /></td>
                  <td className="px-4 py-2 text-mute">{s.score ?? "—"}</td>
                  <td className="px-4 py-2 text-mute">{s.executionTimeMs != null ? `${s.executionTimeMs} ms` : "—"}</td>
                  <td className="px-4 py-2 text-body-sm text-mute">{formatWhen(s.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.total > data.size && (
        <div className="flex items-center justify-end gap-3 text-body-sm text-mute">
          <span>Page {page + 1} of {pages} · {data.total} submissions</span>
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</Button>
        </div>
      )}
    </div>
  );
}
