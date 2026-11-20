"use client";
import { useEffect, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { errorMessage } from "@/lib/api";
import {
  createTag, createTagCategory, deleteTag, deleteTagCategory, fetchAdminTags, fetchTagCategories, updateTagCategory,
  updateTagCategoryInfo,
} from "@/lib/services";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import type { AdminTag, TagCategory, TagCategoryInfo } from "@/types/api";

const selectClass = "h-9 rounded-sm border border-border bg-card px-2 text-body-md text-ink";
const FALLBACK_COLOR = "#64748b";

/** Nhóm chủ đề cho khối Topics trên trang Problems. Chỉ ADMIN. Trái: nhóm (chọn để lọc), phải: chủ đề. */
export function AdminTagsView() {
  const [tags, setTags] = useState<AdminTag[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [category, setCategory] = useState<TagCategory>("Other");
  const [cats, setCats] = useState<TagCategoryInfo[]>([]);
  const [catName, setCatName] = useState("");
  // null = "All".
  const [selected, setSelected] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const confirm = useConfirm();

  const reload = () =>
    Promise.all([fetchAdminTags(), fetchTagCategories()])
      .then(([data, c]) => { setTags(data); setCats(c); setError(null); })
      .catch((e) => setError(errorMessage(e)));

  // Thao tác nhóm: lỗi thì báo, luôn tải lại cả nhóm lẫn chủ đề (đổi tên/xoá nhóm ảnh hưởng chủ đề).
  const catAction = async (run: () => Promise<unknown>, done?: string) => {
    try {
      await run();
      if (done) toast(done, "success");
    } catch (err) {
      toast(errorMessage(err), "error");
    }
    await reload();
  };
  const addCat = (e: React.FormEvent) => {
    e.preventDefault();
    const n = catName.trim();
    if (!n) return;
    setCatName("");
    void catAction(() => createTagCategory({ name: n }), `Category "${n}" added.`);
  };
  const removeCat = async (c: TagCategoryInfo) => {
    const ok = await confirm({
      title: `Delete category "${c.name}"?`, action: "Delete", destructive: true,
      description: "Its tags move to Other.",
    });
    if (!ok) return;
    if (selected === c.name) setSelected(null);
    void catAction(() => deleteTagCategory(c.id), `Category "${c.name}" deleted.`);
  };
  const rename = (c: TagCategoryInfo, value: string) => {
    setRenaming(null);
    const n = value.trim();
    if (!n || n === c.name) return;
    if (selected === c.name) setSelected(n);
    void catAction(() => updateTagCategoryInfo(c.id, { name: n }));
  };
  const select = (c: string | null) => {
    setSelected(c);
    setCategory(c ?? "Other");
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void reload(); }, []);

  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    try {
      await createTag(name.trim(), category);
      toast(`Tag "${name.trim()}" added.`, "success");
      setName("");
      await reload();
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setBusy(false);
    }
  };

  const regroup = async (tag: AdminTag, next: TagCategory) => {
    setTags((list) => list?.map((t) => (t.name === tag.name ? { ...t, category: next } : t)) ?? null);
    try {
      await updateTagCategory(tag.name, next);
    } catch (err) {
      toast(errorMessage(err), "error");
      await reload();
    }
  };

  const remove = async (tag: AdminTag) => {
    const ok = await confirm({ title: `Delete tag "${tag.name}"?`, action: "Delete", destructive: true });
    if (!ok) return;
    try {
      await deleteTag(tag.name);
      toast(`Tag "${tag.name}" deleted.`, "success");
      await reload();
    } catch (err) {
      toast(errorMessage(err), "error");
    }
  };

  const colorOf = (c: string) => cats.find((x) => x.name === c)?.color ?? FALLBACK_COLOR;
  const countOf = (c: string) => tags?.filter((t) => t.category === c).length ?? 0;
  const shown = tags?.filter((t) => selected === null || t.category === selected) ?? null;
  const rowClass = (active: boolean) =>
    cn("group flex items-center gap-2 rounded-sm px-2 py-1.5", active ? "bg-muted" : "hover:bg-muted/60");

  return (
    <div className="mx-auto grid w-full max-w-container gap-6 px-8 py-6 md:grid-cols-[18rem_minmax(0,1fr)]">
      <section aria-labelledby="tag-categories" className="space-y-3">
        <h2 id="tag-categories" className="text-heading-md font-semibold text-ink">Categories</h2>
        <ul className="space-y-0.5 rounded-sm border border-border bg-card p-2">
          <li className={rowClass(selected === null)}>
            <button type="button" onClick={() => select(null)} aria-pressed={selected === null} className="flex flex-1 items-center gap-2 text-left text-body-md text-ink">
              <span className="h-3 w-3 rounded-full border border-border" aria-hidden />
              All
            </button>
            <span className="font-mono text-body-sm text-mute">{tags?.length ?? 0}</span>
            <span className="w-10" aria-hidden />
          </li>
          {cats.map((c) => (
            <li key={c.id} className={rowClass(selected === c.name)}>
              <label className="relative h-3 w-3 shrink-0 cursor-pointer rounded-full" style={{ backgroundColor: c.color }}>
                <input
                  type="color" value={c.color} aria-label={`Color for ${c.name}`}
                  onChange={(e) => setCats((l) => l.map((x) => (x.id === c.id ? { ...x, color: e.target.value } : x)))}
                  onBlur={(e) => void catAction(() => updateTagCategoryInfo(c.id, { color: e.target.value }))}
                  className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                />
              </label>
              {renaming === c.id ? (
                <input
                  autoFocus defaultValue={c.name} aria-label={`Rename ${c.name}`} maxLength={50}
                  onBlur={(e) => rename(c, e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") e.currentTarget.blur();
                    if (e.key === "Escape") setRenaming(null);
                  }}
                  className="min-w-0 flex-1 rounded-sm border border-border bg-card px-1 text-body-md text-ink outline-none"
                />
              ) : (
                <button type="button" onClick={() => select(c.name)} aria-pressed={selected === c.name} className="min-w-0 flex-1 truncate text-left text-body-md text-ink">
                  {c.name}
                </button>
              )}
              <span className="font-mono text-body-sm text-mute">{countOf(c.name)}</span>
              <span className="flex w-10 justify-end gap-1 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
                {c.name !== "Other" && (<>
                  <button type="button" onClick={() => setRenaming(c.id)} aria-label={`Rename category ${c.name}`} className="text-mute hover:text-ink">
                    <Pencil className="h-3.5 w-3.5" />
                  </button>
                  <button type="button" onClick={() => removeCat(c)} aria-label={`Delete category ${c.name}`} className="text-mute hover:text-destructive">
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </>)}
              </span>
            </li>
          ))}
          <li>
            <form onSubmit={addCat} className="flex items-center gap-1 px-1 pt-1" aria-label="Add category">
              <Input value={catName} onChange={(e) => setCatName(e.target.value)} placeholder="New category" maxLength={50} aria-label="Category name" className="h-8 flex-1" />
              <Button type="submit" variant="ghost" size="icon" disabled={!catName.trim()} aria-label="Add category"><Plus className="h-4 w-4" /></Button>
            </form>
          </li>
        </ul>
      </section>

      <section aria-labelledby="tag-list" className="min-w-0 space-y-3">
        <h2 id="tag-list" className="text-heading-md font-semibold text-ink">Tags</h2>
        <form onSubmit={add} className="flex flex-wrap items-center gap-2" aria-label="Add tag">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New tag name" maxLength={50} aria-label="Tag name" className="w-56" />
          <select value={category} onChange={(e) => setCategory(e.target.value as TagCategory)} aria-label="Category for new tag" className={selectClass}>
            {cats.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
          <Button type="submit" disabled={busy || !name.trim()}><Plus className="h-4 w-4" />Add tag</Button>
        </form>

        <div className="overflow-hidden rounded-sm border border-border bg-card" role="region" aria-label="Tags">
          {error && <p className="p-6 text-body-md text-destructive" role="alert">{error}</p>}
          {!error && shown === null && <p className="p-6 text-body-md text-mute">Loading tags…</p>}
          {shown?.length === 0 && <p className="p-6 text-body-md text-mute">No tags yet.</p>}
          {shown && shown.length > 0 && (
            <table className="w-full text-left text-body-md">
              <thead className="border-b border-border text-body-sm text-mute">
                <tr>
                  <th className="px-4 py-2 font-medium">Tag</th>
                  <th className="px-4 py-2 font-medium">Category</th>
                  <th className="px-4 py-2 text-right font-medium">Problems</th>
                  <th className="w-16 px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {shown.map((t) => (
                  <tr key={t.name} className="border-b border-border last:border-0">
                    <td className="px-4 py-2 font-medium text-ink">{t.name}</td>
                    <td className="px-4 py-2">
                      <DropdownMenu>
                        <DropdownMenuTrigger
                          aria-label={`Category for ${t.name}`}
                          className="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-body-sm text-ink hover:bg-muted"
                          style={{ borderColor: colorOf(t.category) }}
                        >
                          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: colorOf(t.category) }} aria-hidden />
                          {t.category}
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="start">
                          <DropdownMenuRadioGroup value={t.category} onValueChange={(v) => void regroup(t, v as TagCategory)}>
                            {cats.map((c) => (
                              <DropdownMenuRadioItem key={c.id} value={c.name} className="gap-2">
                                <span className="h-2 w-2 rounded-full" style={{ backgroundColor: c.color }} aria-hidden />
                                {c.name}
                              </DropdownMenuRadioItem>
                            ))}
                          </DropdownMenuRadioGroup>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </td>
                    <td className="px-4 py-2 text-right font-mono text-mute">{t.problemCount}</td>
                    <td className="px-4 py-2 text-right">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => remove(t)}
                        disabled={t.problemCount > 0}
                        title={t.problemCount > 0 ? "Remove the tag from its problems before deleting it" : "Delete tag"}
                        aria-label={`Delete ${t.name}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </div>
  );
}
