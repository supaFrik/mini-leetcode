"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, Copy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { VerdictBadge } from "@/components/shared/verdict-badge";
import { CodeEditor } from "@/components/workspace/code-editor";
import { ResultPanel } from "@/components/workspace/result-panel";
import { errorMessage } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";
import { fetchSubmission, fetchSubmissionCode, fetchSubmissionResults } from "@/lib/services";
import { FILE_NAME, LANGUAGES } from "@/lib/templates";
import type { CaseResult, Submission } from "@/types/api";

const Note = ({ children, error }: { children: React.ReactNode; error?: boolean }) => (
  <p role={error ? "alert" : undefined} className={`p-10 text-center text-body-md ${error ? "text-destructive" : "text-mute"}`}>
    {children}
  </p>
);
const langLabel = (l?: string) => LANGUAGES.find((x) => x.value === l)?.label ?? l ?? "—";

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
