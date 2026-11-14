"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  FileCode,
  Loader2,
  Pencil,
  Play,
  Plus,
  Sparkles,
  Trash2,
  Wand2,
  X,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ConsolePanel } from "@/components/workspace/console-panel";
import { ApiError, errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import { useConfirm } from "@/components/ui/confirm";
import {
  addTestCase,
  deleteTestCase,
  fetchTestCases,
  runReference,
  saveGradingConfig,
  saveReferenceSolution,
  updateTestCase,
  validateGradingConfig,
} from "@/lib/services";
import type {
  GradingValidation,
  ProblemAuthoring,
  ReferenceCaseResult,
  ReferenceCaseStatus,
  ReferenceRunResult,
  SubmissionFile,
  TestCase,
} from "@/types/api";

/* ------------------------------------------------------------------ *
 * Shared bits
 * ------------------------------------------------------------------ */

const STATUS_LABEL: Record<ReferenceCaseStatus, string> = {
  MATCH: "Matches",
  DIFF: "Differs",
  OK: "Ran",
  TIME_LIMIT: "Time limit",
  MEMORY_LIMIT: "Memory limit",
  RUNTIME_ERROR: "Runtime error",
  OUTPUT_LIMIT: "Output limit",
};

const STATUS_TONE: Record<ReferenceCaseStatus, "success" | "warning" | "destructive" | "secondary"> = {
  MATCH: "success",
  DIFF: "warning",
  OK: "secondary",
  TIME_LIMIT: "destructive",
  MEMORY_LIMIT: "destructive",
  RUNTIME_ERROR: "destructive",
  OUTPUT_LIMIT: "destructive",
};

const isFailure = (s: ReferenceCaseStatus) => STATUS_TONE[s] === "destructive";

function FrozenNotice({ what }: { what: string }) {
  return (
    <p className="flex items-start gap-2 rounded-sm border border-warning/30 bg-warning/10 px-3 py-2 text-body-sm text-ink">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
      <span>
        This problem is <strong>Published</strong>, so its {what} are locked. Move it back to Draft (via Archived) to
        change them.
      </span>
    </p>
  );
}

function ErrorText({ children }: { children: React.ReactNode }) {
  return (
    <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-sm text-destructive">
      {children}
    </p>
  );
}

/** Lỗi build / sandbox của một lần chạy lời giải mẫu, hoặc null nếu chạy được. */
function runProblem(r: ReferenceRunResult): string | null {
  if (!r.compiled) return r.compileOutput || r.error || "The reference solution did not compile.";
  return r.error;
}

/* ------------------------------------------------------------------ *
 * Multi-file Java editor — controlled, so the parent owns the files.
 * ------------------------------------------------------------------ */

export function SourceFilesEditor({
  label,
  files,
  onChange,
  readOnly = false,
  rows = 10,
}: {
  label: string;
  files: SubmissionFile[];
  onChange: (files: SubmissionFile[]) => void;
  readOnly?: boolean;
  rows?: number;
}) {
  const [activeIdx, setActiveIdx] = useState(0);
  const idx = Math.min(activeIdx, Math.max(0, files.length - 1));
  const active = files[idx];

  const addFile = () => {
    const name = window.prompt("New Java file name (e.g. Account.java or bank/Account.java):", "NewClass.java");
    if (!name || !name.trim()) return;
    const path = name.trim().endsWith(".java") ? name.trim() : `${name.trim()}.java`;
    if (files.some((f) => f.path.toLowerCase() === path.toLowerCase())) {
      toast("A file with that name already exists.", "error");
      return;
    }
    const className = path.replace(/^.*\//, "").replace(/\.java$/, "");
    onChange([...files, { path, content: `public class ${className} {\n    \n}\n` }]);
    setActiveIdx(files.length);
  };

  const ask = useConfirm();
  const removeFile = async (i: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!(await ask({ title: `Delete ${files[i].path}?`, action: "Delete", destructive: true }))) return;
    onChange(files.filter((_, j) => j !== i));
    if (idx >= i && idx > 0) setActiveIdx(idx - 1);
  };

  const setContent = (content: string) => onChange(files.map((f, j) => (j === idx ? { ...f, content } : f)));

  return (
    <div className="space-y-3 rounded-sm border border-border bg-card p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="eyebrow block text-mute">{label}</span>
        {!readOnly && (
          <Button type="button" variant="outline" size="sm" onClick={addFile} className="h-7 gap-1 text-xs">
            <Plus className="h-3.5 w-3.5" />
            Add Java file
          </Button>
        )}
      </div>

      {files.length === 0 ? (
        <p className="text-body-sm text-mute">No files yet.</p>
      ) : (
        <>
          <div role="tablist" aria-label={`${label} files`} className="flex items-center gap-1 overflow-x-auto border-b border-border pb-1">
            {files.map((f, i) => (
              <div
                key={f.path}
                role="tab"
                aria-selected={i === idx}
                tabIndex={0}
                onClick={() => setActiveIdx(i)}
                onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && setActiveIdx(i)}
                className={cn(
                  "group flex cursor-pointer items-center gap-1.5 rounded px-2.5 py-1 font-mono text-xs transition-colors",
                  i === idx
                    ? "border border-border bg-background font-medium text-ink shadow-sm"
                    : "text-mute hover:bg-hairline-soft hover:text-ink",
                )}
              >
                <FileCode className="h-3.5 w-3.5 text-link" />
                <span>{f.path}</span>
                {!readOnly && files.length > 1 && (
                  <button
                    type="button"
                    onClick={(e) => removeFile(i, e)}
                    aria-label={`Delete ${f.path}`}
                    className="ml-1 rounded p-0.5 text-mute opacity-0 transition-opacity hover:bg-destructive/10 hover:text-destructive focus:opacity-100 group-hover:opacity-100"
                  >
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
            ))}
          </div>
          {active && (
            <Textarea
              aria-label={`Contents of ${active.path}`}
              value={active.content}
              readOnly={readOnly}
              onChange={(e) => setContent(e.target.value)}
              onKeyDown={(e) => {
                // Tab chèn 4 dấu cách thay vì rời ô soạn thảo.
                if (e.key !== "Tab" || readOnly) return;
                e.preventDefault();
                const el = e.currentTarget;
                const { selectionStart: s, selectionEnd: end } = el;
                setContent(el.value.slice(0, s) + "    " + el.value.slice(end));
                requestAnimationFrame(() => el.setSelectionRange(s + 4, s + 4));
              }}
              rows={rows}
              spellCheck={false}
              className="rounded-sm bg-background font-mono text-xs text-ink"
            />
          )}
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Test cases — stdin → expected stdout, generated from the reference.
 * ------------------------------------------------------------------ */

interface CaseForm {
  input: string;
  expectedOutput: string;
  isHidden: boolean;
  inputLocked: boolean;
  outputLocked: boolean;
}

function CaseEditor({
  problemId,
  initial,
  hasReference,
  onCancel,
  onSave,
}: {
  problemId: number;
  initial: CaseForm;
  hasReference: boolean;
  onCancel: () => void;
  onSave: (form: CaseForm) => Promise<void>;
}) {
  const [form, setForm] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [filling, setFilling] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  const fill = async () => {
    setFilling(true);
    setNote(null);
    try {
      const r = await runReference(problemId, { inputs: [form.input] });
      const problem = runProblem(r);
      const c = r.cases[0];
      if (problem) setNote(problem);
      else if (!c) setNote("The reference solution produced no result.");
      else if (isFailure(c.status)) setNote(`${STATUS_LABEL[c.status]}${c.stderr ? `:\n${c.stderr}` : ""}`);
      else setForm((f) => ({ ...f, expectedOutput: c.stdout, outputLocked: false }));
    } catch (e) {
      setNote(errorMessage(e));
    } finally {
      setFilling(false);
    }
  };

  const save = async () => {
    setBusy(true);
    try {
      await onSave(form);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3 rounded-sm border border-link/30 bg-background p-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="space-y-1">
          <span className="eyebrow block text-mute">Input (stdin)</span>
          <Textarea
            value={form.input}
            readOnly={form.inputLocked}
            onChange={(e) => setForm({ ...form, input: e.target.value })}
            rows={7}
            spellCheck={false}
            placeholder={"deposit 100\nwithdraw 30\nquit"}
            className="rounded-sm bg-card font-mono text-xs text-ink"
          />
          {form.inputLocked && <span className="block text-body-sm text-mute">Larger than 8 KB — kept as is.</span>}
        </label>
        <label className="space-y-1">
          <span className="eyebrow block text-mute">Expected output (stdout)</span>
          <Textarea
            value={form.expectedOutput}
            readOnly={form.outputLocked}
            onChange={(e) => setForm({ ...form, expectedOutput: e.target.value })}
            rows={7}
            spellCheck={false}
            className="rounded-sm bg-card font-mono text-xs text-ink"
          />
          {form.outputLocked && <span className="block text-body-sm text-mute">Larger than 8 KB — kept as is.</span>}
        </label>
      </div>
      {note && <pre className="max-h-40 overflow-auto whitespace-pre-wrap rounded-sm bg-destructive/5 p-2 font-mono text-xs text-destructive">{note}</pre>}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <label className="flex items-center gap-2 text-body-sm text-ink">
          <input
            type="checkbox"
            checked={form.isHidden}
            onChange={(e) => setForm({ ...form, isHidden: e.target.checked })}
            className="h-4 w-4 accent-[var(--color-link,#2563eb)]"
          />
          Hidden (used only on Submit)
        </label>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={fill}
            disabled={!hasReference || filling || form.inputLocked}
            title={hasReference ? "Run the saved reference solution on this input" : "Save a reference solution first"}
          >
            {filling ? <Loader2 className="h-4 w-4 animate-spin" /> : <Wand2 className="h-4 w-4" />}
            Fill from reference
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
            Cancel
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={save}
            // Server từ chối file rỗng.
            disabled={busy || (!form.inputLocked && form.input === "") || (!form.outputLocked && form.expectedOutput === "")}
            title="Input and expected output must not be empty"
          >
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            Save case
          </Button>
        </div>
      </div>
    </div>
  );
}

function Preview({ label, text, truncated }: { label: string; text: string; truncated?: boolean }) {
  return (
    <div className="min-w-0">
      <span className="eyebrow block text-mute">{label}</span>
      <pre className="thin-scroll mt-1 max-h-28 overflow-auto whitespace-pre-wrap break-words rounded-sm bg-background p-2 font-mono text-xs text-ink">
        {text === "" ? <span className="text-mute">(empty)</span> : text}
        {truncated && <span className="text-mute">{"\n"}… (first 8 KB)</span>}
      </pre>
    </div>
  );
}

export function TestCasesPanel({
  problemId,
  frozen,
  hasReference,
}: {
  problemId: number;
  frozen: boolean;
  hasReference: boolean;
}) {
  const [cases, setCases] = useState<TestCase[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [run, setRun] = useState<ReferenceRunResult | null>(null);
  const [running, setRunning] = useState<"check" | "apply" | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setCases(await fetchTestCases(problemId));
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [problemId]);

  useEffect(() => {
    setCases(null);
    setRun(null);
    load();
  }, [load]);

  const resultById = useMemo(() => {
    const m = new Map<number, ReferenceCaseResult>();
    run?.cases.forEach((c) => c.testCaseId != null && m.set(c.testCaseId, c));
    return m;
  }, [run]);
  const diffCount = run?.cases.filter((c) => c.status === "DIFF").length ?? 0;
  const failCount = run?.cases.filter((c) => isFailure(c.status)).length ?? 0;

  const check = async (apply: boolean) => {
    setRunning(apply ? "apply" : "check");
    try {
      const r = await runReference(problemId, { apply });
      setRun(r);
      const problem = runProblem(r);
      if (problem) toast("The reference solution could not run — see details above the list.", "error");
      else if (apply) toast(`Updated ${r.appliedCount} expected output${r.appliedCount === 1 ? "" : "s"}.`, "success");
      // Server chạy trên bộ testcase hiện tại — tải lại để danh sách khớp với kết quả.
      await load();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setRunning(null);
    }
  };

  const saveCase = async (id: number | "new", form: CaseForm, original?: TestCase) => {
    try {
      if (id === "new") {
        await addTestCase(problemId, { input: form.input, expectedOutput: form.expectedOutput, isHidden: form.isHidden });
        toast("Test case added.", "success");
      } else {
        const patch: { input?: string; expectedOutput?: string; isHidden?: boolean } = {};
        if (!form.inputLocked && form.input !== original?.input) patch.input = form.input;
        if (!form.outputLocked && form.expectedOutput !== original?.expectedOutput) patch.expectedOutput = form.expectedOutput;
        if (form.isHidden !== original?.isHidden) patch.isHidden = form.isHidden;
        if (Object.keys(patch).length === 0) {
          setEditing(null);
          return;
        }
        await updateTestCase(problemId, id, patch);
        toast("Test case updated.", "success");
      }
      setEditing(null);
      setRun(null);
      await load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const ask = useConfirm();
  const remove = async (tc: TestCase, n: number) => {
    if (!(await ask({ title: `Delete test case #${n}?`, description: "This cannot be undone.", action: "Delete", destructive: true }))) return;
    try {
      await deleteTestCase(problemId, tc.id);
      toast("Test case deleted.", "success");
      setRun(null);
      await load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const runError = run ? runProblem(run) : null;

  return (
    <div className="space-y-4">
      {frozen && <FrozenNotice what="test cases" />}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-body-sm text-mute">
          Run uses public cases; Submit uses all of them. Output is compared ignoring trailing spaces and blank lines.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => check(false)}
            disabled={!hasReference || !cases?.length || running !== null}
            title={hasReference ? "Run the reference solution on every case and compare outputs" : "Save a reference solution first"}
          >
            {running === "check" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            Check against reference
          </Button>
          {!frozen && (
            <Button size="sm" onClick={() => setEditing("new")} disabled={editing !== null}>
              <Plus className="h-4 w-4" />
              Add case
            </Button>
          )}
        </div>
      </div>

      {run && (
        <div className="space-y-2 rounded-sm border border-border bg-card px-3 py-2">
          {runError ? (
            <>
              <p className="text-body-sm font-medium text-destructive">Reference solution failed</p>
              <pre className="thin-scroll max-h-48 overflow-auto whitespace-pre-wrap font-mono text-xs text-destructive">{runError}</pre>
            </>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-body-sm text-ink">
                <span className="font-medium">{run.cases.length - diffCount - failCount}</span> match ·{" "}
                <span className={cn("font-medium", diffCount && "text-[#8a5a00]")}>{diffCount}</span> differ ·{" "}
                <span className={cn("font-medium", failCount && "text-destructive")}>{failCount}</span> failed
                {run.mainClass && <span className="text-mute"> · main: <code className="font-mono">{run.mainClass}</code></span>}
                {run.appliedCount > 0 && <span className="text-success"> · {run.appliedCount} updated</span>}
              </p>
              {diffCount > 0 && !frozen && (
                <Button size="sm" onClick={() => check(true)} disabled={running !== null}>
                  {running === "apply" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                  Use reference output for {diffCount} case{diffCount === 1 ? "" : "s"}
                </Button>
              )}
            </div>
          )}
        </div>
      )}

      {editing === "new" && (
        <CaseEditor
          problemId={problemId}
          hasReference={hasReference}
          initial={{ input: "", expectedOutput: "", isHidden: true, inputLocked: false, outputLocked: false }}
          onCancel={() => setEditing(null)}
          onSave={(f) => saveCase("new", f)}
        />
      )}

      {error && <ErrorText>{error}</ErrorText>}
      {!cases && !error && (
        <p className="flex items-center gap-2 text-body-sm text-mute">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading test cases…
        </p>
      )}
      {cases && cases.length === 0 && editing !== "new" && (
        <div className="rounded-sm border border-dashed border-border px-4 py-8 text-center">
          <p className="text-body-md text-ink">No test cases yet</p>
          <p className="mt-1 text-body-sm text-mute">
            Add an input, then use <em>Fill from reference</em> to generate its expected output.
          </p>
        </div>
      )}

      <ol className="space-y-3">
        {cases?.map((tc, i) => {
          const n = i + 1;
          const result = resultById.get(tc.id);
          if (editing === tc.id) {
            return (
              <li key={tc.id}>
                <CaseEditor
                  problemId={problemId}
                  hasReference={hasReference}
                  initial={{
                    input: tc.input,
                    expectedOutput: tc.expectedOutput,
                    isHidden: tc.isHidden,
                    inputLocked: !!tc.inputTruncated,
                    outputLocked: !!tc.expectedOutputTruncated,
                  }}
                  onCancel={() => setEditing(null)}
                  onSave={(f) => saveCase(tc.id, f, tc)}
                />
              </li>
            );
          }
          const showDiff = result && result.status !== "MATCH" && expanded === tc.id;
          return (
            <li key={tc.id} className="rounded-sm border border-border bg-card p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-body-sm font-medium text-ink">#{n}</span>
                  <Badge variant={tc.isHidden ? "secondary" : "outline"}>{tc.isHidden ? "Hidden" : "Public"}</Badge>
                  {result && (
                    <button
                      type="button"
                      onClick={() => setExpanded(expanded === tc.id ? null : tc.id)}
                      disabled={result.status === "MATCH"}
                      className="inline-flex items-center gap-1"
                      aria-expanded={showDiff ? true : false}
                    >
                      <Badge variant={STATUS_TONE[result.status]}>
                        {result.status === "MATCH" && <Check className="h-3 w-3" />}
                        {STATUS_LABEL[result.status]}
                        {result.runtimeMs != null && ` · ${result.runtimeMs} ms`}
                      </Badge>
                      {result.status !== "MATCH" &&
                        (showDiff ? <ChevronDown className="h-3.5 w-3.5 text-mute" /> : <ChevronRight className="h-3.5 w-3.5 text-mute" />)}
                    </button>
                  )}
                  {result?.applied && <Badge variant="success">Updated</Badge>}
                </div>
                {!frozen && (
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="icon-sm" onClick={() => setEditing(tc.id)} disabled={editing !== null} aria-label={`Edit test case #${n}`}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => remove(tc, n)} aria-label={`Delete test case #${n}`}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>
              <div className="mt-2 grid gap-3 sm:grid-cols-2">
                <Preview label="Input" text={tc.input} truncated={tc.inputTruncated} />
                <Preview label="Expected output" text={tc.expectedOutput} truncated={tc.expectedOutputTruncated} />
              </div>
              {showDiff && result && (
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  <Preview label="Reference stdout" text={result.stdout} />
                  {result.stderr ? <Preview label="Reference stderr" text={result.stderr} /> : null}
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Grading config — JSON validated by the judge's own rule schema.
 * ------------------------------------------------------------------ */

const EXAMPLE_CONFIG = `{
  "rules": [
    {
      "ruleId": "account-encapsulation",
      "ruleName": "Account hides its balance",
      "targetClassName": "Account",
      "expectedType": "CLASS",
      "enforceEncapsulation": true,
      "fields": [
        { "name": "balance", "type": "long", "requiredModifiers": ["private"] }
      ],
      "methods": [
        { "name": "deposit", "returnType": "void", "parameterTypes": ["long"], "requiredModifiers": ["public"] },
        { "name": "getBalance", "returnType": "long", "parameterTypes": [] }
      ],
      "weight": 1
    }
  ]
}
`;

export function GradingPanel({
  problemId,
  authoring,
  onSaved,
}: {
  problemId: number;
  authoring: ProblemAuthoring;
  onSaved: (a: ProblemAuthoring) => void;
}) {
  const saved = authoring.gradingConfig ?? "";
  const ask = useConfirm();
  const pretty = useMemo(() => {
    try {
      return saved ? JSON.stringify(JSON.parse(saved), null, 2) : "";
    } catch {
      return saved;
    }
  }, [saved]);
  const [text, setText] = useState(pretty);
  const [validation, setValidation] = useState<GradingValidation | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [busy, setBusy] = useState<"validate" | "save" | null>(null);

  useEffect(() => {
    setText(pretty);
    setValidation(null);
    setErrors([]);
  }, [pretty]);

  const dirty = text.trim() !== pretty.trim();
  const readOnly = authoring.gradingFrozen;

  const format = () => {
    try {
      setText(JSON.stringify(JSON.parse(text), null, 2));
      setErrors([]);
    } catch (e) {
      setErrors([`Not valid JSON: ${(e as Error).message}`]);
    }
  };

  const validate = async () => {
    setBusy("validate");
    setErrors([]);
    try {
      const v = await validateGradingConfig(problemId, text);
      setValidation(v);
      setErrors(v.errors);
    } catch (e) {
      setErrors([errorMessage(e)]);
    } finally {
      setBusy(null);
    }
  };

  const save = async (value: string | null) => {
    setBusy("save");
    setErrors([]);
    try {
      const a = await saveGradingConfig(problemId, value);
      toast(value ? "Grading rules saved." : "Grading rules cleared.", "success");
      setValidation(null);
      onSaved(a);
    } catch (e) {
      if (e instanceof ApiError && e.problem?.errors?.length) setErrors(e.problem.errors);
      else setErrors([errorMessage(e)]);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-4">
      {readOnly && <FrozenNotice what="grading rules" />}
      <div className="space-y-1 text-body-sm text-mute">
        <p>
          Structural rules are checked by reflection on the compiled classes (the student&apos;s code is not run for
          this). Score weights: rules <strong className="text-ink">40%</strong> + stdin/stdout test cases{" "}
          <strong className="text-ink">60%</strong>; with socket scenarios 30 / 40 / 30. A part that has nothing configured
          is skipped.
        </p>
        <p>Students never see this configuration.</p>
      </div>

      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <label htmlFor="grading-json" className="eyebrow block text-mute">
            grading_config (JSON){dirty && <span className="ml-2 normal-case text-[#8a5a00]">· unsaved</span>}
          </label>
          {!readOnly && (
            <div className="flex flex-wrap items-center gap-1">
              {!text.trim() && (
                <Button type="button" variant="ghost" size="sm" onClick={() => setText(EXAMPLE_CONFIG)}>
                  <Copy className="h-4 w-4" />
                  Insert example
                </Button>
              )}
              <Button type="button" variant="ghost" size="sm" onClick={format} disabled={!text.trim()}>
                Format
              </Button>
            </div>
          )}
        </div>
        <Textarea
          id="grading-json"
          value={text}
          readOnly={readOnly}
          onChange={(e) => {
            setText(e.target.value);
            setValidation(null);
          }}
          rows={20}
          spellCheck={false}
          placeholder='{ "rules": [ { "targetClassName": "Account", "expectedType": "CLASS" } ] }'
          className="rounded-sm bg-card font-mono text-xs text-ink"
        />
      </div>

      {errors.length > 0 && (
        <div role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2">
          <p className="text-body-sm font-medium text-destructive">
            {errors.length} problem{errors.length === 1 ? "" : "s"} found
          </p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5 font-mono text-xs text-destructive">
            {errors.map((err) => (
              <li key={err}>{err}</li>
            ))}
          </ul>
        </div>
      )}
      {validation?.valid && (
        <p className="flex items-center gap-2 rounded-sm border border-success/30 bg-success/10 px-3 py-2 text-body-sm text-ink">
          <Check className="h-4 w-4 text-success" />
          Valid — {validation.ruleCount} rule{validation.ruleCount === 1 ? "" : "s"} (total weight{" "}
          {validation.totalRuleWeight}), {validation.socketScenarioCount} socket scenario
          {validation.socketScenarioCount === 1 ? "" : "s"}.
        </p>
      )}

      {!readOnly && (
        <div className="flex flex-wrap items-center justify-end gap-2">
          {saved && (
            <Button
              variant="ghost"
              onClick={async () => (await ask({ title: "Remove all grading rules?", description: "The problem falls back to plain test-case grading.", action: "Remove", destructive: true })) && save(null)}
              disabled={busy !== null}
            >
              Clear rules
            </Button>
          )}
          <Button variant="outline" onClick={validate} disabled={!text.trim() || busy !== null}>
            {busy === "validate" && <Loader2 className="h-4 w-4 animate-spin" />}
            Validate
          </Button>
          <Button onClick={() => save(text.trim() || null)} disabled={!dirty || busy !== null}>
            {busy === "save" && <Loader2 className="h-4 w-4 animate-spin" />}
            Save rules
          </Button>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Reference solution + live console.
 * ------------------------------------------------------------------ */

/** Đọc danh sách file nguồn lưu dạng chuỗi JSON [{path, content}]; trống hoặc hỏng ⇒ []. */
export function parseSourceFiles(raw: string | null | undefined): SubmissionFile[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

const DEFAULT_MAIN: SubmissionFile = {
  path: "Main.java",
  content:
    "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        while (in.hasNextLine()) {\n            String line = in.nextLine();\n            System.out.println(line);\n        }\n    }\n}\n",
};

export function ReferencePanel({
  problemId,
  authoring,
  starterTemplates,
  onSaved,
}: {
  problemId: number;
  authoring: ProblemAuthoring;
  starterTemplates?: string | null;
  onSaved: (a: ProblemAuthoring) => void;
}) {
  const savedJson = JSON.stringify(authoring.referenceSolution);
  const ask = useConfirm();
  const [files, setFiles] = useState<SubmissionFile[]>(authoring.referenceSolution);
  const [busy, setBusy] = useState(false);
  const [runSignal, setRunSignal] = useState(0);
  const [source, setSource] = useState<"reference" | "starter">("reference");
  const starter = useMemo(() => parseSourceFiles(starterTemplates), [starterTemplates]);

  useEffect(() => {
    setFiles(JSON.parse(savedJson));
  }, [savedJson]);

  const dirty = JSON.stringify(files) !== savedJson;
  const consoleFiles = source === "starter" ? starter : files;

  const save = async () => {
    setBusy(true);
    try {
      const a = await saveReferenceSolution(problemId, files);
      toast(files.length ? "Reference solution saved." : "Reference solution removed.", "success");
      onSaved(a);
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <p className="text-body-sm text-mute">
        The reference solution is your own working program. It generates expected outputs for test cases (in the judge
        sandbox, with this problem&apos;s {authoring.timeLimitMs} ms / {authoring.memoryLimitMb} MB limits) and is never
        shown to students.
      </p>

      {files.length === 0 ? (
        <div className="flex flex-wrap items-center gap-2 rounded-sm border border-dashed border-border px-4 py-5">
          <p className="mr-auto text-body-md text-ink">No reference solution yet</p>
          {starter.length > 0 && (
            <Button variant="outline" size="sm" onClick={() => setFiles(starter.map((f) => ({ ...f })))}>
              <Copy className="h-4 w-4" />
              Start from starter
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={() => setFiles([{ ...DEFAULT_MAIN }])}>
            <Plus className="h-4 w-4" />
            Start with Main.java
          </Button>
        </div>
      ) : (
        <SourceFilesEditor label="Reference solution (.java)" files={files} onChange={setFiles} rows={14} />
      )}

      <div className="flex flex-wrap items-center justify-end gap-2">
        {dirty && <span className="mr-auto text-body-sm text-[#8a5a00]">Unsaved changes</span>}
        {authoring.referenceSolution.length > 0 && files.length > 0 && (
          <Button variant="ghost" onClick={async () => (await ask({ title: "Remove the reference solution?", action: "Remove", destructive: true })) && setFiles([])}>
            Remove
          </Button>
        )}
        <Button onClick={save} disabled={!dirty || busy}>
          {busy && <Loader2 className="h-4 w-4 animate-spin" />}
          Save reference
        </Button>
      </div>

      <section aria-label="Try in console" className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="eyebrow block text-mute">Try it in the console</span>
          <div className="flex items-center gap-2">
            {starter.length > 0 && (
              <select
                aria-label="Program to run"
                value={source}
                onChange={(e) => setSource(e.target.value as "reference" | "starter")}
                className="h-8 rounded-sm border border-border bg-card px-2 text-body-sm text-ink"
              >
                <option value="reference">Reference solution</option>
                <option value="starter">Starter template</option>
              </select>
            )}
            <Button size="sm" variant="outline" onClick={() => setRunSignal((n) => n + 1)} disabled={consoleFiles.length === 0}>
              <Play className="h-4 w-4" />
              Run
            </Button>
          </div>
        </div>
        <div className="h-64 overflow-hidden rounded-sm border border-border">
          <ConsolePanel problemId={problemId} language="JAVA" files={consoleFiles} runSignal={runSignal} />
        </div>
        <p className="text-body-sm text-mute">
          Type a line and press Enter to send it to stdin · Ctrl+D closes stdin · Ctrl+C stops. Runs the code in the editor
          above, saved or not.
        </p>
      </section>
    </div>
  );
}
