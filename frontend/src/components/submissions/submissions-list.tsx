"use client";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Check, Clock, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useLoad } from "@/hooks/use-load";
import { useUrlState } from "@/hooks/use-url-state";
import { parseApiDate } from "@/lib/datetime";
import { fmtMemory } from "@/lib/format";
import { fetchMySubmissionSummary, fetchMySubmissions } from "@/lib/services";
import { VERDICT } from "@/lib/verdict";
import { Pagination } from "@/components/problems/pagination";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./submissions.module.css";
import type { Submission, Verdict } from "@/types/api";

const PAGE_SIZE = 20;
const LANGUAGES = ["JAVA", "PYTHON"];

type Key = Verdict | "PENDING";
const keyOf = (x: Submission): Key => (x.verdict ?? (x.status === "FAILED" ? "RE" : "PENDING")) as Key;
const labelOf = (k: Key) => (k === "PENDING" ? "Pending" : VERDICT[k]?.label ?? k);

const VERDICT_OPTIONS: Key[] = ["AC", "WA", "TLE", "MLE", "RE", "CE", "PENDING"];
/** Luôn hiện trong Summary (như bản mẫu); verdict khác chỉ hiện khi có. */
const SUMMARY_BASE: Key[] = ["AC", "WA", "TLE", "RE", "CE", "PENDING"];

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


export function SubmissionsList() {
  const { sp, set } = useUrlState();
  const verdict = sp.get("verdict") ?? "";
  const language = sp.get("language") ?? "";
  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);
  const filtered = !!(verdict || language);

  const [attempt, setAttempt] = useState(0);
  // Server lọc và phân trang; Summary đếm toàn bộ bài nộp nên không phụ thuộc bộ lọc.
  const load = useLoad(() => fetchMySubmissions(page, PAGE_SIZE, { verdict, language }), [page, verdict, language, attempt]);
  const summary = useLoad(fetchMySubmissionSummary, [attempt]);

  const rows = load.state === "ok" ? load.data.content : [];
  const total = load.state === "ok" ? load.data.totalElements : 0;
  const totalPages = load.state === "ok" ? Math.max(1, load.data.totalPages) : 1;
  const languages = language && !LANGUAGES.includes(language) ? [...LANGUAGES, language] : LANGUAGES;
  const counts = useMemo(() => new Map(Object.entries(summary.state === "ok" ? summary.data : {})), [summary]);
  const summaryTotal = Array.from(counts.values()).reduce((a, b) => a + b, 0);
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
                <select className={p.select} value={verdict} onChange={(e) => set({ verdict: e.target.value || null, page: null })}>
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
                <select className={p.select} value={language} onChange={(e) => set({ language: e.target.value || null, page: null })}>
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
                (total === 0 && !filtered ? (
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
                  Showing {(page - 1) * PAGE_SIZE + 1}–{(page - 1) * PAGE_SIZE + rows.length} of {total}
                </span>
                <Pagination page={page} totalPages={totalPages} onChange={(np) => set({ page: String(np) })} />
              </div>
            )}
          </section>

          <aside className={p.aside} aria-label="Submission summary">
            <section>
              <h2>Summary</h2>
              {summary.state === "ok" ? (
                <>
                  <p className={p.score}>
                    <b>{counts.get("AC") ?? 0}</b> / {summaryTotal} accepted
                  </p>
                  <div className={s.summaryList}>
                    {summaryKeys.map((k) => (
                      <div key={k} className={s.summaryItem}>
                        <span>{labelOf(k)}</span>
                        <span>{counts.get(k) ?? 0}</span>
                      </div>
                    ))}
                  </div>
                </>
              ) : summary.state === "error" ? (
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
