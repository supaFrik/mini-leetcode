"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Check, Clock, ShieldCheck, Trophy, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { VerdictBadge } from "@/components/shared/verdict-badge";
import { useCountdown } from "@/hooks/use-countdown";
import { USE_MOCK, errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fetchContest, fetchLeaderboard, fetchMySubmissions, registerContest, resetMockExam } from "@/lib/services";
import { toast } from "@/lib/toast";
import { ContestReport } from "./contest-report";
import { fmtClock, fmtDateTime } from "@/lib/format";
import { useUser } from "@/hooks/use-user";
import type { ContestInfo, ContestProblem, ContestStatus, LeaderboardRow } from "@/types/api";

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
const WARN_MS = 10 * 60_000;
const REFRESH_MS = 15_000;

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

/** Bảng xếp hạng, tự làm mới mỗi 15s khi tab đang hiển thị. */
function useLeaderboard(contestId: number) {
  const [rows, setRows] = useState<LeaderboardRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updated, setUpdated] = useState<Date | null>(null);
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
    void refresh();
    const id = setInterval(() => {
      if (!document.hidden) void refresh();
    }, REFRESH_MS);
    return () => clearInterval(id);
  }, [refresh]);
  return { rows, error, updated, refresh };
}

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

function LeaderboardTable({ rows, problems, me }: { rows: LeaderboardRow[]; problems: ContestProblem[]; me?: string }) {
  if (rows.length === 0) return <Note>No participants yet.</Note>;
  return (
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
                r.username === me && "bg-link-soft/50",
                r.disqualified && "opacity-60"
              )}
            >
              <td className="px-5 py-3 font-mono text-ink">{r.rank ?? "—"}</td>
              <td className="px-2 py-3 font-medium text-ink">
                {r.username}
                {r.username === me && <span className="ml-2 text-body-sm text-link">(you)</span>}
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
  );
}

/** Trạng thái một bài của tôi, lấy từ ô của tôi trên bảng xếp hạng. */
function ProblemState({ cell }: { cell?: LeaderboardRow["cells"][number] }) {
  if (cell?.solved)
    return (
      <span className="inline-flex items-center gap-1 text-body-sm font-medium text-success">
        <Check className="h-4 w-4" aria-hidden /> Solved
      </span>
    );
  if (cell && cell.attempts > 0)
    return (
      <span className="inline-flex items-center gap-1 text-body-sm font-medium text-destructive">
        <X className="h-4 w-4" aria-hidden /> {cell.attempts} wrong
      </span>
    );
  return <span className="text-body-sm text-mute">Not attempted</span>;
}

function TopFive({ contestId, rows, me }: { contestId: number; rows: LeaderboardRow[] | null; me?: string }) {
  const top = rows?.slice(0, 5) ?? [];
  const mine = rows?.find((r) => r.username === me);
  const extra = mine && !top.includes(mine) ? mine : null;
  const Row = ({ r }: { r: LeaderboardRow }) => (
    <li
      className={cn(
        "grid grid-cols-[32px_minmax(0,1fr)_auto] items-center gap-2 px-4 py-2 text-body-md",
        r.username === me && "bg-link-soft/50 font-medium"
      )}
    >
      <span className="font-mono text-mute">{r.rank ?? "—"}</span>
      <span className="truncate text-ink">
        {r.username}
        {r.username === me && <span className="ml-1.5 text-body-sm text-link">(you)</span>}
      </span>
      <span className="font-mono text-body-sm text-body">
        {r.solved} AC · {r.penaltyMinutes}m
      </span>
    </li>
  );
  return (
    <section aria-labelledby="top5" className="overflow-hidden rounded-xl border border-border bg-card">
      <h2 id="top5" className="flex items-center gap-2 border-b border-border px-4 py-3 text-body-md font-semibold text-ink">
        <Trophy className="h-4 w-4" aria-hidden /> Top 5
      </h2>
      {rows === null ? (
        <p className="px-4 py-3 text-body-sm text-mute">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="px-4 py-3 text-body-sm text-mute">No participants yet.</p>
      ) : (
        <ol className="divide-y divide-border">
          {top.map((r) => (
            <Row key={r.userId} r={r} />
          ))}
          {extra && <Row r={extra} />}
        </ol>
      )}
      <Link
        href={`/exam/${contestId}/leaderboard`}
        className="block border-t border-border px-4 py-2.5 text-body-sm font-medium text-link hover:underline"
      >
        Full leaderboard
      </Link>
    </section>
  );
}

