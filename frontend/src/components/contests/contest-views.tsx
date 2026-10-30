"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { useCountdown } from "@/hooks/use-countdown";
import { USE_MOCK, errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fetchContest, fetchContests, fetchLeaderboard, registerContest, resetMockExam } from "@/lib/services";
import { toast } from "@/lib/toast";
import { ContestReport } from "./contest-report";
import { fmtDateTime, fmtDuration, fmtLong } from "@/lib/format";
import { useUser } from "@/hooks/use-user";
import type { ContestInfo, ContestStatus, LeaderboardRow } from "@/types/api";

const STATUS_STYLE: Record<ContestStatus, string> = {
  RUNNING: "border-success/25 bg-success/10 text-success",
  SCHEDULED: "border-warning/25 bg-warning/10 text-[#8a5a00]",
  COMPLETED: "border-border bg-hairline-soft text-body",
};
const STATUS_LABEL: Record<ContestStatus, string> = { RUNNING: "Live", SCHEDULED: "Upcoming", COMPLETED: "Ended" };
const StatusPill = ({ s }: { s: ContestStatus }) => (
  <span className={cn("inline-flex items-center rounded-full border px-2.5 py-0.5 font-mono text-body-sm", STATUS_STYLE[s])}>
    {STATUS_LABEL[s]}
  </span>
);
const Note = ({ children, error }: { children: React.ReactNode; error?: boolean }) => (
  <p role={error ? "alert" : undefined} className={cn("p-10 text-center text-body-md", error ? "text-destructive" : "text-mute")}>
    {children}
  </p>
);

function useAsync<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [n, setN] = useState(0);
  useEffect(() => {
    let off = false;
    load()
      .then((d) => {
        if (!off) {
          setData(d);
          setError(null);
        }
      })
      .catch((e) => {
        if (!off) setError(errorMessage(e));
      });
    return () => {
      off = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n]);
  return { data, error, reload: () => setN((x) => x + 1) };
}

function Countdown({ target, prefix }: { target: number; prefix: string }) {
  const left = useCountdown(target);
  return (
    <span className="font-mono text-body-sm text-mute">
      {left == null ? "" : `${prefix} ${fmtLong(left)}`}
    </span>
  );
}

const SECTIONS: { status: ContestStatus; title: string; empty: string }[] = [
  { status: "RUNNING", title: "Live", empty: "No contests currently in progress." },
  { status: "SCHEDULED", title: "Upcoming", empty: "No upcoming contests scheduled." },
  { status: "COMPLETED", title: "Ended", empty: "No past contests found." },
];

export function ContestList() {
  const { data, error, reload } = useAsync(fetchContests, []);
  if (error)
    return (
      <Note error>
        {error}{" "}
        <button className="underline" onClick={reload}>
          Try again
        </button>
      </Note>
    );
  if (!data)
    return (
      <div className="mx-auto max-w-3xl space-y-3 p-6">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="h-20 animate-pulse rounded-xl border border-border bg-hairline-soft" />
        ))}
      </div>
    );
  return (
    <div className="mx-auto max-w-3xl space-y-8 px-4 py-8 sm:px-6">
      <div>
        <h1 className="text-heading-lg text-ink">Contests</h1>
        <p className="mt-1 text-body-md text-body">
          Server-timed contests with strict proctoring and anti-cheating measures. Registration is required
          before participating.
        </p>
      </div>
      {SECTIONS.map(({ status, title, empty }) => {
        const list = data.filter((c) => c.status === status);
        return (
          <section key={status} className="space-y-3" aria-label={title}>
            <h2 className="text-heading-md text-ink">
              {title} <span className="font-normal text-mute">({list.length})</span>
            </h2>
            {list.length === 0 && (
              <p className="rounded-xl border border-dashed border-border p-5 text-body-md text-mute">{empty}</p>
            )}
            {list.map((c) => (
              <Link
                key={c.id}
                href={`/exam/${c.id}`}
                className="flex items-center justify-between gap-4 rounded-xl border border-border bg-card p-5 transition-colors hover:bg-hairline-soft"
              >
                <div className="min-w-0 space-y-1">
                  <p className="font-medium text-ink">{c.title}</p>
                  <p className="text-body-sm text-mute">
                    {fmtDateTime(c.startTime)} to {fmtDateTime(c.endTime)}
                  </p>
                  {c.status === "RUNNING" && <Countdown target={parseApiDate(c.endTime)} prefix="Remaining" />}
                  {c.status === "SCHEDULED" && <Countdown target={parseApiDate(c.startTime)} prefix="Starts in" />}
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <StatusPill s={c.status} />
                  {c.myStatus === "DISQUALIFIED" ? (
                    <span className="rounded-full bg-destructive/10 px-2 py-0.5 text-body-sm text-destructive">
                      Disqualified
                    </span>
                  ) : c.myStatus ? (
                    <span className="rounded-full bg-hairline-soft px-2 py-0.5 text-body-sm text-body">Registered</span>
                  ) : c.status !== "COMPLETED" ? (
                    <span className="text-body-sm text-[#8a5a00]">Not registered</span>
                  ) : null}
                </div>
              </Link>
            ))}
          </section>
        );
      })}
    </div>
  );
}

