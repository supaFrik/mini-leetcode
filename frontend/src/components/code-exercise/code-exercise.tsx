"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { java } from "@codemirror/lang-java";
import { EditorView } from "@codemirror/view";
import { Check, Loader2, X } from "lucide-react";
import { Markdown } from "@/components/ui/markdown";
import { useJudge } from "@/hooks/use-judge";
import { errorMessage } from "@/lib/api";
import { fetchProblem, fetchTestCases } from "@/lib/services";
import { TEMPLATES } from "@/lib/templates";
import { cn } from "@/lib/utils";
import type { CaseResult, ProblemFull, Submission, TestCase } from "@/types/api";
import x from "./code-exercise.module.css";

/*
 * Bài code nhỏ làm ngay trong trang (specs/practice-ide-rich-editor.spec.md §2): một file Java, stdin/stdout,
 * chấm bằng judge như mọi bài khác. Đề nhiều file (OOP) dùng `multiFile` (vd nút Open in IntelliJ).
 */

const VERDICT: Record<string, string> = {
  AC: "Accepted", WA: "Wrong answer", TLE: "Time limit exceeded", MLE: "Memory limit exceeded",
  CE: "Compilation error", RE: "Runtime error", SV: "Not allowed", FLAGGED_CHEATING: "Flagged",
};

const draftKey = (problemId: number) => `exercise-draft:${problemId}`;
const readDraft = (problemId: number) => {
  try { return localStorage.getItem(draftKey(problemId)); } catch { return null; }
};
const writeDraft = (problemId: number, code: string) => {
  try { localStorage.setItem(draftKey(problemId), code); } catch { /* trình duyệt chặn lưu: bỏ qua */ }
};

export function CodeExercise({ problemSlug, passed, onSolved, showStatement = true, multiFile }: {
  problemSlug: string;
  passed: boolean;
  /** Gọi khi bài được Accepted (để trang cha tải lại tiến độ). */
  onSolved?: () => void;
  showStatement?: boolean;
  /** Hiện thay cho ô code khi đề là OOP nhiều file. */
  multiFile?: (problem: ProblemFull) => React.ReactNode;
}) {
  const [problem, setProblem] = useState<ProblemFull | null>(null);
  const [samples, setSamples] = useState<TestCase[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let off = false;
    setProblem(null);
    setLoadError(null);
    fetchProblem(problemSlug)
      .then(async (p) => {
        const cases = await fetchTestCases(p.id).catch(() => [] as TestCase[]);
        if (off) return;
        setProblem(p);
        setSamples(cases.filter((c) => !c.isHidden).sort((a, b) => a.orderIndex - b.orderIndex).slice(0, 3));
      })
      .catch((e) => !off && setLoadError(errorMessage(e)));
    return () => { off = true; };
  }, [problemSlug]);

  if (loadError) return <p className={x.error} role="alert">{loadError}</p>;
  if (!problem) return <p className={x.muted}><Loader2 size={14} className={x.spin} aria-hidden /> Loading exercise…</p>;

  return (
    <div className={x.exercise}>
      {showStatement && <div className={x.statement}><Markdown>{problem.description}</Markdown></div>}
      {samples.map((c, i) => (
        <div key={c.id} className={x.sample}>
          <h3>Sample Input {i + 1}:</h3>
          <pre aria-label={`Sample input ${i + 1}`}>{c.input || " "}</pre>
          <h3>Sample Output {i + 1}:</h3>
          <pre aria-label={`Sample output ${i + 1}`}>{c.expectedOutput || " "}</pre>
        </div>
      ))}
      {problem.type === "OOP_GRINDING" && multiFile
        ? multiFile(problem)
        : <Editor problem={problem} passed={passed} onSolved={onSolved} />}
    </div>
  );
}

function Editor({ problem, passed, onSolved }: { problem: ProblemFull; passed: boolean; onSolved?: () => void }) {
  const [code, setCode] = useState(() => readDraft(problem.id) ?? TEMPLATES.JAVA);
  const { result, cases, judging, timedOut, error, submit } = useJudge(problem.id);
  const extensions = useMemo(() => [java(), EditorView.contentAttributes.of({ "aria-label": "Code editor" })], []);

  useEffect(() => { writeDraft(problem.id, code); }, [problem.id, code]);
  // Báo Accepted đúng một lần cho mỗi bài nộp (onSolved có thể đổi identity mỗi lần render).
  const notified = useRef<number | null>(null);
  useEffect(() => {
    if (result?.status !== "COMPLETED" || result.verdict !== "AC" || notified.current === result.id) return;
    notified.current = result.id;
    onSolved?.();
  }, [result, onSolved]);

  return (
    <div className={x.work}>
      <div className={x.workHead}>
        <span>Write a program in Java</span>
        {passed && <span className={x.solved}><Check size={15} aria-hidden /> Solved</span>}
      </div>
      <div className={x.editor}>
        <CodeMirror value={code} onChange={setCode} extensions={extensions} minHeight="180px" maxHeight="480px"
          basicSetup={{ lineNumbers: true, foldGutter: false, highlightActiveLine: true, autocompletion: false }}
          editable={!judging} />
      </div>
      <div className={x.actions}>
        <button type="button" className={x.submit} disabled={judging || !code.trim()} onClick={() => submit("JAVA", code)}>
          {judging ? <><Loader2 size={15} className={x.spin} aria-hidden /> Checking…</> : "Submit"}
        </button>
        <button type="button" className={x.reset} disabled={judging} onClick={() => setCode(TEMPLATES.JAVA)}>Reset code</button>
      </div>
      <Verdict result={result} cases={cases} judging={judging} timedOut={timedOut} error={error?.message ?? null} />
    </div>
  );
}

function Verdict({ result, cases, judging, timedOut, error }: {
  result: Submission | null; cases: CaseResult[] | null; judging: boolean; timedOut: boolean; error: string | null;
}) {
  if (error) return <div className={cn(x.result, x.bad)} role="status">{error}</div>;
  if (timedOut) return <div className={cn(x.result, x.bad)} role="status">The checker is busy. Try again in a minute.</div>;
  if (!result || judging || (result.status !== "COMPLETED" && result.status !== "FAILED")) return null;
  const ok = result.verdict === "AC";
  const failed = cases?.find((c) => c.verdict !== "AC" && c.verdict !== "SKIPPED");
  return (
    <div className={cn(x.result, ok ? x.ok : x.bad)} role="status" aria-live="polite">
      <strong>{ok ? <><Check size={16} aria-hidden /> Correct!</> : <><X size={16} aria-hidden /> {VERDICT[result.verdict ?? ""] ?? "Failed"}</>}</strong>
      {!ok && result.verdict === "CE" && result.errorMessage && <pre className={x.log}>{result.errorMessage}</pre>}
      {!ok && result.verdict !== "CE" && (
        failed && !failed.hidden ? (
          <div className={x.diff}>
            <span>Failed on test {failed.index + 1}</span>
            {failed.input !== undefined && <><h4>Input</h4><pre>{failed.input}</pre></>}
            {failed.expected !== undefined && <><h4>Expected</h4><pre>{failed.expected}</pre></>}
            {failed.actual !== undefined && <><h4>Your output</h4><pre>{failed.actual}</pre></>}
          </div>
        ) : (
          <span>{result.passTestCases}/{result.totalTestCases} tests passed{failed ? ` · failed on hidden test ${failed.index + 1}` : ""}.</span>
        )
      )}
      {!ok && result.verdict === "RE" && result.errorMessage && <pre className={x.log}>{result.errorMessage}</pre>}
    </div>
  );
}
