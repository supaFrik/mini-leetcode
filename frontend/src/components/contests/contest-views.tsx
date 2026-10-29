"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { useCountdown } from "@/hooks/use-countdown";
import { USE_MOCK, errorMessage } from "@/lib/api";
import { fetchContest, fetchContests, fetchLeaderboard, registerContest, resetMockExam } from "@/lib/services";
import { toast } from "@/lib/toast";
import { ContestReport } from "./contest-report";
import { fmtDateTime, fmtDuration, fmtLong } from "@/lib/format";
import { useUser } from "@/hooks/use-user";
import type { ContestInfo, ContestStatus, LeaderboardRow } from "@/types/api";

const STATUS_STYLE: Record<ContestStatus, string> = {
  RUNNING: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30",
  SCHEDULED: "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/30",
  COMPLETED: "bg-secondary text-muted-foreground",
};
const STATUS_LABEL: Record<ContestStatus, string> = { RUNNING: "Đang diễn ra", SCHEDULED: "Sắp diễn ra", COMPLETED: "Đã kết thúc" };
const StatusPill = ({ s }: { s: ContestStatus }) => <span className={cn("rounded border px-2 py-0.5 font-mono text-xs", STATUS_STYLE[s])}>{STATUS_LABEL[s]}</span>;
const Note = ({ children, error }: { children: React.ReactNode; error?: boolean }) => <p role={error ? "alert" : undefined} className={cn("p-8 text-center text-sm", error ? "text-destructive" : "text-muted-foreground")}>{children}</p>;

function useAsync<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [n, setN] = useState(0);
  useEffect(() => {
    let off = false;
    load().then((d) => { if (!off) { setData(d); setError(null); } }).catch((e) => { if (!off) setError(errorMessage(e)); });
    return () => { off = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n]);
  return { data, error, reload: () => setN((x) => x + 1) };
}

// ---------- Danh sách kỳ thi (tab Exam) ----------
function Countdown({ target, prefix }: { target: number; prefix: string }) {
  const left = useCountdown(target);
  return <span className="font-mono text-xs text-muted-foreground">{left == null ? "" : `${prefix} ${fmtLong(left)}`}</span>;
}

const SECTIONS: { status: ContestStatus; title: string; empty: string }[] = [
  { status: "RUNNING", title: "Đang diễn ra", empty: "Hiện không có kỳ thi nào đang diễn ra." },
  { status: "SCHEDULED", title: "Sắp diễn ra", empty: "Chưa có kỳ thi nào được lên lịch." },
  { status: "COMPLETED", title: "Đã kết thúc", empty: "Chưa có kỳ thi nào kết thúc." },
];

export function ContestList() {
  const { data, error, reload } = useAsync(fetchContests, []);
  if (error) return <Note error>{error} <button className="underline" onClick={reload}>Thử lại</button></Note>;
  if (!data) return <div className="mx-auto max-w-3xl space-y-3 p-6">{Array.from({ length: 3 }, (_, i) => <div key={i} className="h-20 animate-pulse rounded-lg border bg-muted/30" />)}</div>;
  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <div><h1 className="text-xl font-semibold">Kỳ thi</h1><p className="mt-1 text-sm text-muted-foreground">Kỳ thi có đồng hồ theo giờ máy chủ và giám sát chống gian lận. Bạn cần đăng ký trước khi làm bài.</p></div>
      {SECTIONS.map(({ status, title, empty }) => {
        const list = data.filter((c) => c.status === status);
        return (
          <section key={status} className="space-y-2" aria-label={title}>
            <h2 className="text-sm font-semibold">{title} <span className="font-normal text-muted-foreground">({list.length})</span></h2>
            {list.length === 0 && <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">{empty}</p>}
            {list.map((c) => (
              <Link key={c.id} href={`/exam/${c.id}`} className="flex items-center justify-between gap-4 rounded-lg border bg-card p-4 hover:bg-accent/40">
                <div className="min-w-0 space-y-1">
                  <p className="font-semibold">{c.title}</p>
                  <p className="text-xs text-muted-foreground">{fmtDateTime(c.startTime)} đến {fmtDateTime(c.endTime)}</p>
                  {c.status === "RUNNING" && <Countdown target={Date.parse(c.endTime)} prefix="Còn lại" />}
                  {c.status === "SCHEDULED" && <Countdown target={Date.parse(c.startTime)} prefix="Bắt đầu sau" />}
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <StatusPill s={c.status} />
                  {c.myStatus === "DISQUALIFIED" ? <span className="rounded bg-destructive/15 px-2 py-0.5 text-xs text-destructive">Bị loại</span>
                    : c.myStatus ? <span className="rounded bg-secondary px-2 py-0.5 text-xs">Đã đăng ký</span>
                    : c.status !== "COMPLETED" ? <span className="text-xs text-amber-600 dark:text-amber-400">Chưa đăng ký</span> : null}
                </div>
              </Link>
            ))}
          </section>
        );
      })}
    </div>
  );
}