function MySubmissions({ contest }: { contest: ContestInfo }) {
  const { data, error } = useAsync(() => fetchMySubmissions(1, 100), [contest.id]);
  if (error) return <Note error>{error}</Note>;
  if (!data) return <Note>Loading submissions…</Note>;
  // shortcut: only the latest 100 submissions are checked, enough for one contest; add a contestId filter to the API if it is not.
  const mine = data.content.filter((x) => x.contestId === contest.id);
  if (mine.length === 0) return <Note>You have not submitted in this contest yet.</Note>;
  const label = (pid: number) => contest.problems.find((p) => p.id === pid)?.label ?? "?";
  return (
    <div className="overflow-x-auto rounded-xl border border-border bg-card">
      <table className="w-full min-w-[520px] text-body-md">
        <thead>
          <tr className="border-b border-border text-left text-[11px] font-medium uppercase tracking-wider text-mute">
            <th className="px-5 py-3">Time</th>
            <th className="px-2 py-3">Problem</th>
            <th className="px-2 py-3">Verdict</th>
            <th className="px-2 py-3">Tests</th>
            <th className="px-5 py-3 text-right" aria-label="Details" />
          </tr>
        </thead>
        <tbody>
          {mine.map((x) => (
            <tr key={x.id} className="border-b border-border last:border-b-0">
              <td className="px-5 py-3 font-mono text-body-sm text-body">{fmtDateTime(x.createdAt)}</td>
              <td className="px-2 py-3 text-ink">
                <span className="mr-2 font-mono">{label(x.problemId)}</span>
                {x.problemTitle}
              </td>
              <td className="px-2 py-3">
                <VerdictBadge verdict={x.verdict} status={x.status} />
              </td>
              <td className="px-2 py-3 font-mono text-body-sm">
                {x.passTestCases}/{x.totalTestCases}
              </td>
              <td className="px-5 py-3 text-right">
                <Link href={`/submissions/${x.id}`} className="text-body-sm font-medium text-link hover:underline">
                  View
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Rules({ c }: { c: ContestInfo }) {
  const acm = c.scoringPolicy === "ACM_ICPC";
  return (
    <ul className="space-y-3 rounded-xl border border-border bg-card p-5 text-body-md text-body">
      <li className="flex gap-3">
        <Trophy className="mt-0.5 h-4 w-4 flex-none text-ink" aria-hidden />
        {acm
          ? "Ranked by problems solved, then by total penalty. Penalty for each solved problem = minutes from the start + 20 per earlier wrong submission."
          : "Each problem gives partial points for the tests it passes. Your best submission per problem counts."}
      </li>
      <li className="flex gap-3">
        <ShieldCheck className="mt-0.5 h-4 w-4 flex-none text-ink" aria-hidden />
        The contest is proctored. Leaving the tab, exiting fullscreen or pasting code counts as a violation. After{" "}
        {c.maxViolationsAllowed} violations you are disqualified.
      </li>
      <li className="flex gap-3">
        <Clock className="mt-0.5 h-4 w-4 flex-none text-ink" aria-hidden />
        Submissions are accepted only while the contest is live. After it ends, the problems stay open for practice,
        which is not ranked.
      </li>
    </ul>
  );
}

export function ContestOverview({ contestId }: { contestId: number }) {
  const { data: c, error, reload } = useAsync(() => fetchContest(contestId), [contestId]);
  const lb = useLeaderboard(contestId);
  const { user } = useUser();
  const [busy, setBusy] = useState(false);
  const target = c?.status === "RUNNING" ? parseApiDate(c.endTime) : c?.status === "SCHEDULED" ? parseApiDate(c.startTime) : null;
  const left = useCountdown(target);
  // Hết giờ: tải lại để lấy trạng thái mới do server tính (bắt đầu hoặc kết thúc, khoá nộp bài).
  useEffect(() => {
    if (left === 0) reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [left]);
  if (error) return <Note error>{error}</Note>;
  if (!c) return <Note>Loading contest…</Note>;
  const dq = c.myStatus === "DISQUALIFIED";
  const registered = !!c.myStatus;
  const me = user?.username;
  const myRow = lb.rows?.find((r) => r.username === me);
  const warn = c.status === "RUNNING" && left != null && left < WARN_MS;

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

  const action = (p: ContestProblem) => {
    if (c.status === "COMPLETED")
      return (
        <Button asChild size="sm" variant="outline">
          <Link href={`/problems/${p.slug}`} aria-label={`Practice ${p.title}`}>
            Practice
          </Link>
        </Button>
      );
    if (c.status === "RUNNING" && registered && !dq)
      return (
        <Button asChild size="sm">
          <Link href={`/exam/${c.id}/problems/${p.id}`}>Enter problem</Link>
        </Button>
      );
    return (
      <Button size="sm" disabled>
        {dq ? "Locked" : !registered ? "Register first" : "Not open"}
      </Button>
    );
  };

  return (
    <div className="mx-auto w-full max-w-container space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-4 border-b border-border pb-5">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-heading-lg text-ink">{c.title}</h1>
            <StatusPill s={c.status} />
          </div>
          <p className="text-body-md text-body">
            {fmtDateTime(c.startTime)} to {fmtDateTime(c.endTime)} · {c.problems.length} problems · Scoring:{" "}
            {c.scoringPolicy === "ACM_ICPC" ? "ACM/ICPC" : "IOI (partial scores)"}
          </p>
        </div>
        {target != null && (
          <div className="text-right" aria-live="off">
            <p className="text-body-sm text-mute">{c.status === "RUNNING" ? "Ends in" : "Starts in"}</p>
            <p
              className={cn("font-mono text-[32px] font-medium leading-tight tabular-nums", warn ? "text-destructive" : "text-ink")}
              role="timer"
              aria-label={c.status === "RUNNING" ? "Time left" : "Time until start"}
            >
              {left == null ? "--:--:--" : fmtClock(left)}
            </p>
            {warn && <p className="text-body-sm font-medium text-destructive">Less than 10 minutes left</p>}
          </div>
        )}
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="min-w-0 space-y-4">
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
            <p role="alert" className="rounded-sm border border-destructive/30 bg-destructive/5 p-3 text-body-md text-destructive">
              You have been disqualified from this contest due to exceeding allowed violations ({c.myViolations}/
              {c.maxViolationsAllowed}).
            </p>
          )}
          {!dq && (c.myViolations ?? 0) > 0 && (
            <p className="text-body-md text-[#8a5a00]">
              You currently have {c.myViolations}/{c.maxViolationsAllowed} violations.
            </p>
          )}

          <Tabs defaultValue="problems">
            <TabsList className="w-full justify-start overflow-x-auto">
              <TabsTrigger value="problems">Problems</TabsTrigger>
              <TabsTrigger value="leaderboard">Leaderboard</TabsTrigger>
              {registered && <TabsTrigger value="mine">My submissions</TabsTrigger>}
              <TabsTrigger value="rules">Rules</TabsTrigger>
            </TabsList>

            <TabsContent value="problems" className="mt-4 space-y-3">
              {c.status === "COMPLETED" && (
                <p className="rounded-md border border-border bg-hairline-soft px-4 py-2.5 text-body-md text-body">
                  This contest has ended. Practice — not ranked: problems open in the normal workspace.
                </p>
              )}
              <div className="overflow-hidden rounded-xl border border-border bg-card">
                {c.problems.map((p) => (
                  <div key={p.id} className="flex flex-wrap items-center gap-3 border-b border-border px-5 py-3.5 last:border-b-0">
                    <span className="grid h-7 w-7 place-items-center rounded-sm bg-hairline-soft font-mono text-body-md text-ink">
                      {p.label}
                    </span>
                    <span className="min-w-0 flex-1 font-medium text-ink">{p.title}</span>
                    <DifficultyBadge value={p.difficulty} />
                    {registered && (
                      <span className="w-28">
                        <ProblemState cell={myRow?.cells[p.id]} />
                      </span>
                    )}
                    {action(p)}
                  </div>
                ))}
              </div>
            </TabsContent>

            <TabsContent value="leaderboard" className="mt-4">
              {lb.rows ? (
                <LeaderboardTable rows={lb.rows} problems={c.problems} me={me} />
              ) : (
                <Note error={!!lb.error}>{lb.error ?? "Loading leaderboard…"}</Note>
              )}
            </TabsContent>

            {registered && (
              <TabsContent value="mine" className="mt-4">
                <MySubmissions contest={c} />
              </TabsContent>
            )}

            <TabsContent value="rules" className="mt-4">
              <Rules c={c} />
            </TabsContent>
          </Tabs>
        </div>

        <aside className="space-y-4">
          <TopFive contestId={c.id} rows={lb.rows} me={me} />
        </aside>
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

/** Bảng kết quả cho trang admin (tab Results), dùng chung bảng với trang thí sinh. */
export function ContestResults({ contestId, problems }: { contestId: number; problems: ContestProblem[] }) {
  const { rows, error, updated, refresh } = useLeaderboard(contestId);
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="text-heading-md text-ink">Leaderboard</h2>
        <span className="ml-auto text-body-sm text-mute" aria-live="polite">
          {error ? <span className="text-destructive">{error}</span> : updated ? `Updated ${updated.toLocaleTimeString("en-US")}` : ""}
        </span>
        <Button size="sm" variant="outline" onClick={() => void refresh()}>
          Refresh
        </Button>
      </div>
      {rows ? <LeaderboardTable rows={rows} problems={problems} /> : !error && <Note>Loading leaderboard…</Note>}
    </div>
  );
}

export function LeaderboardView({ contestId }: { contestId: number }) {
  const [contest, setContest] = useState<ContestInfo | null>(null);
  const [contestError, setContestError] = useState<string | null>(null);
  const { rows, error: lbError, updated, refresh } = useLeaderboard(contestId);
  const error = lbError ?? contestError;
  const { user } = useUser();

  useEffect(() => {
    fetchContest(contestId)
      .then(setContest)
      .catch((e) => setContestError(errorMessage(e)));
  }, [contestId]);

  if (!rows) return error ? <Note error>{error}</Note> : <Note>Loading leaderboard…</Note>;
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

      <LeaderboardTable rows={rows} problems={contest?.problems ?? []} me={user?.username} />
      <p className="text-body-sm text-mute">
        Ranked by solved problems (higher first), then total penalty minutes (lower first). Penalty for each AC = AC
        minute + 20 × earlier WA attempts. Green cell indicates AC minute (+wrong attempts); red cell indicates wrong
        attempts before AC.
      </p>
    </div>
  );
}
