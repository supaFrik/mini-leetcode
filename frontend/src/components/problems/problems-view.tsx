"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Box, Check, CircleDashed, CodeXml, Library, Search, Shuffle, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { useUrlState } from "@/hooks/use-url-state";
import { useUser } from "@/hooks/use-user";
import { errorMessage } from "@/lib/api";
import { countProblems, fetchProblems, fetchProblemTags } from "@/lib/services";
import { toast } from "@/lib/toast";
import { LANG_PREF_KEY, LANGUAGES } from "@/lib/templates";
import { Pagination } from "./pagination";
import theme from "@/components/layout/plex-theme.module.css";
import s from "./problems.module.css";
import type { Difficulty, Page, ProblemSummary, ProblemType, UserProgress } from "@/types/api";

const PAGE_SIZE = 20;

const TRACKS: { id: ProblemType | ""; label: string; icon: LucideIcon }[] = [
  { id: "", label: "All problems", icon: Library },
  { id: "ALGORITHMIC", label: "DSA (Algorithms & Data Structures)", icon: CodeXml },
  { id: "OOP_GRINDING", label: "OOP (Object-Oriented Programming & Socket)", icon: Box },
];

const LEVELS: { value: Difficulty; label: string; rank: number }[] = [
  { value: "EASY", label: "Easy", rank: 1 },
  { value: "MEDIUM", label: "Medium", rank: 2 },
  { value: "HARD", label: "Hard", rank: 3 },
];
const levelOf = (d: Difficulty) => LEVELS.find((l) => l.value === d) ?? LEVELS[0];

const STATUSES: { value: UserProgress | ""; label: string; icon?: LucideIcon }[] = [
  { value: "", label: "Any" },
  { value: "DONE", label: "Solved", icon: Check },
  { value: "IN_PROGRESS", label: "Attempted", icon: CircleDashed },
  { value: "NEVER", label: "Todo" },
];

const STATUS_WORD: Record<UserProgress, string> = { DONE: "solved", IN_PROGRESS: "attempted", NEVER: "todo" };

/** Kết quả một lần tải: đang tải, xong, hoặc lỗi (không bao giờ kẹt ở "đang tải"). */
type Load<T> = { state: "loading" } | { state: "ok"; data: T } | { state: "error"; message: string };

/* ------------------------------------------------------------------ */

function ProblemRow({ p }: { p: ProblemSummary }) {
  const level = levelOf(p.difficulty);
  const word = p.progress ? STATUS_WORD[p.progress] : undefined;
  return (
    <Link
      href={`/problems/${p.slug}`}
      className={s.row}
      aria-label={[p.title, level.label, word].filter(Boolean).join(", ")}
    >
      <span className={s.state}>
        {p.progress === "DONE" ? (
          <Check size={20} style={{ color: "var(--solved)" }} aria-hidden="true" />
        ) : p.progress === "IN_PROGRESS" ? (
          <CircleDashed size={18} style={{ color: "var(--attempted)" }} aria-hidden="true" />
        ) : null}
      </span>
      <span className={cn(s.num, s.colNum)}>{String(p.id).padStart(3, "0")}</span>
      <span className={s.name}>{p.title}</span>
      <span className={cn(s.tags, s.colTags)}>{p.tags.join(" · ")}</span>
      <span className={s.level}>
        <span className={s.meter} aria-hidden="true">
          <i className={s.on} />
          <i className={level.rank >= 2 ? s.on : undefined} />
          <i className={level.rank >= 3 ? s.on : undefined} />
        </span>
        {level.label}
      </span>
    </Link>
  );
}

