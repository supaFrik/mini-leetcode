"use client";
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { FileText, Pencil, Plus, Star, ThumbsDown, ThumbsUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { errorMessage } from "@/lib/api";
import { addTestCase, updateProblem } from "@/lib/services";
import type { ProblemFull, TestCase } from "@/types/api";

interface Props {
  problem: ProblemFull;
  cases: TestCase[];
  canEdit: boolean;
  onProblemChange: (p: ProblemFull) => void;
  onCasesChange: (c: TestCase[]) => void;
}

const field = "w-full rounded-md border border-input bg-transparent px-3 py-2 font-mono text-xs";

export function DescriptionPanel({ problem, cases, canEdit, onProblemChange, onCasesChange }: Props) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(problem.title);
  const [desc, setDesc] = useState(problem.description);
  const [adding, setAdding] = useState(false);
  const [newCase, setNewCase] = useState({ input: "", expectedOutput: "", isHidden: false });
  const [watch, setWatch] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const saveProblem = async () => {
    setBusy(true); setErr(null);
    try { onProblemChange({ ...problem, ...(await updateProblem(problem.id, { title, description: desc })), title, description: desc }); setEditing(false); }
    catch (e) { setErr(errorMessage(e)); }
    finally { setBusy(false); }
  };
  const saveCase = async () => {
    if (!newCase.input.trim() || !newCase.expectedOutput.trim()) { setErr("Cần nhập cả Input và Expected output."); return; }
    setBusy(true); setErr(null);
    try { onCasesChange([...cases, await addTestCase(problem.id, newCase)]); setNewCase({ input: "", expectedOutput: "", isHidden: false }); setAdding(false); }
    catch (e) { setErr(errorMessage(e)); }
    finally { setBusy(false); }
  };

  return (
    <div className="h-full overflow-auto">
      <div className="sticky top-0 z-10 border-b bg-card px-3 pt-2">
        <span className="inline-flex items-center gap-1.5 rounded-t-md bg-secondary px-3 py-1.5 text-sm"><FileText className="h-4 w-4 text-primary" />Description</span>
      </div>
      <div className="space-y-5 p-6">
        <div className="flex items-start justify-between gap-3">
          {editing
            ? <Input value={title} onChange={(e) => setTitle(e.target.value)} className="text-lg font-semibold" aria-label="Tiêu đề đề bài" />
            : <h1 className="text-2xl font-semibold leading-tight">{problem.title}</h1>}
          <div className="flex shrink-0 items-center gap-2">
            {canEdit && !editing && <button aria-label="Sửa đề bài" className="text-primary" onClick={() => { setTitle(problem.title); setDesc(problem.description); setEditing(true); }}><Pencil className="h-4 w-4" /></button>}
            <DifficultyBadge value={problem.difficulty} />
          </div>
        </div>

        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          {problem.likes != null && <span className="flex items-center gap-1"><ThumbsUp className="h-3.5 w-3.5" />{problem.likes}</span>}
          {problem.dislikes != null && <span className="flex items-center gap-1"><ThumbsDown className="h-3.5 w-3.5" />{problem.dislikes}</span>}
          <button className="flex items-center gap-1 hover:text-foreground" aria-pressed={watch} onClick={() => setWatch(!watch)}>
            <Star className={watch ? "h-3.5 w-3.5 fill-amber-400 text-amber-400" : "h-3.5 w-3.5"} />Watchlist
          </button>
          {canEdit && problem.status && <span className="rounded bg-secondary px-1.5 py-0.5 font-mono">{problem.status}</span>}
        </div>

        {editing ? (
          <div className="space-y-2">
            <textarea className={`${field} min-h-56`} value={desc} onChange={(e) => setDesc(e.target.value)} aria-label="Nội dung Markdown" />
            <div className="flex gap-2">
              <Button size="sm" onClick={saveProblem} disabled={busy}>Save changes</Button>
              <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>Cancel</Button>
            </div>
          </div>
        ) : (
          // react-markdown mặc định KHÔNG render HTML thô → an toàn trước XSS. Đừng thêm rehype-raw.
          <article className="prose prose-sm max-w-none dark:prose-invert prose-code:rounded prose-code:bg-secondary prose-code:px-1 prose-code:py-0.5 prose-code:font-mono prose-code:text-emerald-600 dark:prose-code:text-emerald-400 prose-code:before:content-none prose-code:after:content-none prose-pre:bg-secondary">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{problem.description}</ReactMarkdown>
          </article>
        )}

        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="flex items-center gap-2 text-sm font-semibold">Test Cases &amp; Examples</h2>
            {canEdit && <button className="flex items-center gap-1 text-xs text-primary" onClick={() => setAdding(!adding)}><Plus className="h-3.5 w-3.5" />Add Case</button>}
          </div>

          {adding && (
            <div className="space-y-2 rounded-lg border p-3">
              <textarea className={`${field} min-h-16`} placeholder="Input" value={newCase.input} onChange={(e) => setNewCase({ ...newCase, input: e.target.value })} aria-label="Input" />
              <textarea className={`${field} min-h-16`} placeholder="Expected output" value={newCase.expectedOutput} onChange={(e) => setNewCase({ ...newCase, expectedOutput: e.target.value })} aria-label="Expected output" />
              <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={newCase.isHidden} onChange={(e) => setNewCase({ ...newCase, isHidden: e.target.checked })} />Testcase ẩn (dùng để chấm điểm, thí sinh không thấy)</label>
              <div className="flex gap-2"><Button size="sm" onClick={saveCase} disabled={busy}>Add testcase</Button><Button size="sm" variant="ghost" onClick={() => setAdding(false)}>Cancel</Button></div>
            </div>
          )}
          {err && <p role="alert" className="text-xs text-destructive">{err}</p>}

          {cases.length === 0 && <p className="text-sm text-muted-foreground">Đề bài chưa có testcase công khai.</p>}
          {cases.map((c, i) => (
            <div key={c.id} className="space-y-1 rounded-lg bg-secondary p-3 font-mono text-xs">
              <p className="flex items-center gap-2 text-primary">Test Case {i + 1}{c.isHidden && <span className="rounded bg-background px-1.5 py-0.5 text-[10px] text-muted-foreground">hidden</span>}</p>
              {c.input === "" && c.expectedOutput === ""
                ? <p className="text-muted-foreground">Backend chưa trả nội dung testcase (mới có đường dẫn file).</p>
                : <>
                    <p><span className="text-muted-foreground">Input: </span><span className="whitespace-pre-wrap text-cyan-600 dark:text-cyan-300">{c.input}</span></p>
                    <p><span className="text-muted-foreground">Output: </span><span className="whitespace-pre-wrap">{c.expectedOutput}</span></p>
                  </>}
              <p className="text-muted-foreground">Explanation: {c.explanation ?? "N/A"}</p>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
