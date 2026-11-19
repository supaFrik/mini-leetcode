"use client";
import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import { errorMessage } from "@/lib/api";
import { createTag, deleteTag, fetchAdminTags, updateTagCategory } from "@/lib/services";
import { toast } from "@/lib/toast";
import { TAG_CATEGORIES, type AdminTag, type TagCategory } from "@/types/api";
import { AdminNav } from "./admin-nav";

const selectClass = "h-9 rounded-sm border border-border bg-card px-2 text-body-md text-ink";

/** Nhóm chủ đề cho khối Topics trên trang Problems. Chỉ ADMIN. */
export function AdminTagsView() {
  const [tags, setTags] = useState<AdminTag[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [category, setCategory] = useState<TagCategory>("DSA");
  const [busy, setBusy] = useState(false);
  const confirm = useConfirm();

  const reload = () =>
    fetchAdminTags()
      .then((data) => { setTags(data); setError(null); })
      .catch((e) => setError(errorMessage(e)));
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

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <AdminNav />
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-heading-md text-ink">Tags</h1>
          <p className="text-body-md text-mute">The category decides where a tag appears in the Topics panel on Problems.</p>
        </div>
        <form onSubmit={add} className="flex items-center gap-2" aria-label="Add tag">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New tag name" maxLength={50} aria-label="Tag name" className="w-56" />
          <select value={category} onChange={(e) => setCategory(e.target.value as TagCategory)} aria-label="Category for new tag" className={selectClass}>
            {TAG_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
          <Button type="submit" disabled={busy || !name.trim()}><Plus className="h-4 w-4" />Add tag</Button>
        </form>
      </div>

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
                      {TAG_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
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
