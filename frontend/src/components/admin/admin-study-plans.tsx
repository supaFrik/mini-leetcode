"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, Plus, Search, Trash2, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useLoad } from "@/hooks/use-load";
import { useUser } from "@/hooks/use-user";
import {
  createStudyPlan, deleteStudyPlan, fetchAdminProblems, fetchStudyPlan, fetchStudyPlans, transitionStudyPlan, updateStudyPlan,
} from "@/lib/services";
import type { Difficulty, ProblemFull, StudyPlanDetail, StudyPlanItemKind, StudyPlanStatus } from "@/types/api";

/** Khớp ràng buộc slug của backend (UpsertPlanRequest). */
const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
export const slugify = (title: string) =>
  title.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/gi, "d").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 200);

const STATUS_VARIANT: Record<StudyPlanStatus, "success" | "secondary" | "outline"> = {
  PUBLISHED: "success", DRAFT: "secondary", ARCHIVED: "outline",
};

/** Chỉ tác giả hoặc ADMIN sửa được (server cũng kiểm tra, trả 403). */
function useCanEdit() {
  const { user } = useUser();
  return (createdById?: number) => !!user && (user.role === "ADMIN" || createdById === undefined || createdById === user.id);
}

// =====================================================================================
// /admin/study-plans
// =====================================================================================

