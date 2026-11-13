"use client";
import { useEffect, useState } from "react";
import { AlertTriangle, Check, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api";
import { fetchPublishCheck } from "@/lib/services";
import type { PublishCheck, PublishIssue } from "@/types/api";

/* Checklist trước khi publish (specs/course-builder.spec.md, B4): ✗ chặn, ! chỉ cảnh báo. */

export function PublishDialog({ slug, title, itemCount, stageCount, beforeCheck, onChecked, onGoTo, onPublish, onClose }: {
  slug: string; title: string; itemCount: number; stageCount: number;
  /** Lưu các thay đổi đang chờ trước khi kiểm tra (sửa U2); trả slug hiện tại. */
  beforeCheck: () => Promise<string>;
  /** Kết quả kiểm tra, để outline đánh dấu ⚠. */
  onChecked: (c: PublishCheck) => void;
  onGoTo: (issue: PublishIssue) => void;
  onPublish: () => Promise<void>;
  onClose: () => void;
}) {
  const [check, setCheck] = useState<PublishCheck | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let off = false;
    beforeCheck().then((current) => fetchPublishCheck(current || slug))
      .then((c) => { if (off) return; setCheck(c); onChecked(c); })
      .catch((e) => !off && setError(errorMessage(e)));
    return () => { off = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug]);

  const publish = async () => {
    setBusy(true);
    setError(null);
    try {
      await onPublish();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };

  const Issue = ({ issue, error: isError }: { issue: PublishIssue; error: boolean }) => (
    <li className="flex items-start gap-2 py-1">
      {isError ? <X className="mt-0.5 h-4 w-4 shrink-0 text-destructive" aria-label="Error" />
        : <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" aria-label="Warning" />}
      <span className="min-w-0 flex-1">{issue.message}</span>
      {issue.node !== "plan" && <Button size="sm" variant="ghost" onClick={() => onGoTo(issue)}>Go to</Button>}
    </li>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/20 p-4" role="dialog" aria-modal="true" aria-label={`Publish ${title}`}
      onKeyDown={(e) => e.key === "Escape" && onClose()}>
      <div className="w-full max-w-lg space-y-4 rounded-sm border border-border bg-background p-5 shadow-lg">
        <h2 className="text-heading-md text-ink">Publish “{title}”</h2>
        {!check && !error && <p className="flex items-center gap-2 text-body-sm text-mute"><Loader2 className="h-4 w-4 animate-spin" />Checking…</p>}
        {check && (
          <ul className="text-body-sm" aria-label="Publish checklist">
            <li className="flex items-center gap-2 py-1"><Check className="h-4 w-4 text-solved" aria-hidden />
              {itemCount} item{itemCount === 1 ? "" : "s"}, {stageCount} stage{stageCount === 1 ? "" : "s"}</li>
            {check.errors.map((i, k) => <Issue key={`e${k}`} issue={i} error />)}
            {check.warnings.map((i, k) => <Issue key={`w${k}`} issue={i} error={false} />)}
            <li className="pt-2 text-mute">Will publish: plan
              {check.problemsToPublish.length > 0 && `, ${check.problemsToPublish.length} draft problem${check.problemsToPublish.length === 1 ? "" : "s"}`}
              {check.topicsToPublish.length > 0 && `, ${check.topicsToPublish.length} draft topic${check.topicsToPublish.length === 1 ? "" : "s"}`}
            </li>
          </ul>
        )}
        {error && <p role="alert" className="text-body-sm text-destructive">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={publish} disabled={!check?.ok || busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />}Publish</Button>
        </div>
      </div>
    </div>
  );
}
