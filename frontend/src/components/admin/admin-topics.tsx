"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ArrowDown, ArrowUp, Plus, Search, Trash2, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useLoad } from "@/hooks/use-load";
import { useUser } from "@/hooks/use-user";
import { deleteTopic, fetchTopicForAuthoring, fetchTopics, saveTopic, transitionTopic } from "@/lib/services";
import type { AuthoringStep, AuthoringTopic, StudyPlanStatus, TopicStepType, TopicSummary } from "@/types/api";
import { AdminNav } from "./admin-nav";
import { ProblemPicker, slugify } from "./admin-study-plans";
import { useConfirm } from "@/components/ui/confirm";

/* Soạn chủ đề học (specs/topics.spec.md): /admin/topics, /admin/topics/new, /admin/topics/[slug]. */

const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const STATUS_VARIANT: Record<StudyPlanStatus, "success" | "secondary" | "outline"> = {
  PUBLISHED: "success", DRAFT: "secondary", ARCHIVED: "outline",
};
export const STEP_LABEL: Record<TopicStepType, string> = {
  THEORY: "Theory", CHOICE: "Quiz", MATCHING: "Matching", ORDERING: "Ordering", FILL_BLANK: "Fill in the blanks", CODE: "Code exercise",
};
const BLANK = /\[\[([A-Za-z0-9_-]{1,20})]]/g;
const blankIds = (code: string) => Array.from(code.matchAll(BLANK)).map((m) => m[1]);
const field = "space-y-1 text-body-sm text-mute";

function useCanEdit() {
  const { user } = useUser();
  return (createdById?: number) => !!user && (user.role === "ADMIN" || createdById === undefined || createdById === user.id);
}

function move<T>(list: T[], from: number, to: number): T[] {
  if (to < 0 || to >= list.length) return list;
  const copy = [...list];
  const [x] = copy.splice(from, 1);
  copy.splice(to, 0, x);
  return copy;
}

// =====================================================================================
// /admin/topics
// =====================================================================================

