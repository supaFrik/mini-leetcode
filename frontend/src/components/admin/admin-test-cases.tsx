"use client";
import { useCallback, useEffect, useState } from "react";
import { Eye, EyeOff, Loader2, Plus, Save, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { addTestCase, deleteTestCase, fetchTestCases, updateTestCase } from "@/lib/services";
import type { TestCase } from "@/types/api";

/** Server chỉ trả 8 KB đầu của file; bản xem trước dài hơn là đã bị cắt, không được ghi đè. */
const PREVIEW_LIMIT = 8192;

function CaseRow({ problemId, tc, onChanged }: { problemId: number; tc: TestCase; onChanged: () => void }) {
  const [input, setInput] = useState(tc.input);
  const [output, setOutput] = useState(tc.expectedOutput);
  const [busy, setBusy] = useState(false);
  const truncated = tc.input.length >= PREVIEW_LIMIT || tc.expectedOutput.length >= PREVIEW_LIMIT;
  const dirty = input !== tc.input || output !== tc.expectedOutput;

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      toast(ok, "success");
      onChanged();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-2 border-t border-border p-3 first:border-t-0">
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-body-sm text-mute">
          #{tc.orderIndex} · {tc.isHidden ? "hidden" : "public"}
          {truncated && " · large file (read-only preview)"}
        </span>
        <span className="flex gap-1">
          <Button size="sm" variant="outline" className="h-7 px-2" disabled={busy}
            onClick={() => run(() => updateTestCase(problemId, tc.id, { isHidden: !tc.isHidden }), "Visibility updated.")}
            title={tc.isHidden ? "Make public" : "Hide"}>
            {tc.isHidden ? <Eye className="h-3.5 w-3.5" /> : <EyeOff className="h-3.5 w-3.5" />}
          </Button>
          <Button size="sm" variant="outline" className="h-7 px-2" disabled={busy || !dirty || truncated}
            onClick={() => run(() => updateTestCase(problemId, tc.id, {
              input: input !== tc.input ? input : undefined,
              expectedOutput: output !== tc.expectedOutput ? output : undefined,
            }), "Test case saved.")}
            title="Save changes">
            {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
          </Button>
          <Button size="sm" variant="ghost" className="h-7 px-2 text-mute hover:text-destructive" disabled={busy}
            onClick={() => window.confirm("Delete this test case?") && run(() => deleteTestCase(problemId, tc.id), "Test case deleted.")}
            aria-label="Delete test case">
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </span>
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        <Textarea aria-label="Input" value={input} onChange={(e) => setInput(e.target.value)} readOnly={truncated}
          rows={3} className="rounded-sm bg-background font-mono text-xs" />
        <Textarea aria-label="Expected output" value={output} onChange={(e) => setOutput(e.target.value)} readOnly={truncated}
          rows={3} className="rounded-sm bg-background font-mono text-xs" />
      </div>
    </div>
  );
}

/** Quản lý testcase ngay trong trình sửa bài — trước đây chỉ làm được qua script. */
export function TestCaseManager({ problemId }: { problemId: number }) {
  const [cases, setCases] = useState<TestCase[] | null>(null);
  const [input, setInput] = useState("");
  const [output, setOutput] = useState("");
  const [hidden, setHidden] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setCases(await fetchTestCases(problemId));
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }, [problemId]);

  useEffect(() => {
    load();
  }, [load]);

  const add = async () => {
    setBusy(true);
    try {
      await addTestCase(problemId, { input, expectedOutput: output, isHidden: hidden });
      setInput("");
      setOutput("");
      toast("Test case added.", "success");
      await load();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3">
      <p className="eyebrow block text-mute">Test cases ({cases?.length ?? "…"})</p>
      <div className="overflow-hidden rounded-sm border border-border bg-card">
        {cases?.map((tc) => (
          // key gồm nội dung để ô nhập reset sau khi tải lại
          <CaseRow key={`${tc.id}:${tc.isHidden}:${tc.input.length}:${tc.expectedOutput.length}`} problemId={problemId} tc={tc} onChanged={load} />
        ))}
        {cases?.length === 0 && <p className="p-3 text-body-sm text-mute">No test cases yet.</p>}
      </div>
      <div className="space-y-2 rounded-sm border border-dashed border-border p-3">
        <div className="grid gap-2 sm:grid-cols-2">
          <Textarea aria-label="New input" placeholder="Input" value={input} onChange={(e) => setInput(e.target.value)}
            rows={3} className="rounded-sm bg-background font-mono text-xs" />
          <Textarea aria-label="New expected output" placeholder="Expected output" value={output} onChange={(e) => setOutput(e.target.value)}
            rows={3} className="rounded-sm bg-background font-mono text-xs" />
        </div>
        <div className="flex items-center justify-between">
          <label className="flex items-center gap-2 text-body-sm text-body">
            <input type="checkbox" checked={hidden} onChange={(e) => setHidden(e.target.checked)} />
            Hidden from contestants
          </label>
          <Button size="sm" onClick={add} disabled={busy || !input || !output}>
            {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
            Add test case
          </Button>
        </div>
      </div>
    </div>
  );
}
