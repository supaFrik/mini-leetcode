"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Eraser, Play, Send, Terminal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { clearConsoleTranscript, executeConsoleCommand, fetchConsoleTranscript } from "@/lib/services";
import type { ConsoleMode, ConsoleTranscriptEntry, SubmissionFile, TestCase } from "@/types/api";

export interface ConsolePanelProps {
  problemId: number;
  /** Mã nguồn hiện tại trong editor — được gửi lên để biên dịch & chạy. */
  files: SubmissionFile[];
  /** Testcase công khai để gợi ý "chạy mẫu". */
  cases?: TestCase[];
  /** Id kỳ thi (nếu có) — server sẽ chặn console trong exam mode. */
  contestId?: number | null;
  disabled?: boolean;
}

interface LogLine {
  id: string;
  kind: "command" | "stdout" | "stderr" | "compile" | "meta";
  text: string;
}

/** Nhãn + placeholder của 2 chế độ chạy. */
const MODE_LABEL: Record<ConsoleMode, string> = {
  EVALUATE: "Evaluate expression",
  PROGRAM: "Run program (stdin)",
};

const MODE_HINT: Record<ConsoleMode, string> = {
  EVALUATE:
    "Evaluates a Java expression against your own classes, e.g. new Circle(\"c\", 2).getArea(). Your main method is not called.",
  PROGRAM:
    "Runs your public static void main(String[]) with the text below as stdin.",
};

function formatMemory(kb: number | null | undefined): string {
  if (kb == null) return "—";
  if (kb >= 1024) return `${(kb / 1024).toFixed(1)} MB`;
  return `${kb} KB`;
}

/**
 * Bảng điều khiển tương tác cho các bài OOP.
 *
 * Mỗi lệnh được gửi lên API, biên dịch & chạy trong Docker sandbox cách ly
 * (không bao giờ chạy trong trình duyệt). Transcript được lưu ở server nên sống
 * qua cả việc reload trang.
 *
 * Hai chế độ chạy:
 * - `EVALUATE` — đánh giá biểu thức Java trên các lớp của học viên (mặc định).
 * - `PROGRAM` — chạy `main` của học viên với nội dung ô nhập làm stdin.
 */
