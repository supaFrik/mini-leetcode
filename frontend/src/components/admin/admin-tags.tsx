"use client";
import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import { errorMessage } from "@/lib/api";
import {
  createTag, createTagCategory, deleteTag, deleteTagCategory, fetchAdminTags, fetchTagCategories, updateTag,
  updateTagCategoryInfo,
} from "@/lib/services";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import { ColorPicker } from "./color-picker";
import type { AdminTag, TagCategoryInfo } from "@/types/api";

const OTHER = "Other";
const selectClass = "h-9 w-full rounded-sm border border-border bg-card px-2 text-body-md text-ink";
const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/20 p-4" role="dialog" aria-modal="true" aria-label={title}
      onKeyDown={(e) => e.key === "Escape" && onClose()}>
      <div className="w-full max-w-sm space-y-4 rounded-sm bg-background p-5 shadow-lg">
        <div className="flex items-center justify-between">
          <h2 className="text-heading-md text-ink">{title}</h2>
          <Button size="icon-sm" variant="ghost" aria-label="Close" onClick={onClose}><X className="h-4 w-4" /></Button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** `color` null = dùng màu category. */
type TagForm = { original: AdminTag | null; name: string; category: string; color: string | null; error: string | null };
type CatForm = { original: TagCategoryInfo | null; name: string; color: string; error: string | null };

