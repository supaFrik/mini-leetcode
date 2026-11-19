"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Check, Clock, Code, ShieldCheck } from "lucide-react";
import { cn } from "@/lib/utils";
import { useCountdown } from "@/hooks/use-countdown";
import { useUser } from "@/hooks/use-user";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fetchContests, fetchLeaderboard, registerContest } from "@/lib/services";
import { fmtClock, fmtSpan } from "@/lib/format";
import { toast } from "@/lib/toast";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./contests.module.css";
import type { ContestInfo } from "@/types/api";

type Load = { state: "loading" } | { state: "ok"; data: ContestInfo[] } | { state: "error"; message: string };

const minutesOf = (c: ContestInfo) =>
  Math.max(0, Math.round((parseApiDate(c.endTime) - parseApiDate(c.startTime)) / 60000));
/** "90 min" / "90 minutes"; từ một ngày trở lên thì "7d 2h". */
const durationLabel = (c: ContestInfo, unit: string) => {
  const m = minutesOf(c);
  return m >= 1440 ? fmtSpan(m * 60000) : `${m} ${unit}`;
};
const problemsLabel = (c: ContestInfo) => `${c.problems.length} problem${c.problems.length === 1 ? "" : "s"}`;
const fmtStart = (iso: string) =>
  new Date(parseApiDate(iso)).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
const fmtDate = (iso: string) =>
  new Date(parseApiDate(iso)).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });

function StartsIn({ c }: { c: ContestInfo }) {
  const left = useCountdown(parseApiDate(c.startTime));
  return <>{left == null ? "" : left === 0 ? "Starting…" : `Starts in ${fmtSpan(left)}`}</>;
}

/** Đăng ký dùng chung cho thẻ Live và hàng Upcoming. Chưa đăng nhập thì chuyển sang /login. */
function useRegister(onDone: (id: number) => void) {
  const router = useRouter();
  const { user } = useUser();
  const [busy, setBusy] = useState<number | null>(null);
  const register = async (c: ContestInfo, then?: () => void) => {
    if (!user) {
      router.push(`/login?next=${encodeURIComponent("/exam")}`);
      return;
    }
    setBusy(c.id);
    try {
      await registerContest(c.id);
      onDone(c.id);
      toast(`Registered for ${c.title}.`, "success");
      then?.();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(null);
    }
  };
  return { register, busy };
}

function LiveCard({ c, busy, onRegister }: { c: ContestInfo; busy: boolean; onRegister: () => void }) {
  const left = useCountdown(parseApiDate(c.endTime));
  return (
    <section className={s.live} aria-labelledby={`live-${c.id}`}>
      <div style={{ minWidth: 0 }}>
        <p className={s.liveBadge}>
          <span className={s.dot} aria-hidden="true" />
          Live now
        </p>
        <h2 id={`live-${c.id}`} className={s.liveTitle}>
          <Link href={`/exam/${c.id}`}>{c.title}</Link>
        </h2>
        <p className={s.liveMeta}>
          {problemsLabel(c)} · {durationLabel(c, "minutes")} · Proctored
        </p>
      </div>
      <div className={s.liveRight}>
        <div>
          <p className={s.liveLabel}>Time left</p>
          <p className={s.clock}>{left == null ? "--:--:--" : fmtClock(left)}</p>
        </div>
        {c.myStatus === "DISQUALIFIED" ? (
          <p className={s.liveNote}>Disqualified</p>
        ) : c.myStatus ? (
          <Link href={`/exam/${c.id}`} className={s.enter}>
            Enter contest
          </Link>
        ) : (
          <button type="button" className={s.enter} onClick={onRegister} disabled={busy}>
            {busy ? "Registering…" : "Register & enter"}
          </button>
        )}
      </div>
    </section>
  );
}

