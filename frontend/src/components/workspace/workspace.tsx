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
import { DescriptionPanel } from "./description-panel";
import { ProblemSubmissions } from "./problem-submissions";
import { ResultPanel } from "./result-panel";
import { DisqualifiedOverlay, FullscreenOverlay, TimeUpOverlay, ViolationDialog } from "./violation-dialog";
import { useAutosave, type SaveStatus } from "@/hooks/use-autosave";
import { useCountdown } from "@/hooks/use-countdown";
import { useJudge } from "@/hooks/use-judge";
import { useMediaQuery } from "@/hooks/use-media-query";
import { useProctoring } from "@/hooks/use-proctoring";
import { errorMessage } from "@/lib/api";
import { fmtDuration } from "@/lib/format";
import { DRAFT_SYNC, loadDraft, RUN_ENABLED, saveDraft, updateProblem } from "@/lib/services";
import { FILE_NAME, LANG_PREF_KEY, LANGUAGES, TEMPLATES } from "@/lib/templates";
import { toast } from "@/lib/toast";
import { VERDICT } from "@/lib/verdict";
import type { ContestInfo, Language, ProblemFull, ProctoringEventType, Role, TestCase } from "@/types/api";

export type WorkspaceMode = "student" | "instructor" | "exam";
interface Props { problem: ProblemFull; testCases: TestCase[]; mode: WorkspaceMode; contest?: ContestInfo; role?: Role }

const STATUS_TEXT: Record<SaveStatus, string> = { saved: "Auto-saved", saving: "Saving…", offline: "Offline, đã lưu cục bộ" };
const EVENT_LABEL: Record<ProctoringEventType, string> = { WINDOW_BLUR: "Rời khỏi cửa sổ thi", WINDOW_FOCUS: "Quay lại cửa sổ thi", CLIPBOARD_PASTE: "Dán nội dung" };