/** /admin/tags: tab Tags (gom theo category) và tab Categories. Thêm / sửa qua hộp thoại. */
export function AdminTagsView() {
  const [tab, setTab] = useState<"tags" | "categories">("tags");
  const [tags, setTags] = useState<AdminTag[] | null>(null);
  const [cats, setCats] = useState<TagCategoryInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [tagForm, setTagForm] = useState<TagForm | null>(null);
  const [catForm, setCatForm] = useState<CatForm | null>(null);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState("");
  const [catFilter, setCatFilter] = useState("");
  const confirm = useConfirm();

  const reload = () =>
    Promise.all([fetchAdminTags(), fetchTagCategories()])
      .then(([t, c]) => { setTags(t); setCats(c); setError(null); })
      .catch((e) => setError(errorMessage(e)));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void reload(); }, []);

  const catOf = (t: AdminTag) => (cats.some((x) => x.name === t.category) ? t.category : OTHER);
  const tagsIn = (c: string) => tags?.filter((t) => catOf(t) === c) ?? [];
  const catColor = (c: string) => cats.find((x) => x.name === c)?.color ?? "#64748b";
  const shown = (tags ?? []).filter((t) => (!catFilter || catOf(t) === catFilter)
    && t.name.toLowerCase().includes(query.trim().toLowerCase()));

  // Chạy một thao tác trong hộp thoại: lỗi hiện ngay trong hộp thoại, thành công thì đóng và tải lại.
  const run = async (task: () => Promise<unknown>, fail: (msg: string) => void, done: () => void) => {
    setBusy(true);
    try {
      await task();
      done();
      await reload();
    } catch (e) {
      fail(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const saveTag = (e: React.FormEvent) => {
    e.preventDefault();
    if (!tagForm) return;
    const name = tagForm.name.trim();
    if (!name) return setTagForm({ ...tagForm, error: "Enter a tag name." });
    const o = tagForm.original;
    void run(
      () => (o
        ? updateTag(o.name, {
          name: name !== o.name ? name : undefined,
          category: tagForm.category !== o.category ? tagForm.category : undefined,
          color: tagForm.color !== (o.color ?? null) ? tagForm.color ?? "" : undefined,
        })
        : createTag(name, tagForm.category)),
      (msg) => setTagForm((f) => f && { ...f, error: msg }),
      () => { setTagForm(null); toast(o ? `Tag "${name}" saved.` : `Tag "${name}" added.`, "success"); },
    );
  };

  const removeTag = async (t: AdminTag) => {
    const ok = await confirm({
      title: `Delete tag "${t.name}"?`, action: "Delete", destructive: true,
      description: t.problemCount > 0 ? `${plural(t.problemCount, "problem")} use this tag. It will be removed from them.` : undefined,
    });
    if (!ok) return;
    void run(() => deleteTag(t.name), (msg) => toast(msg, "error"),
      () => { setTagForm(null); toast(`Tag "${t.name}" deleted.`, "success"); });
  };

  const saveCat = (e: React.FormEvent) => {
    e.preventDefault();
    if (!catForm) return;
    const name = catForm.name.trim();
    if (!name) return setCatForm({ ...catForm, error: "Enter a category name." });
    const o = catForm.original;
    void run(
      () => (o ? updateTagCategoryInfo(o.id, { name: name !== o.name ? name : undefined, color: catForm.color })
        : createTagCategory({ name, color: catForm.color })),
      (msg) => setCatForm((f) => f && { ...f, error: msg }),
      () => { setCatForm(null); toast(o ? `Category "${name}" saved.` : `Category "${name}" added.`, "success"); },
    );
  };

  // Đổi màu category: lưu ngay khi chọn.
  const recolorCat = (c: TagCategoryInfo, color: string) =>
    void run(() => updateTagCategoryInfo(c.id, { color }), (msg) => toast(msg, "error"), () => toast(`Color of "${c.name}" saved.`, "success"));

  const removeCat = async (c: TagCategoryInfo) => {
    const n = tagsIn(c.name).length;
    const ok = await confirm({
      title: `Delete category "${c.name}"?`, action: "Delete", destructive: true,
      description: n > 0 ? `Its ${plural(n, "tag")} will move to ${OTHER}.` : undefined,
    });
    if (!ok) return;
    void run(() => deleteTagCategory(c.id), (msg) => toast(msg, "error"),
      () => toast(`Category "${c.name}" deleted.${n > 0 ? ` ${plural(n, "tag")} moved to ${OTHER}.` : ""}`, "success"));
  };

  const tabClass = (on: boolean) => cn("-mb-px border-b-2 pb-2", on ? "border-ink font-medium text-ink" : "border-transparent text-mute hover:text-ink");

  return (
    <div className="mx-auto w-full max-w-container space-y-6 px-8 py-6">
      <nav className="flex gap-6 border-b border-border text-body-md" aria-label="Tag sections">
        <button type="button" className={tabClass(tab === "tags")} aria-current={tab === "tags" ? "page" : undefined} onClick={() => setTab("tags")}>Tags</button>
        <button type="button" className={tabClass(tab === "categories")} aria-current={tab === "categories" ? "page" : undefined} onClick={() => setTab("categories")}>Categories</button>
      </nav>

      {tab === "tags" ? (
        <div className="flex flex-wrap items-center gap-2">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search tags…" aria-label="Search tags" className="h-9 min-w-0 flex-1 basis-56" />
          <select value={catFilter} onChange={(e) => setCatFilter(e.target.value)} aria-label="Filter by category" className={cn(selectClass, "w-auto")}>
            <option value="">All categories</option>
            {cats.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
          <Button onClick={() => setTagForm({ original: null, name: "", category: OTHER, color: null, error: null })}>New tag</Button>
        </div>
      ) : (
        <div className="flex justify-end">
          <Button onClick={() => setCatForm({ original: null, name: "", color: "#64748b", error: null })}>New category</Button>
        </div>
      )}

      {error && <p className="text-body-md text-destructive" role="alert">{error}</p>}
      {!error && tags === null && <p className="text-body-md text-mute">Loading tags…</p>}

      {tags && tab === "tags" && (
        <div role="region" aria-label="Tags">
          <p className="pb-2 text-body-sm text-mute">{plural(shown.length, "tag")}</p>
          {shown.length === 0 && <p className="border-t border-border py-3 text-body-md text-mute">No tags match.</p>}
          <ul className="divide-y divide-border border-y border-border">
            {shown.map((t) => (
              <li key={t.name} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 py-2.5 text-body-md sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
                <span className="flex min-w-0 items-center gap-2.5">
                  <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: t.color ?? catColor(catOf(t)) }} aria-hidden />
                  <span className="truncate font-medium text-ink">{t.name}</span>
                </span>
                <span className="hidden truncate text-body-sm text-mute sm:block">{catOf(t)}</span>
                <span className={cn("hidden text-body-sm sm:block", t.problemCount > 0 ? "text-ink/80" : "text-faint")}>
                  {t.problemCount > 0 ? plural(t.problemCount, "problem") : "Unused"}
                </span>
                <button type="button" className="text-body-sm text-link hover:underline" aria-label={`Edit ${t.name}`}
                  onClick={() => setTagForm({ original: t, name: t.name, category: catOf(t), color: t.color ?? null, error: null })}>Edit</button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {tags && tab === "categories" && (
        <ul className="divide-y divide-border" aria-label="Categories">
          {cats.map((c) => (
            <li key={c.id} className="flex items-center gap-3 py-2 text-body-md">
              <ColorPicker value={c.color} label={`Color of ${c.name}`} onChange={(color) => recolorCat(c, color)} />
              <span className="min-w-0 flex-1 truncate text-ink">{c.name}</span>
              <span className="text-body-sm text-mute">{plural(tagsIn(c.name).length, "tag")}</span>
              {c.name !== OTHER ? (
                <span className="flex gap-1">
                  <Button size="sm" variant="ghost" aria-label={`Rename category ${c.name}`}
                    onClick={() => setCatForm({ original: c, name: c.name, color: c.color, error: null })}>Rename</Button>
                  <Button size="sm" variant="ghost" className="text-destructive" aria-label={`Delete category ${c.name}`} onClick={() => removeCat(c)}>Delete</Button>
                </span>
              ) : <span className="w-[136px]" aria-hidden />}
            </li>
          ))}
        </ul>
      )}

      {tagForm && (
        <Dialog title={tagForm.original ? "Edit tag" : "New tag"} onClose={() => setTagForm(null)}>
          <form onSubmit={saveTag} className="space-y-3" noValidate>
            <label className="block space-y-1 text-body-sm text-mute">Name
              <Input autoFocus value={tagForm.name} maxLength={50} aria-invalid={!!tagForm.error}
                onChange={(e) => setTagForm({ ...tagForm, name: e.target.value, error: null })} />
            </label>
            <label className="block space-y-1 text-body-sm text-mute">Category
              <select value={tagForm.category} onChange={(e) => setTagForm({ ...tagForm, category: e.target.value })} className={selectClass}>
                {cats.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
              </select>
            </label>
            <div className="flex items-center gap-2 text-body-sm text-mute">Color
              <ColorPicker value={tagForm.color ?? catColor(tagForm.category)} label="Tag color"
                onChange={(color) => setTagForm({ ...tagForm, color })}>
                {tagForm.color && (
                  <button type="button" className="text-body-sm text-link" onClick={() => setTagForm({ ...tagForm, color: null })}>Use category color</button>
                )}
              </ColorPicker>
              <span>{tagForm.color ? tagForm.color : "Category color"}</span>
            </div>
            {tagForm.original && <p className="text-body-sm text-mute">Used by {plural(tagForm.original.problemCount, "problem")}.</p>}
            {tagForm.error && <p className="text-body-sm text-destructive" role="alert">{tagForm.error}</p>}
            <div className="flex items-center justify-between gap-2 pt-1">
              {tagForm.original
                ? <Button type="button" variant="ghost" className="text-destructive" disabled={busy} onClick={() => removeTag(tagForm.original!)}>Delete</Button>
                : <span />}
              <div className="flex gap-2">
                <Button type="button" variant="ghost" onClick={() => setTagForm(null)}>Cancel</Button>
                <Button type="submit" disabled={busy}>{tagForm.original ? "Save" : "Add tag"}</Button>
              </div>
            </div>
          </form>
        </Dialog>
      )}

      {catForm && (
        <Dialog title={catForm.original ? "Edit category" : "New category"} onClose={() => setCatForm(null)}>
          <form onSubmit={saveCat} className="space-y-3" noValidate>
            <label className="block space-y-1 text-body-sm text-mute">Name
              <Input autoFocus value={catForm.name} maxLength={50} aria-invalid={!!catForm.error}
                onChange={(e) => setCatForm({ ...catForm, name: e.target.value, error: null })} />
            </label>
            <label className="flex items-center gap-2 text-body-sm text-mute">Color
              <input type="color" value={catForm.color} onChange={(e) => setCatForm({ ...catForm, color: e.target.value })} className="h-6 w-8 cursor-pointer bg-transparent" />
            </label>
            {catForm.error && <p className="text-body-sm text-destructive" role="alert">{catForm.error}</p>}
            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="ghost" onClick={() => setCatForm(null)}>Cancel</Button>
              <Button type="submit" disabled={busy}>{catForm.original ? "Save" : "Add category"}</Button>
            </div>
          </form>
        </Dialog>
      )}
    </div>
  );
}
