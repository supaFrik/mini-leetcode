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
import { fetchContests, fetchMySubmissions, fetchProblems } from "@/lib/services";
import type { ContestInfo, Page, ProblemSummary, Submission } from "@/types/api";

interface HomeData { problems: Page<ProblemSummary> | null; subs: Page<Submission> | null; contests: ContestInfo[] | null }

function RunningContest({ c }: { c: ContestInfo }) {
  const left = useCountdown(Date.parse(c.endTime));
  return (
    <Link href={`/exam/${c.id}`} className="flex items-center justify-between gap-3 rounded-lg border bg-card p-3 hover:bg-accent/40">
      <div className="min-w-0">
        <p className="truncate font-medium">{c.title}</p>
        <p className="text-xs text-muted-foreground">Kết thúc {fmtDateTime(c.endTime)}</p>
      </div>
      <span className="flex shrink-0 items-center gap-1.5 rounded-md bg-primary/15 px-2.5 py-1 font-mono text-sm"><Timer className="h-4 w-4" />{left == null ? "--:--" : fmtDuration(left)}</span>
    </Link>
  );
}

const FEATURES = [
  { icon: Shield, title: "Chấm bài cô lập", text: "Mỗi bài nộp chạy trong container dùng một lần: không mạng, giới hạn RAM, CPU và tiến trình. Kết quả trả về trong vài giây." },
  { icon: Timer, title: "Phòng thi có giám sát", text: "Đồng hồ theo giờ máy chủ, phát hiện rời tab, thoát toàn màn hình và dán code. Máy chủ là bên duy nhất tính vi phạm." },
  { icon: Trophy, title: "Bảng xếp hạng ACM/ICPC", text: "Xếp theo số bài AC rồi đến thời gian phạt, cập nhật liên tục trong suốt kỳ thi." },
];

