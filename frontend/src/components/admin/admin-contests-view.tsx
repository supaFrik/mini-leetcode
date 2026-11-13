"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowDown, ArrowUp, ExternalLink, Loader2, Lock, Plus, Search, Trash2, UserMinus, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { toast } from "@/lib/toast";
import { useUser } from "@/hooks/use-user";
import { AdminNav } from "./admin-nav";
import {
  addContestParticipants,
  createContest,
  deleteContest,
  fetchAdminContest,
  fetchAdminContests,
  fetchAdminProblems,
  fetchContestParticipants,
  removeContestParticipant,
  updateContest,
} from "@/lib/services";
import type {
  AdminContestDetail,
  AdminContestSummary,
  AdminParticipant,
  BulkOutcome,
  BulkParticipantsResult,
  ContestStatus,
  Difficulty,
  ProblemFull,
  RegistrationMode,
  ScoringPolicy,
} from "@/types/api";

const STATUSES: { value: ContestStatus; label: string }[] = [
  { value: "RUNNING", label: "Running" },
  { value: "SCHEDULED", label: "Scheduled" },
  { value: "COMPLETED", label: "Completed" },
];

const STATUS_TONE: Record<ContestStatus, "success" | "warning" | "outline"> = {
  RUNNING: "success",
  SCHEDULED: "warning",
  COMPLETED: "outline",
};

const OUTCOME_LABEL: Record<BulkOutcome, string> = {
  ADDED: "added",
  ALREADY_REGISTERED: "already registered",
  NOT_FOUND: "not found",
  BLOCKED: "blocked",
};

const OUTCOME_TONE: Record<BulkOutcome, "success" | "secondary" | "destructive" | "warning"> = {
  ADDED: "success",
  ALREADY_REGISTERED: "secondary",
  NOT_FOUND: "destructive",
  BLOCKED: "warning",
};

/* ------------------------------------------------------------------ *
 * Giờ: server lưu UTC; ô nhập dùng giờ địa phương của người xem.
 * ------------------------------------------------------------------ */

/** "GMT+7 (Asia/Saigon)" — hiện cạnh ô nhập giờ để không ai nhầm múi giờ. */
function zoneLabel(): string {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const offset = -new Date().getTimezoneOffset();
  const sign = offset >= 0 ? "+" : "-";
  const h = Math.floor(Math.abs(offset) / 60);
  const m = Math.abs(offset) % 60;
  return `GMT${sign}${h}${m ? `:${String(m).padStart(2, "0")}` : ""}${tz ? ` (${tz})` : ""}`;
}

/** ISO UTC của API → giá trị `datetime-local` theo giờ máy người xem. */
function toLocalInput(iso: string): string {
  const ms = parseApiDate(iso);
  if (Number.isNaN(ms)) return "";
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** `datetime-local` (giờ địa phương) → ISO UTC cho API. */
function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const ms = new Date(value).getTime();
  return Number.isNaN(ms) ? null : new Date(ms).toISOString();
}

