"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { fetchProblems, fetchProblemTags } from "@/lib/services";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./explore.module.css";
import { COLLECTIONS } from "./collections";
import type { Difficulty, ProblemSummary } from "@/types/api";

type Data = { problems: ProblemSummary[]; tags: { tag: string; count: number }[] };
type Load = { state: "loading" } | ({ state: "ok" } & Data) | { state: "error"; message: string };

const LEVEL: Record<Difficulty, string> = { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" };

function Row({ x }: { x: ProblemSummary }) {
  return (
    <Link href={`/problems/${x.slug}`} className={s.row}>
      <span className={cn(s.num, s.hideSm)}>{String(x.id).padStart(3, "0")}</span>
      <span className={s.name}>{x.title}</span>
      <span className={cn(s.small, s.hideSm)}>{x.type === "OOP_GRINDING" ? "OOP" : "DSA"}</span>
      <span className={s.level}>{LEVEL[x.difficulty]}</span>
    </Link>
  );
}

export function ExploreView() {
  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);

  useEffect(() => {
    let off = false;
    setLoad({ state: "loading" });
    // Backend mặc định sắp theo createdAt giảm dần, nên 5 bài đầu là "Recently added".
    // shortcut: một trang 100 bài (mức trần của backend), đủ cho kho hiện tại; kho lớn hơn thì tải theo slug.
    Promise.all([fetchProblems({ page: 1, size: 100, q: "", difficulty: "", progress: "", tags: [] }), fetchProblemTags()])
      .then(([page, tags]) => !off && setLoad({ state: "ok", problems: page.content, tags }))
      .catch((e) => !off && setLoad({ state: "error", message: errorMessage(e) }));
    return () => {
      off = true;
    };
  }, [attempt]);

  const problems = load.state === "ok" ? load.problems : [];
  const bySlug = new Map(problems.map((x) => [x.slug, x]));
  const collections = COLLECTIONS.map((c) => ({
    ...c,
    items: c.slugs.map((slug) => bySlug.get(slug)).filter((x): x is ProblemSummary => !!x),
  })).filter((c) => c.items.length > 0);
  const opened = collections.find((c) => c.id === openId);

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={cn(p.main, s.stack)}>
        <div>
          <h1 className={s.title}>Explore</h1>
          <p className={s.lead}>Hand-picked problem lists for when you don&apos;t know what to solve next.</p>
        </div>

        {load.state === "error" ? (
          <div className={cn(p.table, p.empty, p.error)} role="alert">
            <h2>Couldn&apos;t load problems</h2>
            <p>{load.message}</p>
            <button className={cn(p.toolBtn, p.centerBtn)} type="button" onClick={() => setAttempt((n) => n + 1)}>
              Try again
            </button>
          </div>
        ) : load.state === "loading" ? (
          <div className={cn(p.table, p.empty)} aria-busy="true">
            <p>Loading problems…</p>
          </div>
        ) : problems.length === 0 ? (
          <div className={cn(p.table, p.empty)}>
            <h2>No problems yet</h2>
            <p style={{ margin: 0 }}>Problems will appear here once they are published.</p>
          </div>
        ) : (
          <>
            {collections.length > 0 && (
              <section className={s.section} aria-labelledby="collections">
                <h2 id="collections">Collections</h2>
                <div className={s.cards}>
                  {collections.map((c) => (
                    <button
                      key={c.id}
                      type="button"
                      className={s.card}
                      aria-expanded={c.id === openId}
                      aria-controls="collection-list"
                      onClick={() => setOpenId(c.id === openId ? null : c.id)}
                    >
                      <span className={s.track}>{c.track}</span>
                      <span className={s.cardName}>{c.name}</span>
                      <span className={s.cardDesc}>{c.desc}</span>
                      <span className={s.cardFoot}>
                        <span className={s.mono}>
                          {c.items.length} problem{c.items.length === 1 ? "" : "s"}
                        </span>
                        <span className={s.open}>{c.id === openId ? "Hide list" : "Open list"}</span>
                      </span>
                    </button>
                  ))}
                </div>
                {opened && (
                  <div id="collection-list" className={cn(p.table, s.picked)} aria-label={opened.name} role="region">
                    {opened.items.map((x) => (
                      <Row key={x.slug} x={x} />
                    ))}
                  </div>
                )}
              </section>
            )}

            <div className={s.split}>
              <section className={cn(s.section, s.main)} aria-labelledby="newest">
                <h2 id="newest">Recently added</h2>
                <div className={p.table}>
                  {problems.slice(0, 5).map((x) => (
                    <Row key={x.slug} x={x} />
                  ))}
                </div>
              </section>
              <section className={cn(s.section, s.side)} aria-labelledby="browse">
                <h2 id="browse" style={{ marginBottom: 6 }}>
                  Browse by topic
                </h2>
                {load.tags.length === 0 ? (
                  <p className={s.emptyRow}>No topics yet.</p>
                ) : (
                  <div className={s.topics}>
                    {load.tags.map((t) => (
                      <Link key={t.tag} href={`/problems?tags=${encodeURIComponent(t.tag)}`} className={s.topic}>
                        <span>{t.tag}</span>
                        <span className={cn(s.mono, s.small)}>{t.count}</span>
                      </Link>
                    ))}
                  </div>
                )}
              </section>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