export function AdminStudyPlansView() {
  const load = useLoad(fetchStudyPlans, []);
  const canEdit = useCanEdit();

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow">Admin</p>
          <h1 className="mt-1 text-heading-lg text-ink">Study plans</h1>
          <p className="mt-1 text-body-md text-mute">
            Build learning paths from published problems. Students see a plan once it is published.{" "}
            <Link href="/admin/problems" className="text-link underline-offset-4 hover:underline">Manage problems</Link>
          </p>
        </div>
        <Button asChild>
          <Link href="/admin/study-plans/new"><Plus className="h-4 w-4" />New plan</Link>
        </Button>
      </header>

      <div className="overflow-hidden rounded-sm border border-border bg-card" aria-label="Study plans" role="region">
        {load.state === "loading" && <p className="p-6 text-body-md text-mute">Loading study plans…</p>}
        {load.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{load.message}</p>}
        {load.state === "ok" && load.data.length === 0 && (
          <p className="p-6 text-body-md text-mute">No study plans yet. Create the first one.</p>
        )}
        {load.state === "ok" && load.data.length > 0 && (
          <table className="w-full text-left text-body-md">
            <thead className="border-b border-border text-body-sm text-mute">
              <tr>
                <th className="px-4 py-2 font-medium">Plan</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="hidden px-4 py-2 font-medium sm:table-cell">Content</th>
                <th className="hidden px-4 py-2 font-medium md:table-cell">Author</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {load.data.map((plan) => (
                <tr key={plan.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <Link href={`/admin/study-plans/${plan.slug}`} className="font-medium text-ink hover:underline">{plan.title}</Link>
                    <div className="font-mono text-body-sm text-mute">{plan.slug}</div>
                  </td>
                  <td className="px-4 py-3"><Badge variant={STATUS_VARIANT[plan.status]}>{plan.status}</Badge></td>
                  <td className="hidden px-4 py-3 text-mute sm:table-cell">{plan.itemCount} items · {plan.stageCount} stages</td>
                  <td className="hidden px-4 py-3 text-mute md:table-cell">{plan.createdByUsername ?? "—"}</td>
                  <td className="px-4 py-3 text-right">
                    {!canEdit(plan.createdById) && <span className="mr-3 text-body-sm text-mute">Read-only</span>}
                    <Link href={`/study-plans/${plan.slug}`} className="text-body-sm text-link hover:underline">Preview</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

// =====================================================================================
// /admin/study-plans/new  và  /admin/study-plans/[slug]
// =====================================================================================

interface StageRef { problemId: number; slug: string | null; title: string | null; difficulty: Difficulty | null }
interface ItemState { key: number; kind: StudyPlanItemKind; title: string; description: string; stages: StageRef[] }
let nextKey = 1;
const emptyItem = (kind: StudyPlanItemKind): ItemState => ({ key: nextKey++, kind, title: "", description: "", stages: [] });

function move<T>(list: T[], from: number, to: number): T[] {
  if (to < 0 || to >= list.length) return list;
  const copy = [...list];
  const [x] = copy.splice(from, 1);
  copy.splice(to, 0, x);
  return copy;
}

/** Tìm đề PUBLISHED theo tên / slug để gắn làm stage. */
function ProblemPicker({ exclude, onPick, disabled }: { exclude: Set<number>; onPick: (p: ProblemFull) => void; disabled?: boolean }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<ProblemFull[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!q.trim()) {
      setResults([]);
      return;
    }
    let off = false;
    const t = setTimeout(() => {
      setBusy(true);
      fetchAdminProblems({ page: 1, q: q.trim(), difficulty: "", status: "PUBLISHED", tags: [] })
        .then((page) => !off && setResults(page.content))
        .catch(() => !off && setResults([]))
        .finally(() => !off && setBusy(false));
    }, 250);
    return () => {
      off = true;
      clearTimeout(t);
    };
  }, [q]);

  const visible = results.filter((p) => !exclude.has(p.id)).slice(0, 8);
  return (
    <div className="space-y-1">
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-mute" aria-hidden="true" />
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Add a published problem — search by title or slug"
          className="pl-8" aria-label="Search published problems" disabled={disabled} />
      </div>
      {q.trim() && (
        <ul className="rounded-sm border border-border bg-card" aria-label="Problem search results">
          {busy && <li className="px-3 py-2 text-body-sm text-mute">Searching…</li>}
          {!busy && visible.length === 0 && <li className="px-3 py-2 text-body-sm text-mute">No published problem matches.</li>}
          {visible.map((p) => (
            <li key={p.id}>
              <button type="button" className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-hairline-soft"
                onClick={() => { onPick(p); setQ(""); }}>
                <span><span className="text-ink">{p.title}</span> <span className="font-mono text-body-sm text-mute">{p.slug}</span></span>
                <DifficultyBadge value={p.difficulty} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function StudyPlanEditor({ slug }: { slug?: string }) {
  const router = useRouter();
  const canEditFn = useCanEdit();
  const [loaded, setLoaded] = useState<StudyPlanDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [planSlug, setPlanSlug] = useState("");
  const [slugTouched, setSlugTouched] = useState(false);
  const [description, setDescription] = useState("");
  const [items, setItems] = useState<ItemState[]>([emptyItem("PROJECT")]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!slug) return;
    fetchStudyPlan(slug)
      .then((plan) => {
        setLoaded(plan);
        setTitle(plan.title);
        setPlanSlug(plan.slug);
        setSlugTouched(true);
        setDescription(plan.description ?? "");
        setItems(plan.items.map((it) => ({
          key: nextKey++, kind: it.kind, title: it.title, description: it.description ?? "",
          stages: it.stages.map((st) => ({ problemId: st.problemId, slug: st.slug, title: st.title, difficulty: st.difficulty })),
        })));
      })
      .catch((e) => setLoadError(errorMessage(e)));
  }, [slug]);

  const canEdit = !slug || (loaded !== null && canEditFn(loaded.createdById));
  const used = useMemo(() => new Set(items.flatMap((i) => i.stages.map((s) => s.problemId))), [items]);
  const patchItem = (key: number, patch: Partial<ItemState>) =>
    setItems((list) => list.map((it) => (it.key === key ? { ...it, ...patch } : it)));

  const validate = (): string | null => {
    if (!title.trim()) return "Give the plan a title.";
    if (!SLUG_RE.test(planSlug)) return "The slug may only contain lowercase letters, digits and single hyphens.";
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (!it.title.trim()) return `Item ${i + 1} needs a title.`;
      if (it.stages.length === 0) return `Item ${i + 1} needs at least one problem.`;
      if (it.kind === "PROBLEM" && it.stages.length !== 1) return `Item ${i + 1} is a practice problem: it must have exactly one problem.`;
    }
    if (loaded?.status === "PUBLISHED" && items.length === 0) return "A published plan must keep at least one item.";
    return null;
  };

  const save = async () => {
    const problem = validate();
    setError(problem);
    if (problem) return;
    setSaving(true);
    try {
      const draft = {
        slug: planSlug, title: title.trim(), description: description.trim() || undefined,
        items: items.map((it) => ({ kind: it.kind, title: it.title.trim(), description: it.description.trim() || undefined,
          problemIds: it.stages.map((s) => s.problemId) })),
      };
      const saved = slug ? await updateStudyPlan(slug, draft) : await createStudyPlan(draft);
      setLoaded(saved);
      toast(slug ? "Study plan saved" : "Study plan created as a draft", "success");
      if (saved.slug !== slug) router.replace(`/admin/study-plans/${saved.slug}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const transition = async (status: StudyPlanStatus) => {
    if (!slug) return;
    try {
      setLoaded(await transitionStudyPlan(slug, status));
      toast(status === "PUBLISHED" ? "Published — students can see this plan" : `Moved to ${status.toLowerCase()}`, "success");
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const remove = async () => {
    if (!slug || !window.confirm(`Delete "${title}"? Students keep their submissions, but the plan disappears.`)) return;
    try {
      await deleteStudyPlan(slug);
      toast("Study plan deleted", "success");
      router.push("/admin/study-plans");
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  if (slug && loadError) {
    return <div className="mx-auto max-w-container px-4 py-6 text-destructive" role="alert">{loadError}</div>;
  }
  if (slug && !loaded) {
    return <div className="mx-auto max-w-container px-4 py-6 text-mute">Loading study plan…</div>;
  }

  const status = loaded?.status;
  return (
    <div className="mx-auto w-full max-w-3xl space-y-5 px-4 py-6 sm:px-6">
      <header className="space-y-2">
        <Link href="/admin/study-plans" className="text-body-sm text-link hover:underline">← Study plans</Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-heading-lg text-ink">{slug ? "Edit study plan" : "New study plan"}</h1>
          {status && <Badge variant={STATUS_VARIANT[status]}>{status}</Badge>}
        </div>
        {!canEdit && (
          <p className="rounded-sm border border-border bg-hairline-soft px-3 py-2 text-body-sm text-mute" role="note">
            Read-only: only {loaded?.createdByUsername ?? "the author"} or an admin can change this plan.
          </p>
        )}
      </header>

      <fieldset disabled={!canEdit || saving} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-body-sm text-mute">
            Title
            <Input value={title} onChange={(e) => { setTitle(e.target.value); if (!slugTouched) setPlanSlug(slugify(e.target.value)); }} />
          </label>
          <label className="space-y-1 text-body-sm text-mute">
            Slug (URL)
            <Input value={planSlug} className="font-mono" onChange={(e) => { setSlugTouched(true); setPlanSlug(e.target.value); }} />
          </label>
        </div>
        <label className="block space-y-1 text-body-sm text-mute">
          Description
          <Textarea value={description} rows={2} onChange={(e) => setDescription(e.target.value)} />
        </label>

        <div className="space-y-3">
          {items.map((it, i) => (
            <section key={it.key} className="space-y-3 rounded-sm border border-border bg-card p-4" aria-label={`Item ${i + 1}`}>
              <div className="flex flex-wrap items-end gap-2">
                <label className="space-y-1 text-body-sm text-mute">
                  Type
                  <select className="block h-9 rounded-sm border border-border bg-card px-2 text-body-md text-ink" value={it.kind}
                    onChange={(e) => patchItem(it.key, { kind: e.target.value as StudyPlanItemKind })}>
                    <option value="PROJECT">Project (multi-stage)</option>
                    <option value="PROBLEM">Practice problem</option>
                  </select>
                </label>
                <label className="min-w-0 flex-1 space-y-1 text-body-sm text-mute">
                  Item title
                  <Input value={it.title} onChange={(e) => patchItem(it.key, { title: e.target.value })} />
                </label>
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move item ${i + 1} up`} disabled={i === 0}
                  onClick={() => setItems((l) => move(l, i, i - 1))}><ArrowUp className="h-4 w-4" /></Button>
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move item ${i + 1} down`} disabled={i === items.length - 1}
                  onClick={() => setItems((l) => move(l, i, i + 1))}><ArrowDown className="h-4 w-4" /></Button>
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove item ${i + 1}`}
                  onClick={() => setItems((l) => l.filter((x) => x.key !== it.key))}><Trash2 className="h-4 w-4" /></Button>
              </div>
              <Textarea value={it.description} rows={1} placeholder="Optional description shown to students"
                aria-label={`Item ${i + 1} description`} onChange={(e) => patchItem(it.key, { description: e.target.value })} />

              <ol className="divide-y divide-border rounded-sm border border-border" aria-label={`Item ${i + 1} stages`}>
                {it.stages.length === 0 && <li className="px-3 py-2 text-body-sm text-mute">No problem yet.</li>}
                {it.stages.map((st, k) => (
                  <li key={st.problemId} className="flex items-center gap-2 px-3 py-2">
                    <span className="w-16 font-mono text-body-sm text-mute">{it.kind === "PROJECT" ? `Stage ${k + 1}` : "Problem"}</span>
                    <span className="min-w-0 flex-1 truncate text-ink">
                      {st.title ?? `#${st.problemId}`} <span className="font-mono text-body-sm text-mute">{st.slug}</span>
                    </span>
                    {st.difficulty && <DifficultyBadge value={st.difficulty} />}
                    <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move ${st.title ?? st.problemId} up`} disabled={k === 0}
                      onClick={() => patchItem(it.key, { stages: move(it.stages, k, k - 1) })}><ArrowUp className="h-4 w-4" /></Button>
                    <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move ${st.title ?? st.problemId} down`} disabled={k === it.stages.length - 1}
                      onClick={() => patchItem(it.key, { stages: move(it.stages, k, k + 1) })}><ArrowDown className="h-4 w-4" /></Button>
                    <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove ${st.title ?? st.problemId}`}
                      onClick={() => patchItem(it.key, { stages: it.stages.filter((x) => x.problemId !== st.problemId) })}><X className="h-4 w-4" /></Button>
                  </li>
                ))}
              </ol>
              {(it.kind === "PROJECT" || it.stages.length === 0) && (
                <ProblemPicker exclude={used} disabled={!canEdit}
                  onPick={(p) => patchItem(it.key, { stages: [...it.stages, { problemId: p.id, slug: p.slug, title: p.title, difficulty: p.difficulty }] })} />
              )}
            </section>
          ))}
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setItems((l) => [...l, emptyItem("PROJECT")])}>
              <Plus className="h-4 w-4" />Add project
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={() => setItems((l) => [...l, emptyItem("PROBLEM")])}>
              <Plus className="h-4 w-4" />Add practice problem
            </Button>
          </div>
        </div>
      </fieldset>

      {error && <p className="rounded-sm border border-destructive/30 bg-destructive/5 px-3 py-2 text-body-sm text-destructive" role="alert">{error}</p>}

      {canEdit && (
        <footer className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
          <Button type="button" onClick={save} disabled={saving}>{saving ? "Saving…" : slug ? "Save changes" : "Create draft"}</Button>
          {status && status !== "PUBLISHED" && <Button type="button" variant="outline" onClick={() => transition("PUBLISHED")}>Publish</Button>}
          {status && status !== "DRAFT" && <Button type="button" variant="outline" onClick={() => transition("DRAFT")}>Move to draft</Button>}
          {status === "PUBLISHED" && <Button type="button" variant="outline" onClick={() => transition("ARCHIVED")}>Archive</Button>}
          {slug && <Button type="button" variant="ghost" className="ml-auto text-destructive" onClick={remove}><Trash2 className="h-4 w-4" />Delete</Button>}
          {slug && status === "PUBLISHED" && (
            <Link href={`/study-plans/${slug}`} className="text-body-sm text-link hover:underline">View as student</Link>
          )}
        </footer>
      )}
    </div>
  );
}
