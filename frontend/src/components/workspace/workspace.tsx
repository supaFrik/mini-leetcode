"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Copy, List, Maximize2, Minimize2, RotateCcw, Shield, Timer } from "lucide-react";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
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
import { DRAFT_SYNC, finalizeContestSubmission, loadDraft, RUN_ENABLED, saveDraft, updateProblem } from "@/lib/services";
import { FILE_NAME, LANG_PREF_KEY, LANGUAGES, TEMPLATES } from "@/lib/templates";
import { toast } from "@/lib/toast";
import { VERDICT } from "@/lib/verdict";
import type { ContestInfo, Language, ProblemFull, ProctoringEventType, Role, TestCase } from "@/types/api";

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
  const onSubmit = () => { if (canAct) { setLastRun(false); setTab("result"); void judge.submit(lang, codes[lang]); } };
  const onRun = () => { if (canAct && RUN_ENABLED && !examMode) { setLastRun(true); setTab("result"); void judge.submit(lang, codes[lang], { run: true }); } };
  const onSave = () => { void saveNow(); toast("Draft saved.", "success"); };

  useEffect(() => {
    if (!judge.error) return;
    toast(judge.error.message, "error");
    setTab("result");
    if (examMode && judge.error.status === 403 && /disqualified/i.test(judge.error.message)) proctor.markDisqualified();
  }, [judge.error]);
  useEffect(() => { if (judge.timedOut) toast("Judging is taking longer than expected. Your submission has been saved; check Submissions.", "info"); }, [judge.timedOut]);
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
      const p = await updateProblem(problem.id, { status: target });
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
      onProblemChange={setProblem}
      showTestCases={false}
      showResult={false}
    />
  );
  const editorNode = (
    <div className={cn("flex h-full flex-col bg-card", maximized && "fixed inset-0 z-40")}>
      <div className="flex h-9 shrink-0 items-center gap-2 border-b border-border px-3 font-mono text-body-sm text-mute">
        <span className="text-ink">{FILE_NAME[lang]}</span>
        <span className="text-faint">·</span>
        <span aria-live="polite" className={cn(autosave.status === "offline" && "text-[#8a5a00]")}>
          {autosave.ready ? STATUS_TEXT[autosave.status] : "Loading draft…"}
        </span>
        <span className="ml-2 hidden truncate xl:inline" title="Shortcuts">{sh}</span>
        <div className="ml-auto flex items-center gap-3">
          {!examMode && (
            <button aria-label="Copy code" title="Copy code" onClick={() => { void navigator.clipboard?.writeText(codes[lang]); toast("Code copied.", "success"); }} className="text-mute transition-colors hover:text-ink">
              <Copy className="h-3.5 w-3.5" />
            </button>
          )}
          <button aria-label="Reset template" title="Reset template" disabled={locked} onClick={() => { if (window.confirm("Reset code to default template? Current changes will be lost.")) setCodes((c) => ({ ...c, [lang]: TEMPLATES[lang] })); }} className="text-mute transition-colors hover:text-ink disabled:opacity-40">
            <RotateCcw className="h-3.5 w-3.5" />
          </button>
          <button aria-label={maximized ? "Minimize" : "Maximize"} title="Maximize / minimize (Esc)" onClick={() => setMaximized(!maximized)} className="text-mute transition-colors hover:text-ink">
            {maximized ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>
      <div className="min-h-0 flex-1">
        <CodeEditor language={lang} value={codes[lang]} readOnly={locked || !autosave.ready} examMode={examMode}
          onChange={(v) => setCodes((c) => ({ ...c, [lang]: v }))} onSave={onSave} onRun={onRun} onSubmit={onSubmit} onPasteAttempt={proctor.reportPaste} />
      </div>
    </div>
  );
  const bottomNode = (
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="m-2 border-b-0">
          <TabsTrigger value="case">Testcase</TabsTrigger>
          <TabsTrigger value="result">Test Result{resultLabel}</TabsTrigger>
          <TabsTrigger value="subs">Submissions</TabsTrigger>
        </TabsList>
        <TabsContent value="case" className="space-y-2 p-4 text-body-sm">
          {cases.filter((c) => !c.isHidden).map((c, i) => (
            <div key={c.id}>
              <p className="mb-1 text-mute">Case {i + 1}</p>
              <pre className="overflow-auto whitespace-pre-wrap rounded-sm border border-border bg-card px-3 py-2 font-mono text-code text-ink">{c.input}</pre>
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
        <label className="flex items-center gap-2 rounded-sm border border-border bg-card px-2 py-1 text-body-md">
          <span className={cn("h-2 w-2 rounded-full", LANGUAGES.find((l) => l.value === lang)?.dot)} />
          <select value={lang} onChange={(e) => changeLang(e.target.value as Language)} aria-label="Language" className="bg-transparent text-ink outline-none">
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value} className="bg-card">
                {l.label}
              </option>
            ))}
          </select>
        </label>
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
              {judge.judging ? "Judging…" : "Submit Solution"}
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
              <ResizablePanel defaultSize={60} minSize={25} className="overflow-hidden rounded-xl border border-border bg-card">
                {editorNode}
              </ResizablePanel>
              <ResizableHandle />
              <ResizablePanel defaultSize={40} minSize={15} className="overflow-auto rounded-xl border border-border bg-card">
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