function formatWhen(iso: string): string {
  const ms = parseApiDate(iso);
  if (Number.isNaN(ms)) return "—";
  return new Date(ms).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/** A..Z rồi AA, AB… — giống server. */
function labelFor(index: number): string {
  let s = "";
  let n = index;
  do {
    s = String.fromCharCode(65 + (n % 26)) + s;
    n = Math.floor(n / 26) - 1;
  } while (n >= 0);
  return s;
}

function nextFreeLabel(used: string[]): string {
  const taken = new Set(used.map((l) => l.toUpperCase()));
  for (let i = 0; ; i++) {
    const l = labelFor(i);
    if (!taken.has(l)) return l;
  }
}

/* ------------------------------------------------------------------ *
 * Trang danh sách
 * ------------------------------------------------------------------ */

export function AdminContestsView() {
  const { user } = useUser();
  const canDelete = user?.role === "ADMIN";
  const [rows, setRows] = useState<AdminContestSummary[] | null>(null);
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<ContestStatus | "">("");
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<AdminContestSummary | null>(null);
  const [pending, setPending] = useState<number | null>(null);

  const load = useCallback(() => {
    setError(null);
    fetchAdminContests({ status: status || undefined, search: search || undefined })
      .then(setRows)
      .catch((e) => setError(errorMessage(e)));
  }, [status, search]);

  useEffect(() => {
    load();
  }, [load]);

  // Tìm theo tên sau khi ngừng gõ một nhịp, không gọi API theo từng phím.
  useEffect(() => {
    const t = setTimeout(() => setSearch(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  const remove = async (c: AdminContestSummary) => {
    setPending(c.id);
    try {
      await deleteContest(c.id);
      toast("Contest deleted.", "success");
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setPending(null);
    }
  };

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <AdminNav />
      <div className="flex flex-wrap items-center gap-2">
        <Button onClick={() => setCreating(true)} className="order-last">
          <Plus className="h-4 w-4" />
          New contest
        </Button>
        <div className="relative min-w-0 flex-1 basis-64">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search contests"
            aria-label="Search contests"
            className="h-10 rounded-sm bg-card pl-9 text-body-md text-ink"
          />
        </div>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as ContestStatus | "")}
          aria-label="Filter by status"
          className="h-10 rounded-sm border border-border bg-card px-2.5 text-body-md text-ink"
        >
          <option value="">Any status</option>
          {STATUSES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </div>

      {error && (
        <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
          {error}
        </p>
      )}

      <section aria-label="Contest administration" className="overflow-hidden rounded-xl border border-border bg-card">
        <div className="hidden items-center gap-3 border-b border-border px-5 py-3 text-[11px] font-medium uppercase tracking-wider text-mute md:grid md:grid-cols-[minmax(0,1fr)_104px_minmax(0,220px)_72px_88px_88px]">
          <span>Title</span>
          <span>Status</span>
          <span>When</span>
          <span>Problems</span>
          <span>Participants</span>
          <span className="text-right">Actions</span>
        </div>

        {rows === null && !error && (
          <div className="space-y-2 p-5">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-9 animate-pulse rounded bg-hairline-soft" />
            ))}
          </div>
        )}

        {rows?.map((c) => (
          <div
            key={c.id}
            className="grid grid-cols-1 items-center gap-2 border-t border-border px-5 py-3.5 md:grid-cols-[minmax(0,1fr)_104px_minmax(0,220px)_72px_88px_88px] md:gap-3"
          >
            <span className="min-w-0">
              <button
                onClick={() => setEditingId(c.id)}
                className="block max-w-full truncate text-left text-body-md text-ink transition-colors hover:text-link"
              >
                {c.title}
              </button>
              {c.registrationMode === "INVITE_ONLY" && (
                <span className="mt-1 inline-flex items-center gap-1 rounded-full bg-hairline-soft px-2 py-px font-mono text-[10px] text-mute">
                  <Lock className="h-2.5 w-2.5" /> Invite only
                </span>
              )}
            </span>
            <span>
              <Badge variant={STATUS_TONE[c.status]} className="font-mono text-[11px]">
                {c.status}
              </Badge>
            </span>
            <span className="text-body-sm text-mute">
              {formatWhen(c.startTime)} → {formatWhen(c.endTime)}
            </span>
            <span className="font-mono text-body-sm text-mute">{c.problemCount}</span>
            <span className="font-mono text-body-sm text-mute">{c.participantCount}</span>
            <span className="flex items-center justify-start gap-1 md:justify-end">
              {canDelete && c.status === "SCHEDULED" && (
                <Button
                  size="icon-sm"
                  variant="ghost"
                  disabled={pending === c.id}
                  onClick={() => setConfirmDelete(c)}
                  aria-label={`Delete ${c.title}`}
                  title="Delete"
                  className="text-mute hover:text-destructive"
                >
                  {pending === c.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                </Button>
              )}
              <Button asChild size="icon-sm" variant="ghost" title="Open contest page">
                <Link href={`/exam/${c.id}`} aria-label={`Open ${c.title}`}>
                  <ExternalLink className="h-3.5 w-3.5" />
                </Link>
              </Button>
            </span>
          </div>
        ))}

        {rows && rows.length === 0 && (
          <div className="border-t border-border p-12 text-center">
            <p className="font-medium text-ink">{search || status ? "No contests match these filters" : "No contests yet"}</p>
            <p className="mx-auto mt-1 max-w-[44ch] text-body-md text-mute">
              {search || status
                ? "Adjust the search or clear the filter to see everything."
                : "Create the first contest, add published problems and invite students."}
            </p>
          </div>
        )}
      </section>

      <AlertDialog open={confirmDelete !== null} onOpenChange={(open) => !open && setConfirmDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete &ldquo;{confirmDelete?.title}&rdquo;?</AlertDialogTitle>
            <AlertDialogDescription>
              This permanently removes the contest, its problem list and its {confirmDelete?.participantCount ?? 0}{" "}
              registration(s). The problems themselves are kept. Only scheduled contests without submissions can be
              deleted.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={() => {
                const target = confirmDelete;
                setConfirmDelete(null);
                if (target) void remove(target);
              }}
            >
              Delete contest
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {(creating || editingId !== null) && (
        <ContestEditor
          contestId={editingId}
          onClose={() => {
            setCreating(false);
            setEditingId(null);
            load();
          }}
          onCreated={(id) => {
            setCreating(false);
            setEditingId(id);
            load();
          }}
          onSaved={load}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Trình soạn kỳ thi
 * ------------------------------------------------------------------ */

interface ProblemRow {
  problemId: number;
  title: string;
  difficulty: Difficulty;
  label: string;
}

interface Form {
  title: string;
  start: string;
  end: string;
  scoringPolicy: ScoringPolicy;
  maxViolationsAllowed: number;
  registrationMode: RegistrationMode;
  problems: ProblemRow[];
}

function emptyForm(): Form {
  // Mặc định: ngày mai 08:00–10:00 giờ địa phương.
  const start = new Date();
  start.setDate(start.getDate() + 1);
  start.setHours(8, 0, 0, 0);
  const end = new Date(start.getTime() + 2 * 3600_000);
  return {
    title: "",
    start: toLocalInput(start.toISOString()),
    end: toLocalInput(end.toISOString()),
    scoringPolicy: "ACM_ICPC",
    maxViolationsAllowed: 3,
    registrationMode: "OPEN",
    problems: [],
  };
}

function formFrom(d: AdminContestDetail): Form {
  return {
    title: d.title,
    start: toLocalInput(d.startTime),
    end: toLocalInput(d.endTime),
    scoringPolicy: d.scoringPolicy,
    maxViolationsAllowed: d.maxViolationsAllowed,
    registrationMode: d.registrationMode,
    problems: d.problems.map((p) => ({ problemId: p.problemId, title: p.title, difficulty: p.difficulty, label: p.label })),
  };
}

function ContestEditor({
  contestId,
  onClose,
  onCreated,
  onSaved,
}: {
  contestId: number | null;
  onClose: () => void;
  onCreated: (id: number) => void;
  onSaved: () => void;
}) {
  const [detail, setDetail] = useState<AdminContestDetail | null>(null);
  const [form, setForm] = useState<Form>(emptyForm);
  const [tab, setTab] = useState("details");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (contestId === null) {
      setDetail(null);
      setForm(emptyForm());
      return;
    }
    let live = true;
    setLoadError(null);
    fetchAdminContest(contestId)
      .then((d) => {
        if (!live) return;
        setDetail(d);
        setForm(formFrom(d));
      })
      .catch((e) => live && setLoadError(errorMessage(e)));
    return () => {
      live = false;
    };
  }, [contestId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !document.querySelector("[role=alertdialog]")) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const status: ContestStatus = detail?.status ?? "SCHEDULED";
  const running = status === "RUNNING";
  const completed = status === "COMPLETED";
  const locked = running || completed; // mọi trường trừ giờ kết thúc

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }));

  // Kiểm tra ngay trên form, cùng luật với server.
  const startIso = fromLocalInput(form.start);
  const endIso = fromLocalInput(form.end);
  const timeError = useMemo(() => {
    if (!startIso || !endIso) return "Start and end time are required";
    if (Date.parse(endIso) <= Date.parse(startIso)) return "End time must be after start time";
    const startChanged = !detail || toLocalInput(detail.startTime) !== form.start;
    if (!locked && startChanged && Date.parse(startIso) <= Date.now()) return "Start time must be in the future";
    if (running && detail && Date.parse(endIso) < parseApiDate(detail.endTime))
      return "The end time can only be extended while the contest is running";
    return null;
  }, [startIso, endIso, detail, form.start, locked, running]);

  const labelError = useMemo(() => {
    const seen = new Set<string>();
    for (const p of form.problems) {
      const l = p.label.trim().toUpperCase();
      if (!/^[A-Z0-9]{1,3}$/.test(l)) return `Label "${p.label}" must be 1–3 letters or digits`;
      if (seen.has(l)) return `Label "${l}" is used more than once`;
      seen.add(l);
    }
    return null;
  }, [form.problems]);

  const valid = form.title.trim().length > 0 && !timeError && !labelError;

  const save = async () => {
    if (!startIso || !endIso) return;
    setBusy(true);
    setError(null);
    const body = {
      title: form.title.trim(),
      startTime: startIso,
      endTime: endIso,
      scoringPolicy: form.scoringPolicy,
      maxViolationsAllowed: form.maxViolationsAllowed,
      registrationMode: form.registrationMode,
      problems: form.problems.map((p, i) => ({ problemId: p.problemId, label: p.label.trim().toUpperCase(), orderIndex: i })),
      version: detail?.version,
    };
    // Đang thi: gửi lại đúng giờ bắt đầu cũ (đến từng giây) để server chỉ thấy thay đổi giờ kết thúc.
    if (detail && locked) body.startTime = detail.startTime;
    try {
      if (detail) {
        const saved = await updateContest(detail.id, body);
        setDetail(saved);
        setForm(formFrom(saved));
        toast("Contest saved.", "success");
        onSaved();
      } else {
        const created = await createContest(body);
        toast("Contest created.", "success");
        onCreated(created.id);
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-ink/20" role="dialog" aria-modal="true" aria-label="Contest editor">
      <div className="flex h-full w-full max-w-3xl flex-col border-l border-border bg-background">
        <header className="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
          <div className="min-w-0">
            <h2 className="truncate text-heading-md text-ink">{detail ? detail.title : form.title || "Untitled"}</h2>
          </div>
          <div className="flex items-center gap-2">
            {detail && (
              <Badge variant={STATUS_TONE[status]} className="font-mono text-[11px]">
                {status}
              </Badge>
            )}
            <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label="Close editor">
              <X className="h-4 w-4" />
            </Button>
          </div>
        </header>

        {loadError && (
          <p role="alert" className="m-5 rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
            {loadError}
          </p>
        )}

        {(running || completed) && (
          <p
            role="status"
            className="mx-5 mt-3 flex items-start gap-2 rounded-sm border border-border bg-hairline-soft px-3 py-2 text-body-sm text-ink"
          >
            <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-mute" />
            {running
              ? "This contest is running. Only the end time can be extended; problems, scoring, start time and the violation limit are locked."
              : "This contest has ended. It is read-only."}
          </p>
        )}

        <Tabs value={tab} onValueChange={setTab} className="flex min-h-0 flex-1 flex-col">
          <TabsList className="mx-5 mt-3 self-start">
            <TabsTrigger value="details">Details</TabsTrigger>
            <TabsTrigger value="problems">Problems ({form.problems.length})</TabsTrigger>
            <TabsTrigger value="participants" disabled={!detail}>
              Participants
            </TabsTrigger>
          </TabsList>

          <TabsContent value="details" className="mt-0 min-h-0 flex-1 overflow-y-auto data-[state=inactive]:hidden">
            <DetailsForm form={form} set={set} locked={locked} completed={completed} timeError={timeError} isNew={!detail} />
          </TabsContent>

          <TabsContent value="problems" className="mt-0 min-h-0 flex-1 overflow-y-auto data-[state=inactive]:hidden">
            <ProblemsEditor
              problems={form.problems}
              onChange={(p) => set("problems", p)}
              locked={locked}
              labelError={labelError}
            />
          </TabsContent>

          <TabsContent value="participants" className="mt-0 min-h-0 flex-1 overflow-y-auto data-[state=inactive]:hidden">
            {detail && <ParticipantsPanel contest={detail} onChanged={onSaved} />}
          </TabsContent>
        </Tabs>

        {tab !== "participants" && !completed && (
          <footer className="flex flex-wrap items-center justify-end gap-3 border-t border-border px-5 py-3">
            {error && (
              <p role="alert" className="mr-auto text-body-sm text-destructive">
                {error}
              </p>
            )}
            {!detail && form.problems.length === 0 && !error && (
              <p className="mr-auto text-body-sm text-mute">You can add problems now or later, before the start time.</p>
            )}
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={save} disabled={busy || !valid}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              {detail ? (running ? "Save end time" : "Save changes") : "Create contest"}
            </Button>
          </footer>
        )}
      </div>
    </div>
  );
}

function DetailsForm({
  form,
  set,
  locked,
  completed,
  timeError,
  isNew,
}: {
  form: Form;
  set: <K extends keyof Form>(k: K, v: Form[K]) => void;
  locked: boolean;
  completed: boolean;
  timeError: string | null;
  isNew: boolean;
}) {
  const zone = useMemo(zoneLabel, []);
  const field = "rounded-sm bg-card text-body-md text-ink";
  const select = "h-10 w-full rounded-sm border border-border bg-card px-2.5 text-body-md text-ink disabled:opacity-60";
  return (
    <div className="space-y-5 px-5 py-5">
      <div className="space-y-2">
        <label htmlFor="contest-title" className="eyebrow block text-mute">
          Title
        </label>
        <Input
          id="contest-title"
          value={form.title}
          disabled={locked}
          onChange={(e) => set("title", e.target.value)}
          placeholder="Giữa kỳ OOP"
          maxLength={200}
          className={field}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <label htmlFor="contest-start" className="eyebrow block text-mute">
            Start <span className="normal-case tracking-normal text-faint">· {zone}</span>
          </label>
          <Input
            id="contest-start"
            type="datetime-local"
            value={form.start}
            disabled={locked}
            onChange={(e) => set("start", e.target.value)}
            className={field}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor="contest-end" className="eyebrow block text-mute">
            End <span className="normal-case tracking-normal text-faint">· {zone}</span>
          </label>
          <Input
            id="contest-end"
            type="datetime-local"
            value={form.end}
            disabled={completed}
            onChange={(e) => set("end", e.target.value)}
            aria-invalid={timeError ? true : undefined}
            aria-describedby={timeError ? "contest-time-error" : undefined}
            className={field}
          />
        </div>
      </div>
      {timeError && (
        <p id="contest-time-error" role="alert" className="-mt-2 text-body-sm text-destructive">
          {timeError}
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="space-y-2">
          <label htmlFor="contest-scoring" className="eyebrow block text-mute">
            Scoring
          </label>
          <select
            id="contest-scoring"
            value={form.scoringPolicy}
            disabled={locked}
            onChange={(e) => set("scoringPolicy", e.target.value as ScoringPolicy)}
            className={select}
          >
            <option value="ACM_ICPC">ACM ICPC (solved, then penalty)</option>
            <option value="PARTIAL_IOI">IOI (partial points)</option>
          </select>
        </div>
        <div className="space-y-2">
          <label htmlFor="contest-violations" className="eyebrow block text-mute">
            Violations allowed
          </label>
          <Input
            id="contest-violations"
            type="number"
            min={0}
            max={100}
            value={form.maxViolationsAllowed}
            disabled={locked}
            onChange={(e) => set("maxViolationsAllowed", Math.max(0, Math.min(100, Number(e.target.value) || 0)))}
            className={field}
          />
        </div>
        <div className="space-y-2">
          <label htmlFor="contest-registration" className="eyebrow block text-mute">
            Registration
          </label>
          <select
            id="contest-registration"
            value={form.registrationMode}
            disabled={locked}
            onChange={(e) => set("registrationMode", e.target.value as RegistrationMode)}
            className={select}
          >
            <option value="OPEN">Open — students register themselves</option>
            <option value="INVITE_ONLY">Invite only — staff adds participants</option>
          </select>
        </div>
      </div>

      {isNew && form.registrationMode === "INVITE_ONLY" && (
        <p className="text-body-sm text-mute">Add participants from the Participants tab after the contest is created.</p>
      )}
    </div>
  );
}

function ProblemsEditor({
  problems,
  onChange,
  locked,
  labelError,
}: {
  problems: ProblemRow[];
  onChange: (p: ProblemRow[]) => void;
  locked: boolean;
  labelError: string | null;
}) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<ProblemFull[] | null>(null);
  const [searching, setSearching] = useState(false);

  // Chỉ bài PUBLISHED mới được đưa vào kỳ thi.
  useEffect(() => {
    if (locked) return;
    let live = true;
    setSearching(true);
    const t = setTimeout(() => {
      fetchAdminProblems({ page: 1, q: q.trim(), difficulty: "", status: "PUBLISHED", tags: [] })
        .then((page) => live && setResults(page.content))
        .catch(() => live && setResults([]))
        .finally(() => live && setSearching(false));
    }, 250);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [q, locked]);

  const chosen = new Set(problems.map((p) => p.problemId));
  const move = (i: number, delta: number) => {
    const next = [...problems];
    const [row] = next.splice(i, 1);
    next.splice(i + delta, 0, row);
    onChange(next);
  };

  return (
    <div className="space-y-5 px-5 py-5">
      <section aria-label="Contest problems" className="overflow-hidden rounded-lg border border-border bg-card">
        {problems.length === 0 && (
          <p className="p-6 text-center text-body-md text-mute">
            No problems yet. A contest needs at least one problem before it starts.
          </p>
        )}
        {problems.map((p, i) => (
          <div key={p.problemId} className="flex items-center gap-3 border-b border-border px-4 py-2.5 last:border-b-0">
            <Input
              value={p.label}
              disabled={locked}
              onChange={(e) => onChange(problems.map((r, j) => (j === i ? { ...r, label: e.target.value.toUpperCase() } : r)))}
              aria-label={`Label for ${p.title}`}
              maxLength={3}
              className="h-8 w-14 rounded-sm bg-background text-center font-mono text-body-md text-ink"
            />
            <span className="min-w-0 flex-1 truncate text-body-md text-ink">{p.title}</span>
            <DifficultyBadge value={p.difficulty} />
            {!locked && (
              <span className="flex items-center gap-0.5">
                <Button size="icon-sm" variant="ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label={`Move ${p.title} up`}>
                  <ArrowUp className="h-3.5 w-3.5" />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  disabled={i === problems.length - 1}
                  onClick={() => move(i, 1)}
                  aria-label={`Move ${p.title} down`}
                >
                  <ArrowDown className="h-3.5 w-3.5" />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  onClick={() => onChange(problems.filter((_, j) => j !== i))}
                  aria-label={`Remove ${p.title}`}
                  className="text-mute hover:text-destructive"
                >
                  <X className="h-3.5 w-3.5" />
                </Button>
              </span>
            )}
          </div>
        ))}
      </section>
      {labelError && (
        <p role="alert" className="-mt-2 text-body-sm text-destructive">
          {labelError}
        </p>
      )}

      {!locked && (
        <section aria-label="Problem picker" className="space-y-2">
          <label htmlFor="contest-problem-search" className="eyebrow block text-mute">
            Add published problems
          </label>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
            <Input
              id="contest-problem-search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search published problems"
              className="h-10 rounded-sm bg-card pl-9 text-body-md text-ink"
            />
          </div>
          <div className="max-h-72 overflow-y-auto rounded-lg border border-border bg-card">
            {searching && results === null && <p className="p-4 text-body-sm text-mute">Searching…</p>}
            {results?.length === 0 && <p className="p-4 text-body-sm text-mute">No published problems match.</p>}
            {results?.map((r) => (
              <div key={r.id} className="flex items-center gap-3 border-b border-border px-4 py-2 last:border-b-0">
                <span className="min-w-0 flex-1 truncate text-body-md text-ink">{r.title}</span>
                <DifficultyBadge value={r.difficulty} />
                <Button
                  size="sm"
                  variant="outline"
                  disabled={chosen.has(r.id)}
                  onClick={() =>
                    onChange([
                      ...problems,
                      { problemId: r.id, title: r.title, difficulty: r.difficulty, label: nextFreeLabel(problems.map((p) => p.label)) },
                    ])
                  }
                  aria-label={`Add ${r.title}`}
                  className="h-7 px-2 text-body-sm"
                >
                  {chosen.has(r.id) ? "Added" : "Add"}
                </Button>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function ParticipantsPanel({ contest, onChanged }: { contest: AdminContestDetail; onChanged: () => void }) {
  const [rows, setRows] = useState<AdminParticipant[] | null>(null);
  const [entries, setEntries] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<BulkParticipantsResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<AdminParticipant | null>(null);
  const completed = contest.status === "COMPLETED";

  const load = useCallback(() => {
    fetchContestParticipants(contest.id)
      .then(setRows)
      .catch((e) => setError(errorMessage(e)));
  }, [contest.id]);

  useEffect(() => {
    load();
  }, [load]);

  const add = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await addContestParticipants(contest.id, entries);
      setResult(r);
      if (r.added > 0) {
        toast(`${r.added} participant(s) added.`, "success");
        onChanged();
      }
      // Giữ lại những dòng chưa thêm được để sửa rồi thử lại.
      setEntries(
        r.lines
          .filter((l) => l.outcome === "NOT_FOUND" || l.outcome === "BLOCKED")
          .map((l) => l.input)
          .join("\n")
      );
      load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (p: AdminParticipant) => {
    try {
      await removeContestParticipant(contest.id, p.userId);
      toast(`${p.username} removed.`, "success");
      onChanged();
      load();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const lineCount = entries.split(/[\n,;]+/).filter((s) => s.trim()).length;

  return (
    <div className="space-y-5 px-5 py-5">
      {!completed && (
        <section aria-label="Participant entry" className="space-y-2">
          <label htmlFor="contest-entries" className="eyebrow block text-mute">
            Add participants
          </label>
          <Textarea
            id="contest-entries"
            value={entries}
            onChange={(e) => setEntries(e.target.value)}
            rows={5}
            placeholder={"sv01\nsv02@school.edu.vn, sv03"}
            className="rounded-sm bg-card font-mono text-code text-ink"
            aria-describedby="contest-entries-help"
          />
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p id="contest-entries-help" className="text-body-sm text-mute">
              Usernames or emails, one per line or comma-separated (up to 500).{" "}
              {contest.registrationMode === "OPEN"
                ? "Students can also register themselves."
                : "This contest is invite-only: only people on this list can take part."}
            </p>
            <Button onClick={add} disabled={busy || lineCount === 0 || lineCount > 500}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              Add {lineCount > 0 ? lineCount : ""}
            </Button>
          </div>
          {lineCount > 500 && (
            <p role="alert" className="text-body-sm text-destructive">
              At most 500 entries per request ({lineCount} entered).
            </p>
          )}
        </section>
      )}

      {error && (
        <p role="alert" className="rounded-sm border border-destructive/25 bg-destructive/5 px-3 py-2 text-body-md text-destructive">
          {error}
        </p>
      )}

      {result && (
        <section aria-label="Add results" className="rounded-lg border border-border bg-card">
          <p className="border-b border-border px-4 py-2 text-body-sm text-mute">
            {result.added} added · {result.lines.length - result.added} not added
          </p>
          <ul className="max-h-48 overflow-y-auto">
            {result.lines.map((l, i) => (
              <li key={i} className="flex items-center justify-between gap-3 border-b border-border px-4 py-1.5 last:border-b-0">
                <span className="truncate font-mono text-body-sm text-ink">{l.input}</span>
                <Badge variant={OUTCOME_TONE[l.outcome]} className="font-mono text-[11px]">
                  {OUTCOME_LABEL[l.outcome]}
                </Badge>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-label="Participants" className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-2.5">
          <span className="text-body-sm text-mute">{rows ? `${rows.length} participant(s)` : "Loading…"}</span>
          <Link href={`/exam/${contest.id}`} className="inline-flex items-center gap-1 text-body-sm text-link hover:underline">
            Proctoring report &amp; unlock <ExternalLink className="h-3 w-3" />
          </Link>
        </div>
        {rows?.length === 0 && <p className="p-6 text-center text-body-md text-mute">Nobody has registered yet.</p>}
        {rows?.map((p) => (
          <div
            key={p.userId}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2 border-b border-border px-4 py-2.5 last:border-b-0 sm:grid-cols-[minmax(0,1fr)_120px_80px_96px_36px]"
          >
            <span className="min-w-0">
              <span className="block truncate text-body-md text-ink">{p.username}</span>
              <span className="block truncate text-body-sm text-mute">{p.email}</span>
            </span>
            <span>
              <Badge variant={p.status === "DISQUALIFIED" ? "destructive" : "secondary"} className="font-mono text-[11px]">
                {p.status === "DISQUALIFIED" ? "LOCKED" : p.status}
              </Badge>
            </span>
            <span className="font-mono text-body-sm text-mute" title="Violations">
              {p.violationCount}/{contest.maxViolationsAllowed}
            </span>
            <span className="font-mono text-body-sm text-mute" title="Solved · penalty minutes">
              {p.solved} · {p.penaltyMinutes}m
            </span>
            <span className="justify-self-end">
              {!completed && (
                <Button
                  size="icon-sm"
                  variant="ghost"
                  disabled={p.hasSubmissions}
                  onClick={() => setConfirmRemove(p)}
                  aria-label={`Remove ${p.username}`}
                  title={p.hasSubmissions ? "Has submissions — cannot be removed" : "Remove from contest"}
                  className="text-mute hover:text-destructive"
                >
                  <UserMinus className="h-3.5 w-3.5" />
                </Button>
              )}
            </span>
          </div>
        ))}
      </section>

      <AlertDialog open={confirmRemove !== null} onOpenChange={(open) => !open && setConfirmRemove(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove {confirmRemove?.username} from &ldquo;{contest.title}&rdquo;?</AlertDialogTitle>
            <AlertDialogDescription>
              Their registration is deleted.{" "}
              {contest.registrationMode === "INVITE_ONLY"
                ? "They will not be able to register again unless you add them back."
                : "They can still register themselves while registration is open."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={() => {
                const target = confirmRemove;
                setConfirmRemove(null);
                if (target) void remove(target);
              }}
            >
              Remove participant
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
