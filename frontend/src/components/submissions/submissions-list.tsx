"use client";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Check, Clock, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useUrlState } from "@/hooks/use-url-state";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fmtMemory } from "@/lib/format";
import { fetchMySubmissions } from "@/lib/services";
import { VERDICT } from "@/lib/verdict";
import { Pagination } from "@/components/problems/pagination";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./submissions.module.css";
import type { Submission, Verdict } from "@/types/api";

/** API chưa lọc/tổng hợp phía server, nên tải tối đa chừng này bài nộp gần nhất rồi lọc tại client. */
const FETCH_SIZE = 100;
const MAX_PAGES = 5;
const PAGE_SIZE = 20;

type Key = Verdict | "PENDING";
const keyOf = (x: Submission): Key => (x.verdict ?? (x.status === "FAILED" ? "RE" : "PENDING")) as Key;
const labelOf = (k: Key) => (k === "PENDING" ? "Pending" : VERDICT[k]?.label ?? k);

const VERDICT_OPTIONS: Key[] = ["AC", "WA", "TLE", "MLE", "RE", "CE", "PENDING"];
/** Luôn hiện trong Summary (như bản mẫu); verdict khác chỉ hiện khi có. */
const SUMMARY_BASE: Key[] = ["AC", "WA", "TLE", "CE", "PENDING"];

const LANG_LABEL: Record<string, string> = { JAVA: "Java 17", PYTHON: "Python 3", CPP: "C++ 20" };
const langLabel = (l?: string) => (l ? LANG_LABEL[l] ?? l : "—");

function tone(k: Key) {
  if (k === "AC") return s.ac;
  if (k === "PENDING") return s.pending;
  if (k === "TLE" || k === "MLE") return s.warn;
  return s.fail;
}

function VerdictIcon({ k }: { k: Key }) {
  if (k === "AC") return <Check size={17} strokeWidth={2.4} aria-hidden="true" />;
  if (k === "PENDING") return <Clock size={17} strokeWidth={2.2} aria-hidden="true" />;
  return <X size={17} strokeWidth={2.4} aria-hidden="true" />;
}

function fmtWhen(iso?: string, now = Date.now()) {
  const t = parseApiDate(iso);
  if (Number.isNaN(t)) return "—";
  const min = Math.floor((now - t) / 60000);
  if (min < 1) return "Just now";
  if (min < 60) return `${min} min ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr} hr ago`;
  const d = new Date(t);
  const y = new Date(now - 86400000);
  if (d.toDateString() === y.toDateString()) return "Yesterday";
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", ...(d.getFullYear() !== new Date(now).getFullYear() ? { year: "numeric" } : {}) });
}

type Load = { state: "loading" } | { state: "ok"; items: Submission[]; truncated: boolean } | { state: "error"; message: string };

async function loadAll(): Promise<{ items: Submission[]; truncated: boolean }> {
  const first = await fetchMySubmissions(1, FETCH_SIZE);
  const items = [...first.content];
  const pages = Math.min(first.totalPages, MAX_PAGES);
  for (let i = 2; i <= pages; i++) items.push(...(await fetchMySubmissions(i, FETCH_SIZE)).content);
  return { items, truncated: first.totalPages > MAX_PAGES };
}