export function ConsolePanel({ problemId, files, cases, contestId, disabled }: ConsolePanelProps) {
  const [mode, setMode] = useState<ConsoleMode>("EVALUATE");
  const [input, setInput] = useState("");
  const [lines, setLines] = useState<LogLine[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);
  const logRef = useRef<HTMLDivElement | null>(null);

  const appendLines = useCallback((next: LogLine[]) => {
    setLines((prev) => [...prev, ...next]);
  }, []);

  // Nạp transcript cũ khi mở tab (sống ~1h trên server).
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const transcript = await fetchConsoleTranscript(problemId);
        if (cancelled) return;
        const restored: LogLine[] = [];
        for (const entry of transcript.entries ?? []) {
          restored.push({ id: crypto.randomUUID(), kind: "command", text: entry.input }, ...toLines(entry));
        }
        setLines(restored);
        setRemaining(transcript.limit != null ? Math.max(0, transcript.limit - transcript.used) : null);
      } catch {
        // Transcript là dữ liệu phụ — không chặn việc dùng console.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [problemId]);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [lines]);

  const run = async () => {
    const command = input.trim();
    if (!command || busy) return;
    setBusy(true);
    setError(null);
    appendLines([{ id: crypto.randomUUID(), kind: "command", text: command }]);
    setInput("");
    try {
      const result = await executeConsoleCommand(problemId, {
        files,
        input: command,
        expression: command,
        contestId: contestId ?? null,
        mode,
      });
      appendLines(toLines(result));
      setRemaining((prev) => (prev == null ? prev : Math.max(0, prev - 1)));
    } catch (e) {
      const message = errorMessage(e, "Console command failed.");
      setError(message);
      appendLines([{ id: crypto.randomUUID(), kind: "stderr", text: message }]);
    } finally {
      setBusy(false);
    }
  };

  const clear = async () => {
    setLines([]);
    setError(null);
    try {
      await clearConsoleTranscript(problemId);
    } catch {
      // Xoá transcript là hành động best-effort.
    }
  };

  const runSample = () => {
    const sample = cases?.find((c) => !c.isHidden)?.input ?? "";
    if (sample) setInput(sample);
  };

  return (
    <div className="flex h-full min-h-0 flex-col p-4">
      <div className="flex shrink-0 flex-wrap items-center gap-2 pb-3">
        <span className="flex items-center gap-1.5 text-body-sm font-medium text-ink">
          <Terminal className="h-3.5 w-3.5" aria-hidden="true" />
          OOP Console
        </span>
        <div
          role="radiogroup"
          aria-label="Console mode"
          className="flex items-center gap-1 rounded-sm border border-border bg-hairline-soft/60 p-0.5"
        >
          {(Object.keys(MODE_LABEL) as ConsoleMode[]).map((m) => (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={mode === m}
              title={MODE_HINT[m]}
              onClick={() => setMode(m)}
              className={cn(
                "rounded-xs px-2 py-1 text-body-xs font-medium transition-colors",
                mode === m ? "bg-card text-ink shadow-sm" : "text-mute hover:text-ink",
              )}
            >
              {MODE_LABEL[m]}
            </button>
          ))}
        </div>
        {remaining != null ? (
          <span className="font-mono text-body-xs text-mute" data-testid="console-remaining">
            {remaining} commands left
          </span>
        ) : null}
        <div className="ml-auto flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            className="h-7 gap-1"
            onClick={runSample}
            disabled={busy || !cases?.some((c) => !c.isHidden)}
            title="Fill the input with the first public example"
          >
            <Play className="h-3.5 w-3.5" aria-hidden="true" />
            Run example
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="h-7 gap-1"
            onClick={clear}
            disabled={busy || lines.length === 0}
          >
            <Eraser className="h-3.5 w-3.5" aria-hidden="true" />
            Clear
          </Button>
        </div>
      </div>

      <p className="shrink-0 pb-2 text-body-xs text-mute">{MODE_HINT[mode]}</p>

      <div
        ref={logRef}
        role="log"
        aria-live="polite"
        aria-label="Console output"
        className="thin-scroll min-h-0 flex-1 overflow-auto whitespace-pre-wrap rounded-sm border border-border bg-card p-3 font-mono text-code text-ink"
      >
        {lines.length === 0 ? (
          <p className="text-mute">
            No commands yet. Type an expression above and press Send.
          </p>
        ) : null}
        {lines.map((line) => (
          <div
            key={line.id}
            className={cn(
              line.kind === "command" && "font-medium text-link",
              line.kind === "stderr" && "text-destructive",
              line.kind === "compile" && "text-destructive",
              line.kind === "meta" && "text-mute",
              line.kind === "stdout" && "text-ink",
            )}
          >
            {line.kind === "command" ? `$ ${line.text}` : line.text}
          </div>
        ))}
        {busy ? <div className="text-mute">Running…</div> : null}
      </div>

      {error ? (
        <p role="alert" className="shrink-0 pt-2 text-body-sm text-destructive">
          {error}
        </p>
      ) : null}

      <div className="flex shrink-0 items-end gap-2 pt-3">
        <Textarea
          aria-label="Console command"
          placeholder={mode === "EVALUATE" ? 'new Circle("c", 2).getArea()' : "stdin for your main method…"}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
              e.preventDefault();
              void run();
            }
          }}
          rows={2}
          disabled={disabled || busy}
          className="font-mono text-code"
        />
        <Button
          size="sm"
          className="h-9 gap-1.5"
          onClick={run}
          disabled={disabled || busy || input.trim().length === 0}
        >
          <Send className="h-3.5 w-3.5" aria-hidden="true" />
          {busy ? "Running…" : "Send"}
        </Button>
      </div>
    </div>
  );
}

/** Chuyển kết quả một lệnh thành các dòng log hiển thị. */
function toLines(
  result:
    | {
        stdout?: string | null;
        stderr?: string | null;
        exitCode?: number | null;
        runtimeMs?: number | null;
        peakMemoryKb?: number | null;
        compileErrors?: string | null;
        truncated?: boolean;
        timedOut?: boolean;
      }
    | ConsoleTranscriptEntry,
): LogLine[] {
  const out: LogLine[] = [];
  const stdout = (result.stdout ?? "").trim();
  const stderr = (result.stderr ?? "").trim();
  const compileErrors = (result as { compileErrors?: string | null }).compileErrors ?? null;
  if (compileErrors) {
    out.push({ id: crypto.randomUUID(), kind: "compile", text: compileErrors });
    return out;
  }
  if (stdout) {
    out.push({ id: crypto.randomUUID(), kind: "stdout", text: stdout });
  }
  if (stderr) {
    out.push({ id: crypto.randomUUID(), kind: "stderr", text: stderr });
  }
  const bits: string[] = [];
  bits.push(`exit=${result.exitCode ?? "—"}`);
  bits.push(`runtime=${result.runtimeMs ?? "—"} ms`);
  bits.push(`peak memory=${formatMemory(result.peakMemoryKb)}`);
  const flags = result as { truncated?: boolean; timedOut?: boolean };
  if (flags.truncated) bits.push("truncated");
  if (flags.timedOut) bits.push("timed out");
  out.push({ id: crypto.randomUUID(), kind: "meta", text: bits.join(" | ") });
  return out;
}
