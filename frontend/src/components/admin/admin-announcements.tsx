"use client";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import { useCanEdit } from "@/hooks/use-can-edit";
import { useLoad } from "@/hooks/use-load";
import { errorMessage } from "@/lib/api";
import { createAnnouncement, deleteAnnouncement, fetchAnnouncements } from "@/lib/services";
import { toast } from "@/lib/toast";
import type { Announcement } from "@/types/api";
import { formatWhen } from "./admin-overview";

/** /admin/announcements: đăng và xoá thông báo; học viên xem ở chuông thông báo trên header. */
export function AdminAnnouncementsView() {
  const [version, setVersion] = useState(0);
  const load = useLoad(fetchAnnouncements, [version]);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [saving, setSaving] = useState(false);
  const confirm = useConfirm();
  const canEdit = useCanEdit();

  async function post(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !body.trim()) return;
    setSaving(true);
    try {
      await createAnnouncement({ title: title.trim(), body: body.trim() });
      setTitle("");
      setBody("");
      toast("Announcement posted.", "success");
      setVersion((v) => v + 1);
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setSaving(false);
    }
  }

  async function remove(a: Announcement) {
    if (!(await confirm({ title: `Delete "${a.title}"?`, description: "Learners will no longer see it.", action: "Delete", destructive: true }))) return;
    try {
      await deleteAnnouncement(a.id);
      toast("Announcement deleted.", "success");
      setVersion((v) => v + 1);
    } catch (err) {
      toast(errorMessage(err), "error");
    }
  }

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">

      <form onSubmit={post} className="space-y-3 rounded-sm border border-border bg-card p-4" aria-label="New announcement">
        <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" aria-label="Title" maxLength={200} className="bg-card" />
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="Message"
          aria-label="Message"
          rows={3}
          className="w-full rounded-sm border border-border bg-card px-3 py-2 text-body-md text-ink"
        />
        <div className="flex justify-end">
          <Button type="submit" disabled={saving || !title.trim() || !body.trim()}>{saving ? "Posting…" : "Post"}</Button>
        </div>
      </form>

      <div className="overflow-hidden rounded-sm border border-border bg-card" role="region" aria-label="Announcements">
        {load.state === "loading" && <p className="p-6 text-body-md text-mute">Loading announcements…</p>}
        {load.state === "error" && <p className="p-6 text-body-md text-destructive" role="alert">{load.message}</p>}
        {load.state === "ok" && load.data.length === 0 && <p className="p-6 text-body-md text-mute">No announcements yet.</p>}
        {load.state === "ok" && load.data.map((a) => (
          <article key={a.id} className="flex items-start gap-4 border-b border-border p-4 last:border-0">
            <div className="min-w-0 flex-1">
              <h2 className="font-medium text-ink">{a.title}</h2>
              <p className="whitespace-pre-line text-body-md text-body">{a.body}</p>
              <p className="mt-1 text-body-sm text-mute">{a.createdByUsername} · {formatWhen(a.createdAt)}</p>
            </div>
            {canEdit(a.createdById) ? (
              <Button variant="ghost" size="icon-sm" aria-label={`Delete ${a.title}`} onClick={() => void remove(a)}>
                <Trash2 className="h-4 w-4" />
              </Button>
            ) : (
              <span className="text-body-sm text-mute">Read-only</span>
            )}
          </article>
        ))}
      </div>
    </div>
  );
}
