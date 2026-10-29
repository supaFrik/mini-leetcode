"use client";
import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { useTheme } from "next-themes";
import type { BeforeMount, OnMount } from "@monaco-editor/react";
import type { Language } from "@/types/api";
import { FILE_NAME, MONACO_LANG } from "@/lib/templates";

// Monaco dùng `window` nên chỉ được nạp ở trình duyệt (ssr: false)
const Editor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => <div className="p-4 text-sm text-muted-foreground">Đang tải trình soạn thảo…</div>,
});

interface Props {
  language: Language;
  value: string;
  onChange?: (v: string) => void;
  readOnly?: boolean;
  examMode?: boolean;
  onSave?: () => void;
  onRun?: () => void;
  onSubmit?: () => void;
  onPasteAttempt?: () => void; // chỉ dùng ở Exam
}

const beforeMount: BeforeMount = (monaco) => {
  monaco.editor.defineTheme("mini-dark", { base: "vs-dark", inherit: true, rules: [], colors: { "editor.background": "#14171b" } });
};

// Monaco mặc định tải từ CDN (cdn.jsdelivr.net). Nếu mạng chặn CDN, editor sẽ kẹt ở "Loading..." mãi.
// Khi đó dùng khung <textarea> dự phòng để thí sinh vẫn làm bài được.
function FallbackEditor({ value, onChange, readOnly, examMode, onSave, onRun, onSubmit, onPasteAttempt }: Props) {
  return (
    <div className="flex h-full flex-col">
      <p role="status" className="shrink-0 bg-amber-500/15 px-3 py-1 text-xs text-amber-600 dark:text-amber-400">
        Không tải được trình soạn thảo nâng cao (có thể mạng chặn CDN). Đang dùng khung soạn thảo đơn giản.
      </p>
      <textarea
        aria-label="Mã nguồn" spellCheck={false} value={value} readOnly={readOnly}
        onChange={(e) => onChange?.(e.target.value)}
        onPaste={(e) => { if (examMode) { e.preventDefault(); onPasteAttempt?.(); } }}
        onDrop={(e) => { if (examMode) { e.preventDefault(); onPasteAttempt?.(); } }}
        onContextMenu={(e) => { if (examMode) e.preventDefault(); }}
        onKeyDown={(e) => {
          const mod = e.ctrlKey || e.metaKey;
          if (mod && e.key.toLowerCase() === "s") { e.preventDefault(); onSave?.(); }
          else if (mod && e.key === "Enter") { e.preventDefault(); if (e.shiftKey) onSubmit?.(); else onRun?.(); }
          else if (examMode && ((mod && e.key.toLowerCase() === "v") || (e.shiftKey && e.key === "Insert"))) { e.preventDefault(); onPasteAttempt?.(); }
          else if (e.key === "Tab" && !readOnly) {
            e.preventDefault();
            const t = e.currentTarget, s = t.selectionStart;
            onChange?.(value.slice(0, s) + "    " + value.slice(t.selectionEnd));
            requestAnimationFrame(() => { t.selectionStart = t.selectionEnd = s + 4; });
          }
        }}
        className="min-h-0 flex-1 resize-none bg-[#14171b] p-3 font-mono text-[13px] leading-5 text-white outline-none"
      />
    </div>
  );
}

export function CodeEditor(props: Props) {
  const { resolvedTheme } = useTheme();
  const [loadState, setLoadState] = useState<"loading" | "ready" | "failed">("loading");
  // onMount chỉ chạy 1 lần → các hàm bên trong bị "đóng băng" với props cũ (stale closure).
  // Dùng ref luôn trỏ tới props mới nhất để phím tắt luôn gọi đúng hàm hiện tại.
  const latest = useRef(props);
  latest.current = props;

  useEffect(() => {
    let off = false;
    import("@monaco-editor/react")
      .then((m) => m.loader.init())
      .then(() => { if (!off) setLoadState("ready"); })
      .catch(() => { if (!off) setLoadState("failed"); });
    return () => { off = true; };
  }, []);

  const onMount: OnMount = (editor, monaco) => {
    // CtrlCmd = Ctrl (Windows/Linux) hoặc Cmd (Mac)
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => latest.current.onSave?.());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => latest.current.onRun?.());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => latest.current.onSubmit?.());

    if (props.examMode) {
      // Chặn paste. Đây chỉ là UX, server mới là bên phạt (và phạt cả paste lọt qua client).
      const block = () => latest.current.onPasteAttempt?.();
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyV, block);
      editor.addCommand(monaco.KeyMod.Shift | monaco.KeyCode.Insert, block);
      const dom = editor.getDomNode();
      dom?.addEventListener("paste", (e) => { e.preventDefault(); e.stopPropagation(); block(); }, true); // capture: chặn trước Monaco
      dom?.addEventListener("drop", (e) => { e.preventDefault(); block(); }, true); // kéo-thả chữ vào editor
    }
  };

  if (loadState === "failed") return <FallbackEditor {...props} />;
  return (
    <Editor
      height="100%"
      path={FILE_NAME[props.language]} // mỗi ngôn ngữ một "model" riêng, giữ undo history
      theme={resolvedTheme === "light" ? "light" : "mini-dark"}
      language={MONACO_LANG[props.language]}
      value={props.value}
      onChange={(v) => props.onChange?.(v ?? "")}
      beforeMount={beforeMount}
      onMount={onMount}
      options={{
        readOnly: props.readOnly, fontSize: 13, minimap: { enabled: false }, scrollBeyondLastLine: false,
        automaticLayout: true, tabSize: 4, padding: { top: 8 },
        contextmenu: !props.examMode, // tắt chuột phải khi thi
      }}
    />
  );
}