export function ContestOverview({ contestId }: { contestId: number }) {
  const { data: c, error, reload } = useAsync(() => fetchContest(contestId), [contestId]);
  const { user } = useUser();
  const [busy, setBusy] = useState(false);
  const left = useCountdown(c?.status === "RUNNING" ? parseApiDate(c.endTime) : null);
  if (error) return <Note error>{error}</Note>;
  if (!c) return <Note>Loading contest…</Note>;
  const dq = c.myStatus === "DISQUALIFIED";
  const registered = !!c.myStatus;

  const join = async () => {
    setBusy(true);
    try {
      await registerContest(c.id);
      toast("Registered successfully.", "success");
      reload();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-heading-lg text-ink">{c.title}</h1>
        <StatusPill s={c.status} />
        {c.status === "RUNNING" && left != null && (
          <span className="font-mono text-body-md text-body">Remaining {fmtDuration(left)}</span>
        )}
        <Button asChild variant="outline" size="sm" className="ml-auto">
          <Link href={`/exam/${c.id}/leaderboard`}>
            <Trophy className="mr-1.5 h-4 w-4" />
            Leaderboard
          </Link>
        </Button>
      </div>
      <p className="text-body-md text-body">
        {fmtDateTime(c.startTime)} to {fmtDateTime(c.endTime)}. Scoring policy:{" "}
        {c.scoringPolicy === "ACM_ICPC" ? "ACM/ICPC" : "IOI (partial scores)"}.
      </p>

      {!registered && c.status !== "COMPLETED" && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-5">
          <p className="text-body-md text-body">
            You have not registered for this contest. Register now to enter the arena when it starts.
          </p>
          <Button onClick={() => void join()} disabled={busy}>
            {busy ? "Registering…" : "Register Now"}
          </Button>
        </div>
      )}
      {dq && (
        <p
          role="alert"
          className="rounded-sm border border-destructive/30 bg-destructive/5 p-3 text-body-md text-destructive"
        >
          You have been disqualified from this contest due to exceeding allowed violations (
          {c.myViolations}/{c.maxViolationsAllowed}).
        </p>
      )}
      {!dq && (c.myViolations ?? 0) > 0 && (
        <p className="text-body-md text-[#8a5a00]">
          You currently have {c.myViolations}/{c.maxViolationsAllowed} violations.
        </p>
      )}

      <div className="overflow-hidden rounded-xl border border-border bg-card">
        {c.problems.map((p) => (
          <div key={p.id} className="flex items-center gap-3 border-b border-border px-5 py-3.5 last:border-b-0">
            <span className="grid h-7 w-7 place-items-center rounded-sm bg-hairline-soft font-mono text-body-md text-ink">
              {p.label}
            </span>
            <span className="flex-1 font-medium text-ink">{p.title}</span>
            <DifficultyBadge value={p.difficulty} />
            {c.status === "RUNNING" && registered && !dq ? (
              <Button asChild size="sm">
                <Link href={`/exam/${c.id}/problems/${p.id}`}>Enter Problem</Link>
              </Button>
            ) : (
              <Button size="sm" disabled>
                {dq ? "Locked" : !registered && c.status !== "COMPLETED" ? "Register first" : c.status === "SCHEDULED" ? "Not open" : "Closed"}
              </Button>
            )}
          </div>
        ))}
      </div>

      {user && user.role !== "USER" && <ContestReport contestId={c.id} role={user.role} />}

      {USE_MOCK && user && (
        <p className="text-body-sm text-mute">
          Mock mode:{" "}
          <button className="underline" onClick={() => { resetMockExam(c.id); reload(); }}>
            reset this contest
          </button>{" "}
          (clears violations, submissions, resets 45-min timer).
        </p>
      )}
    </div>
  );
}