export function ContestList() {
  const router = useRouter();
  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let off = false;
    setLoad({ state: "loading" });
    fetchContests()
      .then((data) => !off && setLoad({ state: "ok", data }))
      .catch((e) => !off && setLoad({ state: "error", message: errorMessage(e) }));
    return () => {
      off = true;
    };
  }, [attempt]);

  const [participants, setParticipants] = useState<Record<number, number>>({});
  const pastIds = load.state === "ok" ? load.data.filter((c) => c.status === "COMPLETED").map((c) => c.id).join(",") : "";
  useEffect(() => {
    if (!pastIds) return;
    let off = false;
    // shortcut: one leaderboard request per past contest; add participantCount to the contest API if the list grows.
    Promise.all(pastIds.split(",").map((id) => fetchLeaderboard(Number(id)).then((rows) => [Number(id), rows.length] as const).catch(() => null)))
      .then((pairs) => !off && setParticipants(Object.fromEntries(pairs.filter((x) => x !== null))));
    return () => {
      off = true;
    };
  }, [pastIds]);

  const markRegistered = (id: number) =>
    setLoad((l) =>
      l.state === "ok" ? { ...l, data: l.data.map((c) => (c.id === id ? { ...c, myStatus: c.myStatus ?? "REGISTERED" } : c)) } : l
    );
  const { register, busy } = useRegister(markRegistered);

  const data = load.state === "ok" ? load.data : [];
  const live = data.filter((c) => c.status === "RUNNING");
  const upcoming = data.filter((c) => c.status === "SCHEDULED");
  const past = data
    .filter((c) => c.status === "COMPLETED")
    .sort((a, b) => parseApiDate(b.startTime) - parseApiDate(a.startTime));
  const joined = data.filter((c) => c.myStatus).length;
  const history = data
    .filter((c) => c.myStatus)
    .sort((a, b) => parseApiDate(b.startTime) - parseApiDate(a.startTime));
  const ranks = data.map((c) => c.myRank).filter((r): r is number => typeof r === "number");
  const bestRank = ranks.length ? Math.min(...ranks) : null;

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <h1 className={s.title}>Contest</h1>

        {load.state === "error" ? (
          <div className={cn(p.table, p.empty, p.error)} role="alert">
            <h2>Couldn&apos;t load contests</h2>
            <p>{load.message}</p>
            <button className={cn(p.toolBtn, p.centerBtn)} type="button" onClick={() => setAttempt((n) => n + 1)}>
              Try again
            </button>
          </div>
        ) : load.state === "loading" ? (
          <div className={cn(p.table, p.empty)} aria-busy="true">
            <p>Loading contests…</p>
          </div>
        ) : data.length === 0 ? (
          <div className={cn(p.table, p.empty)}>
            <h2>No contests yet</h2>
            <p style={{ margin: 0 }}>Contests will appear here once they are scheduled.</p>
          </div>
        ) : (
          <div className={p.layout}>
            <div className={s.column}>
              {live.length > 0 && (
                <section className={s.section} aria-labelledby="running">
                  <h2 id="running">Running now</h2>
                  <div className={s.liveList}>
                    {live.map((c) => (
                      <LiveCard
                        key={c.id}
                        c={c}
                        busy={busy === c.id}
                        onRegister={() => register(c, () => router.push(`/exam/${c.id}`))}
                      />
                    ))}
                  </div>
                </section>
              )}

              <section className={s.section} aria-labelledby="upcoming">
                <h2 id="upcoming">Upcoming</h2>
                <div className={p.table}>
                  {upcoming.length === 0 ? (
                    <p className={s.emptyRow}>No upcoming contests scheduled.</p>
                  ) : (
                    upcoming.map((c) => {
                      const on = !!c.myStatus;
                      return (
                        <div key={c.id} className={s.upRow}>
                          <Link href={`/exam/${c.id}`} className={s.name}>
                            {c.title}
                          </Link>
                          <span className={cn(s.muted, s.hideSm)}>{fmtStart(c.startTime)}</span>
                          <span className={cn(s.muted, s.hideSm)}>
                            {problemsLabel(c)} · {durationLabel(c, "min")}
                          </span>
                          <span className={cn(s.muted, s.hideSm)}>
                            <StartsIn c={c} />
                          </span>
                          <button
                            type="button"
                            className={s.register}
                            aria-pressed={on}
                            aria-label={on ? `Registered for ${c.title}` : `Register for ${c.title}`}
                            disabled={on || busy === c.id}
                            onClick={() => register(c)}
                          >
                            {on && <Check size={15} aria-hidden="true" />}
                            {on ? "Registered" : busy === c.id ? "Registering…" : "Register"}
                          </button>
                        </div>
                      );
                    })
                  )}
                </div>
              </section>

              <section className={s.section} aria-labelledby="past">
                <h2 id="past">Past contests</h2>
                <div className={p.table}>
                  <div className={s.pastHead}>
                    <span>Contest</span>
                    <span>Date</span>
                    <span>Participants</span>
                    <span>Rank</span>
                    <span>Solved</span>
                    <span aria-hidden="true" />
                  </div>
                  {past.length === 0 ? (
                    <p className={s.emptyRow}>No past contests yet.</p>
                  ) : (
                    past.map((c) => (
                      <Link key={c.id} href={`/exam/${c.id}/leaderboard`} className={s.pastRow}>
                        <span className={s.name}>{c.title}</span>
                        <span className={cn(s.muted, s.hideSm)}>{fmtDate(c.startTime)}</span>
                        <span className={cn(s.num, s.hideSm)}>{participants[c.id] ?? "…"}</span>
                        <span className={cn(s.num, s.hideSm)}>
                          {c.myRank != null ? `#${c.myRank}` : <span className={s.dim}>{c.myStatus ? "Unranked" : "—"}</span>}
                        </span>
                        <span className={cn(s.num, s.hideSm)}>
                          {c.mySolved != null ? `${c.mySolved}/${c.problems.length}` : <span className={s.dim}>—</span>}
                        </span>
                        <span className={s.results}>Results</span>
                      </Link>
                    ))
                  )}
                </div>
              </section>
            </div>

            <aside className={p.aside} aria-label="Contest record and rules">
              <section>
                <h2 style={{ marginBottom: 12 }}>Your record</h2>
                <div className={s.record}>
                  <div>
                    <p className={s.big}>{joined}</p>
                    <p className={s.muted}>Contests joined</p>
                  </div>
                  <div>
                    <p className={s.big}>{bestRank != null ? `#${bestRank}` : <span className="text-zinc-600 dark:text-zinc-400 font-normal text-lg">None</span>}</p>
                    <p className={s.muted}>Best rank</p>
                  </div>
                </div>
              </section>
              <section aria-labelledby="history">
                <h2 id="history">My contest history</h2>
                {history.length === 0 ? (
                  <p className={s.muted} style={{ margin: 0 }}>You have not joined a contest yet.</p>
                ) : (
                  <ul className={s.history}>
                    {history.map((c) => (
                      <li key={c.id}>
                        <Link href={`/exam/${c.id}`} className={s.name}>
                          {c.title}
                        </Link>
                        <span className={s.muted}>
                          {c.status === "RUNNING"
                            ? "Running · in progress"
                            : c.status === "SCHEDULED"
                              ? `Registered · ${fmtDate(c.startTime)}`
                              : `${c.myRank != null ? `Rank #${c.myRank}` : "Unranked"} · ${c.mySolved ?? 0}/${c.problems.length} solved`}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
              <section>
                <h2>Before you enter</h2>
                <ul className={s.rules}>
                  <li>
                    <ShieldCheck size={16} aria-hidden="true" />
                    Contests are proctored. Your activity is logged on the server.
                  </li>
                  <li>
                    <Clock size={16} aria-hidden="true" />
                    The timer keeps running if you leave the page.
                  </li>
                  <li>
                    <Code size={16} aria-hidden="true" />
                    Submit in Java 17 or Python 3.
                  </li>
                </ul>
              </section>
            </aside>
          </div>
        )}
      </div>
    </div>
  );
}
