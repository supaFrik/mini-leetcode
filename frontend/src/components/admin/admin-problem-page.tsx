"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Archive, Check, ChevronRight, ExternalLink, Loader2, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useUser } from "@/hooks/use-user";
import { createProblem, deleteProblem, transitionProblem } from "@/lib/services";
import type { ProblemFull, ProblemLifecycle } from "@/types/api";
import { AdminNav } from "./admin-nav";
import { ProblemEditor } from "./problem-editor";
import type { SaveState } from "./admin-topics";

/** Cạnh hợp lệ của vòng đời — mirror đúng luật server. */
export const EDGES: Record<ProblemLifecycle, ProblemLifecycle[]> = {
  DRAFT: ["REVIEWING", "ARCHIVED"],
  REVIEWING: ["DRAFT", "PUBLISHED"],
  PUBLISHED: ["ARCHIVED"],
  ARCHIVED: ["DRAFT"],
};

export const STATUS_TONE: Record<ProblemLifecycle, "default" | "secondary" | "success" | "warning" | "outline"> = {
  DRAFT: "secondary",
  REVIEWING: "warning",
  PUBLISHED: "success",
  ARCHIVED: "outline",
};

const MOVE_LABEL: Record<ProblemLifecycle, string> = {
  DRAFT: "Back to draft", REVIEWING: "Send to review", PUBLISHED: "Publish", ARCHIVED: "Archive",
};

const SAVE_TEXT: Record<SaveState, string> = {
  idle: "", saving: "Saving…", saved: "Saved", error: "Couldn't save", invalid: "Title and statement are required",
};

/** /admin/problems/[id]: soạn một đề lẻ trên cả trang (thay cho side dialog cũ). */
export function AdminProblemPage({ id }: { id: number }) {
  const router = useRouter();
  const ask = useConfirm();
  const { user } = useUser();
  const [problem, setProblem] = useState<ProblemFull | null>(null);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (saveState !== "saving" && saveState !== "invalid") return;
    const guard = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ""; };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [saveState]);

  const status = (problem?.status ?? "DRAFT") as ProblemLifecycle;

  const move = async (target: ProblemLifecycle) => {
    if (!problem) return;
    if (target === "ARCHIVED" && !(await ask({
      title: `Archive “${problem.title}”?`, description: "It disappears from the problem list. You can move it back to draft later.", action: "Archive",
    }))) return;
    setBusy(true);
    try {
      setProblem(await transitionProblem(problem.id, target));
      toast(target === "PUBLISHED" ? "Published." : `Moved to ${target.toLowerCase()}.`, "success");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!problem) return;
    if (!(await ask({
      title: `Delete “${problem.title}”?`,
      description: "Its test cases, grading rules and reference solution are deleted too. Problems with submissions can only be archived.",
      action: "Delete problem", destructive: true,
    }))) return;
    setBusy(true);
    try {
      await deleteProblem(problem.id);
      toast("Problem deleted.", "success");
      router.push("/admin/problems");
    } catch (e) {
      toast(errorMessage(e), "error");
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <AdminNav />
      <div className="flex flex-wrap items-center gap-2" role="toolbar" aria-label="Problem actions">
        {problem && <Badge variant={STATUS_TONE[status]} className="font-mono text-[11px]">{status}</Badge>}
        <span className="text-body-sm text-mute" role="status" aria-live="polite">
          {saveState === "saving" && <Loader2 className="mr-1 inline h-3.5 w-3.5 animate-spin" aria-hidden />}
          <span className={saveState === "error" || saveState === "invalid" ? "text-destructive" : undefined}>{SAVE_TEXT[saveState]}</span>
        </span>
        <span className="ml-auto flex flex-wrap items-center gap-1.5">
          {problem && EDGES[status].map((t) => (
            <Button key={t} size="sm" variant={t === "PUBLISHED" ? "default" : "outline"} disabled={busy} onClick={() => move(t)}>
              {t === "PUBLISHED" ? <Check className="h-3.5 w-3.5" /> : t === "ARCHIVED" ? <Archive className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
              {MOVE_LABEL[t]}
            </Button>
          ))}
          {problem && (
            <Button size="sm" variant="ghost" asChild>
              <Link href={`/problems/${problem.slug}`} target="_blank"><ExternalLink className="h-3.5 w-3.5" />View</Link>
            </Button>
          )}
          {problem && user?.role === "ADMIN" && (
            <Button size="icon-sm" variant="ghost" disabled={busy} onClick={remove} aria-label={`Delete ${problem.title}`}
              className="text-mute hover:text-destructive">
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          )}
        </span>
      </div>
      <section aria-label="Problem editor">
        <ProblemEditor problemId={id} label={problem?.title ?? "this problem"} canEdit current={problem}
          onLoad={setProblem} onProblem={setProblem} onSaveState={setSaveState} />
      </section>
    </div>
  );
}

/** /admin/problems/new: tạo đề nháp rồi mở trang soạn (giống /admin/study-plans/new). */
export function NewProblem() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    createProblem({
      title: "Untitled problem", description: "Describe the task.", difficulty: "EASY", timeLimitMs: 1000,
      memoryLimitMb: 256, tags: [], type: "ALGORITHMIC", starterTemplates: null,
    })
      .then((p) => router.replace(`/admin/problems/${p.id}`))
      .catch((e) => setError(errorMessage(e)));
  }, [router]);
  return error ? <div className="mx-auto max-w-container px-4 py-6 text-destructive" role="alert">{error}</div>
    : <div className="mx-auto max-w-container px-4 py-6 text-mute">Creating a draft…</div>;
}
