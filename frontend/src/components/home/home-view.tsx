"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, Shield, Timer, Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { VerdictBadge } from "@/components/shared/verdict-badge";
import { useCountdown } from "@/hooks/use-countdown";
import { useUser } from "@/hooks/use-user";
import { fmtDateTime, fmtDuration } from "@/lib/format";
import { parseApiDate } from "@/lib/datetime";
import { fetchContests, fetchMySubmissions, fetchProblems } from "@/lib/services";
import type { ContestInfo, Page, ProblemSummary, Submission } from "@/types/api";

interface HomeData {
  problems: Page<ProblemSummary> | null;
  subs: Page<Submission> | null;
  contests: ContestInfo[] | null;
}

function RunningContest({ c }: { c: ContestInfo }) {
  const left = useCountdown(parseApiDate(c.endTime));
  return (
    <Link
      href={`/exam/${c.id}`}
      className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-3 transition-colors hover:bg-hairline-soft"
    >
      <div className="min-w-0">
        <p className="truncate font-medium text-ink">{c.title}</p>
        <p className="text-body-sm text-mute">Ends {fmtDateTime(c.endTime)}</p>
      </div>
      <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-hairline-soft px-2.5 py-1 font-mono text-body-md text-ink">
        <Timer className="h-3.5 w-3.5" />
        {left == null ? "--:--" : fmtDuration(left)}
      </span>
    </Link>
  );
}

const FEATURES = [
  {
    icon: Shield,
    title: "Isolated Evaluation",
    text: "Every submission executes inside a disposable sandbox container with strictly isolated networking, CPU, RAM, and process boundaries. Verdicts return in seconds.",
  },
  {
    icon: Timer,
    title: "Proctored Exam Arena",
    text: "Server-synchronized countdown, focus-loss detection, fullscreen monitoring, and paste blocking. The server is the authoritative judge.",
  },
  {
    icon: Trophy,
    title: "ACM/ICPC Leaderboard",
    text: "Ranked by solved problem count and penalty time, dynamically updated throughout the contest.",
  },
];

