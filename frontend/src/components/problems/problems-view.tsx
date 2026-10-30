"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  Check,
  ChevronRight,
  Circle,
  Compass,
  Filter,
  GraduationCap,
  Library,
  Play,
  Search,
  Shuffle,
  Swords,
  Timer,
  X,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { useUrlState } from "@/hooks/use-url-state";
import { errorMessage } from "@/lib/api";
import { fetchContests, fetchMySubmissions, fetchProblems, fetchProblemTags } from "@/lib/services";
import { useUser } from "@/hooks/use-user";
import { DifficultyBadge } from "./difficulty-badge";
import { Pagination } from "./pagination";
import { LANG_PREF_KEY } from "@/lib/templates";
import type { ContestInfo, Page, ProblemSummary, Submission } from "@/types/api";

/* ------------------------------------------------------------------ *
 * Filter vocabulary
 * ------------------------------------------------------------------ */

const DIFFICULTIES = [
  { value: "", label: "Any" },
  { value: "EASY", label: "Easy" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HARD", label: "Hard" },
] as const;

const STATUSES = [
  { value: "", label: "Any" },
  { value: "DONE", label: "Solved" },
  { value: "IN_PROGRESS", label: "Attempted" },
  { value: "NEVER", label: "Todo" },
] as const;

function Segmented({
  value,
  onChange,
  options,
}: {
  value: string;
  onChange: (v: string) => void;
  options: readonly { value: string; label: string }[];
}) {
  return (
    <div className="flex flex-wrap gap-1 rounded-sm bg-hairline-soft p-1" role="group">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          aria-pressed={value === o.value}
          className={cn(
            "rounded-sm px-2.5 py-1.5 text-body-sm font-medium transition-colors active:translate-y-px",
            value === o.value ? "bg-ink text-white" : "text-body hover:bg-card hover:text-ink"
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

function Removable({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card py-1 pl-2.5 pr-1.5 text-body-sm text-body">
      {label}
      <button
        aria-label={`Remove filter ${label}`}
        onClick={onRemove}
        className="grid h-4 w-4 place-items-center rounded-full text-mute transition-colors hover:bg-hairline-soft hover:text-ink"
      >
        <X className="h-3 w-3" />
      </button>
    </span>
  );
}

/* ------------------------------------------------------------------ *
 * Left rail — the single filter surface + at-a-glance progress
 * ------------------------------------------------------------------ */

interface RailProps {
  tags: string[];
  tagCounts: { tag: string; count: number }[];
  onToggleTag: (t: string) => void;
  difficulty: string;
  onDifficulty: (v: string) => void;
  progress: string;
  onProgress: (v: string) => void;
  solvedCount: number | null;
  totalCount: number | null;
  nextUp: ProblemSummary | null;
  runningContests: ContestInfo[];
  signedIn: boolean;
}

function Rail({
  tags,
  tagCounts,
  onToggleTag,
  difficulty,
  onDifficulty,
  progress,
  onProgress,
  solvedCount,
  totalCount,
  nextUp,
  runningContests,
  signedIn,
}: RailProps) {
  const item =
    "group flex w-full items-center gap-2.5 rounded-sm px-3 py-2 text-body-md text-body transition-colors hover:bg-hairline-soft hover:text-ink";

  return (
    <aside
      aria-label="Library navigation"
      className="hidden min-h-0 w-64 shrink-0 flex-col overflow-y-auto border-r border-border px-4 py-6 sm:px-6 lg:flex"
    >
      <nav aria-label="Library" className="space-y-0.5">
        <Link href="/problems" className={cn(item, "bg-hairline-soft font-medium text-ink")}>
          <Library className="h-4 w-4" />
          Library
        </Link>
        <Link href="/exam" className={item}>
          <Swords className="h-4 w-4" />
          Contest
          <ChevronRight className="ml-auto h-3.5 w-3.5 opacity-0 transition-opacity group-hover:opacity-60" />
        </Link>
        <span className={cn(item, "cursor-default")}>
          <Compass className="h-4 w-4" />
          Explore
        </span>
        <span className={cn(item, "cursor-default")}>
          <GraduationCap className="h-4 w-4" />
          Study Plan
        </span>
      </nav>

      {/* ---- Progress at a glance (signed-in only) ---- */}
      {signedIn && (
        <>
          <div className="my-5 h-px bg-border" />

          <div className="px-3">
            <p className="eyebrow">Your progress</p>
            {solvedCount != null && totalCount != null ? (
              <>
                <p className="mt-2 text-heading-md text-ink">
                  {solvedCount}
                  <span className="text-body-md text-mute"> / {totalCount} solved</span>
                </p>
                <div
                  className="mt-2 h-1 w-full overflow-hidden rounded-full bg-hairline-soft"
                  role="progressbar"
                  aria-valuenow={solvedCount}
                  aria-valuemin={0}
                  aria-valuemax={totalCount}
                >
                  <div
                    className="h-full rounded-full bg-ink transition-[width] duration-500 ease-smooth"
                    style={{ width: `${totalCount ? (solvedCount / totalCount) * 100 : 0}%` }}
                  />
                </div>
              </>
            ) : (
              <div className="mt-2 h-4 w-24 animate-pulse rounded bg-hairline-soft" />
            )}
          </div>

          {nextUp && (
            <Link
              href={`/problems/${nextUp.slug}`}
              className="mt-3 flex items-start gap-2.5 rounded-sm px-3 py-2 transition-colors hover:bg-hairline-soft"
            >
              <Play className="mt-0.5 h-3.5 w-3.5 shrink-0 text-link" />
              <span className="min-w-0">
                <span className="block truncate text-body-md text-ink">{nextUp.title}</span>
                <span className="mt-0.5 block text-body-sm text-mute">Continue where you left off</span>
              </span>
            </Link>
          )}

          {runningContests.length > 0 && (
            <div className="mt-3 px-3">
              <p className="eyebrow">Live now</p>
              {runningContests.map((c) => (
                <Link
                  key={c.id}
                  href={`/exam/${c.id}`}
                  className="mt-2 flex items-center gap-2 rounded-sm px-0 py-1 text-body-md text-ink transition-colors hover:text-link"
                >
                  <Timer className="h-3.5 w-3.5 text-medium" />
                  <span className="truncate">{c.title}</span>
                </Link>
              ))}
            </div>
          )}
        </>
      )}

      <div className="my-5 h-px bg-border" />

      {/* ---- Topics: the only topic surface, now backed by real data ---- */}
      <div>
        <p className="eyebrow mb-1.5 px-3">Topics</p>
        <div className="space-y-0.5">
          {tagCounts.map(({ tag, count }) => {
            const on = tags.includes(tag);
            return (
              <button
                key={tag}
                onClick={() => onToggleTag(tag)}
                aria-pressed={on}
                className={cn(item, on && "bg-hairline-soft text-ink")}
              >
                <span
                  className={cn(
                    "h-2.5 w-2.5 shrink-0 rounded-[3px]",
                    on ? "bg-link" : "bg-border"
                  )}
                />
                <span className="flex-1 text-left">{tag}</span>
                <span className="font-mono text-[11px] text-mute">{count}</span>
              </button>
            );
          })}
          {tagCounts.length === 0 && (
            <div className="space-y-1.5 px-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="h-8 animate-pulse rounded-sm bg-hairline-soft" />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="my-5 h-px bg-border" />

      {/* ---- Difficulty + status ---- */}
      {signedIn && (
        <div className="mb-4">
          <p className="eyebrow mb-1.5 px-3">Status</p>
          <div className="px-3">
            <Segmented value={progress} onChange={onProgress} options={STATUSES} />
          </div>
        </div>
      )}

      <div>
        <p className="eyebrow mb-1.5 px-3">Difficulty</p>
        <div className="px-3">
          <Segmented value={difficulty} onChange={onDifficulty} options={DIFFICULTIES} />
        </div>
      </div>
    </aside>
  );
}

function RowSkeleton() {
  return (
    <div className="grid grid-cols-[44px_1fr_100px] items-center gap-2 border-t border-border px-5 py-4 sm:grid-cols-[56px_1fr_120px]">
      <div className="h-5 w-5 animate-pulse rounded-full bg-hairline-soft" />
      <div className="space-y-2">
        <div className="h-4 w-2/3 animate-pulse rounded bg-hairline-soft" />
        <div className="flex gap-1.5">
          <div className="h-4 w-14 animate-pulse rounded-full bg-hairline-soft" />
          <div className="h-4 w-16 animate-pulse rounded-full bg-hairline-soft" />
        </div>
      </div>
      <div className="h-5 w-16 animate-pulse rounded bg-hairline-soft" />
    </div>
  );
}

export function ProblemsView() {
  const { sp, set } = useUrlState();
  const { user, ready: userReady } = useUser();
  const signedIn = !!user;
  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);
  const q = sp.get("q") ?? "";
  const difficulty = sp.get("difficulty") ?? "";
  const progress = sp.get("progress") ?? "";
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
  const searchRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (e.key === "/" && !["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName)) {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  const tagsParam = sp.get("tags") ?? "";
  const tags = useMemo(() => tagsParam.split(",").filter(Boolean), [tagsParam]);

  const [res, setRes] = useState<Page<ProblemSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tagCounts, setTagCounts] = useState<{ tag: string; count: number }[]>([]);
  const [subs, setSubs] = useState<Page<Submission> | null>(null);
  const [contests, setContests] = useState<ContestInfo[] | null>(null);

  useEffect(() => {
    fetchProblemTags()
      .then(setTagCounts)
      .catch(() => setTagCounts([]));
  }, []);

  // Dashboard data is only meaningful for a signed-in user.
  useEffect(() => {
    if (!userReady) return;
    if (!signedIn) {
      setSubs(null);
      setContests(null);
      return;
    }
    let off = false;
    Promise.allSettled([fetchMySubmissions(1, 50), fetchContests()]).then(([s, c]) => {
      if (off) return;
      setSubs(s.status === "fulfilled" ? s.value : null);
      setContests(c.status === "fulfilled" ? c.value : null);
    });
    return () => {
      off = true;
    };
  }, [signedIn, userReady]);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    fetchProblems({ page, q, difficulty, progress, tags })
      .then((r) => {
        if (!ignore) setRes(r);
      })
      .catch((e) => {
        if (!ignore) setError(errorMessage(e));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, q, difficulty, progress, tagsParam]);

  const [open, setOpen] = useState(false);
  const [dProgress, setDProgress] = useState(progress);
  const [dDiff, setDDiff] = useState(difficulty);
  const activeCount = (progress ? 1 : 0) + (difficulty ? 1 : 0) + tags.length;

  const toggleTag = (t: string) => {
    const next = tags.includes(t) ? tags.filter((x) => x !== t) : [...tags, t];
    set({ tags: next.join(",") || null });
  };

  const doneOnPage = res?.content.filter((p) => p.progress === "DONE").length ?? 0;
  const inProgressOnPage = res?.content.filter((p) => p.progress === "IN_PROGRESS").length ?? 0;

  /* ---- Dashboard figures ---- */
  const solvedProblemIds = useMemo(
    () => new Set((subs?.content ?? []).filter((s) => s.verdict === "AC").map((s) => s.problemId)),
    [subs]
  );
  const attemptedProblemIds = useMemo(
    () => new Set((subs?.content ?? []).map((s) => s.problemId)),
    [subs]
  );
  const solvedCount = subs ? solvedProblemIds.size : null;
  const totalCount = res?.totalElements ?? null;
  const nextUp = useMemo(() => {
    if (!signedIn) return null;
    const pool = res?.content ?? [];
    return (
      pool.find((p) => p.progress === "IN_PROGRESS") ??
      pool.find((p) => !solvedProblemIds.has(p.id) && !attemptedProblemIds.has(p.id)) ??
      null
    );
  }, [res, signedIn, solvedProblemIds, attemptedProblemIds]);
  const runningContests = useMemo(
    () => (contests ?? []).filter((c) => c.status === "RUNNING"),
    [contests]
  );

  const pickRandom = () => {
    const pool = res?.content ?? [];
    if (!pool.length) return;
    const pick = pool[Math.floor(Math.random() * pool.length)];
    window.location.href = `/problems/${pick.slug}`;
  };

  return (
    <div className="flex h-full min-h-0">
      <Rail
        tags={tags}
        tagCounts={tagCounts}
        onToggleTag={toggleTag}
        difficulty={difficulty}
        onDifficulty={(v) => set({ difficulty: v || null })}
        progress={progress}
        onProgress={(v) => set({ progress: v || null })}
        solvedCount={solvedCount}
        totalCount={totalCount}
        nextUp={nextUp}
        runningContests={runningContests}
        signedIn={signedIn}
      />

      {/* Center feed — scrolls independently so the library rail stays put. */}
      <div className="min-h-0 min-w-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
          {/* Search + actions */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-0 flex-1 basis-64">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
              <Input
                ref={searchRef}
                key={q}
                defaultValue={q}
                className="h-10 rounded-sm bg-card pl-9 pr-14 text-body-md text-ink"
                aria-label="Search problems"
                placeholder="Search questions"
                onKeyDown={(e) => {
                  if (e.key === "Enter") set({ q: e.currentTarget.value || null });
                }}
              />
              <kbd className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 rounded-sm border border-border bg-hairline-soft px-1.5 font-mono text-[10px] text-mute sm:block">
                /
              </kbd>
            </div>
            <Button variant="outline" size="icon" aria-label="Pick random problem" title="Pick random" onClick={pickRandom} className="h-10 w-10">
              <Shuffle className="h-4 w-4" />
            </Button>
            <Popover
              open={open}
              onOpenChange={(o) => {
                setOpen(o);
                if (o) {
                  setDProgress(progress);
                  setDDiff(difficulty);
                }
              }}
            >
              <PopoverTrigger asChild>
                <Button size="sm" variant="outline" className="h-10 gap-1.5 px-3.5">
                  <Filter className="h-3.5 w-3.5" />
                  Filter
                  {activeCount > 0 && (
                    <Badge className="ml-0.5 h-5 min-w-5 justify-center rounded-full px-1.5 font-mono text-[10px]">
                      {activeCount}
                    </Badge>
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent align="end" className="w-80 space-y-4 p-4">
                <div>
                  <p className="eyebrow mb-1.5">Status</p>
                  <Segmented
                    value={dProgress}
                    onChange={setDProgress}
                    options={STATUSES}
                  />
                </div>
                <div>
                  <p className="eyebrow mb-1.5">Difficulty</p>
                  <Segmented
                    value={dDiff}
                    onChange={setDDiff}
                    options={DIFFICULTIES}
                  />
                </div>
                <div>
                  <p className="eyebrow mb-1.5">Preferred language</p>
                  <Segmented value={lang} onChange={pickLang} options={[{ value: "", label: "All" }, { value: "JAVA", label: "Java" }, { value: "PYTHON", label: "Python" }]} />
                </div>
                <div className="flex items-center justify-between pt-1">
                  <button
                    className="text-body-sm text-body transition-colors hover:text-ink"
                    onClick={() => {
                      setDProgress("");
                      setDDiff("");
                    }}
                  >
                    Reset
                  </button>
                  <Button
                    size="sm"
                    onClick={() => {
                      set({ progress: dProgress || null, difficulty: dDiff || null });
                      setOpen(false);
                    }}
                  >
                    Apply
                  </Button>
                </div>
              </PopoverContent>
            </Popover>
          </div>

          {/* Active filters */}
          {(q || difficulty || progress || tags.length > 0) && (
            <div className="flex flex-wrap items-center gap-1.5" aria-label="Active filters">
              <span className="mr-1 text-body-sm text-mute">Active:</span>
              {q && <Removable label={`“${q}”`} onRemove={() => set({ q: null })} />}
              {difficulty && <Removable label={difficulty} onRemove={() => set({ difficulty: null })} />}
              {progress && <Removable label={progress} onRemove={() => set({ progress: null })} />}
              {tags.map((t) => (
                <Removable
                  key={t}
                  label={t}
                  onRemove={() => set({ tags: tags.filter((x) => x !== t).join(",") || null })}
                />
              ))}
              <button
                className="ml-1 text-body-sm text-body underline-offset-2 transition-colors hover:text-ink hover:underline"
                onClick={() => set({ q: null, difficulty: null, progress: null, tags: null })}
              >
                Clear all
              </button>
            </div>
          )}

          {/* List */}
          <section aria-label="Problem list" className="overflow-hidden rounded-xl border border-border bg-card">
            <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
              <div className="grid flex-1 grid-cols-[44px_1fr_100px] items-center gap-2 text-[11px] font-medium uppercase tracking-wider text-mute sm:grid-cols-[56px_1fr_120px]">
                <span>Status</span>
                <span>Title</span>
                <span className="text-right sm:text-left">Difficulty</span>
              </div>
              <span className="hidden shrink-0 items-center gap-3 font-mono text-[11px] text-mute sm:flex" aria-live="polite">
                {res ? (
                  <>
                    <span className="inline-flex items-center gap-1.5">
                      <span className="h-1.5 w-1.5 rounded-full bg-easy" />
                      {doneOnPage} solved
                    </span>
                    {inProgressOnPage > 0 && (
                      <span className="inline-flex items-center gap-1.5">
                        <span className="h-1.5 w-1.5 rounded-full bg-medium" />
                        {inProgressOnPage} tried
                      </span>
                    )}
                  </>
                ) : (
                  <span className="h-3 w-24 animate-pulse rounded bg-hairline-soft" />
                )}
              </span>
            </div>

            {error && (
              <div role="alert" className="space-y-2 p-10 text-center">
                <p className="font-medium text-destructive">Couldn&apos;t load problems</p>
                <p className="mx-auto max-w-[42ch] text-body-md text-mute">{error}</p>
                <Button variant="outline" size="sm" className="mt-2" onClick={() => set({ page: String(page) })}>
                  Try again
                </Button>
              </div>
            )}

            {loading && !res && !error && (
              <div aria-label="Loading problems">
                {Array.from({ length: 8 }).map((_, i) => (
                  <RowSkeleton key={i} />
                ))}
              </div>
            )}

            {res?.content.map((p, i) => (
              <Link
                key={p.id}
                href={`/problems/${p.slug}`}
                style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}
                className="group grid animate-[fadeSlideIn_0.35s_cubic-bezier(0.16,1,0.3,1)_both] grid-cols-[44px_1fr_100px] items-center gap-2 border-t border-border px-5 py-4 transition-colors hover:bg-hairline-soft sm:grid-cols-[56px_1fr_120px]"
              >
                <span
                  aria-label={
                    p.progress === "DONE" ? `Solved: ${p.title}` : p.progress === "IN_PROGRESS" ? `Attempted: ${p.title}` : `Todo: ${p.title}`
                  }
                >
                  {p.progress === "DONE" ? (
                    <span className="grid h-5 w-5 place-items-center rounded-full bg-easy text-white">
                      <Check className="h-3 w-3" strokeWidth={3} />
                    </span>
                  ) : (
                    <Circle
                      className={cn(
                        "h-5 w-5",
                        p.progress === "IN_PROGRESS" ? "fill-medium/20 text-medium" : "text-faint"
                      )}
                    />
                  )}
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-body-md text-ink transition-colors group-hover:text-link">
                    <span className="mr-1.5 font-mono text-mute">{p.id}.</span>
                    {p.title}
                  </span>
                  {p.tags.length > 0 && (
                    <span className="mt-1.5 hidden flex-wrap gap-1 sm:flex" aria-label={`Topics: ${p.tags.join(", ")}`}>
                      {p.tags.slice(0, 3).map((t) => (
                        <span key={t} className="rounded-full bg-hairline-soft px-2 py-px font-mono text-[10px] text-mute">
                          {t}
                        </span>
                      ))}
                    </span>
                  )}
                </span>
                <span className="flex justify-end sm:justify-start">
                  <DifficultyBadge value={p.difficulty} />
                </span>
              </Link>
            ))}

            {res && res.content.length === 0 && (
              <div className="border-t border-border p-12 text-center">
                <div className="mx-auto mb-4 grid h-11 w-11 place-items-center rounded-xl bg-hairline-soft">
                  <Search className="h-5 w-5 text-mute" />
                </div>
                <p className="font-medium text-ink">No problems match your filters</p>
                <p className="mx-auto mt-1 max-w-[40ch] text-body-md text-mute">
                  Try a different keyword, or clear difficulty, status and topic filters.
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-5"
                  onClick={() => set({ q: null, difficulty: null, progress: null, tags: null })}
                >
                  Clear filters
                </Button>
              </div>
            )}

            {res && res.content.length > 0 && (
              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border bg-hairline-soft px-5 py-3">
                <span className="font-mono text-[11px] text-mute">
                  Showing {res.totalElements === 0 ? 0 : res.number * res.size + 1}–
                  {res.number * res.size + res.content.length} of {res.totalElements}
                </span>
                <Pagination page={page} totalPages={res.totalPages} onChange={(np) => set({ page: String(np) })} />
              </div>
            )}
          </section>

          <p className="px-1 font-mono text-[11px] text-mute">
            Tip: press <kbd className="rounded-sm border border-border bg-card px-1">/</kbd> to jump to search.
          </p>
        </div>
      </div>

      <style jsx global>{`
        @keyframes fadeSlideIn {
          from {
            opacity: 0;
            transform: translateY(6px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
        @media (prefers-reduced-motion: reduce) {
          a[class*="animate-"] {
            animation: none !important;
          }
        }
      `}</style>
    </div>
  );
}
