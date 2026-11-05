"use client";

import { useState } from "react";
import { Play, Terminal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { errorMessage } from "@/lib/api";
import { executeConsoleCommand } from "@/lib/services";
import type { ConsoleExecuteResponse, SubmissionFile, TestCase } from "@/types/api";

export interface ConsolePanelProps {
  problemId: number;
  /** Mã nguồn hiện tại trong editor — được gửi lên để biên dịch & chạy. */
  files: SubmissionFile[];
  /** Testcase công khai để điền stdin mẫu. */
  cases?: TestCase[];
}

function formatMemory(kb: number | null): string {
  if (kb == null) return "—";
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}

/**
 * Console kiểu terminal IDE cho bài OOP: chạy `main` của học viên với nội dung
 * ô nhập làm stdin, hiển thị stdout/stderr của lần chạy gần nhất.
 * Code chạy trong Docker sandbox phía server, không bao giờ trong trình duyệt.
 */
export function ConsolePanel({ problemId, files, cases }: ConsolePanelProps) {
  const [stdin, setStdin] = useState("");
  const [result, setResult] = useState<ConsoleExecuteResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sample = cases?.find((c) => !c.isHidden)?.input;

  const run = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      setResult(await executeConsoleCommand(problemId, { files, input: stdin }));
    } catch (e) {
      setResult(null);
      setError(errorMessage(e, "Run failed."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-3 p-4">
      <div className="flex shrink-0 items-center gap-2">
        <span className="flex items-center gap-1.5 text-body-sm font-medium text-ink">
          <Terminal className="h-3.5 w-3.5" aria-hidden="true" />
          Console
        </span>
        <span className="text-body-xs text-mute">Runs your main method with the input below as stdin.</span>
        <div className="ml-auto flex items-center gap-2">
          {sample ? (
            <Button variant="outline" size="sm" className="h-7" onClick={() => setStdin(sample)} disabled={busy}>
              Run example
            </Button>
          ) : null}
          <Button size="sm" className="h-7 gap-1" onClick={run} disabled={busy}>
            <Play className="h-3.5 w-3.5" aria-hidden="true" />
            {busy ? "Running…" : "Run main"}
          </Button>
        </div>
      </div>

      <Textarea
        aria-label="Standard input"
        placeholder="stdin (optional)"
        value={stdin}
        onChange={(e) => setStdin(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
            e.preventDefault();
            void run();
          }
        }}
        rows={3}
        className="shrink-0 font-mono text-code"
      />

      <pre
        role="log"
        aria-live="polite"
        aria-label="Console output"
        className="thin-scroll min-h-0 flex-1 overflow-auto whitespace-pre-wrap rounded-sm border border-border bg-card p-3 font-mono text-code text-ink"
      >
        {error ? <span role="alert" className="text-destructive">{error}</span> : null}
        {!error && !result ? <span className="text-mute">Press Run main (Ctrl+Enter) to compile and run your code.</span> : null}
        {result?.compileErrors ? <span className="text-destructive">{result.compileErrors}</span> : null}
        {result && !result.compileErrors ? (
          <>
            {result.stdout}
            {result.stderr ? <span className="text-destructive">{`\n${result.stderr}`}</span> : null}
            <span className="text-mute">
              {`\n\nexit=${result.exitCode ?? "—"} | runtime=${result.runtimeMs ?? "—"} ms | peak memory=${formatMemory(result.peakMemoryKb)}`}
              {result.truncated ? " | truncated" : ""}
              {result.timedOut ? " | timed out" : ""}
            </span>
          </>
        ) : null}
      </pre>
    </div>
  );
}