export function HomeView() {
  const { user } = useUser();
  const [data, setData] = useState<HomeData | null>(null);

  useEffect(() => {
    let off = false;
    Promise.allSettled([
      fetchProblems({ page: 1, q: "", difficulty: "", progress: "", tags: [] }),
      fetchMySubmissions(1, 50),
      fetchContests(),
    ]).then(([p, s, c]) => {
      if (off) return;
      setData({
        problems: p.status === "fulfilled" ? p.value : null,
        subs: s.status === "fulfilled" ? s.value : null,
        contests: c.status === "fulfilled" ? c.value : null,
      });
    });
    return () => {
      off = true;
    };
  }, []);

  const total = data?.problems?.totalElements;
  const subs = data?.subs?.content ?? [];
  const solved = new Set(subs.filter((s) => s.verdict === "AC").map((s) => s.problemId)).size;
  const acRate = subs.length ? Math.round((subs.filter((s) => s.verdict === "AC").length / subs.length) * 100) : null;
  const pick = data?.problems?.content.length
    ? data.problems.content[new Date().getDate() % data.problems.content.length]
    : null;
  const running = data?.contests?.filter((c) => c.status === "RUNNING") ?? [];
  const pct = total ? Math.min(100, Math.round((solved / total) * 100)) : 0;

  return (
    <div className="mx-auto max-w-container space-y-16 px-4 py-12 sm:px-6 lg:px-8">
      {/* ---------------- Hero: the one place colour is allowed ---------------- */}
      <section className="relative overflow-hidden rounded-2xl border border-border bg-background">
        <div className="mesh-hero absolute inset-0" aria-hidden="true" />
        <div className="relative grid items-center gap-10 p-8 md:grid-cols-[1.15fr_1fr] md:p-12 lg:p-16">
          <div className="space-y-6">
            <h1 className="max-w-[18ch] text-display-xl text-ink">
              Master algorithms. Compete under proctor.
            </h1>
            <p className="max-w-[52ch] text-body-lg text-body">
              Solve algorithmic challenges in Java or Python, review execution runtime and memory
              usage, and inspect diffs. Enter proctored contest rooms with server-synchronized timing.
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <Button asChild size="pill-lg" variant="pill">
                <Link href="/problems">
                  Browse Problems
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>
              <Button asChild size="pill-lg" variant="pill-outline">
                <Link href="/exam">Join Contest</Link>
              </Button>
            </div>
            {user ? (
              <p className="font-mono text-body-sm text-mute">
                Signed in as <span className="text-ink">{user.username}</span>
              </p>
            ) : null}
          </div>

          {/* Proof, not a claim: the product's own mechanism, shown working. */}
          <div className="rounded-xl border border-border bg-card p-5 shadow-whisper">
            <p className="eyebrow">How a submission is graded</p>
            <ol className="mt-4 space-y-3">
              {[
                ["Source compiled in a disposable container", "java", "17"],
                ["Public testcases replayed", "cases", "5 / 5"],
                ["Hidden testcases replayed", "cases", "12 / 12"],
              ].map(([label, k, v]) => (
                <li key={label} className="flex items-center justify-between gap-4 border-b border-border pb-3 last:border-b-0 last:pb-0">
                  <span className="text-body-md text-body">{label}</span>
                  <span className="rounded-full bg-hairline-soft px-2.5 py-1 font-mono text-body-sm text-ink">{v}</span>
                </li>
              ))}
            </ol>
            <div className="mt-4 flex items-center justify-between rounded-lg bg-hairline-soft px-3 py-2.5">
              <span className="text-body-md text-ink">Verdict</span>
              <span className="rounded-full border border-success/30 bg-success/10 px-2.5 py-0.5 font-mono text-body-sm text-success">Accepted</span>
            </div>
          </div>
        </div>
      </section>

      {/* ---------------- Your progress ---------------- */}
      <section aria-label="Your progress">
        <h2 className="text-heading-md text-ink">Your progress</h2>
        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {!data ? (
            Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl border border-border bg-hairline-soft" />
            ))
          ) : (
            <>
              <div className="rounded-xl border border-border bg-card p-5">
                <p className="eyebrow">Solved</p>
                <p className="mt-2 text-heading-lg text-ink">
                  {data.subs ? solved : "—"}
                  <span className="text-body-lg text-mute">{total != null ? ` / ${total}` : ""}</span>
                </p>
                <div
                  className="mt-3 h-1 w-full overflow-hidden rounded-full bg-hairline-soft"
                  role="progressbar"
                  aria-valuenow={pct}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div className="h-full rounded-full bg-ink transition-[width] duration-500 ease-smooth" style={{ width: `${pct}%` }} />
                </div>
              </div>
              <div className="rounded-xl border border-border bg-card p-5">
                <p className="eyebrow">Submissions</p>
                <p className="mt-2 text-heading-lg text-ink">{data.subs ? data.subs.totalElements : "—"}</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-5">
                <p className="eyebrow">AC rate, last 50</p>
                <p className="mt-2 text-heading-lg text-ink">{acRate != null ? `${acRate}%` : "—"}</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-5">
                <p className="eyebrow">Active contests</p>
                <p className="mt-2 text-heading-lg text-ink">{data.contests ? running.length : "—"}</p>
              </div>
            </>
          )}
        </div>
      </section>

      {/* ---------------- Recommendation + recent ---------------- */}
      <div className="grid gap-8 lg:grid-cols-2">
        <section className="space-y-4">
          <h2 className="text-heading-md text-ink">Daily recommendation</h2>
          {pick ? (
            <Link
              href={`/problems/${pick.slug}`}
              className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-5 transition-colors hover:bg-hairline-soft"
            >
              <div>
                <p className="font-medium text-ink">{pick.id}. {pick.title}</p>
                <p className="mt-1 text-body-sm text-mute">{pick.tags.join(" · ")}</p>
              </div>
              <DifficultyBadge value={pick.difficulty} />
            </Link>
          ) : (
            <p className="rounded-xl border border-border bg-card p-5 text-body-md text-mute">
              {data ? "Could not load problems list." : "Loading…"}
            </p>
          )}

          <h2 className="pt-4 text-heading-md text-ink">Active contests</h2>
          {running.length > 0 ? (
            running.map((c) => <RunningContest key={c.id} c={c} />)
          ) : (
            <p className="rounded-xl border border-border bg-card p-5 text-body-md text-mute">
              {data ? (data.contests ? "No active contests at the moment." : "Could not load contests list.") : "Loading…"}
            </p>
          )}
        </section>

        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-heading-md text-ink">Recent submissions</h2>
            <Link href="/submissions" className="text-body-md text-link transition-colors hover:text-link-deep">
              View all
            </Link>
          </div>
          <div className="overflow-hidden rounded-xl border border-border bg-card">
            {subs.slice(0, 5).map((s) => (
              <Link
                key={s.id}
                href={`/submissions/${s.id}`}
                className="flex items-center justify-between gap-3 border-b border-border px-5 py-3.5 text-body-md last:border-b-0 transition-colors hover:bg-hairline-soft"
              >
                <span className="min-w-0 truncate text-ink">{s.problemTitle ?? `Problem #${s.problemId}`}</span>
                <span className="flex shrink-0 items-center gap-3">
                  <span className="hidden text-body-sm text-mute sm:inline">{fmtDateTime(s.createdAt)}</span>
                  <VerdictBadge verdict={s.verdict} status={s.status} />
                </span>
              </Link>
            ))}
            {subs.length === 0 && (
              <p className="p-5 text-body-md text-mute">
                You haven&apos;t submitted any solutions yet. Try an Easy problem to get started.
              </p>
            )}
          </div>
        </section>
      </div>

      {/* ---------------- Feature grid ---------------- */}
      <section aria-label="Features">
        <h2 className="text-heading-md text-ink">Built for serious practice</h2>
        <div className="mt-5 grid gap-4 md:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <div key={title} className="rounded-xl border border-border bg-card p-6">
              <span className="grid h-9 w-9 place-items-center rounded-lg bg-hairline-soft text-ink">
                <Icon className="h-4 w-4" />
              </span>
              <h3 className="mt-4 text-heading-md text-ink">{title}</h3>
              <p className="mt-2 text-body-md text-body">{text}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
