"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { freshToken } from "@/lib/api";
import type { Language, SubmissionFile } from "@/types/api";

export interface ConsolePanelProps {
  problemId: number;
  language: Language;
  /** Mã nguồn hiện tại trong editor, gửi lên mỗi lần chạy. */
  files: SubmissionFile[];
  /** Tăng mỗi lần bấm nút Run của workspace ⇒ chạy (lại) chương trình. */
  runSignal: number;
}

type ChunkKind = "out" | "err" | "in" | "meta" | "error";
interface Chunk {
  kind: ChunkKind;
  text: string;
}
type ServerFrame =
  | { type: "stdout" | "stderr"; data: string }
  | { type: "exit"; code: number | null; elapsedMs: number; reason: string | null }
  | { type: "error"; message: string };

const EXIT_REASON: Record<string, string> = {
  "compile-error": "Compilation failed",
  timeout: "Process killed: time limit exceeded",
  idle: "Process killed: no input or output for too long",
  "output-limit": "Process killed: output limit exceeded",
  killed: "Process stopped",
};

/** Mặc định đi qua rewrite `/api` của Next; NEXT_PUBLIC_CONSOLE_WS_URL để trỏ thẳng nếu cần. */
function consoleUrl() {
  if (process.env.NEXT_PUBLIC_CONSOLE_WS_URL) return process.env.NEXT_PUBLIC_CONSOLE_WS_URL;
  return `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/api/v1/console/ws`;
}

/**
 * Terminal live như tab Run của IDE. Nút Run của workspace khởi động chương trình; gõ một dòng +
 * Enter để gửi vào stdin, Ctrl+D đóng stdin, Ctrl+C dừng chương trình.
 */
export function ConsolePanel({ problemId, language, files, runSignal }: ConsolePanelProps) {
  const [chunks, setChunks] = useState<Chunk[]>([]);
  const [running, setRunning] = useState(false);
  const [line, setLine] = useState("");
  const socket = useRef<WebSocket | null>(null);
  const logRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  // Mã nguồn mới nhất tại thời điểm bấm Run (effect chỉ phụ thuộc runSignal).
  const latest = useRef({ problemId, language, files });
  latest.current = { problemId, language, files };

  /** Gộp chunk liền kề cùng loại để không sinh hàng nghìn node khi chương trình in nhiều. */
  const append = useCallback((kind: ChunkKind, text: string) => {
    setChunks((prev) => {
      const last = prev[prev.length - 1];
      if (last && last.kind === kind && (kind === "out" || kind === "err")) {
        return [...prev.slice(0, -1), { kind, text: last.text + text }];
      }
      return [...prev, { kind, text }];
    });
  }, []);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [chunks]);

  // Rời trang / đổi bài ⇒ đóng kết nối ⇒ server kill container.
  useEffect(() => () => socket.current?.close(), [problemId]);

  const run = useCallback(async () => {
    socket.current?.close(); // chạy lại ⇒ server kill lần chạy cũ
    setChunks([]);
    setLine("");
    setRunning(true);
    const { problemId: pid, language: lang, files: sources } = latest.current;
    const token = await freshToken();
    const ws = new WebSocket(consoleUrl());
    socket.current = ws;
    let finished = false;

    ws.onopen = () => ws.send(JSON.stringify({ type: "start", token, problemId: pid, language: lang, files: sources }));
    ws.onmessage = (event) => {
      const frame = JSON.parse(event.data as string) as ServerFrame;
      if (frame.type === "stdout") append("out", frame.data);
      else if (frame.type === "stderr") append("err", frame.data);
      else if (frame.type === "error") {
        finished = true;
        append("error", frame.message);
      } else if (frame.type === "exit") {
        finished = true;
        const seconds = (frame.elapsedMs / 1000).toFixed(1);
        append(
          "meta",
          frame.reason && EXIT_REASON[frame.reason]
            ? `${EXIT_REASON[frame.reason]} (${seconds}s)`
            : `Process finished with exit code ${frame.code} (${seconds}s)`,
        );
      }
    };
    ws.onclose = () => {
      if (socket.current !== ws) return; // đã bị thay bởi lần chạy mới
      socket.current = null;
      setRunning(false);
      if (!finished) append("error", "Connection to the console service was lost.");
    };
    ws.onerror = () => {
      if (!finished) {
        finished = true;
        append("error", "Cannot connect to the console service.");
      }
    };
  }, [append]);

  // Ô nhập chỉ có khi chương trình đang chạy ⇒ focus sau khi nó xuất hiện.
  useEffect(() => {
    if (running) inputRef.current?.focus();
  }, [running]);

  useEffect(() => {
    if (runSignal > 0) void run();
  }, [runSignal, run]);

  const send = (frame: object) => {
    if (socket.current?.readyState === WebSocket.OPEN) socket.current.send(JSON.stringify(frame));
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      send({ type: "stdin", data: `${line}\n` });
      append("in", `${line}\n`);
      setLine("");
    } else if (e.ctrlKey && e.key === "d") {
      // Ctrl+D: phần đang gõ được gửi không kèm xuống dòng, rồi đóng stdin.
      e.preventDefault();
      if (line) {
        send({ type: "stdin", data: line });
        append("in", line);
        setLine("");
      }
      send({ type: "eof" });
      append("meta", "^D");
    } else if (e.ctrlKey && e.key === "c" && e.currentTarget.selectionStart === e.currentTarget.selectionEnd) {
      // Ctrl+C không bôi đen ⇒ dừng chương trình (bôi đen thì vẫn là copy).
      e.preventDefault();
      send({ type: "kill" });
    }
  };

  return (
    <div
      ref={logRef}
      role="log"
      aria-live="polite"
      aria-label="Console output"
      onClick={() => inputRef.current?.focus()}
      className="thin-scroll h-full min-h-0 overflow-auto whitespace-pre-wrap break-words bg-card p-3 font-mono text-code text-ink"
    >
      {chunks.map((chunk, i) =>
        chunk.kind === "meta" || chunk.kind === "error" ? (
          <div key={i} className={cn("my-1", chunk.kind === "meta" ? "text-mute" : "text-destructive")}>
            {chunk.text}
          </div>
        ) : (
          <span
            key={i}
            className={cn(chunk.kind === "err" && "text-destructive", chunk.kind === "in" && "text-link")}
          >
            {chunk.text}
          </span>
        ),
      )}
      {running ? (
        <input
          ref={inputRef}
          aria-label="Program input"
          value={line}
          onChange={(e) => setLine(e.target.value)}
          onKeyDown={onKeyDown}
          maxLength={64 * 1024}
          className="inline w-auto min-w-[2ch] max-w-full bg-transparent font-mono text-code text-link caret-ink outline-none"
          style={{ width: `${Math.max(line.length + 1, 2)}ch` }}
        />
      ) : null}
    </div>
  );
}
