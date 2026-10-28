"use client";
import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { CodeEditor } from "./code-editor";
import { ResultPanel } from "./result-panel";
import { api, USE_MOCK } from "@/lib/api";
import { pollSubmission } from "@/lib/polling";
import { FILE_NAME, TEMPLATES } from "@/lib/templates";
import type { Difficulty, Language, Submission } from "@/types/api";

export interface WorkspaceProblem { id: number; title: string; difficulty: Difficulty; descriptionMd: string }
interface Props { problem: WorkspaceProblem; mode: "student" | "instructor" | "exam"; contestId?: number }

const draftKey = (pid: number, lang: Language) => `draft:${pid}:${lang}`;

export function Workspace({ problem, mode, contestId }: Props) {
  const [lang, setLang] = useState<Language>("JAVA");
  const [codes, setCodes] = useState<Record<Language, string>>({ ...TEMPLATES });
  const [result, setResult] = useState<Submission | null>(null);
  const [judging, setJudging] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  // Khôi phục bản nháp (chỉ chạy ở trình duyệt)
  useEffect(() => {
    try {
      const saved = localStorage.getItem(draftKey(problem.id, lang));
      if (saved) setCodes((c) => ({ ...c, [lang]: JSON.parse(saved).value }));
    } catch {}
  }, [problem.id, lang]);

  const save = () => {
    try { localStorage.setItem(draftKey(problem.id, lang), JSON.stringify({ value: codes[lang], savedAt: Date.now() })); } catch {}
  };

  const submit = async () => {
    abortRef.current?.abort();
    const ctrl = (abortRef.current = new AbortController());
    setJudging(true); setResult(null);
    try {
      if (USE_MOCK) {                                   // giả lập 2 giây rồi trả kết quả
        await new Promise((r) => setTimeout(r, 2000));
        setResult({ id: 1, problemId: problem.id, status: "COMPLETED", verdict: "WA",
          executionTimeMs: 85, memoryUsedKb: 32768, passTestCases: 2, totalTestCases: 3, errorMessage: null });
        return;
      }
      const created = await api<Submission>(`/api/v1/problems/${problem.id}/submit`, {
        method: "POST",
        body: JSON.stringify({ language: lang, code: codes[lang], ...(contestId ? { contestId } : {}) }),
      });
      setResult(created);                               // status = PENDING
      await pollSubmission(created.id, { signal: ctrl.signal, onUpdate: setResult });
    } catch (e) {
      // 400: code rỗng/quá 64KB – 403: bị truất quyền thi / contest hết giờ → hiện thông điệp e.message
    } finally {
      setJudging(false);
    }
  };

  useEffect(() => () => abortRef.current?.abort(), []);  // rời trang thì dừng polling

  return (
    // 3.5rem = header (h-14), 3rem = footer (h-12)
    <div className="flex h-[calc(100vh-3.5rem-3rem)] flex-col">
      {/* Thanh công cụ */}
      <div className="flex h-12 items-center gap-3 border-b px-4">
        <span className="font-semibold">#{problem.id} {problem.title}</span>
        <DifficultyBadge value={problem.difficulty} />
        <select value={lang} onChange={(e) => setLang(e.target.value as Language)}
          className="rounded-md border bg-card px-2 py-1 text-sm">
          <option value="JAVA">Java</option><option value="PYTHON">Python</option><option value="CPP">C++</option>
        </select>
        <div className="ml-auto flex items-center gap-2">
          {mode === "student" && (<>
            <Button variant="outline" size="sm" onClick={save}>Save</Button>
            <Button variant="outline" size="sm" onClick={submit}>Run</Button>
          </>)}
          {mode === "instructor" && (<>
            <Button variant="outline" size="sm" onClick={save}>Save</Button>
            <Button variant="outline" size="sm">Preview</Button>
            <Button size="sm">Publish Problem</Button>
          </>)}
          {mode !== "instructor" && <Button size="sm" onClick={submit} disabled={judging}>Submit Solution</Button>}
        </div>
      </div>

      {/* Chia đôi màn hình */}
      <ResizablePanelGroup direction="horizontal" className="min-h-0 flex-1 gap-1 p-2">
        <ResizablePanel defaultSize={40} minSize={25} className="overflow-auto rounded-lg border bg-card p-6">
          <article className="prose prose-invert max-w-none prose-pre:bg-secondary">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{problem.descriptionMd}</ReactMarkdown>
          </article>
        </ResizablePanel>
        <ResizableHandle withHandle />
        <ResizablePanel defaultSize={60} minSize={35}>
          <ResizablePanelGroup direction="vertical" className="gap-1">
            <ResizablePanel defaultSize={62} className="overflow-hidden rounded-lg border bg-card">
              <div className="flex h-9 items-center gap-2 border-b px-3 font-mono text-xs text-muted-foreground">
                {FILE_NAME[lang]} <span>·</span> Auto-saved
              </div>
              <div className="h-[calc(100%-2.25rem)]">
                <CodeEditor language={lang} value={codes[lang]} readOnly={false}
                  onChange={(v) => setCodes((c) => ({ ...c, [lang]: v }))}
                  onSave={save} onRun={submit} onSubmit={submit} examMode={mode === "exam"} />
              </div>
            </ResizablePanel>
            <ResizableHandle />
            <ResizablePanel defaultSize={38} className="overflow-auto rounded-lg border bg-card">
              <Tabs defaultValue="result">
                <TabsList className="m-2"><TabsTrigger value="case">Testcase</TabsTrigger><TabsTrigger value="result">Test Result</TabsTrigger></TabsList>
                <TabsContent value="case" className="p-4 text-sm text-muted-foreground">Testcase công khai hiển thị ở đây.</TabsContent>
                <TabsContent value="result"><ResultPanel result={result} judging={judging} /></TabsContent>
              </Tabs>
            </ResizablePanel>
          </ResizablePanelGroup>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  );
}