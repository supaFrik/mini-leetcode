"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { java } from "@codemirror/lang-java";
import { EditorView } from "@codemirror/view";
import { Check, FileCode, Loader2, Play, Plug, Plus, X } from "lucide-react";
import { Markdown } from "@/components/ui/markdown";
import { useJudge } from "@/hooks/use-judge";
import { errorMessage } from "@/lib/api";
import { fetchProblem, fetchTestCases } from "@/lib/services";
import { parseStarterFiles, starterCodes } from "@/lib/templates";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import type { CaseResult, ProblemFull, Submission, SubmissionFile, TestCase } from "@/types/api";
import x from "./code-exercise.module.css";

/*
 * Bài code nhỏ làm ngay trong trang (specs/practice-ide-rich-editor.spec.md & specs/study-plan-web-oop-editor.spec.md):
 * - Bài một file (DSA / Stdin-stdout): CodeMirror nhỏ, Run/Submit.
 * - Bài nhiều file (OOP Grinding): Multi-file editor với các tab Java file, thêm/xoá file, Run/Submit,
 *   chấm điểm kèm theo luật OOP và kết nối tùy chọn tới IntelliJ.
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

const multiDraftKey = (problemId: number) => `exercise-draft-files:${problemId}`;
const readMultiDraft = (problemId: number): SubmissionFile[] | null => {
  try {
    const raw = localStorage.getItem(multiDraftKey(problemId));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed.filter((f): f is SubmissionFile => Boolean(f && typeof f.path === "string" && typeof f.content === "string"));
    }
  } catch {
    /* ignore parse error */
  }
  return null;
};
const writeMultiDraft = (problemId: number, files: SubmissionFile[]) => {
  try {
    localStorage.setItem(multiDraftKey(problemId), JSON.stringify(files));
  } catch {
    /* ignore */
  }
};

export function CodeExercise({
  problemSlug,
  passed,
  onSolved,
  showStatement = true,
  multiFile,
  ideAction,
}: {
  problemSlug: string;
  passed: boolean;
  /** Gọi khi bài được Accepted (để trang cha tải lại tiến độ). */
  onSolved?: () => void;
  showStatement?: boolean;
  /** Hiện thay cho ô code khi đề là OOP nhiều file (nếu được truyền thủ công). */
  multiFile?: (problem: ProblemFull) => React.ReactNode;
  /** Nút kết nối IDE đặt kèm dưới editor OOP. */
  ideAction?: React.ReactNode;
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
      {problem.type === "OOP_GRINDING"
        ? (multiFile ? multiFile(problem) : <MultiFileEditor problem={problem} passed={passed} onSolved={onSolved} ideAction={ideAction} />)
        : <Editor problem={problem} passed={passed} onSolved={onSolved} />}
    </div>
  );
}

