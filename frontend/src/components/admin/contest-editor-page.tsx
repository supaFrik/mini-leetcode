"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Loader2,
  Lock,
  Plus,
  Search,
  Trash2,
  UserMinus,
  AlertCircle,
} from "lucide-react";
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
  fetchAdminProblems,
  fetchContestParticipants,
  removeContestParticipant,
  updateContest,
} from "@/lib/services";
import type {
  AdminContestDetail,
  AdminParticipant,
  BulkParticipantsResult,
  ContestStatus,
  Difficulty,
  ProblemFull,
  RegistrationMode,
  ScoringPolicy,
} from "@/types/api";

const STATUS_TONE: Record<ContestStatus, "success" | "warning" | "outline"> = {
  RUNNING: "success",
  SCHEDULED: "warning",
  COMPLETED: "outline",
};

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

function toLocalInput(iso: string): string {
  const d = new Date(parseApiDate(iso));
  const pad = (n: number) => String(n).padStart(2, "0");
  const y = d.getFullYear();
  const m = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const h = pad(d.getHours());
  const min = pad(d.getMinutes());
  return `${y}-${m}-${day}T${h}:${min}`;
}

function fromLocalInput(local: string): string | null {
  if (!local) return null;
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

function zoneLabel(): string {
  try {
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const offsetMin = -new Date().getTimezoneOffset();
    const sign = offsetMin >= 0 ? "+" : "-";
    const hours = Math.floor(Math.abs(offsetMin) / 60);
    return `GMT${sign}${hours} (${zone})`;
  } catch {
    return "local time";
  }
}

function emptyForm(): Form {
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
    problems: d.problems.map((p) => ({
      problemId: p.problemId,
      title: p.title,
      difficulty: p.difficulty,
      label: p.label,
    })),
  };
}

