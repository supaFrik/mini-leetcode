"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Markdown } from "@/components/ui/markdown";
import { useConfirm } from "@/components/ui/confirm";
import { POLICY_PROSE } from "@/components/policies/policy-view";
import { errorMessage } from "@/lib/api";
import { fetchSitePages, updateSitePage } from "@/lib/services";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import type { SitePage } from "@/types/api";

/** Sửa trang chính sách bằng Markdown. Chỉ ADMIN; không lưu lịch sử phiên bản. */
export function AdminPoliciesView() {
  const [pages, setPages] = useState<SitePage[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [slug, setSlug] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [saving, setSaving] = useState(false);
  const confirm = useConfirm();

  useEffect(() => {
    fetchSitePages()
      .then((data) => { setPages(data); if (data[0]) select(data[0]); })
      .catch((e) => setError(errorMessage(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const select = (p: SitePage) => { setSlug(p.slug); setTitle(p.title); setContent(p.contentMd); };
  const current = pages?.find((p) => p.slug === slug);
  const dirty = !!current && (current.title !== title || current.contentMd !== content);

  const pick = async (p: SitePage) => {
    if (dirty && !(await confirm({ title: "Discard unsaved changes?", action: "Discard", destructive: true }))) return;
    select(p);
  };

  const save = async () => {
    if (!slug) return;
    setSaving(true);
    try {
      const saved = await updateSitePage(slug, title, content);
      setPages((list) => list?.map((p) => (p.slug === slug ? saved : p)) ?? null);
      toast("Page saved.", "success");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      {error && <p className="text-body-md text-destructive" role="alert">{error}</p>}
      {!error && pages === null && <p className="text-body-md text-mute">Loading pages…</p>}
      {pages && (
        <div className="grid grid-cols-[200px_1fr] gap-6">
          <nav aria-label="Policy pages" className="space-y-1">
            {pages.map((p) => (
              <button
                key={p.slug}
                type="button"
                onClick={() => pick(p)}
                aria-current={p.slug === slug ? "page" : undefined}
                className={cn(
                  "block w-full rounded-sm px-3 py-2 text-left text-body-md",
                  p.slug === slug ? "bg-hairline-soft font-medium text-ink" : "text-mute hover:text-ink"
                )}
              >
                {p.title}
                <span className="block font-mono text-body-sm text-mute">/policies/{p.slug}</span>
              </button>
            ))}
          </nav>
          {current && (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} aria-label="Page title" className="max-w-md" />
                <Link href={`/policies/${current.slug}`} target="_blank" className="text-body-sm text-link hover:underline">View page</Link>
                <Button className="ml-auto" onClick={save} disabled={saving || !dirty || !title.trim()}>
                  {saving ? "Saving…" : "Save"}
                </Button>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <Textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  aria-label="Page content (Markdown)"
                  spellCheck={false}
                  className="min-h-[480px] font-mono text-body-sm"
                />
                <div className="min-h-[480px] overflow-auto rounded-sm border border-border bg-card p-4" aria-label="Preview" role="region">
                  <div className={POLICY_PROSE}><Markdown>{content}</Markdown></div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