export function SubmissionsList() {
  const { sp, set } = useUrlState();
  const verdict = sp.get("verdict") ?? "";
  const language = sp.get("language") ?? "";
  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);

  const [load, setLoad] = useState<Load>({ state: "loading" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let off = false;
    setLoad({ state: "loading" });
    loadAll()
      .then((r) => !off && setLoad({ state: "ok", ...r }))
      .catch((e) => !off && setLoad({ state: "error", message: errorMessage(e) }));
    return () => {
      off = true;
    };
  }, [attempt]);

  const all = useMemo(() => (load.state === "ok" ? load.items : []), [load]);
  const shown = useMemo(
    () => all.filter((x) => (!verdict || keyOf(x) === verdict) && (!language || x.language === language)),
    [all, verdict, language]
  );
  const languages = useMemo(() => Array.from(new Set(["JAVA", "PYTHON", ...all.map((x) => x.language).filter(Boolean)])) as string[], [all]);
  const totalPages = Math.max(1, Math.ceil(shown.length / PAGE_SIZE));
  const rows = shown.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const counts = useMemo(() => {
    const c = new Map<Key, number>();
    all.forEach((x) => c.set(keyOf(x), (c.get(keyOf(x)) ?? 0) + 1));
    return c;
  }, [all]);
  const summaryKeys = [...SUMMARY_BASE, ...VERDICT_OPTIONS.filter((k) => !SUMMARY_BASE.includes(k) && counts.get(k))];

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <h1 className={s.title}>Submissions</h1>
        <div className={p.layout}>
          <section className={p.list} aria-label="Submission history">
            <div className={p.tools}>
              <label>
                <span className={p.sr}>Filter by verdict</span>
                <select className={p.select} value={verdict} onChange={(e) => set({ verdict: e.target.value || null })}>
                  <option value="">All verdicts</option>
                  {VERDICT_OPTIONS.map((k) => (
                    <option key={k} value={k}>
                      {labelOf(k)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span className={p.sr}>Filter by language</span>
                <select className={p.select} value={language} onChange={(e) => set({ language: e.target.value || null })}>
                  <option value="">All languages</option>
                  {languages.map((l) => (
                    <option key={l} value={l}>
                      {langLabel(l)}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className={p.table}>
              <div className={s.head}>
                <span>Verdict</span>
                <span>Problem</span>
                <span>Language</span>
                <span>Runtime</span>
                <span>Memory</span>
                <span>Submitted</span>
              </div>

              {load.state === "loading" && (
                <div className={p.empty} aria-busy="true">
                  <p>Loading submissions…</p>
                </div>
              )}

              {load.state === "error" && (
                <div className={cn(p.empty, p.error)} role="alert">
                  <h2>Couldn&apos;t load submissions</h2>
                  <p>{load.message}</p>
                  <button className={cn(p.toolBtn, p.centerBtn)} type="button" onClick={() => setAttempt((n) => n + 1)}>
                    Try again
                  </button>
                </div>
              )}

              {load.state === "ok" &&
                (all.length === 0 ? (
                  <div className={p.empty}>
                    <h2>No submissions yet</h2>
                    <p>Solve a problem and your attempts will appear here.</p>
                    <Link href="/problems" className={s.browse}>
                      Browse problems
                    </Link>
                  </div>
                ) : rows.length === 0 ? (
                  <div className={p.empty}>
                    <h2>No submissions match your filters</h2>
                    <p>Try another verdict or language.</p>
                  </div>
                ) : (
                  rows.map((x) => {
                    const k = keyOf(x);
                    return (
                      <Link key={x.id} href={`/submissions/${x.id}`} className={s.row}>
                        <span className={cn(s.verdict, tone(k))}>
                          <VerdictIcon k={k} />
                          {labelOf(k)}
                        </span>
                        <span className={s.problem}>
                          {x.problemTitle ?? `#${x.problemId}`}
                          {x.contestId && <span className={s.tag}>Exam</span>}
                        </span>
                        <span className={cn(s.small, s.hideSm)}>{langLabel(x.language)}</span>
                        <span className={cn(s.num, s.hideSm)}>{x.executionTimeMs != null ? `${x.executionTimeMs} ms` : "-"}</span>
                        <span className={cn(s.num, s.hideSm)}>{x.memoryUsedKb != null ? fmtMemory(x.memoryUsedKb) : "-"}</span>
                        <span className={cn(s.small, s.hideSm)}>{fmtWhen(x.createdAt)}</span>
                      </Link>
                    );
                  })
                ))}
            </div>

            {load.state === "ok" && totalPages > 1 && (
              <div className={p.pager}>
                <span className={p.mono}>
                  Showing {(page - 1) * PAGE_SIZE + 1}–{(page - 1) * PAGE_SIZE + rows.length} of {shown.length}
                </span>
                <Pagination page={page} totalPages={totalPages} onChange={(np) => set({ page: String(np) })} />
              </div>
            )}
          </section>

          <aside className={p.aside} aria-label="Submission summary">
            <section>
              <h2>Summary</h2>
              {load.state === "ok" ? (
                <>
                  <p className={p.score}>
                    <b>{counts.get("AC") ?? 0}</b> / {all.length} accepted
                  </p>
                  <div className={s.summaryList}>
                    {summaryKeys.map((k) => (
                      <div key={k} className={s.summaryItem}>
                        <span>{labelOf(k)}</span>
                        <span>{counts.get(k) ?? 0}</span>
                      </div>
                    ))}
                  </div>
                  {load.truncated && <p className={p.note}>Based on your latest {FETCH_SIZE * MAX_PAGES} submissions.</p>}
                </>
              ) : load.state === "error" ? (
                <p className={p.inlineError}>Summary unavailable.</p>
              ) : (
                <p className={p.note}>Loading…</p>
              )}
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
}
