"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { fetchProblems } from "@/lib/services";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./study-plan.module.css";
import { STUDY_PLANS } from "./plans";
import type { Difficulty, ProblemSummary } from "@/types/api";

type Load = { state: "loading" } | { state: "ok"; bySlug: Map<string, ProblemSummary> } | { state: "error"; message: string };

const LEVEL: Record<Difficulty, string> = { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" };
// `progress` không có khi chưa đăng nhập, nên tiến độ là 0.
const solved = (x: ProblemSummary) => x.progress === "DONE";

export function StudyPlanView() {
  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [planId, setPlanId] = useState(STUDY_PLANS[0].id);

  useEffect(() => {
    let off = false;
    setLoad({ state: "loading" });
    // shortcut: một trang 100 bài (mức trần của backend), đủ cho kho hiện tại; kho lớn hơn thì tải theo slug.
    fetchProblems({ page: 1, size: 100, q: "", difficulty: "", progress: "", tags: [] })
      .then((page) => !off && setLoad({ state: "ok", bySlug: new Map(page.content.map((x) => [x.slug, x])) }))
      .catch((e) => !off && setLoad({ state: "error", message: errorMessage(e) }));
    return () => {
      off = true;
    };
  }, [attempt]);

  const plan = STUDY_PLANS.find((x) => x.id === planId) ?? STUDY_PLANS[0];
  const bySlug = load.state === "ok" ? load.bySlug : new Map<string, ProblemSummary>();
  const modules = plan.modules
    .map((m) => ({ name: m.name, items: m.slugs.map((slug) => bySlug.get(slug)).filter((x): x is ProblemSummary => !!x) }))
    .filter((m) => m.items.length > 0);
  const items = modules.flatMap((m) => m.items);
  const done = items.filter(solved).length;
  const next = items.find((x) => !solved(x));

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <h1 className={s.title}>Study Plan</h1>
        <div className={s.tabs}>
          {STUDY_PLANS.map((x) => (
            <button key={x.id} type="button" className={s.tab} aria-pressed={x.id === plan.id} onClick={() => setPlanId(x.id)}>
              {x.name}
            </button>
          ))}
        </div>

        {load.state === "error" ? (
          <div className={cn(p.table, p.empty, p.error)} role="alert">
            <h2>Couldn&apos;t load the study plan</h2>
            <p>{load.message}</p>
            <button className={cn(p.toolBtn, p.centerBtn)} type="button" onClick={() => setAttempt((n) => n + 1)}>
              Try again
            </button>
          </div>
        ) : load.state === "loading" ? (
          <div className={cn(p.table, p.empty)} aria-busy="true">
            <p>Loading study plan…</p>
          </div>
        ) : items.length === 0 ? (
          <div className={cn(p.table, p.empty)}>
            <h2>No problems in this plan yet</h2>
            <p style={{ margin: 0 }}>Problems will appear here once they are published.</p>
          </div>
        ) : (
          <div className={p.layout}>
            <section aria-label="Plan modules" className={s.modules}>
              {modules.map((m) => (
                <div key={m.name}>
                  <div className={s.moduleHead}>
                    <h2>{m.name}</h2>
                    <span className={s.count}>
                      {m.items.filter(solved).length}/{m.items.length}
                    </span>
                  </div>
                  <div className={p.table}>
                    {m.items.map((x) => (
                      <Link key={x.slug} href={`/problems/${x.slug}`} className={s.row}>
                        <span className={s.tick}>
                          {solved(x) && <Check size={20} role="img" aria-label="Solved" />}
                        </span>
                        <span className={s.name}>{x.title}</span>
                        <span className={s.level}>{LEVEL[x.difficulty]}</span>
                      </Link>
                    ))}
                  </div>
                </div>
              ))}
            </section>

            <aside className={p.aside} aria-label="Plan progress">
              <section>
                <h2 style={{ margin: "0 0 4px" }}>{plan.name}</h2>
                <p className={s.desc}>{plan.desc}</p>
                <p className={s.done}>
                  <span className={s.big}>{done}</span> / {items.length} completed
                </p>
                <div className={s.bar} role="progressbar" aria-valuemin={0} aria-valuemax={items.length} aria-valuenow={done} aria-label="Plan progress">
                  <div style={{ width: `${Math.round((done / items.length) * 100)}%` }} />
                </div>
              </section>
              <section className={s.next}>
                <h2>Next up</h2>
                <p>{next ? next.title : "Plan completed"}</p>
                {next && (
                  <Link href={`/problems/${next.slug}`} className={s.continue}>
                    Continue
                  </Link>
                )}
              </section>
            </aside>
          </div>
        )}
      </div>
    </div>
  );
}
