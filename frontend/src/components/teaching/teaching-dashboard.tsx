"use client";
import Link from "next/link";
import { formatWhen, Outcome } from "@/components/admin/admin-overview";
import { useLoad } from "@/hooks/use-load";
import { fetchTeachingDashboard } from "@/lib/services";

const th = "px-4 py-2 font-medium";
const td = "px-4 py-2";

/** /teaching: tổng quan của giảng viên, chỉ tính nội dung do họ tạo. */
export function TeachingDashboardView() {
  const load = useLoad(fetchTeachingDashboard, []);
  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <div>
        <h1 className="text-heading-md text-ink">Teaching</h1>
        <p className="text-body-md text-mute">Your learners, recent submissions to your problems and your upcoming contests.</p>
      </div>
      {load.state === "loading" && <p className="text-body-md text-mute">Loading…</p>}
      {load.state === "error" && <p className="text-body-md text-destructive" role="alert">{load.message}</p>}
      {load.state === "ok" && (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Stat label="Learners in your study plans" value={load.data.learners} />
            <Stat label="Your study plans" value={load.data.plans} />
          </div>

          <section className="space-y-2">
            <div className="flex items-baseline justify-between">
              <h2 className="text-heading-sm text-ink">Recent submissions</h2>
              <Link href="/admin/submissions" className="text-body-sm text-mute hover:text-ink hover:underline">View all</Link>
            </div>
            <div className="overflow-x-auto rounded-sm border border-border bg-card" role="region" aria-label="Recent submissions">
              {load.data.recentSubmissions.length === 0 ? (
                <p className="p-6 text-body-md text-mute">No submissions to your problems yet.</p>
              ) : (
                <table className="w-full text-left text-body-md">
                  <thead className="border-b border-border text-body-sm text-mute">
                    <tr><th className={th}>User</th><th className={th}>Problem</th><th className={th}>Verdict</th><th className={th}>Submitted</th></tr>
                  </thead>
                  <tbody>
                    {load.data.recentSubmissions.map((s) => (
                      <tr key={s.id} className="border-b border-border last:border-0">
                        <td className={`${td} font-medium text-ink`}>{s.username}</td>
                        <td className={td}><Link href={`/problems/${s.problemSlug}`} className="text-ink hover:underline">{s.problemTitle}</Link></td>
                        <td className={td}><Outcome s={s} /></td>
                        <td className={`${td} text-body-sm text-mute`}>{formatWhen(s.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </section>

          <section className="space-y-2">
            <h2 className="text-heading-sm text-ink">Upcoming contests</h2>
            <div className="rounded-sm border border-border bg-card" role="region" aria-label="Upcoming contests">
              {load.data.upcomingContests.length === 0 ? (
                <p className="p-6 text-body-md text-mute">No upcoming contests.</p>
              ) : (
                <ul>
                  {load.data.upcomingContests.map((c) => (
                    <li key={c.id} className="flex items-center justify-between gap-4 border-b border-border px-4 py-3 last:border-0">
                      <Link href={`/admin/contests/${c.id}`} className="font-medium text-ink hover:underline">{c.title}</Link>
                      <span className="text-body-sm text-mute">{formatWhen(c.startTime)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-sm border border-border bg-card p-4">
      <p className="text-body-sm text-mute">{label}</p>
      <p className="text-heading-md text-ink">{value}</p>
    </div>
  );
}