export function HomeView() {
  const { user } = useUser();
  const [data, setData] = useState<HomeData | null>(null);

  useEffect(() => {
    let off = false;
    // allSettled: một phần lỗi (ví dụ backend chưa có API kỳ thi) không làm hỏng cả trang chủ
    Promise.allSettled([
      fetchProblems({ page: 1, q: "", difficulty: "", progress: "", tags: [] }),
      fetchMySubmissions(1, 50),
      fetchContests(),
    ]).then(([p, s, c]) => {
      if (off) return;
      setData({ problems: p.status === "fulfilled" ? p.value : null, subs: s.status === "fulfilled" ? s.value : null, contests: c.status === "fulfilled" ? c.value : null });
    });
    return () => { off = true; };
  }, []);

  const total = data?.problems?.totalElements;
  const subs = data?.subs?.content ?? [];
  const solved = new Set(subs.filter((s) => s.verdict === "AC").map((s) => s.problemId)).size;
  const acRate = subs.length ? Math.round((subs.filter((s) => s.verdict === "AC").length / subs.length) * 100) : null;
  const pick = data?.problems?.content.length ? data.problems.content[new Date().getDate() % data.problems.content.length] : null;
  const running = data?.contests?.filter((c) => c.status === "RUNNING") ?? [];
  const pct = total ? Math.min(100, Math.round((solved / total) * 100)) : 0;

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6">
      {/* Hero */}
      <section className="grid items-center gap-6 rounded-2xl border bg-gradient-to-br from-primary/15 via-card to-card p-8 md:grid-cols-[1.2fr_1fr]">
        <div className="space-y-4">
          <p className="font-mono text-xs text-primary">{user ? `Xin chào, ${user.username}` : "Mini LeetCode"}</p>
          <h1 className="text-3xl font-semibold leading-tight md:text-4xl">Luyện thuật toán, thi có giám sát, chấm tự động trong vài giây.</h1>
          <p className="max-w-xl text-muted-foreground">Giải đề bằng Java, Python hoặc C++, xem ngay thời gian chạy, bộ nhớ và so sánh output. Khi cần đánh giá năng lực, vào phòng thi với đồng hồ và giám sát từ máy chủ.</p>
          <div className="flex flex-wrap gap-3">
            <Button asChild><Link href="/problems">Bắt đầu luyện tập<ArrowRight className="h-4 w-4" /></Link></Button>
            <Button asChild variant="outline"><Link href="/exam">Vào phòng thi</Link></Button>
          </div>
        </div>
        <div> //placeholder cho bên phải
        </div>
      </section>

      {/* Thống kê cá nhân */}
      <section aria-label="Tiến độ của bạn" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {!data
          ? Array.from({ length: 4 }, (_, i) => <div key={i} className="h-24 animate-pulse rounded-lg border bg-muted/30" />)
          : (
            <>
              <div className="rounded-lg border bg-card p-4">
                <p className="text-xs text-muted-foreground">Đã giải</p>
                <p className="mt-1 text-2xl font-semibold">{data.subs ? solved : "—"}<span className="text-base text-muted-foreground">{total != null ? ` / ${total}` : ""}</span></p>
                <div className="mt-2 h-1.5 rounded-full bg-secondary" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}><div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} /></div>
              </div>
              <div className="rounded-lg border bg-card p-4"><p className="text-xs text-muted-foreground">Bài đã nộp</p><p className="mt-1 text-2xl font-semibold">{data.subs ? data.subs.totalElements : "—"}</p></div>
              <div className="rounded-lg border bg-card p-4"><p className="text-xs text-muted-foreground">Tỷ lệ AC (50 bài gần nhất)</p><p className="mt-1 text-2xl font-semibold">{acRate != null ? `${acRate}%` : "—"}</p></div>
              <div className="rounded-lg border bg-card p-4"><p className="text-xs text-muted-foreground">Kỳ thi đang diễn ra</p><p className="mt-1 text-2xl font-semibold">{data.contests ? running.length : "—"}</p></div>
            </>
          )}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Bài gợi ý + kỳ thi */}
        <section className="space-y-3">
          <h2 className="text-sm font-semibold">Bài gợi ý hôm nay</h2>
          {pick
            ? (
              <Link href={`/problems/${pick.slug}`} className="flex items-center justify-between gap-3 rounded-lg border bg-card p-4 hover:bg-accent/40">
                <div><p className="font-semibold">{pick.id}. {pick.title}</p><p className="mt-1 text-xs text-muted-foreground">{pick.tags.join(" · ")}</p></div>
                <DifficultyBadge value={pick.difficulty} />
              </Link>
            )
            : <p className="rounded-lg border bg-card p-4 text-sm text-muted-foreground">{data ? "Chưa tải được danh sách bài tập." : "Đang tải…"}</p>}

          <h2 className="pt-2 text-sm font-semibold">Kỳ thi đang diễn ra</h2>
          {running.length > 0 ? running.map((c) => <RunningContest key={c.id} c={c} />)
            : <p className="rounded-lg border bg-card p-4 text-sm text-muted-foreground">{data ? (data.contests ? "Hiện không có kỳ thi nào đang diễn ra." : "Chưa tải được danh sách kỳ thi.") : "Đang tải…"}</p>}
        </section>

        {/* Bài nộp gần đây */}
        <section className="space-y-3">
          <div className="flex items-center justify-between"><h2 className="text-sm font-semibold">Bài nộp gần đây</h2><Link href="/submissions" className="text-xs text-primary hover:underline">Xem tất cả</Link></div>
          <div className="overflow-hidden rounded-lg border bg-card">
            {subs.slice(0, 5).map((s) => (
              <Link key={s.id} href={`/submissions/${s.id}`} className="flex items-center justify-between gap-3 border-b px-4 py-3 text-sm last:border-b-0 hover:bg-accent/40">
                <span className="min-w-0 truncate">{s.problemTitle ?? `Đề #${s.problemId}`}</span>
                <span className="flex shrink-0 items-center gap-3"><span className="hidden text-xs text-muted-foreground sm:inline">{fmtDateTime(s.createdAt)}</span><VerdictBadge verdict={s.verdict} status={s.status} /></span>
              </Link>
            ))}
            {subs.length === 0 && <p className="p-4 text-sm text-muted-foreground">{data ? "Bạn chưa nộp bài nào. Hãy thử một đề Easy." : "Đang tải…"}</p>}
          </div>
        </section>
      </div>

      <section aria-label="Tính năng" className="grid gap-4 md:grid-cols-3">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="rounded-lg border bg-card p-5">
            <span className="grid h-9 w-9 place-items-center rounded-lg bg-primary/15 text-primary"><Icon className="h-5 w-5" /></span>
            <h3 className="mt-3 font-semibold">{title}</h3>
            <p className="mt-1 text-sm text-muted-foreground">{text}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
