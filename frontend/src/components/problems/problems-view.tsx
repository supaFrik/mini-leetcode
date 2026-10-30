"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  BookOpen,
  Check,
  ChevronRight,
  Circle,
  Compass,
  Filter,
  GraduationCap,
  Library,
  ListFilter,
  Lock,
  Plus,
  Search,
  Shuffle,
  Star,
  Swords,
  X,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { useUrlState } from "@/hooks/use-url-state";
import { errorMessage } from "@/lib/api";
import { fetchProblems } from "@/lib/services";
import { DifficultyBadge } from "./difficulty-badge";
import { Pagination } from "./pagination";
import { LANG_PREF_KEY } from "@/lib/templates";
import type { Language, Page, ProblemSummary } from "@/types/api";

const LANGS: [string, Language][] = [
  ["Java", "JAVA"],
  ["Python", "PYTHON"],
];

// Real tags known from the problem catalogue. No counts — the backend does not
// expose them, and a fabricated number is worse than an absent one.
const TOPICS = [
  "Array",
  "String",
  "Hash Table",
  "Two Pointers",
  "Math",
  "Dynamic Programming",
  "Sorting",
  "Greedy",
  "Graph",
  "Binary Search",
  "Design",
  "DFS / BFS",
];

// Category presets wire directly into the same tags filter.
const CATEGORIES: { label: string; tags: string[] }[] = [
  { label: "All Topics", tags: [] },
  { label: "Algorithms", tags: ["Array", "Sorting", "Dynamic Programming"] },
  { label: "Data Structures", tags: ["Array", "Hash Table", "Graph"] },
  { label: "Math", tags: ["Math"] },
  { label: "Graph", tags: ["Graph", "DFS / BFS"] },
  { label: "Design", tags: ["Design"] },
];

