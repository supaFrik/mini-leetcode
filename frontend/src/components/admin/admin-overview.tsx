"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fetchAdminStats } from "@/lib/services";
import { VERDICT } from "@/lib/verdict";
import { difficultyMeta } from "@/components/problems/difficulty";
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
    { label: "AC rate", value: `${Math.round(stats.acRate * 100)}%` },
    { label: "Active users (7d)", value: stats.activeUsers7d },
  ];

  return (
    <div className="mx-auto w-full max-w-container space-y-6 px-8 py-6">
      {error && <p className="text-body-md text-destructive" role="alert">{error}</p>}
      {!error && !stats && <p className="text-body-md text-mute">Loading overview…</p>}
      {stats && cards && (
        <>
          <dl className="grid grid-cols-6 gap-4">
            {cards.map((c) => {
              const body = (
                <>
                  <dt className="text-body-sm text-mute">{c.label}</dt>
                  <dd className="mt-1 font-mono text-heading-md text-ink">{typeof c.value === "number" ? c.value.toLocaleString("en-US") : c.value}</dd>
                </>
              );
              const box = "block rounded-sm border border-border bg-card px-4 py-3";
              return c.href
                ? <Link key={c.label} href={c.href} className={`${box} hover:border-ink`}>{body}</Link>
                : <div key={c.label} className={box}>{body}</div>;
            })}
          </dl>

          <div className="grid grid-cols-[minmax(0,2fr)_minmax(0,1fr)] gap-6">
            <SubmissionsChart daily={stats.daily} />
            <Panel title="Problems by difficulty">
              <ul className="space-y-3 p-4">
                {Object.entries(stats.byDifficulty).map(([d, n]) => {
                  const { label, color } = difficultyMeta(d);
                  const max = Math.max(1, ...Object.values(stats.byDifficulty));
                  return (
                    <li key={d} className="text-body-sm">
                      <div className="flex justify-between"><span className="text-ink">{label}</span><span className="font-mono text-mute">{n}</span></div>
                      <div className="mt-1 h-2 rounded-full bg-hairline-soft">
                        <div className="h-2 rounded-full" style={{ width: `${(n / max) * 100}%`, backgroundColor: color }} />
                      </div>
                    </li>
                  );
                })}
              </ul>
            </Panel>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <Panel title="Lowest acceptance">
              {stats.lowestAc.length === 0 ? <p className="p-4 text-body-md text-mute">No judged submissions yet.</p> : (
                <ul>
                  {stats.lowestAc.map((p) => (
                    <li key={p.slug} className="flex items-center gap-3 border-b border-border px-4 py-2 text-body-md last:border-0">
                      <Link href={`/problems/${p.slug}`} className="min-w-0 truncate text-link hover:underline">{p.title}</Link>
                      <span className="ml-auto shrink-0 font-mono text-body-sm text-mute">{p.submissions} subs</span>
                      <span className="w-12 shrink-0 text-right font-mono text-body-sm text-ink">{Math.round(p.acRate * 100)}%</span>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
            <Panel title="Upcoming contests" action={<Link href="/admin/contests" className="text-body-sm text-link hover:underline">All contests</Link>}>
              {stats.upcomingContests.length === 0 ? <p className="p-4 text-body-md text-mute">No upcoming contests.</p> : (
                <ul>
                  {stats.upcomingContests.map((c) => (
                    <li key={c.id} className="flex items-center gap-3 border-b border-border px-4 py-2 text-body-md last:border-0">
                      <Link href={`/admin/contests/${c.id}`} className="min-w-0 truncate text-link hover:underline">{c.title}</Link>
                      <span className="ml-auto shrink-0 text-body-sm text-mute">{formatWhen(c.startTime)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </div>

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

function Panel({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="overflow-hidden rounded-sm border border-border bg-card" aria-label={title}>
      <div className="flex items-center border-b border-border px-4 py-3">
        <h2 className="text-body-md font-medium text-ink">{title}</h2>
        {action && <span className="ml-auto">{action}</span>}
      </div>
      {children}
    </section>
  );
}

/** Cột chồng: phần AC (xanh) trên tổng bài nộp mỗi ngày. */
function SubmissionsChart({ daily }: { daily: AdminStats["daily"] }) {
  const max = Math.max(1, ...daily.map((d) => d.total));
  const day = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric" });
  return (
    <Panel title="Submissions, last 14 days">
      <div className="flex h-48 items-end gap-1.5 px-4 pb-2 pt-4" role="img"
        aria-label={`Submissions per day: ${daily.map((d) => `${day(d.date)} ${d.total}`).join(", ")}`}>
        {daily.map((d) => (
          <div key={d.date} className="flex h-full flex-1 flex-col justify-end" title={`${day(d.date)}: ${d.total} submissions, ${d.accepted} accepted`}>
            <div className="flex flex-col justify-end overflow-hidden rounded-t-sm bg-hairline" style={{ height: `${(d.total / max) * 100}%` }}>
              <div className="bg-emerald-500" style={{ height: d.total ? `${(d.accepted / d.total) * 100}%` : 0 }} />
            </div>
          </div>
        ))}
      </div>
      <div className="flex justify-between px-4 pb-3 text-body-sm text-mute">
        <span>{daily[0] && day(daily[0].date)}</span>
        <span className="flex items-center gap-3">
          <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-emerald-500" />Accepted</span>
          <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-sm bg-hairline" />Other</span>
        </span>
        <span>{daily.at(-1) && day(daily.at(-1)!.date)}</span>
      </div>
    </Panel>
  );
}