export function Workspace({ problem: initialProblem, testCases: initialCases, mode, contest, role = "USER" }: Props) {
  const router = useRouter();
  const examMode = mode === "exam";
  const contestId = contest?.id;
  const desktop = useMediaQuery("(min-width: 1024px)");
  const [problem, setProblem] = useState(initialProblem);
  const [cases, setCases] = useState(initialCases);
  const [lang, setLang] = useState<Language>("JAVA");
  const [codes, setCodes] = useState<Record<Language, string>>({ ...TEMPLATES });
  const [lastRun, setLastRun] = useState(false);
  const [maximized, setMaximized] = useState(false);
  const [tab, setTab] = useState("result");
  const [busy, setBusy] = useState(false);

  // Ngôn ngữ ưu tiên (chọn ở trang danh sách hoặc lần làm bài trước)
  useEffect(() => {
    try { const l = localStorage.getItem(LANG_PREF_KEY) as Language | null; if (l && LANGUAGES.some((x) => x.value === l)) setLang(l); } catch { /* bỏ qua */ }
  }, []);
  const changeLang = (l: Language) => { setLang(l); try { localStorage.setItem(LANG_PREF_KEY, l); } catch { /* bỏ qua */ } };

  // ----- Phòng thi: đồng hồ theo giờ server + giám sát -----
  const left = useCountdown(examMode && contest ? Date.parse(contest.endTime) : null);
  const timeUp = examMode && left === 0;
  const proctor = useProctoring({
    contestId, enabled: examMode && !timeUp, initialCount: contest?.myViolations ?? 0,
    max: contest?.maxViolationsAllowed ?? 3, initialDisqualified: contest?.myStatus === "DISQUALIFIED",
  });
  const locked = examMode && (proctor.disqualified || timeUp);

  // ----- Auto-save: LocalStorage + (chỉ ở Exam) đồng bộ server 15s -----
  const storageKey = `draft:${contestId ?? "practice"}:${problem.id}:${lang}`;
  const remote = useMemo(
    () => (DRAFT_SYNC && examMode && contestId
      ? { load: () => loadDraft(contestId, problem.id, lang), save: (v: string) => saveDraft(contestId, problem.id, lang, v) }
      : undefined),
    [examMode, contestId, problem.id, lang],
  );
  const autosave = useAutosave({ storageKey, value: codes[lang], setValue: (v) => setCodes((c) => ({ ...c, [lang]: v })), remote });

  // Bị loại / hết giờ: đẩy bản nháp cuối cùng lên server (BRD: "tự động thu bài với mã nguồn hiện tại")
  const flushed = useRef(false);
  const { saveNow } = autosave;
  useEffect(() => { if (locked && !flushed.current) { flushed.current = true; void saveNow(); } }, [locked, saveNow]);

  // ----- Chấm bài -----
  const judge = useJudge(problem.id, contestId);
  const canAct = !locked && autosave.ready && !judge.judging;
  const onSubmit = () => { if (canAct) { setLastRun(false); setTab("result"); void judge.submit(lang, codes[lang]); } };
  const onRun = () => { if (canAct && RUN_ENABLED && !examMode) { setLastRun(true); setTab("result"); void judge.submit(lang, codes[lang], { run: true }); } };
  const onSave = () => { void saveNow(); toast("Đã lưu bản nháp.", "success"); };

  // Lỗi nộp bài (400 code rỗng, 403 bị loại / hết giờ / chưa đăng ký, mất mạng...) luôn hiện toast,
  // kể cả khi thí sinh đang đứng ở tab khác. Và 403 "disqualified" thì khoá giao diện như khi nhận cờ isDisqualified.
  useEffect(() => {
    if (!judge.error) return;
    toast(judge.error.message, "error");
    setTab("result");
    if (examMode && judge.error.status === 403 && /disqualified/i.test(judge.error.message)) proctor.markDisqualified();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [judge.error]);
  useEffect(() => { if (judge.timedOut) toast("Chấm bài lâu hơn dự kiến. Bài của bạn vẫn được lưu, xem kết quả trong Bài đã nộp.", "info"); }, [judge.timedOut]);
  useEffect(() => {
    if (!maximized) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setMaximized(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [maximized]);

  // Instructor: INSTRUCTOR gửi duyệt (REVIEWING), ADMIN phát hành (PUBLISHED) theo vòng đời đề bài
  const publish = async () => {
    const target = role === "ADMIN" ? "PUBLISHED" : "REVIEWING";
    setBusy(true);
    try {
      const p = await updateProblem(problem.id, { status: target });
      setProblem({ ...problem, ...p, status: p.status ?? target });
      toast(target === "PUBLISHED" ? "Đã phát hành đề bài." : "Đã gửi đề bài đi duyệt.", "success");
    } catch (e) { toast(errorMessage(e), "error"); }
    finally { setBusy(false); }
  };

  const crumbHref = examMode && contestId ? `/exam/${contestId}` : "/problems";
  const sh = examMode ? "Ctrl+Shift+Enter nộp bài" : "Ctrl+Enter chạy · Ctrl+Shift+Enter nộp · Ctrl+S lưu";
  const resultLabel = judge.result?.verdict ? ` (${VERDICT[judge.result.verdict].label})` : "";

  // ===== Các khối giao diện (dùng chung cho bố cục desktop và mobile) =====
  const descriptionNode = (
    <DescriptionPanel problem={problem} cases={cases} canEdit={mode === "instructor"} onProblemChange={setProblem} onCasesChange={setCases} />
  );
  const editorNode = (
    <div className={cn("flex h-full flex-col bg-card", maximized && "fixed inset-0 z-40")}>
      <div className="flex h-9 shrink-0 items-center gap-2 border-b px-3 font-mono text-xs text-muted-foreground">
        <span className="text-foreground">{FILE_NAME[lang]}</span><span>·</span>
        <span aria-live="polite" className={autosave.status === "offline" ? "text-amber-500" : ""}>{autosave.ready ? STATUS_TEXT[autosave.status] : "Đang tải bản nháp…"}</span>
        <span className="ml-2 hidden truncate xl:inline" title="Phím tắt">{sh}</span>
        <div className="ml-auto flex items-center gap-3">
          {!examMode && <button aria-label="Sao chép code" title="Sao chép code" onClick={() => { void navigator.clipboard?.writeText(codes[lang]); toast("Đã sao chép code.", "success"); }}><Copy className="h-3.5 w-3.5" /></button>}
          <button aria-label="Đặt lại template" title="Đặt lại template" disabled={locked} onClick={() => { if (window.confirm("Đặt lại code về template mẫu? Code hiện tại sẽ mất.")) setCodes((c) => ({ ...c, [lang]: TEMPLATES[lang] })); }}><RotateCcw className="h-3.5 w-3.5" /></button>
          <button aria-label={maximized ? "Thu nhỏ" : "Phóng to"} title="Phóng to / thu nhỏ (Esc)" onClick={() => setMaximized(!maximized)}>{maximized ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}</button>
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
      <TabsList className="m-2 bg-transparent">
        <TabsTrigger value="case">Testcase</TabsTrigger>
        <TabsTrigger value="result">Test Result{resultLabel}</TabsTrigger>
        <TabsTrigger value="subs">Submissions</TabsTrigger>
      </TabsList>
      <TabsContent value="case" className="space-y-2 p-4 text-xs">
        {cases.filter((c) => !c.isHidden).map((c, i) => (
          <div key={c.id}><p className="mb-1 text-muted-foreground">Case {i + 1}</p><pre className="rounded-md border bg-black/90 px-3 py-2 font-mono text-white">{c.input}</pre></div>
        ))}
        {cases.every((c) => c.isHidden) && <p className="text-muted-foreground">Chưa có testcase công khai.</p>}
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
      {/* ===== Thanh công cụ ===== */}
      <div className="flex min-h-12 shrink-0 flex-wrap items-center gap-3 border-b px-4 py-1.5">
        <Link href={crumbHref} className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"><List className="h-4 w-4" />{examMode ? "Kỳ thi" : "Problems"}</Link>
        <span className="text-muted-foreground">/</span>
        {examMode && contest && (
          <nav className="flex gap-1" aria-label="Chọn đề trong kỳ thi">
            {contest.problems.map((p) => (
              <Link key={p.id} href={`/exam/${contest.id}/problems/${p.id}`} title={p.title} aria-current={p.id === problem.id ? "page" : undefined}
                className={cn("grid h-7 w-7 place-items-center rounded font-mono text-xs", p.id === problem.id ? "bg-primary text-primary-foreground" : "bg-secondary hover:bg-accent")}>{p.label}</Link>
            ))}
          </nav>
        )}
        <span className="font-semibold">#{problem.id} {problem.title}</span>
        <DifficultyBadge value={problem.difficulty} />
        <label className="flex items-center gap-2 rounded-md border bg-card px-2 py-1 text-sm">
          <span className={cn("h-2 w-2 rounded-full", LANGUAGES.find((l) => l.value === lang)?.dot)} />
          <select value={lang} onChange={(e) => changeLang(e.target.value as Language)} aria-label="Ngôn ngữ" className="bg-transparent outline-none">
            {LANGUAGES.map((l) => <option key={l.value} value={l.value} className="bg-card">{l.label}</option>)}
          </select>
        </label>
        {mode === "student" && role !== "USER" && (
          <Link href={`/problems/${problem.slug}`} className="text-xs text-primary underline">Quay lại chế độ giảng viên</Link>
        )}

        <div className="ml-auto flex items-center gap-2">
          {examMode && (
            <>
              <Popover>
                <PopoverTrigger asChild>
                  <button aria-label="Nhật ký giám sát"
                    className={cn("flex items-center gap-1 rounded-md px-2 py-1 text-xs", proctor.count > 0 ? "bg-destructive/15 text-destructive" : "bg-secondary text-muted-foreground")}>
                    <Shield className="h-3.5 w-3.5" />Vi phạm {proctor.count}/{proctor.max}
                  </button>
                </PopoverTrigger>
                <PopoverContent align="end" className="w-80 space-y-2 text-xs">
                  <p className="font-medium">Nhật ký giám sát của bạn</p>
                  <p className="text-muted-foreground">Máy chủ là bên duy nhất tính vi phạm. Rời cửa sổ hoặc thoát toàn màn hình quá 1,5 giây, hoặc dán code, đều bị tính.</p>
                  {proctor.log.length === 0 && <p className="text-muted-foreground">Chưa có sự kiện nào.</p>}
                  <ul className="max-h-48 space-y-1 overflow-auto">
                    {proctor.log.map((l, i) => (
                      <li key={i} className="rounded bg-secondary px-2 py-1">
                        <span className="font-mono text-muted-foreground">{new Date(l.at).toLocaleTimeString("vi-VN", { hour12: false })}</span> {EVENT_LABEL[l.type]}
                        <div className="text-muted-foreground">{l.message}</div>
                      </li>
                    ))}
                  </ul>
                </PopoverContent>
              </Popover>
              <span className={cn("flex items-center gap-1.5 rounded-md border bg-card px-3 py-1 font-mono text-sm", left != null && left < 5 * 60_000 && "border-destructive text-destructive")} aria-label="Thời gian còn lại">
                <Timer className="h-4 w-4" />{left == null ? "--:--" : fmtDuration(left)}
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
            <Button size="sm" className="h-8" onClick={onSubmit} disabled={!canAct} title={locked ? "Bài thi đã bị khoá" : undefined}>
              {judge.judging ? "Judging…" : "Submit Solution"}
            </Button>
          )}
        </div>
      </div>

      {/* ===== Nội dung: desktop chia đôi có kéo thả, mobile xếp dọc ===== */}
      {desktop ? (
        <ResizablePanelGroup direction="horizontal" className="min-h-0 flex-1 gap-1 p-2">
          <ResizablePanel defaultSize={40} minSize={25} className="overflow-hidden rounded-lg border bg-card">{descriptionNode}</ResizablePanel>
          <ResizableHandle withHandle />
          <ResizablePanel defaultSize={60} minSize={35}>
            <ResizablePanelGroup direction="vertical" className="gap-1">
              <ResizablePanel defaultSize={60} minSize={25} className="overflow-hidden rounded-lg border bg-card">{editorNode}</ResizablePanel>
              <ResizableHandle />
              <ResizablePanel defaultSize={40} minSize={15} className="overflow-auto rounded-lg border bg-card">{bottomNode}</ResizablePanel>
            </ResizablePanelGroup>
          </ResizablePanel>
        </ResizablePanelGroup>
      ) : (
        <div className="min-h-0 flex-1 space-y-2 overflow-auto p-2">
          <div className="h-[55vh] overflow-hidden rounded-lg border bg-card">{descriptionNode}</div>
          <div className="h-[480px] overflow-hidden rounded-lg border bg-card">{editorNode}</div>
          <div className="overflow-auto rounded-lg border bg-card">{bottomNode}</div>
        </div>
      )}

      {/* ===== Lớp phủ phòng thi ===== */}
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
