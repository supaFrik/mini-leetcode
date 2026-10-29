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

const Note = ({ children, error }: { children: React.ReactNode; error?: boolean }) => <p role={error ? "alert" : undefined} className={`p-8 text-center text-sm ${error ? "text-destructive" : "text-muted-foreground"}`}>{children}</p>;
const langLabel = (l?: string) => LANGUAGES.find((x) => x.value === l)?.label ?? l ?? "—";

// ---------- Lịch sử nộp bài ----------
export function SubmissionsView() {
  const { sp, set } = useUrlState();
  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);
  const [res, setRes] = useState<Page<Submission> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let off = false;
    setError(null);
    fetchMySubmissions(page).then((r) => { if (!off) setRes(r); }).catch((e) => { if (!off) setError(errorMessage(e)); });
    return () => { off = true; };
  }, [page]);

  if (error) return <Note error>{error}</Note>;
  if (!res) return <Note>Đang tải lịch sử nộp bài…</Note>;
  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">Bài đã nộp</h1>
      <div className="overflow-x-auto rounded-lg border bg-card">
        <table className="w-full min-w-[640px] text-sm">
          <thead><tr className="border-b text-left font-mono text-xs text-muted-foreground">
            <th className="px-4 py-3">Thời gian</th><th className="px-2 py-3">Đề bài</th><th className="px-2 py-3">Ngôn ngữ</th><th className="px-2 py-3">Kết quả</th><th className="px-2 py-3">Runtime</th><th className="px-2 py-3">Memory</th>
          </tr></thead>
          <tbody>
            {res.content.map((s) => (
              <tr key={s.id} className="border-b last:border-b-0 hover:bg-accent/40">
                <td className="px-4 py-3"><Link href={`/submissions/${s.id}`} className="underline-offset-2 hover:underline">{fmtDateTime(s.createdAt)}</Link></td>
                <td className="px-2 py-3">{s.problemTitle ?? `#${s.problemId}`}{s.contestId && <span className="ml-2 rounded bg-secondary px-1.5 py-0.5 text-xs">Exam</span>}</td>
                <td className="px-2 py-3">{langLabel(s.language)}</td>
                <td className="px-2 py-3"><VerdictBadge verdict={s.verdict} status={s.status} /></td>
                <td className="px-2 py-3 font-mono">{s.executionTimeMs != null ? `${s.executionTimeMs} ms` : "—"}</td>
                <td className="px-2 py-3 font-mono">{fmtMemory(s.memoryUsedKb)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {res.content.length === 0 && <Note>Bạn chưa nộp bài nào. Hãy chọn một đề trong <Link className="underline" href="/problems">danh sách bài tập</Link>.</Note>}
      </div>
      {res.totalPages > 1 && <div className="flex justify-end"><Pagination page={page} totalPages={res.totalPages} onChange={(p) => set({ page: String(p) })} /></div>}
    </div>
  );
}

// ---------- Chi tiết bài nộp: xem lại code + diff testcase công khai ----------
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
        setSub(s); setCode(c);
        fetchSubmissionResults(id).then((r) => { if (!off) setCases(r); }).catch(() => undefined);
      } catch (e) { if (!off) setError(errorMessage(e)); }
    })();
    return () => { off = true; };
  }, [id]);

  if (error) return <Note error>{error}</Note>;
  if (!sub || code == null) return <Note>Đang tải bài nộp…</Note>;
  const lang = sub.language ?? "JAVA";
  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <Link href="/submissions" className="flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />Bài đã nộp</Link>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{sub.problemTitle ?? `Đề #${sub.problemId}`}</h1>
        <VerdictBadge verdict={sub.verdict} status={sub.status} />
        <span className="text-sm text-muted-foreground">{langLabel(lang)} · {fmtDateTime(sub.createdAt)}</span>
        <Button asChild variant="outline" size="sm" className="ml-auto"><Link href={`/problems/${sub.problemId}`}>Mở đề bài</Link></Button>
      </div>

      <Tabs defaultValue="result">
        <TabsList><TabsTrigger value="result">Kết quả</TabsTrigger><TabsTrigger value="code">Mã nguồn</TabsTrigger></TabsList>
        <TabsContent value="result" className="mt-3 rounded-lg border bg-card">
          <ResultPanel result={sub} cases={cases} judging={false} timedOut={false} error={null} />
          {cases === null && sub.verdict && <p className="px-4 pb-4 text-xs text-muted-foreground">Chưa có dữ liệu chi tiết từng testcase từ máy chủ.</p>}
        </TabsContent>
        <TabsContent value="code" className="mt-3 overflow-hidden rounded-lg border bg-card">
          <div className="flex h-9 items-center justify-between border-b px-3 font-mono text-xs text-muted-foreground">
            <span>{FILE_NAME[lang]}</span>
            <button className="flex items-center gap-1 hover:text-foreground" onClick={() => { void navigator.clipboard?.writeText(code); setCopied(true); setTimeout(() => setCopied(false), 1500); }}><Copy className="h-3.5 w-3.5" />{copied ? "Đã sao chép" : "Sao chép"}</button>
          </div>
          <div className="h-[420px]"><CodeEditor language={lang} value={code} readOnly /></div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
