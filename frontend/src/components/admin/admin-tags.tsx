"use client";
import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import { errorMessage } from "@/lib/api";
import {
  createTag, createTagCategory, deleteTag, deleteTagCategory, fetchAdminTags, fetchTagCategories, updateTagCategory,
  updateTagCategoryInfo,
} from "@/lib/services";
import { toast } from "@/lib/toast";
import type { AdminTag, TagCategory, TagCategoryInfo } from "@/types/api";

const selectClass = "h-9 rounded-sm border border-border bg-card px-2 text-body-md text-ink";

/** Nhóm chủ đề cho khối Topics trên trang Problems. Chỉ ADMIN. */
export function AdminTagsView() {
  const [tags, setTags] = useState<AdminTag[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [category, setCategory] = useState<TagCategory>("Other");
  const [cats, setCats] = useState<TagCategoryInfo[]>([]);
  const [catName, setCatName] = useState("");
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
    catAction(() => createTagCategory({ name: n }), `Category "${n}" added.`);
  };
  const removeCat = async (c: TagCategoryInfo) => {
    const ok = await confirm({
      title: `Delete category "${c.name}"?`, action: "Delete", destructive: true,
      description: "Its tags move to Other.",
    });
    if (ok) catAction(() => deleteTagCategory(c.id), `Category "${c.name}" deleted.`);
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { reload(); }, []);

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

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <div className="flex items-end justify-between gap-4">
        <form onSubmit={add} className="flex items-center gap-2" aria-label="Add tag">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New tag name" maxLength={50} aria-label="Tag name" className="w-56" />
          <select value={category} onChange={(e) => setCategory(e.target.value as TagCategory)} aria-label="Category for new tag" className={selectClass}>
            {cats.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
          <Button type="submit" disabled={busy || !name.trim()}><Plus className="h-4 w-4" />Add tag</Button>
        </form>
      </div>

      <section className="rounded-sm border border-border bg-card p-4" aria-label="Categories">
        <div className="flex flex-wrap items-center gap-2">
          {cats.map((c) => (
            <div key={c.id} className="flex items-center gap-1.5 rounded-sm border border-border px-2 py-1">
              <input
                type="color" value={c.color} aria-label={`Color for ${c.name}`}
                onChange={(e) => setCats((l) => l.map((x) => (x.id === c.id ? { ...x, color: e.target.value } : x)))}
                onBlur={(e) => catAction(() => updateTagCategoryInfo(c.id, { color: e.target.value }))}
                className="h-5 w-5 cursor-pointer rounded-sm border-0 bg-transparent p-0"
              />
              {c.name === "Other" ? (
                <span className="px-1 text-body-md text-ink">{c.name}</span>
              ) : (
                <input
                  defaultValue={c.name} aria-label={`Rename ${c.name}`} maxLength={50}
                  onBlur={(e) => {
                    const n = e.target.value.trim();
                    if (n && n !== c.name) catAction(() => updateTagCategoryInfo(c.id, { name: n }));
                    else e.target.value = c.name;
                  }}
                  onKeyDown={(e) => { if (e.key === "Enter") e.currentTarget.blur(); }}
                  className="w-28 bg-transparent px-1 text-body-md text-ink outline-none focus:underline"
                />
              )}
              {c.name !== "Other" && (
                <button type="button" onClick={() => removeCat(c)} aria-label={`Delete category ${c.name}`} className="text-mute hover:text-destructive">
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          ))}
          <form onSubmit={addCat} className="flex items-center gap-1" aria-label="Add category">
            <Input value={catName} onChange={(e) => setCatName(e.target.value)} placeholder="New category" maxLength={50} aria-label="Category name" className="h-8 w-36" />
            <Button type="submit" variant="ghost" size="icon" disabled={!catName.trim()} aria-label="Add category"><Plus className="h-4 w-4" /></Button>
          </form>
        </div>
      </section>

      <div className="overflow-hidden rounded-sm border border-border bg-card" role="region" aria-label="Tags">
        {error && <p className="p-6 text-body-md text-destructive" role="alert">{error}</p>}
        {!error && tags === null && <p className="p-6 text-body-md text-mute">Loading tags…</p>}
        {tags?.length === 0 && <p className="p-6 text-body-md text-mute">No tags yet.</p>}
        {tags && tags.length > 0 && (
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
              {tags.map((t) => (
                <tr key={t.name} className="border-b border-border last:border-0">
                  <td className="px-4 py-2 font-medium text-ink">{t.name}</td>
                  <td className="px-4 py-2">
                    <select value={t.category} onChange={(e) => regroup(t, e.target.value as TagCategory)} aria-label={`Category for ${t.name}`} className={selectClass}>
                      {cats.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
                    </select>
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
    </div>
  );
}