function SkeletonRows() {
  return (
    <div aria-label="Loading problems" aria-busy="true">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className={s.row} aria-hidden="true">
          <span />
          <span className={cn(s.skeletonBar, s.colNum)} style={{ width: 28 }} />
          <span className={s.skeletonBar} style={{ width: `${45 + ((i * 17) % 35)}%` }} />
          <span className={cn(s.skeletonBar, s.colTags)} style={{ width: "60%" }} />
          <span className={s.skeletonBar} style={{ width: 60 }} />
        </div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */

interface Stats {
  /** Toàn kho (mọi loại) — cho dòng tóm tắt cạnh tiêu đề. */
  total: number;
  /** Số bài user có bài nộp Accepted (toàn kho). 0 khi server không nhận ra user. */
  solved: number;
  /** Server có nhận ra user hay không (mỗi bài trả về có trường `progress`). */
  recognized: boolean;
  /** Theo loại đang chọn, từng độ khó. */
  byLevel: { total: number; solved: number }[];
}

/**
 * Số "đã giải" chỉ tin vào server: nếu server không nhận ra user (chưa đăng nhập, token
 * hết hạn...) thì các bài không có `progress` và số đã giải là 0, dù localStorage còn token.
 * Nhờ vậy dòng tóm tắt, Your progress, dấu tick và bộ lọc Status luôn cùng một nguồn.
 */
async function loadStats(type: ProblemType | ""): Promise<Stats> {
  const t = type || undefined;
  const probe = await fetchProblems({ page: 1, size: 1, q: "", difficulty: "", progress: "", tags: [] });
  const recognized = probe.content[0]?.progress != null;
  const totals = await Promise.all(LEVELS.map((l) => countProblems({ type: t, difficulty: l.value })));
  if (!recognized) {
    return { total: probe.totalElements, solved: 0, recognized, byLevel: totals.map((total) => ({ total, solved: 0 })) };
  }
  const [solved, ...solvedByLevel] = await Promise.all([
    countProblems({ progress: "DONE" }),
    ...LEVELS.map((l) => countProblems({ type: t, difficulty: l.value, progress: "DONE" })),
  ]);
  return {
    total: probe.totalElements,
    solved,
    recognized,
    byLevel: totals.map((total, i) => ({ total, solved: solvedByLevel[i] })),
  };
}

export function ProblemsView() {
  const router = useRouter();
  const { sp, set } = useUrlState();
  const { user, ready: userReady } = useUser();
  const signedIn = !!user;

  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);
  const q = sp.get("q") ?? "";
  const difficulty = sp.get("difficulty") ?? "";
  const progress = sp.get("progress") ?? "";
  const type = (sp.get("type") ?? "") as ProblemType | "";
  const topic = (sp.get("tags") ?? "").split(",").filter(Boolean)[0] ?? "";
  const filtersActive = !!(q || difficulty || progress || topic);

  /* ---- Search: gõ là lọc (trễ 300ms), Enter là lọc ngay, phím "/" để nhảy vào ---- */
  const [draft, setDraft] = useState(q);
  const searchRef = useRef<HTMLInputElement>(null);
  useEffect(() => setDraft(q), [q]);
  useEffect(() => {
    if (draft.trim() === q) return;
    const id = window.setTimeout(() => set({ q: draft.trim() || null }), 300);
    return () => window.clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (e.key === "/" && !["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName) && !t.isContentEditable) {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  /* ---- Danh sách bài ---- */
  const [list, setList] = useState<Load<Page<ProblemSummary>>>({ state: "loading" });
  const [listTry, setListTry] = useState(0);
  useEffect(() => {
    let ignore = false;
    setList((prev) => (prev.state === "ok" ? prev : { state: "loading" }));
    fetchProblems({ page, size: PAGE_SIZE, q, difficulty, progress, tags: topic ? [topic] : [], type: type || undefined })
      .then((data) => !ignore && setList({ state: "ok", data }))
      .catch((e) => !ignore && setList({ state: "error", message: errorMessage(e) }));
    return () => {
      ignore = true;
    };
  }, [page, q, difficulty, progress, topic, type, listTry, signedIn]);

  /* ---- Chủ đề (đếm theo loại đang chọn) ---- */
  const [topics, setTopics] = useState<Load<{ tag: string; count: number }[]>>({ state: "loading" });
  const [topicsTry, setTopicsTry] = useState(0);
  useEffect(() => {
    let ignore = false;
    setTopics({ state: "loading" });
    fetchProblemTags(type || undefined)
      .then((data) => !ignore && setTopics({ state: "ok", data }))
      .catch((e) => !ignore && setTopics({ state: "error", message: errorMessage(e) }));
    return () => {
      ignore = true;
    };
  }, [type, topicsTry]);

  /* ---- Tóm tắt + tiến độ ---- */
  const [stats, setStats] = useState<Load<Stats>>({ state: "loading" });
  const [statsTry, setStatsTry] = useState(0);
  useEffect(() => {
    if (!userReady) return;
    let ignore = false;
    setStats({ state: "loading" });
    loadStats(type)
      .then((data) => !ignore && setStats({ state: "ok", data }))
      .catch((e) => !ignore && setStats({ state: "error", message: errorMessage(e) }));
    return () => {
      ignore = true;
    };
  }, [type, signedIn, userReady, statsTry]);

  /* ---- Ngôn ngữ mặc định cho workspace ---- */
  const [lang, setLang] = useState("");
  useEffect(() => {
    try {
      setLang(localStorage.getItem(LANG_PREF_KEY) ?? "");
    } catch {
      /* ignore */
    }
  }, []);
  const pickLang = (v: string) => {
    setLang(v);
    try {
      if (v) localStorage.setItem(LANG_PREF_KEY, v);
      else localStorage.removeItem(LANG_PREF_KEY);
    } catch {
      /* ignore */
    }
  };

  /* ---- Pick random: chọn trong toàn bộ kết quả đang lọc rồi mở bài ---- */
  const [picking, setPicking] = useState(false);
  const pickRandom = async () => {
    if (list.state !== "ok" || list.data.totalElements === 0) {
      toast("No problems to pick from.", "info");
      return;
    }
    setPicking(true);
    try {
      const idx = Math.floor(Math.random() * list.data.totalElements);
      const one = await fetchProblems({ page: idx + 1, size: 1, q, difficulty, progress, tags: topic ? [topic] : [], type: type || undefined });
      const pick = one.content[0];
      if (!pick) throw new Error("Could not pick a problem. Please try again.");
      router.push(`/problems/${pick.slug}`);
    } catch (e) {
      toast(errorMessage(e), "error");
      setPicking(false);
    }
  };

  const clearFilters = () => set({ q: null, difficulty: null, progress: null, tags: null });

  /* ---- Dòng tóm tắt cạnh tiêu đề ---- */
  let summary = "";
  if (stats.state === "ok") {
    const { total, solved } = stats.data;
    summary = total === 0 ? "No problems yet" : `${total} problems, ${solved} solved`;
  }

  return (
    <div className={cn(theme.theme, s.page)}>
      <div className={s.main}>
        <div className={s.titleRow}>
          <h1>Problems</h1>
          <p aria-live="polite">{summary}</p>
        </div>

        <div className={s.tracks} role="group" aria-label="Problem type">
          {TRACKS.map((t) => (
            <button
              key={t.id || "all"}
              type="button"
              className={s.track}
              aria-pressed={type === t.id}
              onClick={() => set({ type: t.id || null, tags: null })}
            >
              <t.icon size={17} aria-hidden="true" />
              {t.label}
            </button>
          ))}
        </div>

        <div className={s.layout}>
          {/* ================= List ================= */}
          <section className={s.list} aria-label="Problem list">
            <div className={s.tools}>
              <label className={s.search}>
                <span className={s.sr}>Search problems</span>
                <Search size={18} aria-hidden="true" />
                <input
                  ref={searchRef}
                  className={s.input}
                  type="search"
                  aria-label="Search problems"
                  placeholder="Search by title or number  ( / )"
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") set({ q: e.currentTarget.value.trim() || null });
                  }}
                />
              </label>
              <label>
                <span className={s.sr}>Filter by difficulty</span>
                <select
                  className={s.select}
                  value={difficulty}
                  onChange={(e) => set({ difficulty: e.target.value || null })}
                >
                  <option value="">All difficulties</option>
                  {LEVELS.map((l) => (
                    <option key={l.value} value={l.value}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </label>
              <button className={s.toolBtn} type="button" onClick={pickRandom} disabled={picking}>
                <Shuffle size={17} aria-hidden="true" />
                {picking ? "Picking…" : "Pick random"}
              </button>
            </div>

            {filtersActive && list.state === "ok" && (
              <div className={s.filterNote}>
                <span>{list.data.totalElements} matching</span>
                <button className={s.link} type="button" onClick={clearFilters}>
                  Clear filters
                </button>
              </div>
            )}

            <div className={s.table}>
              <div className={s.head}>
                <span aria-hidden="true" />
                <span className={s.colNum}>No.</span>
                <span>Title</span>
                <span className={s.colTags}>Topics</span>
                <span>Difficulty</span>
              </div>

              {list.state === "loading" && <SkeletonRows />}

              {list.state === "error" && (
                <div className={cn(s.empty, s.error)} role="alert">
                  <h2>Couldn&apos;t load problems</h2>
                  <p>{list.message}</p>
                  <button className={cn(s.toolBtn, s.centerBtn)} type="button" onClick={() => setListTry((n) => n + 1)}>
                    Try again
                  </button>
                </div>
              )}

              {list.state === "ok" &&
                (list.data.content.length > 0 ? (
                  list.data.content.map((p) => <ProblemRow key={p.id} p={p} />)
                ) : filtersActive || page > 1 ? (
                  // An out-of-range ?page= is not "no problems yet"; "Clear filters" also resets the page.
                  <div className={s.empty}>
                    <h2>No problems match your filters</h2>
                    <p>Try another keyword or remove a filter.</p>
                    <button className={cn(s.toolBtn, s.centerBtn)} type="button" onClick={clearFilters}>
                      Clear filters
                    </button>
                  </div>
                ) : (
                  <div className={s.empty}>
                    <h2>No problems yet</h2>
                    <p style={{ margin: 0 }}>Problems will appear here once they are added.</p>
                  </div>
                ))}
            </div>

            {list.state === "ok" && list.data.totalPages > 1 && (
              <div className={s.pager}>
                <span className={s.mono}>
                  Showing {list.data.number * list.data.size + 1}–{list.data.number * list.data.size + list.data.content.length} of{" "}
                  {list.data.totalElements}
                </span>
                <Pagination page={page} totalPages={list.data.totalPages} onChange={(np) => set({ page: String(np) })} />
              </div>
            )}
          </section>

          {/* ================= Aside ================= */}
          <aside className={s.aside} aria-label="Filters and progress">
            <section>
              <h2>Status</h2>
              <div className={s.status} role="group" aria-label="Filter by status">
                {STATUSES.map((st) => (
                  <button
                    key={st.value || "any"}
                    type="button"
                    aria-pressed={progress === st.value}
                    onClick={() => set({ progress: st.value || null })}
                  >
                    {st.icon && <st.icon size={15} aria-hidden="true" />}
                    {st.label}
                  </button>
                ))}
              </div>
            </section>

            <section>
              <h2>Your progress</h2>
              {!userReady || stats.state === "loading" ? (
                <p className={s.note} aria-busy="true">
                  Loading…
                </p>
              ) : stats.state === "error" ? (
                <div role="alert">
                  <p className={s.inlineError}>Couldn&apos;t load progress. {stats.message}</p>
                  <button className={s.link} style={{ paddingLeft: 0 }} type="button" onClick={() => setStatsTry((n) => n + 1)}>
                    Try again
                  </button>
                </div>
              ) : (
                <>
                  <p className={s.score}>
                    <b>{stats.data.byLevel.reduce((a, l) => a + l.solved, 0)}</b> / {stats.data.byLevel.reduce((a, l) => a + l.total, 0)} solved
                  </p>
                  {LEVELS.map((l, i) => {
                    const { total, solved } = stats.data.byLevel[i];
                    return (
                      <div key={l.value} className={s.prog}>
                        <div className={s.progHead}>
                          <span>{l.label}</span>
                          <span className={s.mono}>
                            {solved}/{total}
                          </span>
                        </div>
                        <div
                          className={s.trackBar}
                          role="progressbar"
                          aria-label={`${l.label} solved`}
                          aria-valuemin={0}
                          aria-valuemax={total}
                          aria-valuenow={solved}
                        >
                          <i style={{ width: `${total ? (solved / total) * 100 : 0}%` }} />
                        </div>
                      </div>
                    );
                  })}
                  {!stats.data.recognized && (
                    <p className={s.note}>
                      <Link href="/login?next=/problems" className={s.link} style={{ padding: 0, minHeight: 0 }}>
                        Log in
                      </Link>{" "}
                      to track your progress.
                    </p>
                  )}
                </>
              )}
            </section>

            <section>
              <h2 style={{ marginBottom: 6 }}>Topics</h2>
              {topics.state === "loading" ? (
                <p className={s.note} aria-busy="true">
                  Loading…
                </p>
              ) : topics.state === "error" ? (
                <div role="alert">
                  <p className={s.inlineError}>Couldn&apos;t load topics. {topics.message}</p>
                  <button className={s.link} style={{ paddingLeft: 0 }} type="button" onClick={() => setTopicsTry((n) => n + 1)}>
                    Try again
                  </button>
                </div>
              ) : topics.data.length === 0 ? (
                <p className={s.note}>No topics yet.</p>
              ) : (
                <div>
                  {topics.data.map(({ tag, count }) => (
                    <button
                      key={tag}
                      type="button"
                      className={s.topic}
                      aria-pressed={topic === tag}
                      onClick={() => set({ tags: topic === tag ? null : tag })}
                    >
                      <span>{tag}</span>
                      <span className={s.mono}>{count}</span>
                    </button>
                  ))}
                </div>
              )}
            </section>

            <section>
              <h2>Preferred language</h2>
              <div className={s.langRow} role="group" aria-label="Preferred language">
                {[{ value: "", label: "All" }, ...LANGUAGES].map((l) => (
                  <button key={l.value || "all"} type="button" aria-pressed={lang === l.value} onClick={() => pickLang(l.value)}>
                    {l.label}
                  </button>
                ))}
              </div>
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
}