function MultiFileEditor({
  problem,
  passed,
  onSolved,
  ideAction,
}: {
  problem: ProblemFull;
  passed: boolean;
  onSolved?: () => void;
  ideAction?: React.ReactNode;
}) {
  const initialFiles = useMemo(() => parseStarterFiles(problem.starterTemplates), [problem.starterTemplates]);
  const [files, setFiles] = useState<SubmissionFile[]>(() => {
    const draft = readMultiDraft(problem.id);
    if (draft && draft.length > 0) return draft;
    return initialFiles;
  });
  const [activeIdx, setActiveIdx] = useState(0);
  const [isRunMode, setIsRunMode] = useState(false);

  const idx = Math.min(activeIdx, Math.max(0, files.length - 1));
  const activeFile = files[idx];

  const { result, cases, judging, timedOut, error, submit } = useJudge(problem.id);
  const extensions = useMemo(() => [java(), EditorView.contentAttributes.of({ "aria-label": "Code editor" })], []);

  useEffect(() => {
    writeMultiDraft(problem.id, files);
  }, [problem.id, files]);

  // Báo Accepted đúng một lần cho mỗi bài nộp chính thức (không phải run mode)
  const notified = useRef<number | null>(null);
  useEffect(() => {
    if (isRunMode || result?.status !== "COMPLETED" || result.verdict !== "AC" || notified.current === result.id) return;
    notified.current = result.id;
    onSolved?.();
  }, [isRunMode, result, onSolved]);

  const updateActiveContent = (content: string) => {
    setFiles((prev) => prev.map((f, i) => (i === idx ? { ...f, content } : f)));
  };

  const handleAddFile = () => {
    const name = window.prompt("Enter new Java class name (e.g. Helper.java):", "NewClass.java");
    if (!name || !name.trim()) return;
    const cleanName = name.trim().endsWith(".java") ? name.trim() : `${name.trim()}.java`;
    if (files.some((f) => f.path.toLowerCase() === cleanName.toLowerCase())) {
      toast("A file with that name already exists.", "error");
      return;
    }
    const className = cleanName.replace(/^.*\//, "").replace(/\.java$/, "");
    const initialContent = `public class ${className} {\n    \n}\n`;
    setFiles((prev) => [...prev, { path: cleanName, content: initialContent }]);
    setActiveIdx(files.length);
  };

  const handleDeleteFile = (fileIdx: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (files.length <= 1) {
      toast("Cannot delete the only file in the workspace.", "error");
      return;
    }
    const targetFile = files[fileIdx];
    if (!window.confirm(`Delete ${targetFile.path}?`)) return;
    setFiles((prev) => prev.filter((_, i) => i !== fileIdx));
    if (activeIdx >= fileIdx && activeIdx > 0) {
      setActiveIdx(activeIdx - 1);
    }
  };

  const handleReset = () => {
    if (!window.confirm("Reset all files to original starter code? Any unsaved edits will be lost.")) return;
    setFiles(initialFiles.map((f) => ({ ...f })));
    setActiveIdx(0);
    try {
      localStorage.removeItem(multiDraftKey(problem.id));
    } catch {
      /* ignore */
    }
    toast("Reset code to starter templates.", "info");
  };

  const handleRun = () => {
    setIsRunMode(true);
    void submit("JAVA", files, { run: true });
  };

  const handleSubmit = () => {
    setIsRunMode(false);
    void submit("JAVA", files);
  };

  const hasCode = files.some((f) => f.content.trim().length > 0);

  return (
    <div className={x.work}>
      <div className={x.workHead}>
        <span>Write a multi-file Java program</span>
        {passed && <span className={x.solved}><Check size={15} aria-hidden /> Solved</span>}
      </div>

      <div className={x.tabsContainer}>
        <div role="tablist" aria-label="Java files" className={x.fileTabsBar}>
          {files.map((f, i) => (
            <button
              key={f.path}
              type="button"
              role="tab"
              aria-selected={i === idx}
              className={cn(x.fileTab, i === idx && x.fileTabActive)}
              onClick={() => setActiveIdx(i)}
            >
              <FileCode size={13} className={x.fileIcon} aria-hidden />
              <span>{f.path}</span>
              {files.length > 1 && (
                <span
                  role="button"
                  tabIndex={0}
                  className={x.deleteTab}
                  title={`Delete ${f.path}`}
                  onClick={(e) => handleDeleteFile(i, e)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      handleDeleteFile(i, e as unknown as React.MouseEvent);
                    }
                  }}
                >
                  <X size={11} aria-hidden />
                </span>
              )}
            </button>
          ))}

          <button
            type="button"
            className={x.addFileBtn}
            onClick={handleAddFile}
            title="Add a new Java class file"
          >
            <Plus size={13} aria-hidden />
            Add Java file
          </button>
        </div>

        <div className={x.editor}>
          <CodeMirror
            key={activeFile?.path ?? "editor"}
            value={activeFile?.content ?? ""}
            onChange={updateActiveContent}
            extensions={extensions}
            minHeight="240px"
            maxHeight="520px"
            basicSetup={{ lineNumbers: true, foldGutter: false, highlightActiveLine: true, autocompletion: false }}
            editable={!judging}
          />
        </div>
      </div>

      <div className={x.actions}>
        <button
          type="button"
          className={x.run}
          disabled={judging || !hasCode}
          onClick={handleRun}
          title="Run solution with public sample testcases"
        >
          {judging && isRunMode ? (
            <><Loader2 size={15} className={x.spin} aria-hidden /> Running…</>
          ) : (
            <><Play size={14} aria-hidden /> Run</>
          )}
        </button>

        <button
          type="button"
          className={x.submit}
          disabled={judging || !hasCode}
          onClick={handleSubmit}
          title="Submit solution for full grading against all tests and OOP rules"
        >
          {judging && !isRunMode ? (
            <><Loader2 size={15} className={x.spin} aria-hidden /> Checking…</>
          ) : (
            "Submit"
          )}
        </button>

        <button
          type="button"
          className={x.reset}
          disabled={judging}
          onClick={handleReset}
        >
          Reset code
        </button>
      </div>

      {ideAction && (
        <div className={x.ideToolbar}>
          <div className={x.ideToolbarLabel}>
            <Plug size={15} aria-hidden />
            <span>Prefer desktop IDE?</span>
          </div>
          <div>{ideAction}</div>
          <Link href={`/problems/${problem.slug}`} className={x.reset} style={{ marginLeft: "auto", fontSize: "12px" }}>
            Prefer the browser? Solve it in the web workspace
          </Link>
        </div>
      )}

      <Verdict
        result={result}
        cases={cases}
        judging={judging}
        timedOut={timedOut}
        error={error?.message ?? null}
        runMode={isRunMode}
      />
    </div>
  );
}

