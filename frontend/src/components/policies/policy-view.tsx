"use client";
import { Markdown } from "@/components/ui/markdown";
import { useLoad } from "@/hooks/use-load";
import { parseApiDate } from "@/lib/datetime";
import { fetchSitePage } from "@/lib/services";

export const POLICY_PROSE =
  "prose prose-sm max-w-none prose-headings:tracking-tight prose-headings:text-ink prose-p:text-body prose-li:text-body prose-strong:text-ink";

/** Trang chính sách (Terms of use, Contest rules), nội dung Markdown do ADMIN sửa. */
export function PolicyView({ slug }: { slug: string }) {
  const load = useLoad(() => fetchSitePage(slug), [slug]);

  return (
    <article className="mx-auto w-full max-w-3xl px-8 py-8">
      {load.state === "loading" && <p className="text-body-md text-mute">Loading…</p>}
      {load.state === "error" && <p className="text-body-md text-destructive" role="alert">{load.message}</p>}
      {load.state === "ok" && (
        <>
          <header className="mb-6 space-y-1 border-b border-border pb-4">
            <h1 className="text-heading-lg text-ink">{load.data.title}</h1>
            <p className="text-body-sm text-mute">
              Last updated {new Date(parseApiDate(load.data.updatedAt)).toLocaleDateString("en-US", { dateStyle: "long" })}
            </p>
          </header>
          <div className={POLICY_PROSE}>
            <Markdown>{load.data.contentMd}</Markdown>
          </div>
        </>
      )}
    </article>
  );
}
