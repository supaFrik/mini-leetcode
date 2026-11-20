"use client";
import { Medal } from "lucide-react";
import { useLoad } from "@/hooks/use-load";
import { useUser } from "@/hooks/use-user";
import { fetchRanking } from "@/lib/services";
import { cn } from "@/lib/utils";

// Màu huy chương cho top 3 (vàng, bạc, đồng).
const MEDAL = ["text-amber-500", "text-zinc-400", "text-orange-700"];

/** Bảng xếp hạng toàn site: số bài khác nhau đã AC, hoà thì ít lần nộp hơn đứng trên. */
export function RankingView({ hideHeader = false }: { hideHeader?: boolean } = {}) {
  const load = useLoad(fetchRanking, []);
  const { user } = useUser();

  return (
    <div className="mx-auto w-full max-w-3xl space-y-5 px-8 py-8">
      {!hideHeader && <header className="space-y-1">
        <h1 className="text-heading-lg text-ink">Ranking</h1>
        <p className="text-body-md text-mute">
          Students ranked by distinct problems solved. Ties go to the one with fewer submissions.
        </p>
      </header>}

      <div className="overflow-hidden rounded-sm border border-border bg-card" role="region" aria-label="Ranking">
        {load.state === "loading" && <p className="p-6 text-body-md text-mute">Loading ranking…</p>}
        {load.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{load.message}</p>}
        {load.state === "ok" && load.data.length === 0 && (
          <p className="p-6 text-body-md text-mute">No one has solved a problem yet. Be the first.</p>
        )}
        {load.state === "ok" && load.data.length > 0 && (
          <table className="w-full text-left text-body-md">
            <thead className="border-b border-border text-body-sm text-mute">
              <tr>
                <th className="w-20 px-4 py-2 font-medium">Rank</th>
                <th className="px-4 py-2 font-medium">User</th>
                <th className="px-4 py-2 text-right font-medium">Solved</th>
                <th className="px-4 py-2 text-right font-medium">Submissions</th>
              </tr>
            </thead>
            <tbody>
              {load.data.map((r) => {
                const me = user?.id === r.userId;
                const top = r.rank <= 3;
                return (
                  <tr
                    key={r.userId}
                    aria-current={me ? "true" : undefined}
                    className={cn(
                      "border-b border-border last:border-0",
                      top && "bg-amber-50/60 dark:bg-amber-500/5",
                      me && "bg-sky-50 dark:bg-sky-500/10"
                    )}
                  >
                    <td className="px-4 py-3 font-mono">
                      <span className="inline-flex items-center gap-1.5">
                        {top && <Medal className={cn("h-4 w-4", MEDAL[r.rank - 1])} aria-hidden="true" />}
                        <span className={cn(top && "font-semibold text-ink")}>{r.rank}</span>
                      </span>
                    </td>
                    <td className={cn("px-4 py-3", top ? "font-semibold text-ink" : "text-ink")}>
                      {r.username}
                      {me && <span className="ml-2 text-body-sm font-normal text-mute">(you)</span>}
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-ink">{r.solved}</td>
                    <td className="px-4 py-3 text-right font-mono text-mute">{r.submissions}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