export function ContestEditorPage({ contestId }: { contestId?: number | null }) {
  const router = useRouter();
  const { user } = useUser();
  const canDelete = user?.role === "ADMIN";

  const [detail, setDetail] = useState<AdminContestDetail | null>(null);
  const [form, setForm] = useState<Form>(emptyForm);
  const [tab, setTab] = useState("details");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadContest = useCallback(() => {
    if (!contestId) {
      setDetail(null);
      setForm(emptyForm());
      return;
    }
    setLoadError(null);
    fetchAdminContest(contestId)
      .then((d) => {
        setDetail(d);
        setForm(formFrom(d));
      })
      .catch((e) => setLoadError(errorMessage(e)));
  }, [contestId]);

  useEffect(() => {
    loadContest();
  }, [loadContest]);

  const status: ContestStatus = detail?.status ?? "SCHEDULED";
  const running = status === "RUNNING";
  const completed = status === "COMPLETED";
  const locked = running || completed;

  const set = <K extends keyof Form>(k: K, v: Form[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const startIso = fromLocalInput(form.start);
  const endIso = fromLocalInput(form.end);

  const timeError = useMemo(() => {
    if (!startIso || !endIso) return "Start and end time are required";
    if (Date.parse(endIso) <= Date.parse(startIso))
      return "End time must be after start time";
    const startChanged = !detail || toLocalInput(detail.startTime) !== form.start;
    if (!locked && startChanged && Date.parse(startIso) <= Date.now())
      return "Start time must be in the future";
    if (running && detail && Date.parse(endIso) < parseApiDate(detail.endTime))
      return "The end time can only be extended while the contest is running";
    return null;
  }, [startIso, endIso, detail, form.start, locked, running]);

  const labelError = useMemo(() => {
    const seen = new Set<string>();
    for (const p of form.problems) {
      const l = p.label.trim().toUpperCase();
      if (!/^[A-Z0-9]{1,3}$/.test(l))
        return `Label "${p.label}" must be 1–3 letters or digits`;
      if (seen.has(l)) return `Label "${l}" is used more than once`;
      seen.add(l);
    }
    return null;
  }, [form.problems]);

  const valid = form.title.trim().length > 0 && !timeError && !labelError;

  const handleSave = async () => {
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
      problems: form.problems.map((p, i) => ({
        problemId: p.problemId,
        label: p.label.trim().toUpperCase(),
        orderIndex: i,
      })),
      version: detail?.version,
    };
    if (detail && locked) body.startTime = detail.startTime;
    try {
      if (detail) {
        const saved = await updateContest(detail.id, body);
        setDetail(saved);
        setForm(formFrom(saved));
        toast("Contest saved successfully.", "success");
      } else {
        const created = await createContest(body);
        toast("Contest created successfully.", "success");
        router.push(`/admin/contests/${created.id}`);
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!detail) return;
    setIsDeleting(true);
    try {
      await deleteContest(detail.id);
      toast("Contest deleted.", "success");
      router.push("/admin/contests");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setIsDeleting(false);
      setConfirmDelete(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-container space-y-6 px-4 py-6 sm:px-6 lg:px-8">
      <AdminNav />

      {/* Breadcrumb Header */}
      <div className="flex flex-col gap-4 border-b border-border pb-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-sm text-zinc-500">
            <Link
              href="/admin/contests"
              className="inline-flex items-center gap-1 font-medium text-zinc-600 transition-colors hover:text-ink dark:text-zinc-400"
            >
              <ArrowLeft className="h-4 w-4" /> Contests
            </Link>
            <span>/</span>
            <span className="text-zinc-900 dark:text-zinc-100 font-medium">
              {detail ? "Edit Contest" : "New Contest"}
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-ink sm:text-3xl">
              {detail ? detail.title : form.title || "Untitled Contest"}
            </h1>
            {detail && (
              <Badge variant={STATUS_TONE[status]} className="font-sans text-xs">
                {status}
              </Badge>
            )}
            {form.registrationMode === "INVITE_ONLY" && (
              <span className="inline-flex items-center gap-1 rounded-full border border-zinc-200 bg-zinc-100 px-2.5 py-0.5 text-xs font-medium text-zinc-700 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-300">
                <Lock className="h-3 w-3" /> Invite only
              </span>
            )}
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2">
          {detail && canDelete && (
            <Button
              variant="outline"
              onClick={() => setConfirmDelete(true)}
              className="text-destructive hover:bg-destructive/10"
              disabled={busy || isDeleting}
            >
              <Trash2 className="mr-1.5 h-4 w-4" /> Delete
            </Button>
          )}
          <Button variant="outline" asChild disabled={busy || isDeleting}>
            <Link href="/admin/contests">Cancel</Link>
          </Button>
          {!completed && (
            <Button onClick={handleSave} disabled={busy || !valid || isDeleting}>
              {busy && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
              {detail ? (running ? "Save end time" : "Save changes") : "Create contest"}
            </Button>
          )}
        </div>
      </div>

      {loadError && (
        <div
          role="alert"
          className="flex items-center gap-2 rounded-lg border border-destructive/25 bg-destructive/5 p-4 text-sm text-destructive"
        >
          <AlertCircle className="h-5 w-5 shrink-0" />
          <span>{loadError}</span>
        </div>
      )}

      {error && (
        <div
          role="alert"
          className="flex items-center gap-2 rounded-lg border border-destructive/25 bg-destructive/5 p-4 text-sm text-destructive"
        >
          <AlertCircle className="h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {(running || completed) && (
        <div
          role="status"
          className="flex items-start gap-2.5 rounded-lg border border-border bg-muted/40 p-4 text-sm text-zinc-700 dark:text-zinc-300"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-zinc-500" />
          <span>
            {running
              ? "This contest is actively running. Only the end time can be extended; problems, scoring, start time, and violation limits are locked."
              : "This contest has ended and is now locked in read-only mode."}
          </span>
        </div>
      )}

      {/* Main Tabs Container */}
      <section className="overflow-hidden rounded-xl border border-border bg-card shadow-xs">
        <Tabs value={tab} onValueChange={setTab} className="w-full">
          <div className="border-b border-border bg-muted/20 px-6 py-2.5">
            <TabsList className="bg-transparent p-0 gap-2">
              <TabsTrigger
                value="details"
                className="data-[state=active]:bg-card data-[state=active]:shadow-xs px-4 py-2 font-medium"
              >
                Details
              </TabsTrigger>
              <TabsTrigger
                value="problems"
                className="data-[state=active]:bg-card data-[state=active]:shadow-xs px-4 py-2 font-medium"
              >
                Problems ({form.problems.length})
              </TabsTrigger>
              <TabsTrigger
                value="participants"
                disabled={!detail}
                className="data-[state=active]:bg-card data-[state=active]:shadow-xs px-4 py-2 font-medium disabled:opacity-50"
              >
                Participants
              </TabsTrigger>
            </TabsList>
          </div>

          <TabsContent value="details" className="m-0 p-6 sm:p-8">
            <FullDetailsForm
              form={form}
              set={set}
              locked={locked}
              completed={completed}
              timeError={timeError}
              isNew={!detail}
            />
          </TabsContent>

          <TabsContent value="problems" className="m-0 p-6 sm:p-8">
            <FullProblemsEditor
              problems={form.problems}
              onChange={(p) => set("problems", p)}
              locked={locked}
              labelError={labelError}
            />
          </TabsContent>

          <TabsContent value="participants" className="m-0 p-6 sm:p-8">
            {detail && <ParticipantsPanel contest={detail} onChanged={loadContest} />}
          </TabsContent>
        </Tabs>
      </section>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete contest &ldquo;{detail?.title}&rdquo;?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete the contest, its problem associations, and participant registrations. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isDeleting ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : null}
              Delete contest
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function FullDetailsForm({
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

  return (
    <div className="max-w-3xl space-y-6">
      <div className="space-y-2">
        <label htmlFor="contest-title" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
          Contest Title
        </label>
        <Input
          id="contest-title"
          value={form.title}
          disabled={locked}
          onChange={(e) => set("title", e.target.value)}
          placeholder="e.g. Midterm Exam - OOP & Data Structures"
          maxLength={200}
          className="h-11 rounded-md bg-background px-3.5 text-base text-ink"
        />
      </div>

      <div className="grid gap-6 sm:grid-cols-2">
        <div className="space-y-2">
          <label htmlFor="contest-start" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Start Time <span className="font-normal text-zinc-500">· {zone}</span>
          </label>
          <Input
            id="contest-start"
            type="datetime-local"
            value={form.start}
            disabled={locked}
            onChange={(e) => set("start", e.target.value)}
            className="h-10 rounded-md bg-background px-3 text-sm text-ink"
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="contest-end" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            End Time <span className="font-normal text-zinc-500">· {zone}</span>
          </label>
          <Input
            id="contest-end"
            type="datetime-local"
            value={form.end}
            disabled={completed}
            onChange={(e) => set("end", e.target.value)}
            aria-invalid={timeError ? true : undefined}
            className="h-10 rounded-md bg-background px-3 text-sm text-ink"
          />
        </div>
      </div>

      {timeError && (
        <p role="alert" className="text-sm font-medium text-destructive">
          {timeError}
        </p>
      )}

      <div className="grid gap-6 sm:grid-cols-3">
        <div className="space-y-2">
          <label htmlFor="contest-scoring" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Scoring Policy
          </label>
          <select
            id="contest-scoring"
            value={form.scoringPolicy}
            disabled={locked}
            onChange={(e) => set("scoringPolicy", e.target.value as ScoringPolicy)}
            className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm text-ink"
          >
            <option value="ACM_ICPC">ACM ICPC (solved, penalty)</option>
            <option value="PARTIAL_IOI">IOI (partial points)</option>
          </select>
        </div>

        <div className="space-y-2">
          <label htmlFor="contest-violations" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Violations Allowed
          </label>
          <Input
            id="contest-violations"
            type="number"
            min={0}
            max={100}
            value={form.maxViolationsAllowed}
            disabled={locked}
            onChange={(e) =>
              set("maxViolationsAllowed", Math.max(0, Math.min(100, Number(e.target.value) || 0)))
            }
            className="h-10 rounded-md bg-background px-3 text-sm text-ink"
          />
        </div>

        <div className="space-y-2">
          <label htmlFor="contest-registration" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Registration Mode
          </label>
          <select
            id="contest-registration"
            value={form.registrationMode}
            disabled={locked}
            onChange={(e) => set("registrationMode", e.target.value as RegistrationMode)}
            className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm text-ink"
          >
            <option value="OPEN">Open (self-registration)</option>
            <option value="INVITE_ONLY">Invite Only (staff-managed)</option>
          </select>
        </div>
      </div>

      {isNew && form.registrationMode === "INVITE_ONLY" && (
        <div className="rounded-md border border-blue-200 bg-blue-50/50 p-4 text-sm text-blue-800 dark:border-blue-900/40 dark:bg-blue-950/20 dark:text-blue-300">
          💡 You will be able to bulk invite students from the <strong>Participants</strong> tab once the contest is created.
        </div>
      )}
    </div>
  );
}

function FullProblemsEditor({
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
  const [adding, setAdding] = useState(false);

  const move = (index: number, dir: -1 | 1) => {
    const target = index + dir;
    if (target < 0 || target >= problems.length) return;
    const copy = [...problems];
    const [row] = copy.splice(index, 1);
    copy.splice(target, 0, row);
    copy.forEach((r, i) => {
      if (!r.label || /^[A-Z]$/.test(r.label)) {
        r.label = String.fromCharCode(65 + i);
      }
    });
    onChange(copy);
  };

  const setLabel = (index: number, label: string) => {
    const copy = [...problems];
    copy[index] = { ...copy[index], label };
    onChange(copy);
  };

  const remove = (index: number) => {
    const copy = problems.filter((_, i) => i !== index);
    copy.forEach((r, i) => {
      if (!r.label || /^[A-Z]$/.test(r.label)) {
        r.label = String.fromCharCode(65 + i);
      }
    });
    onChange(copy);
  };

  const onAddProblem = (p: ProblemFull) => {
    if (problems.some((r) => r.problemId === p.id)) return;
    const nextLabel = String.fromCharCode(65 + (problems.length % 26));
    onChange([
      ...problems,
      {
        problemId: p.id,
        title: p.title,
        difficulty: p.difficulty,
        label: nextLabel,
      },
    ]);
    setAdding(false);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            Contest Problems ({problems.length})
          </h3>
          <p className="text-sm text-zinc-500">
            Assign problem letters (A, B, C...) and order how questions appear to candidates.
          </p>
        </div>
        {!locked && (
          <Button onClick={() => setAdding((a) => !a)} size="sm">
            <Plus className="mr-1.5 h-4 w-4" />
            {adding ? "Cancel picker" : "Add problems"}
          </Button>
        )}
      </div>

      {labelError && (
        <p role="alert" className="text-sm font-medium text-destructive">
          {labelError}
        </p>
      )}

      {adding && (
        <div className="rounded-xl border border-border bg-muted/20 p-5">
          <ProblemPicker selectedIds={new Set(problems.map((p) => p.problemId))} onPick={onAddProblem} />
        </div>
      )}

      {problems.length === 0 && !adding ? (
        <div className="rounded-xl border border-dashed border-border p-12 text-center">
          <p className="text-sm font-medium text-zinc-900 dark:text-zinc-100">No problems added yet</p>
          <p className="mt-1 text-xs text-zinc-500">
            Click &ldquo;Add problems&rdquo; to choose questions from your problem bank.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-border bg-card">
          <div className="hidden grid-cols-[60px_1fr_120px_100px_90px] items-center gap-3 border-b border-border bg-muted/30 px-5 py-3 text-xs font-semibold uppercase tracking-wider text-zinc-600 dark:text-zinc-400 sm:grid">
            <span>Label</span>
            <span>Title</span>
            <span>Difficulty</span>
            <span>Order</span>
            <span className="text-right">Action</span>
          </div>
          <div className="divide-y divide-border">
            {problems.map((p, i) => (
              <div
                key={p.problemId}
                className="grid grid-cols-1 items-center gap-3 px-5 py-3.5 sm:grid-cols-[60px_1fr_120px_100px_90px]"
              >
                <div>
                  <Input
                    value={p.label}
                    disabled={locked}
                    onChange={(e) => setLabel(i, e.target.value.toUpperCase())}
                    maxLength={3}
                    className="h-8 w-12 text-center font-bold"
                  />
                </div>
                <div className="min-w-0">
                  <p className="truncate font-medium text-ink">{p.title}</p>
                  <p className="text-xs text-zinc-500">ID #{p.problemId}</p>
                </div>
                <div>
                  <DifficultyBadge value={p.difficulty} />
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    disabled={locked || i === 0}
                    onClick={() => move(i, -1)}
                    title="Move up"
                  >
                    <ArrowUp className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    disabled={locked || i === problems.length - 1}
                    onClick={() => move(i, 1)}
                    title="Move down"
                  >
                    <ArrowDown className="h-4 w-4" />
                  </Button>
                </div>
                <div className="text-right">
                  {!locked && (
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => remove(i)}
                      className="text-zinc-400 hover:text-destructive"
                      title="Remove from contest"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function ProblemPicker({
  selectedIds,
  onPick,
}: {
  selectedIds: Set<number>;
  onPick: (p: ProblemFull) => void;
}) {
  const [q, setQ] = useState("");
  const [diff, setDiff] = useState("");
  const [items, setItems] = useState<ProblemFull[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let live = true;
    const t = setTimeout(() => {
      setLoading(true);
      fetchAdminProblems({ page: 1, q: q.trim(), difficulty: diff, status: "PUBLISHED", tags: [] })
        .then((res) => live && setItems(res.content))
        .catch(() => live && setItems([]))
        .finally(() => live && setLoading(false));
    }, 200);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [q, diff]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2.5">
        <div className="relative flex-1 min-w-52">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search published problems..."
            className="pl-9 h-10"
          />
        </div>
        <select
          value={diff}
          onChange={(e) => setDiff(e.target.value)}
          className="h-10 rounded-md border border-border bg-background px-3 text-sm text-ink"
        >
          <option value="">All difficulties</option>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
      </div>

      <div className="max-h-64 overflow-y-auto rounded-lg border border-border bg-card divide-y divide-border">
        {loading ? (
          <div className="p-6 text-center text-sm text-zinc-500">
            <Loader2 className="mx-auto h-5 w-5 animate-spin mb-2" />
            Loading problems...
          </div>
        ) : items.length === 0 ? (
          <div className="p-6 text-center text-sm text-zinc-500">No published problems found</div>
        ) : (
          items.map((p) => {
            const picked = selectedIds.has(p.id);
            return (
              <div
                key={p.id}
                className="flex items-center justify-between gap-3 p-3 transition-colors hover:bg-muted/40"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-sm text-ink">{p.title}</p>
                  <p className="text-xs text-zinc-500">#{p.id} · {p.type === "OOP_GRINDING" ? "OOP" : "DSA"}</p>
                </div>
                <div className="flex items-center gap-3">
                  <DifficultyBadge value={p.difficulty} />
                  <Button
                    size="sm"
                    variant={picked ? "outline" : "default"}
                    disabled={picked}
                    onClick={() => onPick(p)}
                  >
                    {picked ? "Added" : "Add"}
                  </Button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

function ParticipantsPanel({
  contest,
  onChanged,
}: {
  contest: AdminContestDetail;
  onChanged: () => void;
}) {
  const [list, setList] = useState<AdminParticipant[]>([]);
  const [raw, setRaw] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<BulkParticipantsResult | null>(null);
  const [filter, setFilter] = useState("");
  const [confirmRemove, setConfirmRemove] = useState<AdminParticipant | null>(null);

  const load = useCallback(() => {
    fetchContestParticipants(contest.id).then(setList).catch(() => setList([]));
  }, [contest.id]);

  useEffect(() => {
    load();
  }, [load]);

  const submit = async () => {
    const tokens = raw.split(/[\s,;]+/).map((s) => s.trim()).filter((s) => s.length > 0);
    if (tokens.length === 0) return;
    setBusy(true);
    setResult(null);
    try {
      const res = await addContestParticipants(contest.id, raw.trim());
      setResult(res);
      if (res.added > 0) {
        toast(`Added ${res.added} participant(s).`, "success");
        setRaw("");
        load();
        onChanged();
      }
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  const removeOne = async () => {
    if (!confirmRemove) return;
    try {
      await removeContestParticipant(contest.id, confirmRemove.userId);
      toast(`Removed ${confirmRemove.username}.`, "success");
      setConfirmRemove(null);
      load();
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const filtered = useMemo(() => {
    const f = filter.trim().toLowerCase();
    if (!f) return list;
    return list.filter(
      (p) => p.username.toLowerCase().includes(f) || p.email.toLowerCase().includes(f)
    );
  }, [list, filter]);

  return (
    <div className="space-y-6">
      {/* Invite box */}
      <div className="rounded-xl border border-border bg-muted/20 p-5 space-y-3">
        <label htmlFor="bulk-invite" className="block text-sm font-semibold text-zinc-900 dark:text-zinc-100">
          Bulk Invite Participants
        </label>
        <p className="text-xs text-zinc-500">
          Paste emails or usernames separated by commas, spaces, or newlines.
        </p>
        <Textarea
          id="bulk-invite"
          rows={3}
          value={raw}
          onChange={(e) => setRaw(e.target.value)}
          placeholder="student1@university.edu, student2@university.edu"
          className="font-mono text-sm bg-background"
        />
        <Button onClick={submit} disabled={busy || raw.trim().length === 0} size="sm">
          {busy && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
          Add participants
        </Button>
      </div>

      {result && (
        <div className="rounded-xl border border-border bg-card p-4 space-y-2">
          <p className="text-sm font-semibold">Bulk addition outcome:</p>
          <div className="max-h-40 overflow-y-auto space-y-1 text-xs">
            {result.lines.map((o) => (
              <div key={o.input} className="flex items-center justify-between py-1">
                <span className="font-mono text-ink">{o.input}</span>
                <span
                  className={
                    o.outcome === "ADDED"
                      ? "text-emerald-600 font-medium"
                      : o.outcome === "ALREADY_REGISTERED"
                      ? "text-amber-600 font-medium"
                      : "text-red-600 font-medium"
                  }
                >
                  {o.outcome}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Participant List */}
      <div className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2.5">
          <h4 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            Registered Candidates ({list.length})
          </h4>
          <div className="relative w-64">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <Input
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Search participants..."
              className="pl-9 h-9 text-sm"
            />
          </div>
        </div>

        {list.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border p-12 text-center text-sm text-zinc-500">
            No participants registered yet. Use the bulk invite box above to add candidates.
          </div>
        ) : (
          <div className="overflow-hidden rounded-xl border border-border bg-card">
            <div className="hidden grid-cols-[1fr_1fr_120px_80px] items-center gap-3 border-b border-border bg-muted/30 px-5 py-3 text-xs font-semibold uppercase tracking-wider text-zinc-600 dark:text-zinc-400 sm:grid">
              <span>Username</span>
              <span>Email</span>
              <span>Status</span>
              <span className="text-right">Action</span>
            </div>
            <div className="divide-y divide-border">
              {filtered.map((p) => (
                <div
                  key={p.userId}
                  className="grid grid-cols-1 items-center gap-2 px-5 py-3 sm:grid-cols-[1fr_1fr_120px_80px]"
                >
                  <span className="font-medium text-sm text-ink">{p.username}</span>
                  <span className="text-sm text-zinc-500 truncate">{p.email}</span>
                  <span>
                    <Badge variant={p.status === "REGISTERED" ? "success" : "outline"} className="font-sans text-xs">
                      {p.status}
                    </Badge>
                  </span>
                  <div className="text-right">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={() => setConfirmRemove(p)}
                      className="text-zinc-400 hover:text-destructive"
                      title="Remove participant"
                    >
                      <UserMinus className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <AlertDialog open={confirmRemove !== null} onOpenChange={(open) => !open && setConfirmRemove(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove {confirmRemove?.username} from contest?</AlertDialogTitle>
            <AlertDialogDescription>
              This candidate will lose access to the contest. Their existing submissions will be retained for archival purposes.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={removeOne}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