export function AdminTopicsView() {
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  useEffect(() => { const t = setTimeout(() => setQuery(q.trim()), 250); return () => clearTimeout(t); }, [q]);
  const load = useLoad(() => fetchTopics(query), [query]);
  const canEdit = useCanEdit();

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <AdminNav />
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 max-w-sm flex-1">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-mute" aria-hidden="true" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search" className="pl-8" aria-label="Search topics" />
        </div>
        <Button asChild className="ml-auto"><Link href="/admin/topics/new"><Plus className="h-4 w-4" />New topic</Link></Button>
      </div>
      <div className="overflow-hidden rounded-sm border border-border bg-card" aria-label="Topics" role="region">
        {load.state === "loading" && <p className="p-6 text-body-md text-mute">Loading topics…</p>}
        {load.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{load.message}</p>}
        {load.state === "ok" && load.data.length === 0 && <p className="p-6 text-body-md text-mute">No topics yet. Create the first one.</p>}
        {load.state === "ok" && load.data.length > 0 && (
          <table className="w-full text-left text-body-md">
            <thead className="border-b border-border text-body-sm text-mute">
              <tr>
                <th className="px-4 py-2 font-medium">Topic</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="hidden px-4 py-2 font-medium sm:table-cell">Steps</th>
                <th className="hidden px-4 py-2 font-medium md:table-cell">Author</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {load.data.map((t: TopicSummary) => (
                <tr key={t.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <Link href={`/admin/topics/${t.slug}`} className="font-medium text-ink hover:underline">{t.title}</Link>
                    <div className="font-mono text-body-sm text-mute">{t.slug}</div>
                  </td>
                  <td className="px-4 py-3"><Badge variant={STATUS_VARIANT[t.status]}>{t.status}</Badge></td>
                  <td className="hidden px-4 py-3 text-mute sm:table-cell">{t.stepCount} steps · {t.estMinutes} min</td>
                  <td className="hidden px-4 py-3 text-mute md:table-cell">{t.createdByUsername}</td>
                  <td className="px-4 py-3 text-right">
                    {!canEdit(t.createdById) && <span className="mr-3 text-body-sm text-mute">Read-only</span>}
                    <Link href={`/topics/${t.slug}`} className="text-body-sm text-link hover:underline">Preview</Link>
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
// Trạng thái soạn một step (dạng dễ sửa) <-> AuthoringStep (hợp đồng API)
// =====================================================================================

interface StepState {
  key: number; id?: number | null; type: TopicStepType; title: string;
  prompt: string; hint: string; explanation: string;
  markdown: string;
  multiple: boolean; options: { text: string; correct: boolean }[];
  pairs: { left: string; right: string }[];
  lines: string[];
  code: string; accepted: Record<string, string>; caseSensitive: boolean;
  intro: string; problem: { id: number; slug: string | null; title: string | null } | null;
}
let nextKey = 1;

function emptyStep(type: TopicStepType): StepState {
  return {
    key: nextKey++, type, title: type === "THEORY" ? "Theory" : "", prompt: "", hint: "", explanation: "",
    markdown: type === "THEORY" ? "## First section\n\nWrite the explanation here.\n\n## Second section\n\n…" : "",
    multiple: false, options: [{ text: "", correct: true }, { text: "", correct: false }],
    pairs: [{ left: "", right: "" }, { left: "", right: "" }], lines: ["", ""],
    code: type === "FILL_BLANK" ? "public [[1]] class Shape {\n}" : "", accepted: {}, caseSensitive: false, intro: "", problem: null,
  };
}

const str = (v: unknown) => (typeof v === "string" ? v : "");
type IdText = { id: string; text: string };

export function fromAuthoring(s: AuthoringStep): StepState {
  const c = s.content as Record<string, unknown>;
  const a = (s.answer ?? {}) as Record<string, unknown>;
  const base: StepState = { ...emptyStep(s.type), id: s.id, title: s.title, prompt: str(c.prompt), hint: str(c.hint), explanation: str(a.explanation) };
  switch (s.type) {
    case "THEORY": return { ...base, markdown: str(c.markdown) };
    case "CHOICE": {
      const correct = (a.correct as string[] | undefined) ?? [];
      return { ...base, multiple: !!c.multiple, options: ((c.options as IdText[]) ?? []).map((o) => ({ text: o.text, correct: correct.includes(o.id) })) };
    }
    case "MATCHING": {
      const pairs = (a.pairs as Record<string, string> | undefined) ?? {};
      const right = new Map(((c.right as IdText[]) ?? []).map((r) => [r.id, r.text]));
      return { ...base, pairs: ((c.left as IdText[]) ?? []).map((l) => ({ left: l.text, right: right.get(pairs[l.id]) ?? "" })) };
    }
    case "ORDERING": {
      const items = new Map(((c.items as IdText[]) ?? []).map((i) => [i.id, i.text]));
      return { ...base, lines: ((a.order as string[] | undefined) ?? []).map((id) => items.get(id) ?? "") };
    }
    case "FILL_BLANK": {
      const blanks = (a.blanks as Record<string, string[]> | undefined) ?? {};
      return { ...base, code: str(c.code), caseSensitive: !!a.caseSensitive,
        accepted: Object.fromEntries(Object.entries(blanks).map(([k, v]) => [k, v.join(" | ")])) };
    }
    case "CODE": return { ...base, intro: str(c.intro), problem: s.problemId ? { id: s.problemId, slug: s.problemSlug ?? null, title: s.problemSlug ?? null } : null };
  }
}

export function toAuthoring(s: StepState): AuthoringStep {
  const opt = (v: string) => (v.trim() ? v.trim() : undefined);
  const extra = { hint: opt(s.hint) };
  const explanation = opt(s.explanation);
  const common = { id: s.id ?? undefined, type: s.type, title: s.title.trim() };
  switch (s.type) {
    case "THEORY": return { ...common, content: { markdown: s.markdown } };
    case "CHOICE": {
      const options = s.options.map((o, i) => ({ id: `o${i + 1}`, text: o.text.trim() }));
      return { ...common, content: { prompt: s.prompt, multiple: s.multiple, options, ...extra },
        answer: { correct: options.filter((_, i) => s.options[i].correct).map((o) => o.id), explanation } };
    }
    case "MATCHING": return { ...common,
      content: { prompt: s.prompt, left: s.pairs.map((p, i) => ({ id: `l${i + 1}`, text: p.left.trim() })),
        right: s.pairs.map((p, i) => ({ id: `r${i + 1}`, text: p.right.trim() })), ...extra },
      answer: { pairs: Object.fromEntries(s.pairs.map((_, i) => [`l${i + 1}`, `r${i + 1}`])), explanation } };
    case "ORDERING": return { ...common,
      content: { prompt: s.prompt, items: s.lines.map((text, i) => ({ id: `i${i + 1}`, text })), ...extra },
      answer: { order: s.lines.map((_, i) => `i${i + 1}`), explanation } };
    case "FILL_BLANK": return { ...common, content: { prompt: s.prompt, code: s.code, ...extra },
      answer: { blanks: Object.fromEntries(blankIds(s.code).map((id) => [id, (s.accepted[id] ?? "").split("|").map((x) => x.trim()).filter(Boolean)])),
        caseSensitive: s.caseSensitive, explanation } };
    case "CODE": return { ...common, content: { intro: s.intro.trim() || undefined }, problemId: s.problem?.id ?? null };
  }
}

// =====================================================================================
// Ô soạn từng loại step
// =====================================================================================

/** Markdown lý thuyết với tab Preview (react-markdown, cùng cách hiển thị cho học viên). */
function TheoryField({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [preview, setPreview] = useState(false);
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-body-sm text-mute">
        <span>Theory (Markdown — every “## Heading” becomes a section)</span>
        <span className="flex gap-1" role="group" aria-label="Theory view">
          <button type="button" aria-pressed={!preview} onClick={() => setPreview(false)}
            className={`rounded-sm px-2 py-0.5 ${preview ? "" : "bg-hairline-soft text-ink"}`}>Write</button>
          <button type="button" aria-pressed={preview} onClick={() => setPreview(true)}
            className={`rounded-sm px-2 py-0.5 ${preview ? "bg-hairline-soft text-ink" : ""}`}>Preview</button>
        </span>
      </div>
      {preview ? (
        <div className="prose prose-sm max-w-none rounded-sm border border-border bg-card px-4 py-3" aria-label="Theory preview">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{value}</ReactMarkdown>
        </div>
      ) : (
        <Textarea rows={12} className="font-mono" aria-label="Theory markdown" value={value} onChange={(e) => onChange(e.target.value)} />
      )}
    </div>
  );
}

function StepFields({ s, patch, label }: { s: StepState; patch: (p: Partial<StepState>) => void; label: string }) {
  const prompt = (
    <label className={`block ${field}`}>Question / instruction (Markdown)
      <Textarea rows={2} value={s.prompt} onChange={(e) => patch({ prompt: e.target.value })} />
    </label>
  );
  const hintAndExplanation = (
    <div className="grid gap-3 sm:grid-cols-2">
      <label className={field}>Hint (optional)<Input value={s.hint} onChange={(e) => patch({ hint: e.target.value })} /></label>
      <label className={field}>Explanation shown after a correct answer
        <Input value={s.explanation} onChange={(e) => patch({ explanation: e.target.value })} /></label>
    </div>
  );

  switch (s.type) {
    case "THEORY":
      return <TheoryField value={s.markdown} onChange={(markdown) => patch({ markdown })} />;
    case "CHOICE":
      return (
        <>
          {prompt}
          <label className="flex items-center gap-2 text-body-sm text-ink">
            <input type="checkbox" checked={s.multiple} onChange={(e) => patch({ multiple: e.target.checked,
              options: e.target.checked ? s.options : s.options.map((o, i) => ({ ...o, correct: i === s.options.findIndex((x) => x.correct) })) })} />
            Several correct options
          </label>
          <ol className="space-y-2" aria-label={`${label} options`}>
            {s.options.map((o, i) => (
              <li key={i} className="flex items-center gap-2">
                <input type={s.multiple ? "checkbox" : "radio"} name={`correct-${s.key}`} checked={o.correct}
                  aria-label={`Option ${i + 1} is correct`}
                  onChange={() => patch({ options: s.options.map((x, k) => ({ ...x, correct: s.multiple ? (k === i ? !x.correct : x.correct) : k === i })) })} />
                <Input value={o.text} placeholder={`Option ${i + 1}`} aria-label={`Option ${i + 1}`}
                  onChange={(e) => patch({ options: s.options.map((x, k) => (k === i ? { ...x, text: e.target.value } : x)) })} />
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove option ${i + 1}`} disabled={s.options.length <= 2}
                  onClick={() => patch({ options: s.options.filter((_, k) => k !== i) })}><X className="h-4 w-4" /></Button>
              </li>
            ))}
          </ol>
          <Button type="button" variant="outline" size="sm" disabled={s.options.length >= 10}
            onClick={() => patch({ options: [...s.options, { text: "", correct: false }] })}><Plus className="h-4 w-4" />Add option</Button>
          {hintAndExplanation}
        </>
      );
    case "MATCHING":
      return (
        <>
          {prompt}
          <p className="text-body-sm text-mute">Write each pair on one row; students see the right column shuffled.</p>
          <ol className="space-y-2" aria-label={`${label} pairs`}>
            {s.pairs.map((p, i) => (
              <li key={i} className="flex items-center gap-2">
                <Input value={p.left} placeholder="Left" aria-label={`Pair ${i + 1} left`}
                  onChange={(e) => patch({ pairs: s.pairs.map((x, k) => (k === i ? { ...x, left: e.target.value } : x)) })} />
                <span aria-hidden className="text-mute">↔</span>
                <Input value={p.right} placeholder="Right" aria-label={`Pair ${i + 1} right`}
                  onChange={(e) => patch({ pairs: s.pairs.map((x, k) => (k === i ? { ...x, right: e.target.value } : x)) })} />
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove pair ${i + 1}`} disabled={s.pairs.length <= 2}
                  onClick={() => patch({ pairs: s.pairs.filter((_, k) => k !== i) })}><X className="h-4 w-4" /></Button>
              </li>
            ))}
          </ol>
          <Button type="button" variant="outline" size="sm" disabled={s.pairs.length >= 10}
            onClick={() => patch({ pairs: [...s.pairs, { left: "", right: "" }] })}><Plus className="h-4 w-4" />Add pair</Button>
          {hintAndExplanation}
        </>
      );
    case "ORDERING":
      return (
        <>
          {prompt}
          <p className="text-body-sm text-mute">Enter the lines in the <strong>correct</strong> order; students get them shuffled.</p>
          <ol className="space-y-2" aria-label={`${label} lines`}>
            {s.lines.map((line, i) => (
              <li key={i} className="flex items-center gap-2">
                <span className="w-6 text-right font-mono text-body-sm text-mute">{i + 1}</span>
                <Input value={line} className="font-mono" aria-label={`Line ${i + 1}`}
                  onChange={(e) => patch({ lines: s.lines.map((x, k) => (k === i ? e.target.value : x)) })} />
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move line ${i + 1} up`} disabled={i === 0}
                  onClick={() => patch({ lines: move(s.lines, i, i - 1) })}><ArrowUp className="h-4 w-4" /></Button>
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move line ${i + 1} down`} disabled={i === s.lines.length - 1}
                  onClick={() => patch({ lines: move(s.lines, i, i + 1) })}><ArrowDown className="h-4 w-4" /></Button>
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove line ${i + 1}`} disabled={s.lines.length <= 2}
                  onClick={() => patch({ lines: s.lines.filter((_, k) => k !== i) })}><X className="h-4 w-4" /></Button>
              </li>
            ))}
          </ol>
          <Button type="button" variant="outline" size="sm" disabled={s.lines.length >= 15}
            onClick={() => patch({ lines: [...s.lines, ""] })}><Plus className="h-4 w-4" />Add line</Button>
          {hintAndExplanation}
        </>
      );
    case "FILL_BLANK": {
      const ids = blankIds(s.code);
      return (
        <>
          {prompt}
          <label className={`block ${field}`}>Code — write a blank as [[1]], [[2]]…
            <Textarea rows={6} className="font-mono" value={s.code} onChange={(e) => patch({ code: e.target.value })} />
          </label>
          {ids.length === 0 && <p className="text-body-sm text-destructive">Add at least one blank such as [[1]].</p>}
          <div className="grid gap-2 sm:grid-cols-2">
            {ids.map((id) => (
              <label key={id} className={field}>Blank [[{id}]] — accepted answers, separated by |
                <Input value={s.accepted[id] ?? ""} className="font-mono" onChange={(e) => patch({ accepted: { ...s.accepted, [id]: e.target.value } })} />
              </label>
            ))}
          </div>
          <label className="flex items-center gap-2 text-body-sm text-ink">
            <input type="checkbox" checked={s.caseSensitive} onChange={(e) => patch({ caseSensitive: e.target.checked })} />
            Case-sensitive
          </label>
          {hintAndExplanation}
        </>
      );
    }
    case "CODE":
      return (
        <>
          <label className={`block ${field}`}>Intro shown above the exercise (optional)
            <Input value={s.intro} onChange={(e) => patch({ intro: e.target.value })} />
          </label>
          {s.problem ? (
            <div className="flex items-center gap-2 rounded-sm border border-border px-3 py-2">
              <span className="min-w-0 flex-1 truncate text-ink">{s.problem.title ?? `#${s.problem.id}`}
                {" "}<span className="font-mono text-body-sm text-mute">{s.problem.slug}</span></span>
              <Button type="button" variant="ghost" size="icon-sm" aria-label="Change problem" onClick={() => patch({ problem: null })}>
                <X className="h-4 w-4" /></Button>
            </div>
          ) : (
            <ProblemPicker exclude={new Set()} onPick={(p) => patch({ problem: { id: p.id, slug: p.slug, title: p.title } })} />
          )}
          <p className="text-body-sm text-mute">The step is done when the student has an accepted submission for this problem.</p>
        </>
      );
  }
}

const draftJson = (slug: string, title: string, summary: string, estMinutes: number, steps: StepState[]) =>
  JSON.stringify([slug, title, summary, estMinutes, steps.map((s) => ({ ...toAuthoring(s), id: undefined }))]);

// =====================================================================================
// /admin/topics/new  và  /admin/topics/[slug]
// =====================================================================================

export type SaveState = "idle" | "saving" | "saved" | "error" | "invalid";

/**
 * {@code embedded}: nhúng trong Course Builder — không header / footer riêng, tự lưu 1 s sau lần sửa cuối,
 * báo trạng thái qua {@code onSaveState} và bản đã lưu qua {@code onSaved}.
 */
export function TopicEditor({ slug, embedded = false, onSaved, onSaveState }: {
  slug?: string; embedded?: boolean; onSaved?: (t: AuthoringTopic) => void; onSaveState?: (s: SaveState) => void;
}) {
  const router = useRouter();
  const lastSaved = useRef<string>("");
  const canEditFn = useCanEdit();
  const [loaded, setLoaded] = useState<AuthoringTopic | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [topicSlug, setTopicSlug] = useState("");
  const [slugTouched, setSlugTouched] = useState(false);
  const [summary, setSummary] = useState("");
  const [estMinutes, setEstMinutes] = useState(10);
  const [steps, setSteps] = useState<StepState[]>([emptyStep("THEORY")]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const apply = (t: AuthoringTopic) => {
    setLoaded(t);
    setTitle(t.title);
    setTopicSlug(t.slug);
    setSlugTouched(true);
    setSummary(t.summary ?? "");
    setEstMinutes(t.estMinutes);
    const next = t.steps.map(fromAuthoring);
    setSteps(next);
    lastSaved.current = draftJson(t.slug, t.title, t.summary ?? "", t.estMinutes, next);
  };

  useEffect(() => {
    if (!slug) return;
    fetchTopicForAuthoring(slug).then(apply).catch((e) => setLoadError(errorMessage(e)));
  }, [slug]);

  const canEdit = !slug || (loaded !== null && canEditFn(loaded.createdById));
  const patchStep = (key: number, p: Partial<StepState>) => setSteps((list) => list.map((s) => (s.key === key ? { ...s, ...p } : s)));

  const validate = (): string | null => {
    if (!title.trim()) return "Give the topic a title.";
    if (!SLUG_RE.test(topicSlug)) return "The slug may only contain lowercase letters, digits and single hyphens.";
    for (let i = 0; i < steps.length; i++) {
      const s = steps[i];
      const where = `Step ${i + 1} (${STEP_LABEL[s.type]})`;
      if (!s.title.trim()) return `${where} needs a title.`;
      if (s.type === "CHOICE" && !s.options.some((o) => o.correct)) return `${where}: mark the correct option.`;
      if (s.type === "CODE" && !s.problem) return `${where}: choose the problem.`;
    }
    return null;
  };

  const save = async () => {
    const problem = validate();
    setError(problem);
    if (problem) {
      onSaveState?.("invalid");
      return;
    }
    setSaving(true);
    onSaveState?.("saving");
    const json = draftJson(topicSlug, title, summary, estMinutes, steps);
    try {
      const saved = await saveTopic(slug ?? null, {
        slug: topicSlug, title: title.trim(), summary: summary.trim() || undefined, estMinutes, steps: steps.map(toAuthoring),
      });
      onSaved?.(saved);
      onSaveState?.("saved");
      if (embedded) {
        // Giữ nguyên ô đang gõ: chỉ gán id cho step vừa tạo.
        setLoaded(saved);
        setSteps((list) => list.map((s, i) => (s.id ? s : { ...s, id: saved.steps[i]?.id ?? null })));
        lastSaved.current = json;
        return;
      }
      apply(saved);
      toast(slug ? "Topic saved" : "Topic created as a draft", "success");
      if (saved.slug !== slug) router.replace(`/admin/topics/${saved.slug}`);
    } catch (e) {
      setError(errorMessage(e));
      onSaveState?.("error");
    } finally {
      setSaving(false);
    }
  };

  // Autosave khi nhúng trong Course Builder.
  const current = draftJson(topicSlug, title, summary, estMinutes, steps);
  useEffect(() => {
    if (!embedded || !slug || !loaded || !canEdit || saving || current === lastSaved.current) return;
    const t = setTimeout(() => void save(), 1000);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current, embedded, slug, loaded, saving]);

  const transition = async (status: StudyPlanStatus) => {
    if (!slug) return;
    try {
      apply(await transitionTopic(slug, status));
      toast(status === "PUBLISHED" ? "Published — attach it to a study plan stage" : `Moved to ${status.toLowerCase()}`, "success");
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const ask = useConfirm();
  const remove = async () => {
    if (!slug || !(await ask({ title: `Delete "${title}"?`, description: "Student progress on this topic is deleted too.", action: "Delete", destructive: true }))) return;
    try {
      await deleteTopic(slug);
      toast("Topic deleted", "success");
      router.push("/admin/topics");
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  if (slug && loadError) return <div className="mx-auto max-w-container px-4 py-6 text-destructive" role="alert">{loadError}</div>;
  if (slug && !loaded) return <div className="mx-auto max-w-container px-4 py-6 text-mute">Loading topic…</div>;

  const status = loaded?.status;
  return (
    <div className={embedded ? "space-y-5" : "mx-auto w-full max-w-3xl space-y-5 px-4 py-6 sm:px-6"}>
      {embedded ? (
        !canEdit && (
          <p className="rounded-sm border border-border bg-hairline-soft px-3 py-2 text-body-sm text-mute" role="note">
            Read-only: only {loaded?.createdByUsername ?? "the author"} or an admin can change this topic.
          </p>
        )
      ) : (
      <header className="space-y-2">
        <Link href="/admin/topics" className="text-body-sm text-link hover:underline">← Topics</Link>
        <div className="flex flex-wrap items-center gap-3">
          {status && <Badge variant={STATUS_VARIANT[status]}>{status}</Badge>}
        </div>
        {!canEdit && (
          <p className="rounded-sm border border-border bg-hairline-soft px-3 py-2 text-body-sm text-mute" role="note">
            Read-only: only {loaded?.createdByUsername ?? "the author"} or an admin can change this topic.
          </p>
        )}
      </header>
      )}

      <fieldset disabled={!canEdit || (saving && !embedded)} className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-[1fr_1fr_120px]">
          <label className={field}>Title
            <Input value={title} onChange={(e) => { setTitle(e.target.value); if (!slugTouched) setTopicSlug(slugify(e.target.value)); }} />
          </label>
          <label className={field}>Slug (URL)
            <Input value={topicSlug} className="font-mono" onChange={(e) => { setSlugTouched(true); setTopicSlug(e.target.value); }} />
          </label>
          <label className={field}>Minutes
            <Input type="number" min={1} max={600} value={estMinutes} onChange={(e) => setEstMinutes(Number(e.target.value) || 1)} />
          </label>
        </div>
        <label className={`block ${field}`}>Summary shown on the stage timeline
          <Textarea rows={2} value={summary} onChange={(e) => setSummary(e.target.value)} />
        </label>

        <div className="space-y-3">
          {steps.map((s, i) => {
            const label = `Step ${i + 1}`;
            return (
              <section key={s.key} className="space-y-3 rounded-sm border border-border bg-card p-4" aria-label={`${label}: ${STEP_LABEL[s.type]}`}>
                <div className="flex flex-wrap items-end gap-2">
                  <span className="pb-2 font-mono text-body-sm uppercase text-mute">{i + 1}. {STEP_LABEL[s.type]}</span>
                  <label className={`min-w-0 flex-1 ${field}`}>Step title
                    <Input value={s.title} onChange={(e) => patchStep(s.key, { title: e.target.value })} />
                  </label>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move ${label} up`} disabled={i === 0}
                    onClick={() => setSteps((l) => move(l, i, i - 1))}><ArrowUp className="h-4 w-4" /></Button>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Move ${label} down`} disabled={i === steps.length - 1}
                    onClick={() => setSteps((l) => move(l, i, i + 1))}><ArrowDown className="h-4 w-4" /></Button>
                  <Button type="button" variant="ghost" size="icon-sm" aria-label={`Remove ${label}`}
                    onClick={() => setSteps((l) => l.filter((x) => x.key !== s.key))}><Trash2 className="h-4 w-4" /></Button>
                </div>
                <StepFields s={s} label={label} patch={(p) => patchStep(s.key, p)} />
              </section>
            );
          })}
          <div className="flex flex-wrap gap-2" role="group" aria-label="Add a step">
            {(Object.keys(STEP_LABEL) as TopicStepType[]).map((type) => (
              <Button key={type} type="button" variant="outline" size="sm" onClick={() => setSteps((l) => [...l, emptyStep(type)])}>
                <Plus className="h-4 w-4" />{STEP_LABEL[type]}
              </Button>
            ))}
          </div>
        </div>
      </fieldset>

      {error && <p className="rounded-sm border border-destructive/30 bg-destructive/5 px-3 py-2 text-body-sm text-destructive" role="alert">{error}</p>}

      {canEdit && !embedded && (
        <footer className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
          <Button type="button" onClick={save} disabled={saving}>{saving ? "Saving…" : slug ? "Save changes" : "Create draft"}</Button>
          {status && status !== "PUBLISHED" && <Button type="button" variant="outline" onClick={() => transition("PUBLISHED")}>Publish</Button>}
          {status && status !== "DRAFT" && <Button type="button" variant="outline" onClick={() => transition("DRAFT")}>Move to draft</Button>}
          {status === "PUBLISHED" && <Button type="button" variant="outline" onClick={() => transition("ARCHIVED")}>Archive</Button>}
          {slug && <Button type="button" variant="ghost" className="ml-auto text-destructive" onClick={remove}><Trash2 className="h-4 w-4" />Delete</Button>}
          {slug && <Link href={`/topics/${slug}`} className="text-body-sm text-link hover:underline">Preview as student</Link>}
        </footer>
      )}
    </div>
  );
}