function Segmented({
  value,
  onChange,
  options,
}: {
  value: string;
  onChange: (v: string) => void;
  options: [string, string][];
}) {
  return (
    <div className="flex flex-wrap gap-1 rounded-sm bg-hairline-soft p-1" role="group">
      {options.map(([v, label]) => (
        <button
          key={v}
          onClick={() => onChange(v)}
          aria-pressed={value === v}
          className={cn(
            "rounded-sm px-2.5 py-1.5 text-body-sm font-medium transition-colors active:translate-y-px",
            value === v ? "bg-ink text-white" : "text-body hover:bg-card hover:text-ink"
          )}
        >
          {label}
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

function Sidebar({ activeTags, onPickTag }: { activeTags: string[]; onPickTag: (t: string) => void }) {
  const item =
    "group flex w-full items-center gap-2.5 rounded-sm px-3 py-2 text-body-md text-body transition-colors hover:bg-hairline-soft hover:text-ink";
  return (
    <aside className="hidden w-60 shrink-0 lg:block">
      <div className="sticky top-5 space-y-5 rounded-xl border border-border bg-card p-3">
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

        <div className="h-px bg-border" />

        <div>
          <div className="mb-1.5 flex items-center justify-between px-3">
            <span className="eyebrow">My lists</span>
            <button
              aria-label="New list"
              className="rounded p-0.5 text-mute transition-colors hover:bg-hairline-soft hover:text-ink"
            >
              <Plus className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="space-y-0.5">
            <span className={item}>
              <Star className="h-4 w-4 fill-amber-400 text-amber-400" />
              <span className="flex-1">Favorite</span>
              <Lock className="h-3.5 w-3.5 opacity-50" />
            </span>
            <span className={cn(item, "cursor-default")}>
              <BookOpen className="h-4 w-4 text-link" />
              Need Practice
            </span>
          </div>
        </div>

        <div className="h-px bg-border" />

        <div>
          <p className="eyebrow mb-1.5 px-3">Saved topics</p>
          <div className="space-y-0.5">
            {["Binary Search", "Dynamic Programming"].map((t) => {
              const on = activeTags.includes(t);
              return (
                <button
                  key={t}
                  onClick={() => onPickTag(t)}
                  className={cn(item, on && "bg-hairline-soft text-ink")}
                >
                  <span
                    className={cn(
                      "h-2.5 w-2.5 rounded-[4px]",
                      t === "Binary Search" ? "bg-link" : "bg-easy"
                    )}
                  />
                  <span className="flex-1 text-left">{t}</span>
                  {on && <Check className="h-3.5 w-3.5 text-ink" />}
                </button>
              );
            })}
            <button className={cn(item, "text-mute")}>
              <Plus className="h-3.5 w-3.5" /> New List
            </button>
          </div>
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
      <div className="h-5 w-16 animate-pulse rounded-full bg-hairline-soft" />
    </div>
  );
}

export function ProblemsView() {
  const { sp, set } = useUrlState();
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

  const applyCategory = (cats: string[]) => set({ tags: cats.join(",") || null });
  const isCategoryActive = (cats: string[]) =>
    cats.length === 0 ? tags.length === 0 : cats.length === tags.length && cats.every((c) => tags.includes(c));

  const doneOnPage = res?.content.filter((p) => p.progress === "DONE").length ?? 0;
  const inProgressOnPage = res?.content.filter((p) => p.progress === "IN_PROGRESS").length ?? 0;

  const pickRandom = () => {
    const pool = res?.content ?? [];
    if (!pool.length) return;
    const pick = pool[Math.floor(Math.random() * pool.length)];
    window.location.href = `/problems/${pick.slug}`;
  };

  return (
    <div className="mx-auto w-full max-w-container px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex items-start gap-8">
        <Sidebar activeTags={tags} onPickTag={toggleTag} />

        {/* Center feed */}
        <div className="min-w-0 flex-1 space-y-5">
          <div>
            <h1 className="text-heading-lg text-ink">Problems</h1>
            <p className="mt-1 text-body-md text-body">
              {res ? `${res.totalElements} problems in the catalogue.` : "Loading the catalogue."}
            </p>
          </div>

          {/* Topic pills — horizontally scrollable, wired, no counts */}
          <div
            aria-label="Filter by topic"
            className="scrollbar-none -mx-1 flex gap-2 overflow-x-auto px-1 pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          >
            {TOPICS.map((t) => {
              const on = tags.includes(t);
              return (
                <button
                  key={t}
                  onClick={() => toggleTag(t)}
                  aria-pressed={on}
                  className={cn(
                    "shrink-0 rounded-pill-category border border-border bg-card px-4 py-1.5 text-body-md transition-colors active:translate-y-px",
                    on ? "border-ink bg-ink text-white" : "text-body hover:bg-hairline-soft hover:text-ink"
                  )}
                >
                  {t}
                </button>
              );
            })}
          </div>

          {/* Category presets */}
          <div aria-label="Problem categories" className="flex flex-wrap items-center gap-2">
            {CATEGORIES.map((c) => {
              const on = isCategoryActive(c.tags);
              return (
                <button
                  key={c.label}
                  onClick={() => applyCategory(c.tags)}
                  aria-pressed={on}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-body-md transition-colors active:translate-y-px",
                    on ? "border-ink bg-ink text-white" : "border-border bg-card text-body hover:border-ink/20 hover:text-ink"
                  )}
                >
                  {c.label === "All Topics" && <ListFilter className="h-3.5 w-3.5" />}
                  {c.label}
                </button>
              );
            })}
          </div>

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
                    options={[["", "All"], ["DONE", "Solved"], ["IN_PROGRESS", "Attempted"], ["NEVER", "Todo"]]}
                  />
                </div>
                <div>
                  <p className="eyebrow mb-1.5">Difficulty</p>
                  <Segmented
                    value={dDiff}
                    onChange={setDDiff}
                    options={[["", "All"], ["EASY", "Easy"], ["MEDIUM", "Med."], ["HARD", "Hard"]]}
                  />
                </div>
                <div>
                  <p className="eyebrow mb-1.5">Preferred language</p>
                  <Segmented value={lang} onChange={pickLang} options={[["", "All"], ["JAVA", "Java"], ["PYTHON", "Python"]]} />
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
                {Array.from({ length: 8 }, (_, i) => (
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