// ---------- Tổng quan một kỳ thi ----------
export function ContestOverview({ contestId }: { contestId: number }) {
  const { data: c, error, reload } = useAsync(() => fetchContest(contestId), [contestId]);
  const { user } = useUser();
  const [busy, setBusy] = useState(false);
  const left = useCountdown(c?.status === "RUNNING" ? Date.parse(c.endTime) : null);
  if (error) return <Note error>{error}</Note>;
  if (!c) return <Note>Đang tải kỳ thi…</Note>;
  const dq = c.myStatus === "DISQUALIFIED";
  const registered = !!c.myStatus;

  const join = async () => {
    setBusy(true);
    try { await registerContest(c.id); toast("Đăng ký thành công.", "success"); reload(); }
    catch (e) { toast(errorMessage(e), "error"); }
    finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-5 p-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{c.title}</h1><StatusPill s={c.status} />
        {c.status === "RUNNING" && left != null && <span className="font-mono text-sm text-muted-foreground">Còn lại {fmtDuration(left)}</span>}
        <Button asChild variant="outline" size="sm" className="ml-auto"><Link href={`/exam/${c.id}/leaderboard`}><Trophy className="mr-1.5 h-4 w-4" />Bảng xếp hạng</Link></Button>
      </div>
      <p className="text-sm text-muted-foreground">{fmtDateTime(c.startTime)} đến {fmtDateTime(c.endTime)}. Luật tính điểm: {c.scoringPolicy === "ACM_ICPC" ? "ACM/ICPC" : "IOI (điểm thành phần)"}.</p>

      {!registered && c.status !== "COMPLETED" && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card p-4">
          <p className="text-sm">Bạn chưa đăng ký kỳ thi này. Hãy đăng ký để được vào phòng thi khi kỳ thi bắt đầu.</p>
          <Button onClick={() => void join()} disabled={busy}>{busy ? "Đang đăng ký…" : "Đăng ký tham gia"}</Button>
        </div>
      )}
      {dq && <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">Bạn đã bị loại khỏi kỳ thi này do vi phạm quy chế ({c.myViolations}/{c.maxViolationsAllowed} lần).</p>}
      {!dq && (c.myViolations ?? 0) > 0 && <p className="text-sm text-amber-600 dark:text-amber-400">Bạn đã vi phạm {c.myViolations}/{c.maxViolationsAllowed} lần.</p>}

      <div className="overflow-hidden rounded-lg border bg-card">
        {c.problems.map((p) => (
          <div key={p.id} className="flex items-center gap-3 border-b px-4 py-3 last:border-b-0">
            <span className="grid h-7 w-7 place-items-center rounded bg-secondary font-mono text-sm">{p.label}</span>
            <span className="flex-1 font-medium">{p.title}</span>
            <DifficultyBadge value={p.difficulty} />
            {c.status === "RUNNING" && registered && !dq
              ? <Button asChild size="sm"><Link href={`/exam/${c.id}/problems/${p.id}`}>Làm bài</Link></Button>
              : <Button size="sm" disabled>{dq ? "Đã khoá" : !registered && c.status !== "COMPLETED" ? "Cần đăng ký" : c.status === "SCHEDULED" ? "Chưa mở" : "Đã đóng"}</Button>}
          </div>
        ))}
      </div>

      {user && user.role !== "USER" && <ContestReport contestId={c.id} role={user.role} />}

      {USE_MOCK && user && (
        <p className="text-xs text-muted-foreground">
          Chế độ mock: <button className="underline" onClick={() => { resetMockExam(c.id); reload(); }}>đặt lại kỳ thi này</button> (xoá vi phạm, bài nộp, đồng hồ 45 phút mới).
        </p>
      )}
    </div>
  );
}

