"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AlignLeft, ChevronDown, ChevronUp, Copy, Download, FileCode, List, Maximize2, Minimize2, Plus, RotateCcw, Shield, Timer, Upload, X } from "lucide-react";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import {
  Combobox,
  ComboboxContent,
  ComboboxItem,
  ComboboxList,
  ComboboxTrigger,
  ComboboxValue,
} from "@/components/ui/combobox";
import { CodeEditor } from "./code-editor";
import { ProblemDetailPanel } from "./problem-detail-panel";
import { ProblemSubmissions } from "./problem-submissions";
import { ResultPanel } from "./result-panel";
import { DisqualifiedOverlay, FullscreenOverlay, TimeUpOverlay, ViolationDialog } from "./violation-dialog";
import { useAutosave, type SaveStatus } from "@/hooks/use-autosave";
import { useCountdown } from "@/hooks/use-countdown";
import { useJudge } from "@/hooks/use-judge";
import { useMediaQuery } from "@/hooks/use-media-query";
import { useProctoring } from "@/hooks/use-proctoring";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { fmtDuration } from "@/lib/format";
import { DRAFT_SYNC, downloadStarterZip, finalizeContestSubmission, loadDraft, RUN_ENABLED, saveDraft, submitCodeMultipart, transitionProblem } from "@/lib/services";
import { FILE_NAME, LANG_PREF_KEY, LANGUAGES, TEMPLATES } from "@/lib/templates";
import { formatCode } from "@/lib/format-code";
import { toast } from "@/lib/toast";
import { VERDICT } from "@/lib/verdict";
import type { ContestInfo, Language, ProblemFull, ProctoringEventType, Role, SubmissionFile, TestCase } from "@/types/api";

export type WorkspaceMode = "student" | "instructor" | "exam";
interface Props { problem: ProblemFull; testCases: TestCase[]; mode: WorkspaceMode; contest?: ContestInfo; role?: Role }

const STATUS_TEXT: Record<SaveStatus, string> = { saved: "Auto-saved", saving: "Saving…", offline: "Offline, saved locally" };
const EVENT_LABEL: Record<ProctoringEventType, string> = { WINDOW_BLUR: "Left exam window", WINDOW_FOCUS: "Returned to exam window", CLIPBOARD_PASTE: "Pasted content" };