const REFRESH_MS = 15_000;
function Cell({ cell }: { cell?: LeaderboardRow["cells"][number] }) {
  if (!cell) return <span className="text-faint">·</span>;
  if (cell.solved)
    return (
      <span className="text-success">
        <b>{cell.minute}</b>
        {cell.attempts > 0 && <span className="text-body-sm"> (+{cell.attempts})</span>}
      </span>
    );
  return <span className="text-destructive">-{cell.attempts}</span>;
}

export function LeaderboardView({ contestId }: { contestId: number }) {
  const [contest, setContest] = useState<ContestInfo | null>(null);
  const [rows, setRows] = useState<LeaderboardRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updated, setUpdated] = useState<Date | null>(null);
  const { user } = useUser();

  const refresh = useCallback(async () => {
    try {
      setRows(await fetchLeaderboard(contestId));
      setUpdated(new Date());
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [contestId]);

  useEffect(() => {
    fetchContest(contestId)
      .then(setContest)
      .catch((e) => setError(errorMessage(e)));
  }, [contestId]);
  useEffect(() => {
    void refresh();
    const id = setInterval(() => {
      if (!document.hidden) void refresh();
    }, REFRESH_MS);
    return () => clearInterval(id);
  }, [refresh]);

  if (!rows) return error ? <Note error>{error}</Note> : <Note>Loading leaderboard…</Note>;
  const problems = contest?.problems ?? [];
  return (
    <div className="mx-auto max-w-5xl space-y-5 px-4 py-8 sm:px-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-heading-lg text-ink">{contest?.title ?? "Leaderboard"}</h1>
        {contest && <StatusPill s={contest.status} />}
        <span className="ml-auto text-body-sm text-mute" aria-live="polite">
          {error ? (
            <span className="text-destructive">{error}</span>
          ) : updated ? (
            `Updated ${updated.toLocaleTimeString("en-US")}, auto-refreshes every ${REFRESH_MS / 1000}s`
          ) : (
            ""
          )}
        </span>
        <Button size="sm" variant="outline" onClick={() => void refresh()}>
          Refresh
        </Button>
      </div>

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[560px] text-body-md">
          <thead>
            <tr className="border-b border-border text-left text-[11px] font-medium uppercase tracking-wider text-mute">
              <th className="w-14 px-5 py-3">Rank</th>
              <th className="px-2 py-3">Participant</th>
              <th className="px-2 py-3 text-center">AC</th>
              <th className="px-2 py-3 text-center">Penalty (min)</th>
              {problems.map((p) => (
                <th key={p.id} className="px-2 py-3 text-center" title={p.title}>
                  {p.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr
                key={r.userId}
                className={cn(
                  "border-b border-border last:border-b-0",
                  r.username === user?.username && "bg-link-soft/50",
                  r.disqualified && "opacity-60"
                )}
              >
                <td className="px-5 py-3 font-mono text-ink">{r.rank ?? "—"}</td>
                <td className="px-2 py-3 font-medium text-ink">
                  {r.username}
                  {r.username === user?.username && <span className="ml-2 text-body-sm text-link">(you)</span>}
                  {r.disqualified && (
                    <span className="ml-2 rounded-full bg-destructive/10 px-2 py-0.5 text-body-sm text-destructive">
                      Disqualified
                    </span>
                  )}
                </td>
                <td className="px-2 py-3 text-center font-medium">{r.solved}</td>
                <td className="px-2 py-3 text-center font-mono">{r.penaltyMinutes}</td>
                {problems.map((p) => (
                  <td key={p.id} className="px-2 py-3 text-center font-mono">
                    <Cell cell={r.cells[p.id]} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-body-sm text-mute">
        Ranked by solved problems (higher first), then total penalty minutes (lower first). Penalty for each AC = AC
        minute + 20 × earlier WA attempts. Green cell indicates AC minute (+wrong attempts); red cell indicates wrong
        attempts before AC.
      </p>
    </div>
  );
}