// ---------- Bảng xếp hạng (ACM/ICPC) ----------
const REFRESH_MS = 15_000;
function Cell({ cell }: { cell?: LeaderboardRow["cells"][number] }) {
  if (!cell) return <span className="text-muted-foreground">·</span>;
  if (cell.solved) return <span className="text-emerald-600 dark:text-emerald-400"><b>{cell.minute}</b>{cell.attempts > 0 && <span className="text-xs"> (+{cell.attempts})</span>}</span>;
  return <span className="text-red-600 dark:text-red-400">-{cell.attempts}</span>;
}

export function LeaderboardView({ contestId }: { contestId: number }) {
  const [contest, setContest] = useState<ContestInfo | null>(null);
  const [rows, setRows] = useState<LeaderboardRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updated, setUpdated] = useState<Date | null>(null);
  const { user } = useUser();

  const refresh = useCallback(async () => {
    try { setRows(await fetchLeaderboard(contestId)); setUpdated(new Date()); setError(null); }
    catch (e) { setError(errorMessage(e)); }
  }, [contestId]);

  useEffect(() => { fetchContest(contestId).then(setContest).catch((e) => setError(errorMessage(e))); }, [contestId]);
  useEffect(() => {
    void refresh();
    const id = setInterval(() => { if (!document.hidden) void refresh(); }, REFRESH_MS); // tự làm mới, tab ẩn thì tạm dừng
    return () => clearInterval(id);
  }, [refresh]);

  if (!rows) return error ? <Note error>{error}</Note> : <Note>Đang tải bảng xếp hạng…</Note>;
  const problems = contest?.problems ?? [];
  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{contest?.title ?? "Bảng xếp hạng"}</h1>
        {contest && <StatusPill s={contest.status} />}
        <span className="ml-auto text-xs text-muted-foreground" aria-live="polite">{error ? <span className="text-destructive">{error}</span> : updated ? `Cập nhật ${updated.toLocaleTimeString("vi-VN")}, tự làm mới mỗi ${REFRESH_MS / 1000}s` : ""}</span>
        <Button size="sm" variant="outline" onClick={() => void refresh()}>Làm mới</Button>
      </div>

      <div className="overflow-x-auto rounded-lg border bg-card">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className="border-b text-left font-mono text-xs text-muted-foreground">
              <th className="w-14 px-4 py-3">Hạng</th><th className="px-2 py-3">Thí sinh</th><th className="px-2 py-3 text-center">AC</th><th className="px-2 py-3 text-center">Phạt (phút)</th>
              {problems.map((p) => <th key={p.id} className="px-2 py-3 text-center" title={p.title}>{p.label}</th>)}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.userId} className={cn("border-b last:border-b-0", r.username === user?.username && "bg-primary/10", r.disqualified && "opacity-60")}>
                <td className="px-4 py-3 font-mono">{r.rank ?? "—"}</td>
                <td className="px-2 py-3 font-medium">{r.username}{r.username === user?.username && <span className="ml-2 text-xs text-primary">(bạn)</span>}{r.disqualified && <span className="ml-2 rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive">Bị loại</span>}</td>
                <td className="px-2 py-3 text-center font-semibold">{r.solved}</td>
                <td className="px-2 py-3 text-center font-mono">{r.penaltyMinutes}</td>
                {problems.map((p) => <td key={p.id} className="px-2 py-3 text-center font-mono"><Cell cell={r.cells[p.id]} /></td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-muted-foreground">
        Xếp hạng theo số bài AC (nhiều hơn xếp trên), rồi theo tổng phạt (ít hơn xếp trên). Phạt mỗi bài AC = phút nộp đúng + 20 × số lần nộp sai trước đó.
        Ô xanh là số phút khi AC (+số lần sai), ô đỏ là số lần sai khi chưa AC.
      </p>
    </div>
  );
}
