"use client";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ExternalLink, Loader2, Lock, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { deleteContest, fetchAdminContests } from "@/lib/services";
import type { AdminContestSummary, ContestStatus } from "@/types/api";

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

function formatWhen(iso: string): string {
  const d = new Date(parseApiDate(iso));
  return d.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function AdminContestsView() {
  const [rows, setRows] = useState<AdminContestSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<ContestStatus | "">("");
  const [pending, setPending] = useState<number | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<AdminContestSummary | null>(null);

  const { user } = useUser();
  const canDelete = user?.role === "ADMIN";

  const load = useCallback(() => {
    fetchAdminContests({ search: search, status: status || undefined })
      .then(setRows)
      .catch((e) => setError(errorMessage(e)));
  }, [search, status]);

  useEffect(() => {
    load();
  }, [load]);

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
      <div className="flex flex-wrap items-center gap-2.5">
        <Button asChild className="order-last sm:order-none">
          <Link href="/admin/contests/new">
            <Plus className="mr-1.5 h-4 w-4" />
            New contest
          </Link>
        </Button>
        <div className="relative min-w-0 flex-1 basis-64">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search contests"
            aria-label="Search contests"
            className="h-10 rounded-md bg-card pl-9 text-sm text-ink"
          />
        </div>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as ContestStatus | "")}
          aria-label="Filter by status"
          className="h-10 rounded-md border border-border bg-card px-3 text-sm text-ink"
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
        <p role="alert" className="rounded-md border border-destructive/25 bg-destructive/5 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}

      <section aria-label="Contest administration" className="overflow-hidden rounded-xl border border-border bg-card shadow-xs">
        <div className="hidden items-center gap-3 border-b border-border bg-muted/30 px-5 py-3 font-sans text-xs font-semibold uppercase tracking-wider text-zinc-600 dark:text-zinc-400 md:grid md:grid-cols-[minmax(0,1fr)_104px_minmax(0,220px)_72px_88px_96px]">
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
            className="grid grid-cols-1 items-center gap-2 border-t border-border px-5 py-3.5 transition-colors hover:bg-hairline-soft/60 md:grid-cols-[minmax(0,1fr)_104px_minmax(0,220px)_72px_88px_96px] md:gap-3"
          >
            <span className="min-w-0">
              <Link
                href={`/admin/contests/${c.id}`}
                className="block max-w-full truncate font-medium text-sm text-ink transition-colors hover:text-link"
              >
                {c.title}
              </Link>
              {c.registrationMode === "INVITE_ONLY" && (
                <span className="mt-1 inline-flex items-center gap-1 rounded-full border border-zinc-200 bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800 dark:text-zinc-400">
                  <Lock className="h-2.5 w-2.5" /> Invite only
                </span>
              )}
            </span>
            <span>
              <Badge variant={STATUS_TONE[c.status]} className="font-sans text-xs">
                {c.status}
              </Badge>
            </span>
            <span className="text-xs text-zinc-600 dark:text-zinc-400">
              {formatWhen(c.startTime)} → {formatWhen(c.endTime)}
            </span>
            <span className="font-sans text-sm text-zinc-600 dark:text-zinc-400">{c.problemCount}</span>
            <span className="font-sans text-sm text-zinc-600 dark:text-zinc-400">{c.participantCount}</span>
            <span className="flex items-center justify-start gap-1 md:justify-end">
              <Button asChild size="icon-sm" variant="ghost" title="Edit contest">
                <Link href={`/admin/contests/${c.id}`} aria-label={`Edit ${c.title}`}>
                  <Pencil className="h-3.5 w-3.5 text-zinc-600 dark:text-zinc-400" />
                </Link>
              </Button>
              {canDelete && c.status === "SCHEDULED" && (
                <Button
                  size="icon-sm"
                  variant="ghost"
                  disabled={pending === c.id}
                  onClick={() => setConfirmDelete(c)}
                  aria-label={`Delete ${c.title}`}
                  title="Delete"
                  className="text-zinc-400 hover:text-destructive"
                >
                  {pending === c.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                </Button>
              )}
              <Button asChild size="icon-sm" variant="ghost" title="Open student view">
                <Link href={`/exam/${c.id}`} target="_blank" aria-label={`Open ${c.title}`}>
                  <ExternalLink className="h-3.5 w-3.5 text-zinc-600 dark:text-zinc-400" />
                </Link>
              </Button>
            </span>
          </div>
        ))}

        {rows && rows.length === 0 && (
          <div className="border-t border-border p-12 text-center">
            <p className="font-semibold text-ink">{search || status ? "No contests match these filters" : "No contests yet"}</p>
            <p className="mx-auto mt-1 max-w-[44ch] text-sm text-zinc-500">
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
    </div>
  );
}
