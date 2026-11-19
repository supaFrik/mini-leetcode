"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fetchAdminStats } from "@/lib/services";
import { VERDICT } from "@/lib/verdict";
import type { AdminRecentSubmission, AdminStats } from "@/types/api";

export function formatWhen(iso: string): string {
  return new Date(parseApiDate(iso)).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

export function Outcome({ s }: { s: Pick<AdminRecentSubmission, "status" | "verdict"> }) {
  if (!s.verdict) return <Badge variant="secondary">{s.status === "FAILED" ? "Failed" : "Judging"}</Badge>;
  const v = VERDICT[s.verdict];
  return <Badge variant={v.ok ? "success" : "destructive"} title={v.label}>{v.short}</Badge>;
}

const th = "px-4 py-2 font-medium";
const td = "px-4 py-2";

/** Tổng quan quản trị: 4 số liệu, 10 bài nộp mới, 5 người dùng mới. Chỉ ADMIN. */
export function AdminOverviewView() {
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    fetchAdminStats().then(setStats).catch((e) => setError(errorMessage(e)));
  }, []);

  const cards = stats && [
    { label: "Users", value: stats.users, href: "/admin/users" },
    { label: "Problems", value: stats.problems, href: "/admin/problems" },
    { label: "Submissions", value: stats.submissions },
    { label: "Contests", value: stats.contests, href: "/admin/contests" },
  ];

  return (
    <div className="mx-auto w-full max-w-container space-y-6 px-8 py-6">
      {error && <p className="text-body-md text-destructive" role="alert">{error}</p>}
      {!error && !stats && <p className="text-body-md text-mute">Loading overview…</p>}
      {stats && cards && (
        <>
          <dl className="grid grid-cols-4 gap-4">
            {cards.map((c) => {
              const body = (
                <>
                  <dt className="text-body-sm text-mute">{c.label}</dt>
                  <dd className="mt-1 font-mono text-heading-md text-ink">{c.value.toLocaleString("en-US")}</dd>
                </>
              );
              const box = "block rounded-sm border border-border bg-card px-4 py-3";
              return c.href
                ? <Link key={c.label} href={c.href} className={`${box} hover:border-ink`}>{body}</Link>
                : <div key={c.label} className={box}>{body}</div>;
            })}
          </dl>

          <div className="grid grid-cols-[minmax(0,2fr)_minmax(0,1fr)] gap-6">
            <section className="overflow-hidden rounded-sm border border-border bg-card" aria-labelledby="recent-subs">
              <h2 id="recent-subs" className="border-b border-border px-4 py-3 text-body-md font-medium text-ink">Latest submissions</h2>
              {stats.recentSubmissions.length === 0 ? (
                <p className="p-4 text-body-md text-mute">No submissions yet.</p>
              ) : (
                <table className="w-full text-left text-body-md">
                  <thead className="border-b border-border text-body-sm text-mute">
                    <tr><th className={th}>User</th><th className={th}>Problem</th><th className={th}>Result</th><th className={`${th} text-right`}>When</th></tr>
                  </thead>
                  <tbody>
                    {stats.recentSubmissions.map((s) => (
                      <tr key={s.id} className="border-b border-border last:border-0">
                        <td className={`${td} font-medium text-ink`}>{s.username}</td>
                        <td className={`${td} max-w-0 truncate`}>
                          <Link href={`/problems/${s.problemSlug}`} className="text-link hover:underline">{s.problemTitle}</Link>
                        </td>
                        <td className={td}><Outcome s={s} /></td>
                        <td className={`${td} whitespace-nowrap text-right text-body-sm text-mute`}>{formatWhen(s.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>

            <section className="overflow-hidden rounded-sm border border-border bg-card" aria-labelledby="recent-users">
              <div className="flex items-center border-b border-border px-4 py-3">
                <h2 id="recent-users" className="text-body-md font-medium text-ink">New users</h2>
                <Link href="/admin/users" className="ml-auto text-body-sm text-link hover:underline">All users</Link>
              </div>
              <ul>
                {stats.recentUsers.map((u) => (
                  <li key={u.id} className="flex items-center gap-2 border-b border-border px-4 py-2 last:border-0">
                    <div className="min-w-0">
                      <p className="truncate text-body-md font-medium text-ink">{u.username}</p>
                      <p className="truncate text-body-sm text-mute">{u.email}</p>
                    </div>
                    <span className="ml-auto shrink-0 text-body-sm text-mute">{formatWhen(u.createdAt)}</span>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
