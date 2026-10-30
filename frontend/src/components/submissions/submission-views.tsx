"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, Copy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Pagination } from "@/components/problems/pagination";
import { VerdictBadge } from "@/components/shared/verdict-badge";
import { CodeEditor } from "@/components/workspace/code-editor";
import { ResultPanel } from "@/components/workspace/result-panel";
import { useUrlState } from "@/hooks/use-url-state";
import { errorMessage } from "@/lib/api";
import { fmtDateTime, fmtMemory } from "@/lib/format";
import { fetchMySubmissions, fetchSubmission, fetchSubmissionCode, fetchSubmissionResults } from "@/lib/services";
import { FILE_NAME, LANGUAGES } from "@/lib/templates";
import type { CaseResult, Page, Submission } from "@/types/api";

const Note = ({ children, error }: { children: React.ReactNode; error?: boolean }) => (
  <p role={error ? "alert" : undefined} className={`p-10 text-center text-body-md ${error ? "text-destructive" : "text-mute"}`}>
    {children}
  </p>
);
const langLabel = (l?: string) => LANGUAGES.find((x) => x.value === l)?.label ?? l ?? "—";

/**
 * Số lần bài nộp bị chấm lại. Bài nộp bị chấm lại khi judge worker chết giữa
 * chừng và message trong Redis Stream được XCLAIM lại.
 */
const attemptsLabel = (retryCount?: number | null) => (retryCount != null && retryCount > 0 ? String(retryCount + 1) : "1");
const attemptsTitle = (retryCount?: number | null) =>
  retryCount != null && retryCount > 0
    ? `Bài nộp được chấm lại ${retryCount} lần do worker bị gián đoạn`
    : "Chấm lần đầu";

export function SubmissionsView() {
  const { sp, set } = useUrlState();
  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);
  const [res, setRes] = useState<Page<Submission> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let off = false;
    setError(null);
    fetchMySubmissions(page)
      .then((r) => {
        if (!off) setRes(r);
      })
      .catch((e) => {
        if (!off) setError(errorMessage(e));
      });
    return () => {
      off = true;
    };
  }, [page]);

  if (error) return <Note error>{error}</Note>;
  if (!res) return <Note>Loading submissions…</Note>;
  return (
    <div className="mx-auto max-w-5xl space-y-5 px-4 py-8 sm:px-6">
      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[640px] text-body-md">
          <thead>
            <tr className="border-b border-border text-left text-[11px] font-medium uppercase tracking-wider text-mute">
              <th className="px-5 py-3">Time</th>
              <th className="px-2 py-3">Problem</th>
              <th className="px-2 py-3">Language</th>
              <th className="px-2 py-3">Status</th>
              <th className="px-2 py-3">Runtime</th>
              <th className="px-2 py-3">Peak memory</th>
              <th className="px-2 py-3">Attempts</th>
            </tr>
          </thead>
          <tbody>
            {res.content.map((s) => (
              <tr key={s.id} className="border-b border-border last:border-b-0 transition-colors hover:bg-hairline-soft">
                <td className="px-5 py-3">
                  <Link href={`/submissions/${s.id}`} className="text-ink underline-offset-2 hover:underline">
                    {fmtDateTime(s.createdAt)}
                  </Link>
                </td>
                <td className="px-2 py-3 text-ink">
                  {s.problemTitle ?? `#${s.problemId}`}
                  {s.contestId && (
                    <span className="ml-2 rounded-full bg-hairline-soft px-2 py-0.5 text-body-sm text-body">Exam</span>
                  )}
                </td>
                <td className="px-2 py-3 text-body">{langLabel(s.language)}</td>
                <td className="px-2 py-3">
                  <VerdictBadge verdict={s.verdict} status={s.status} />
                </td>
                <td className="px-2 py-3 font-mono text-ink">
                  {s.executionTimeMs != null ? `${s.executionTimeMs} ms` : "—"}
                </td>
                <td className="px-2 py-3 font-mono text-ink">{fmtMemory(s.memoryUsedKb)}</td>
                <td className="px-2 py-3 font-mono text-ink" title={attemptsTitle(s.retryCount)}>
                  {attemptsLabel(s.retryCount)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {res.content.length === 0 && (
          <Note>
            You haven&apos;t submitted any solutions yet. Pick a problem from the{" "}
            <Link className="underline" href="/problems">
              problem list
            </Link>
            .
          </Note>
        )}
      </div>
      {res.totalPages > 1 && (
        <div className="flex justify-end">
          <Pagination page={page} totalPages={res.totalPages} onChange={(p) => set({ page: String(p) })} />
        </div>
      )}
    </div>
  );
}

export function SubmissionDetail({ id }: { id: number }) {
  const [sub, setSub] = useState<Submission | null>(null);
  const [code, setCode] = useState<string | null>(null);
  const [cases, setCases] = useState<CaseResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let off = false;
    (async () => {
      try {
        const [s, c] = await Promise.all([fetchSubmission(id), fetchSubmissionCode(id)]);
        if (off) return;
        setSub(s);
        setCode(c);
        fetchSubmissionResults(id)
          .then((r) => {
            if (!off) setCases(r);
          })
          .catch(() => undefined);
      } catch (e) {
        if (!off) setError(errorMessage(e));
      }
    })();
    return () => {
      off = true;
    };
  }, [id]);

  if (error) return <Note error>{error}</Note>;
  if (!sub || code == null) return <Note>Loading submission…</Note>;
  const lang = sub.language ?? "JAVA";
  return (
    <div className="mx-auto max-w-5xl space-y-5 px-4 py-8 sm:px-6">
      <Link
        href="/submissions"
        className="flex w-fit items-center gap-1.5 text-body-md text-body transition-colors hover:text-ink"
      >
        <ArrowLeft className="h-4 w-4" />
        Submissions
      </Link>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-heading-lg text-ink">{sub.problemTitle ?? `Problem #${sub.problemId}`}</h1>
        <VerdictBadge verdict={sub.verdict} status={sub.status} />
        <span className="text-body-md text-body">
          {langLabel(lang)} · {fmtDateTime(sub.createdAt)}
        </span>
        <Button asChild variant="outline" size="sm" className="ml-auto">
          <Link href={`/problems/${sub.problemId}`}>Open Problem</Link>
        </Button>
      </div>

      <Tabs defaultValue="result">
        <TabsList>
          <TabsTrigger value="result">Result</TabsTrigger>
          <TabsTrigger value="code">Source Code</TabsTrigger>
        </TabsList>
        <TabsContent value="result" className="mt-4 overflow-hidden rounded-xl border border-border bg-card">
          <ResultPanel result={sub} cases={cases} judging={false} timedOut={false} error={null} />
          {cases === null && sub.verdict && (
            <p className="px-5 pb-5 text-body-sm text-mute">Detailed testcase breakdown is not available from the server.</p>
          )}
        </TabsContent>
        <TabsContent value="code" className="mt-4 overflow-hidden rounded-xl border border-border bg-card">
          <div className="flex h-9 items-center justify-between border-b border-border px-3 font-mono text-body-sm text-mute">
            <span className="text-ink">{FILE_NAME[lang]}</span>
            <button
              className="flex items-center gap-1 transition-colors hover:text-ink"
              onClick={() => {
                void navigator.clipboard?.writeText(code);
                setCopied(true);
                setTimeout(() => setCopied(false), 1500);
              }}
            >
              <Copy className="h-3.5 w-3.5" />
              {copied ? "Copied" : "Copy"}
            </button>
          </div>
          <div className="h-[420px]">
            <CodeEditor language={lang} value={code} readOnly />
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