export function Workspace({ problem: initialProblem, testCases: initialCases, mode, contest, role = "USER" }: Props) {
  const router = useRouter();
  const examMode = mode === "exam";
  const contestId = contest?.id;
  const desktop = useMediaQuery("(min-width: 1024px)");
  const [problem, setProblem] = useState(initialProblem);
  const [cases] = useState(initialCases);
  const [lang, setLang] = useState<Language>("JAVA");
  const [codes, setCodes] = useState<Record<Language, string>>({ ...TEMPLATES });
  const [lastRun, setLastRun] = useState(false);
  const [maximized, setMaximized] = useState(false);
  const [tab, setTab] = useState("result");
  const [bottomMode, setBottomMode] = useState<"normal" | "max" | "fold">("normal");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try { const l = localStorage.getItem(LANG_PREF_KEY) as Language | null; if (l && LANGUAGES.some((x) => x.value === l)) setLang(l); } catch { /* ignore */ }
  }, []);
  const changeLang = (l: Language) => { setLang(l); try { localStorage.setItem(LANG_PREF_KEY, l); } catch { /* ignore */ } };

  const left = useCountdown(examMode && contest ? parseApiDate(contest.endTime) : null);
  const timeUp = examMode && left === 0;
  const proctor = useProctoring({
    contestId, enabled: examMode && !timeUp, initialCount: contest?.myViolations ?? 0,
    max: contest?.maxViolationsAllowed ?? 3, initialDisqualified: contest?.myStatus === "DISQUALIFIED",
  });
  const locked = examMode && (proctor.disqualified || timeUp);

  const storageKey = `draft:${contestId ?? "practice"}:${problem.id}:${lang}`;
  const remote = useMemo(
    () => (DRAFT_SYNC && examMode && contestId
      ? { load: () => loadDraft(contestId, problem.id, lang), save: (v: string) => saveDraft(contestId, problem.id, lang, v) }
      : undefined),
    [examMode, contestId, problem.id, lang],
  );
  const autosave = useAutosave({ storageKey, value: codes[lang], setValue: (v) => setCodes((c) => ({ ...c, [lang]: v })), remote });

  const flushed = useRef(false);
  const { saveNow } = autosave;
  useEffect(() => { if (locked && !flushed.current) { flushed.current = true; void saveNow(); } }, [locked, saveNow]);

  // Hết giờ: lưu bản nháp cuối cùng và chốt bài thi ở phía server.
  // Nếu không chốt, thí sinh vẫn ở trạng thái IN_PROGRESS và server không biết
  // bài làm cuối cùng của họ là bài nào.
  const finalized = useRef(false);
  useEffect(() => {
    if (!examMode || !timeUp || !contestId || finalized.current) return;
    finalized.current = true;
    void saveNow();
    void finalizeContestSubmission(contestId).then(() => undefined);
  }, [examMode, timeUp, contestId, saveNow]);

  const judge = useJudge(problem.id, contestId);
  const canAct = !locked && autosave.ready && !judge.judging;

  const isOopGrinding = problem.type === "OOP_GRINDING";
  const defaultFiles = useMemo<SubmissionFile[]>(() => {
    if (problem.starterTemplates) {
      try {
        const parsed = JSON.parse(problem.starterTemplates);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      } catch {
        /* ignore parse error */
      }
    }
    return [{ path: "Solution.java", content: "// Solution.java\npublic class Solution {\n    // Implementation here\n}\n" }];
  }, [problem.starterTemplates]);

  const [multiFiles, setMultiFiles] = useState<SubmissionFile[]>(defaultFiles);
  const [activeFileIdx, setActiveFileIdx] = useState(0);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setMultiFiles(defaultFiles);
    setActiveFileIdx(0);
  }, [defaultFiles]);

  const updateActiveContent = (newContent: string) => {
    setMultiFiles((prev) => {
      const next = [...prev];
      if (next[activeFileIdx]) {
        next[activeFileIdx] = { ...next[activeFileIdx], content: newContent };
      }
      return next;
    });
  };

  const handleAddFile = () => {
    const name = window.prompt("Enter new Java class/file name (e.g. Helper.java):", "NewClass.java");
    if (!name || !name.trim()) return;
    const cleanName = name.trim().endsWith(".java") ? name.trim() : `${name.trim()}.java`;
    if (multiFiles.some((f) => f.path.toLowerCase() === cleanName.toLowerCase())) {
      toast("File already exists with that name.", "error");
      return;
    }
    const className = cleanName.replace(/\.java$/, "");
    const initialContent = `// ${cleanName}\npublic class ${className} {\n    \n}\n`;
    setMultiFiles((prev) => [...prev, { path: cleanName, content: initialContent }]);
    setActiveFileIdx(multiFiles.length);
  };

  const handleDeleteFile = (idx: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (multiFiles.length <= 1) {
      toast("Cannot delete the only file in the workspace.", "error");
      return;
    }
    if (!window.confirm(`Delete ${multiFiles[idx].path}?`)) return;
    setMultiFiles((prev) => prev.filter((_, i) => i !== idx));
    if (activeFileIdx >= idx && activeFileIdx > 0) {
      setActiveFileIdx(activeFileIdx - 1);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.name.endsWith(".zip")) {
        toast(`Uploading ZIP: ${file.name}...`, "info");
        const fd = new FormData();
        fd.append("problemId", String(problem.id));
        fd.append("file", file);
        fd.append("files", file);
        fd.append("language", "JAVA");
        if (contestId) fd.append("contestId", String(contestId));
        try {
          setBusy(true);
          const sub = await submitCodeMultipart(problem.id, fd);
          toast(`Submitted ZIP archive successfully (#${sub.id})`, "success");
          setTab("subs");
        } catch (err) {
          toast(errorMessage(err), "error");
        } finally {
          setBusy(false);
        }
      } else if (file.name.endsWith(".java")) {
        const text = await file.text();
        setMultiFiles((prev) => {
          const existingIdx = prev.findIndex((f) => f.path === file.name);
          if (existingIdx >= 0) {
            const updated = [...prev];
            updated[existingIdx] = { path: file.name, content: text };
            return updated;
          }
          return [...prev, { path: file.name, content: text }];
        });
        toast(`Imported ${file.name}`, "success");
      }
    }
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleDownloadStarter = async () => {
    try {
      toast("Downloading starter template...", "info");
      await downloadStarterZip(problem.id, problem.slug);
      toast("Starter template downloaded.", "success");
    } catch (err) {
      toast(errorMessage(err), "error");
    }
  };

  const onSubmit = () => {
    if (canAct) {
      setLastRun(false);
      setTab("result");
      if (isOopGrinding) {
        void judge.submit("JAVA", multiFiles);
      } else {
        void judge.submit(lang, codes[lang]);
      }
    }
  };
  const onRun = () => {
    if (canAct && RUN_ENABLED && !examMode) {
      setLastRun(true);
      setTab("result");
      if (isOopGrinding) {
        void judge.submit("JAVA", multiFiles, { run: true });
      } else {
        void judge.submit(lang, codes[lang], { run: true });
      }
    }
  };
  const onSave = () => { void saveNow(); toast("Draft saved.", "success"); };

  useEffect(() => {
    if (!judge.error) return;
    toast(judge.error.message, "error");
    setTab("result");
    if (examMode && judge.error.status === 403 && /disqualified/i.test(judge.error.message)) proctor.markDisqualified();
  }, [judge.error]);
  useEffect(() => { if (judge.timedOut) toast("Judging is taking longer than expected. Your submission has been saved; check Submissions.", "info"); }, [judge.timedOut]);
  useEffect(() => {
    if (!desktop) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.altKey && e.code === "Equal") {
        e.preventDefault();
        setBottomMode((m) => (m === "max" ? "normal" : "max"));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [desktop]);
  useEffect(() => {
    if (!maximized) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setMaximized(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [maximized]);

  useEffect(() => {
    if (!examMode) return;
    const onPaste = (e: ClipboardEvent) => {
      e.preventDefault();
      proctor.reportPaste();
    };
    window.addEventListener("paste", onPaste, true);
    return () => window.removeEventListener("paste", onPaste, true);
  }, [examMode, proctor]);

  const publish = async () => {
    const target = role === "ADMIN" ? "PUBLISHED" : "REVIEWING";
    setBusy(true);
    try {
      const p = await transitionProblem(problem.id, target);
      setProblem({ ...problem, ...p, status: p.status ?? target });
      toast(target === "PUBLISHED" ? "Problem published." : "Problem submitted for review.", "success");
    } catch (e) { toast(errorMessage(e), "error"); }
    finally { setBusy(false); }
  };

  const crumbHref = examMode && contestId ? `/exam/${contestId}` : "/problems";
  const sh = examMode ? "Ctrl+Shift+Enter submit" : "Ctrl+Enter run · Ctrl+Shift+Enter submit · Ctrl+S save";
  const resultLabel = judge.result?.verdict ? ` (${VERDICT[judge.result.verdict].label})` : "";

  const descriptionNode = (
    <ProblemDetailPanel
      problem={problem}
      cases={cases}
      canEdit={mode === "instructor"}
      isStaff={role === "ADMIN" || role === "INSTRUCTOR"}
      onProblemChange={setProblem}
      showTestCases={false}
      showResult={false}
    />
  );
  const activeFile = isOopGrinding ? (multiFiles[activeFileIdx] ?? multiFiles[0]) : null;

  const editorNode = (
    <div className={cn("flex h-full flex-col bg-card", maximized && "fixed inset-0 z-40")}>
      <div className="flex h-9 shrink-0 items-center gap-2 border-b border-border px-3 font-mono text-body-sm text-mute">
        <span className="text-ink">{isOopGrinding ? (activeFile?.path ?? "Solution.java") : FILE_NAME[lang]}</span>
        <span className="text-faint">·</span>
        <span aria-live="polite" className={cn(autosave.status === "offline" && "text-[#8a5a00]")}>
          {autosave.ready ? STATUS_TEXT[autosave.status] : "Loading draft…"}
        </span>
        <span className="ml-2 hidden truncate xl:inline" title="Shortcuts">{sh}</span>
        <div className="ml-auto flex items-center gap-3">
          {!examMode && (
            <button aria-label="Copy code" title="Copy code" onClick={() => {
              const codeToCopy = isOopGrinding ? (activeFile?.content ?? "") : codes[lang];
              void navigator.clipboard?.writeText(codeToCopy);
              toast("Code copied.", "success");
            }} className="text-mute transition-colors hover:text-ink">
              <Copy className="h-3.5 w-3.5" />
            </button>
          )}
          <button aria-label="Format code" title="Format code" disabled={locked} onClick={() => {
            if (isOopGrinding) {
              if (activeFile) updateActiveContent(formatCode(activeFile.content, "JAVA"));
            } else {
              setCodes((c) => ({ ...c, [lang]: formatCode(c[lang], lang) }));
            }
          }} className="text-mute transition-colors hover:text-ink disabled:opacity-40">
            <AlignLeft className="h-3.5 w-3.5" />
          </button>
          <button aria-label="Reset template" title="Reset template" disabled={locked} onClick={() => {
            if (window.confirm("Reset code to default template? Current changes will be lost.")) {
              if (isOopGrinding) {
                setMultiFiles(defaultFiles);
                setActiveFileIdx(0);
              } else {
                setCodes((c) => ({ ...c, [lang]: TEMPLATES[lang] }));
              }
            }
          }} className="text-mute transition-colors hover:text-ink disabled:opacity-40">
            <RotateCcw className="h-3.5 w-3.5" />
          </button>
          <button aria-label={maximized ? "Minimize" : "Maximize"} title="Maximize / minimize (Esc)" onClick={() => setMaximized(!maximized)} className="text-mute transition-colors hover:text-ink">
            {maximized ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      {isOopGrinding && (
        <div className="flex items-center gap-1 overflow-x-auto border-b border-border bg-hairline-soft/60 px-2 py-1 text-body-sm">
          {multiFiles.map((f, idx) => (
            <div
              key={f.path}
              onClick={() => setActiveFileIdx(idx)}
              className={cn(
                "group flex cursor-pointer items-center gap-1.5 rounded-md px-2.5 py-1 font-mono text-code transition-colors",
                idx === activeFileIdx
                  ? "border border-border bg-card font-medium text-ink shadow-sm"
                  : "text-mute hover:bg-card/50 hover:text-ink"
              )}
            >
              <FileCode className="h-3.5 w-3.5 text-link" />
              <span>{f.path}</span>
              {multiFiles.length > 1 && (
                <button
                  type="button"
                  onClick={(e) => handleDeleteFile(idx, e)}
                  title={`Delete ${f.path}`}
                  className="ml-1 rounded p-0.5 text-mute opacity-0 transition-opacity hover:bg-destructive/10 hover:text-destructive group-hover:opacity-100"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>
          ))}
          <Button
            variant="ghost"
            size="sm"
            onClick={handleAddFile}
            className="h-7 gap-1 px-2 text-code text-mute hover:text-ink"
            title="Add New Java File"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>New File</span>
          </Button>
          <div className="ml-auto flex items-center gap-1">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              accept=".zip,.java"
              multiple
              className="hidden"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => fileInputRef.current?.click()}
              className="h-7 gap-1 px-2 text-code"
              title="Upload .zip or .java files"
            >
              <Upload className="h-3.5 w-3.5" />
              <span>Upload</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleDownloadStarter}
              className="h-7 gap-1 px-2 text-code"
              title="Download Starter Template (.zip)"
            >
              <Download className="h-3.5 w-3.5" />
              <span>Starter (.zip)</span>
            </Button>
          </div>
        </div>
      )}

      <div className="min-h-0 flex-1">
        <CodeEditor
          language={isOopGrinding ? "JAVA" : lang}
          value={isOopGrinding ? (activeFile?.content ?? "") : codes[lang]}
          readOnly={locked || !autosave.ready}
          examMode={examMode}
          onChange={(v) => {
            if (isOopGrinding) {
              updateActiveContent(v);
            } else {
              setCodes((c) => ({ ...c, [lang]: v }));
            }
          }}
          onSave={onSave}
          onRun={onRun}
          onSubmit={onSubmit}
          onPasteAttempt={proctor.reportPaste}
        />
      </div>
    </div>
  );
  const bottomNode = (
      <Tabs value={tab} onValueChange={setTab} className="flex h-full min-h-0 flex-col">
        <div className="flex shrink-0 items-center gap-2 pr-3">
          {/* Bấm vào tab (kể cả tab đang chọn) khi khung đang thu thì mở lại. */}
          <TabsList className="m-2 border-b-0" onClick={() => bottomMode === "fold" && setBottomMode("normal")}>
            <TabsTrigger value="case">Testcase</TabsTrigger>
            <TabsTrigger value="result">Test Result{resultLabel}</TabsTrigger>
            <TabsTrigger value="subs">Submissions</TabsTrigger>
          </TabsList>
          {desktop && (
            <div className="ml-auto flex items-center gap-3 text-mute">
              <button
                type="button"
                aria-label={bottomMode === "max" ? "Restore panel" : "Maximize panel"}
                title={bottomMode === "max" ? "Restore (Alt + =)" : "Maximize (Alt + =)"}
                onClick={() => setBottomMode((m) => (m === "max" ? "normal" : "max"))}
                className="transition-colors hover:text-ink"
              >
                {bottomMode === "max" ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
              </button>
              <button
                type="button"
                aria-label={bottomMode === "fold" ? "Expand panel" : "Fold panel"}
                title={bottomMode === "fold" ? "Expand" : "Fold"}
                onClick={() => setBottomMode((m) => (m === "fold" ? "normal" : "fold"))}
                className="transition-colors hover:text-ink"
              >
                {bottomMode === "fold" ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
              </button>
            </div>
          )}
        </div>
        <div className={cn("thin-scroll min-h-0 flex-1 overflow-auto", bottomMode === "fold" && desktop && "hidden")}>
        <TabsContent value="case" className="space-y-2 p-4 text-body-sm">
          {cases.filter((c) => !c.isHidden).map((c, i) => (
            <div key={c.id}>
              <p className="mb-1 text-mute">Case {i + 1}</p>
              <pre className="thin-scroll overflow-auto whitespace-pre rounded-sm border border-border bg-card px-3 py-2 font-mono text-code text-ink">{c.input}</pre>
            </div>
          ))}
          {cases.every((c) => c.isHidden) && <p className="text-mute">No public testcases available yet.</p>}
        </TabsContent>
      <TabsContent value="result" className="mt-0">
        <ResultPanel result={judge.result} cases={judge.cases} judging={judge.judging} timedOut={judge.timedOut} error={judge.error} runMode={lastRun} />
      </TabsContent>
      <TabsContent value="subs" className="mt-0">
        <ProblemSubmissions problemId={problem.id} contestId={contestId} refreshKey={`${judge.result?.id}-${judge.result?.status}`} />
      </TabsContent>
        </div>
    </Tabs>
  );

  return (
    <div className="flex h-full flex-col">
      <div className="flex min-h-12 shrink-0 flex-wrap items-center gap-3 border-b border-border px-4 py-1.5">
        <Link href={crumbHref} className="flex items-center gap-1.5 text-body-md text-body transition-colors hover:text-ink">
          <List className="h-4 w-4" />
          {examMode ? "Contest" : "Problems"}
        </Link>
        <span className="text-faint">/</span>
        {examMode && contest && (
          <nav className="flex gap-1" aria-label="Select contest problem">
            {contest.problems.map((p) => (
              <Link
                key={p.id}
                href={`/exam/${contest.id}/problems/${p.id}`}
                title={p.title}
                aria-current={p.id === problem.id ? "page" : undefined}
                className={cn(
                  "grid h-7 w-7 place-items-center rounded-sm font-mono text-body-sm transition-colors",
                  p.id === problem.id ? "bg-ink text-white" : "text-body hover:bg-hairline-soft hover:text-ink"
                )}
              >
                {p.label}
              </Link>
            ))}
          </nav>
        )}
        <span className="font-medium text-ink">#{problem.id} {problem.title}</span>
        <DifficultyBadge value={problem.difficulty} />
        <Combobox
          items={LANGUAGES}
          value={LANGUAGES.find((l) => l.value === lang)}
          itemToStringValue={(l: unknown) => (l && typeof l === "object" && "label" in l ? String((l as { label: string }).label) : String(l ?? ""))}
          onValueChange={(val: unknown) => {
            if (isOopGrinding) return;
            const v = (val && typeof val === "object" && "value" in val ? (val as { value: Language }).value : val) as Language;
            if (v) changeLang(v);
          }}
        >
          <ComboboxTrigger aria-label="Language" disabled={locked || isOopGrinding} className="h-8 min-w-[7.5rem] px-2.5 py-1 text-body-sm font-medium">
            <span className="flex items-center gap-2">
              <span className={cn("h-2 w-2 rounded-full", isOopGrinding ? "bg-amber-500" : LANGUAGES.find((l) => l.value === lang)?.dot)} />
              <ComboboxValue placeholder="Select language">
                {isOopGrinding ? "Java (OOP)" : (LANGUAGES.find((l) => l.value === lang)?.label ?? lang)}
              </ComboboxValue>
            </span>
          </ComboboxTrigger>
          <ComboboxContent align="start" className="w-36">
            <ComboboxList>
              {LANGUAGES.map((l) => (
                <ComboboxItem key={l.value} value={l} className="flex items-center gap-2 text-body-sm">
                  <span className={cn("h-2 w-2 rounded-full", l.dot)} />
                  <span>{l.label}</span>
                </ComboboxItem>
              ))}
            </ComboboxList>
          </ComboboxContent>
        </Combobox>
        {mode === "student" && role !== "USER" && (
          <Link href={`/problems/${problem.slug}`} className="text-body-sm text-link underline underline-offset-4">
            Back to instructor view
          </Link>
        )}

        <div className="ml-auto flex items-center gap-2">
          {examMode && (
            <>
              <Popover>
                <PopoverTrigger asChild>
                  <button
                    aria-label="Proctoring log"
                    className={cn(
                      "flex items-center gap-1 rounded-sm px-2 py-1 text-body-sm transition-colors",
                      proctor.count > 0 ? "bg-destructive/10 text-destructive" : "bg-hairline-soft text-body"
                    )}
                  >
                    <Shield className="h-3.5 w-3.5" />
                    Violations {proctor.count}/{proctor.max}
                  </button>
                </PopoverTrigger>
                <PopoverContent align="end" className="w-80 space-y-2 text-body-sm">
                  <p className="font-medium text-ink">Your Proctoring Log</p>
                  <p className="text-body">The server is the authoritative judge for violations. Leaving the window or full-screen for over 1.5s, or pasting code, will be recorded.</p>
                  {proctor.log.length === 0 && <p className="text-mute">No events recorded yet.</p>}
                  <ul className="max-h-48 space-y-1 overflow-auto">
                    {proctor.log.map((l, i) => (
                      <li key={i} className="rounded-sm bg-hairline-soft px-2 py-1">
                        <span className="font-mono text-mute">{new Date(l.at).toLocaleTimeString("en-US", { hour12: false })}</span>{" "}
                        {EVENT_LABEL[l.type]}
                        <div className="text-mute">{l.message}</div>
                      </li>
                    ))}
                  </ul>
                </PopoverContent>
              </Popover>
              <span
                className={cn(
                  "flex items-center gap-1.5 rounded-sm border border-border bg-card px-3 py-1 font-mono text-body-md",
                  left != null && left < 5 * 60_000 && "border-destructive text-destructive"
                )}
                aria-label="Time remaining"
              >
                <Timer className="h-4 w-4" />
                {left == null ? "--:--" : fmtDuration(left)}
              </span>
            </>
          )}
          {mode === "student" && (
            <>
              <Button variant="outline" size="sm" className="h-8" onClick={onSave}>Save</Button>
              {RUN_ENABLED && <Button variant="outline" size="sm" className="h-8" onClick={onRun} disabled={!canAct}>Run</Button>}
            </>
          )}
          {mode === "instructor" && (
            <>
              <Button variant="outline" size="sm" className="h-8" onClick={onSave}>Save</Button>
              <Button variant="outline" size="sm" className="h-8" onClick={() => router.push(`/problems/${problem.slug}?as=student`)}>Preview</Button>
              <Button size="sm" className="h-8" onClick={publish} disabled={busy || problem.status === "PUBLISHED"}>
                {problem.status === "PUBLISHED" ? "Published" : "Publish Problem"}
              </Button>
            </>
          )}
          {mode !== "instructor" && (
            <Button size="sm" className="h-8" onClick={onSubmit} disabled={!canAct} title={locked ? "Exam locked" : undefined}>
              {judge.judging ? "Judging…" : "Submit"}
            </Button>
          )}
        </div>
      </div>

      {desktop ? (
        <ResizablePanelGroup direction="horizontal" className="min-h-0 flex-1 gap-2 p-2">
          <ResizablePanel defaultSize={40} minSize={25} className="overflow-hidden rounded-xl border border-border bg-card">
            {descriptionNode}
          </ResizablePanel>
          <ResizableHandle withHandle />
          <ResizablePanel defaultSize={60} minSize={35}>
            <ResizablePanelGroup direction="vertical" className="gap-2">
              {/* Maximize/fold ghi đè flex của panel bằng style; bỏ ghi đè là về đúng kích thước đã kéo. Monaco tự co giãn nhờ automaticLayout. */}
              <ResizablePanel
                defaultSize={60}
                minSize={25}
                className="overflow-hidden rounded-xl border border-border bg-card"
                style={bottomMode === "max" ? { display: "none" } : bottomMode === "fold" ? { flex: "1 1 0px" } : undefined}
              >
                {editorNode}
              </ResizablePanel>
              <ResizableHandle className={bottomMode !== "normal" ? "hidden" : undefined} />
              <ResizablePanel
                defaultSize={40}
                minSize={15}
                className="overflow-hidden rounded-xl border border-border bg-card"
                style={bottomMode === "max" ? { flex: "1 1 0px" } : bottomMode === "fold" ? { flex: "0 0 auto" } : undefined}
              >
                {bottomNode}
              </ResizablePanel>
            </ResizablePanelGroup>
          </ResizablePanel>
        </ResizablePanelGroup>
      ) : (
        <div className="min-h-0 flex-1 space-y-2 overflow-auto p-2">
          <div className="h-[55vh] overflow-hidden rounded-xl border border-border bg-card">{descriptionNode}</div>
          <div className="h-[480px] overflow-hidden rounded-xl border border-border bg-card">{editorNode}</div>
          <div className="overflow-auto rounded-xl border border-border bg-card">{bottomNode}</div>
        </div>
      )}

      {examMode && contestId && (
        <>
          <ViolationDialog warning={proctor.warning} onClose={proctor.dismissWarning} />
          {proctor.disqualified && <DisqualifiedOverlay contestId={contestId} />}
          {!proctor.disqualified && timeUp && <TimeUpOverlay contestId={contestId} />}
          {!locked && proctor.fullscreenLost && <FullscreenOverlay onEnter={proctor.enterFullscreen} />}
        </>
      )}
    </div>
  );
}