function Editor({ problem, passed, onSolved }: { problem: ProblemFull; passed: boolean; onSolved?: () => void }) {
  const [code, setCode] = useState(() => readDraft(problem.id) ?? starterCodes(problem.starterTemplates).JAVA);
  const [isRunMode, setIsRunMode] = useState(false);
  const { result, cases, judging, timedOut, error, submit } = useJudge(problem.id);
  const extensions = useMemo(() => [java(), EditorView.contentAttributes.of({ "aria-label": "Code editor" })], []);

  useEffect(() => { writeDraft(problem.id, code); }, [problem.id, code]);
  // Báo Accepted đúng một lần cho mỗi bài nộp (onSolved có thể đổi identity mỗi lần render).
  const notified = useRef<number | null>(null);
  useEffect(() => {
    if (isRunMode || result?.status !== "COMPLETED" || result.verdict !== "AC" || notified.current === result.id) return;
    notified.current = result.id;
    onSolved?.();
  }, [isRunMode, result, onSolved]);

  const handleRun = () => {
    setIsRunMode(true);
    void submit("JAVA", code, { run: true });
  };

  const handleSubmit = () => {
    setIsRunMode(false);
    void submit("JAVA", code);
  };

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
        <button
          type="button"
          className={x.run}
          disabled={judging || !code.trim()}
          onClick={handleRun}
          title="Run solution with public sample testcases"
        >
          {judging && isRunMode ? (
            <><Loader2 size={15} className={x.spin} aria-hidden /> Running…</>
          ) : (
            <><Play size={14} aria-hidden /> Run</>
          )}
        </button>
        <button
          type="button"
          className={x.submit}
          disabled={judging || !code.trim()}
          onClick={handleSubmit}
        >
          {judging && !isRunMode ? <><Loader2 size={15} className={x.spin} aria-hidden /> Checking…</> : "Submit"}
        </button>
        <button type="button" className={x.reset} disabled={judging} onClick={() => setCode(starterCodes(problem.starterTemplates).JAVA)}>Reset code</button>
      </div>
      <Verdict result={result} cases={cases} judging={judging} timedOut={timedOut} error={error?.message ?? null} runMode={isRunMode} />
    </div>
  );
}

function Verdict({ result, cases, judging, timedOut, error, runMode }: {
  result: Submission | null;
  cases: CaseResult[] | null;
  judging: boolean;
  timedOut: boolean;
  error: string | null;
  runMode?: boolean;
}) {
  if (error) return <div className={cn(x.result, x.bad)} role="status">{error}</div>;
  if (timedOut) return <div className={cn(x.result, x.bad)} role="status">The checker is busy. Try again in a minute.</div>;
  if (!result || judging || (result.status !== "COMPLETED" && result.status !== "FAILED")) return null;
  const ok = result.verdict === "AC";
  const failed = cases?.find((c) => c.verdict !== "AC" && c.verdict !== "SKIPPED");
  const hasScore = result.score != null;

  return (
    <div className={cn(x.result, ok ? x.ok : x.bad)} role="status" aria-live="polite">
      {runMode && (
        <span className={x.runNotice}>
          Run mode: evaluated on public sample testcases
        </span>
      )}
      <div>
        <strong>
          {ok ? <><Check size={16} aria-hidden /> Correct!</> : <><X size={16} aria-hidden /> {VERDICT[result.verdict ?? ""] ?? "Failed"}</>}
        </strong>
        {hasScore && (
          <span
            className={cn(
              x.scoreBadge,
              (result.score ?? 0) >= 100
                ? x.scoreBadgeOk
                : (result.score ?? 0) > 0
                ? x.scoreBadgePartial
                : x.scoreBadgeZero
            )}
          >
            Score: {(result.score ?? 0).toFixed(1)} / 100.0
          </span>
        )}
      </div>

      {!ok && result.errorMessage && (
        <pre className={x.log}>{result.errorMessage}</pre>
      )}

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
    </div>
  );
}
