"use client";
import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import type { BeforeMount, OnMount } from "@monaco-editor/react";
import type { Language } from "@/types/api";
import { FILE_NAME, MONACO_LANG } from "@/lib/templates";

// Monaco uses `window`, only loaded on client (ssr: false)
const Editor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => <div className="p-4 text-body-md text-mute">Loading code editor…</div>,
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
  onPasteAttempt?: () => void;
}

/**
 * The editor is a light surface like the rest of the system: white canvas,
 * ink syntax, hairline gutter. It is themed off the design tokens rather than
 * Monaco's stock "light" preset so it matches the canvas exactly.
 */
const beforeMount: BeforeMount = (monaco) => {
  monaco.editor.defineTheme("mini-light", {
    base: "vs",
    inherit: true,
    rules: [
      { token: "comment", foreground: "8f8f8f", fontStyle: "italic" },
      { token: "keyword", foreground: "171717" },
      { token: "string", foreground: "0761d1" },
      { token: "number", foreground: "7928ca" },
      { token: "type", foreground: "0070f3" },
      { token: "delimiter", foreground: "4d4d4d" },
    ],
    colors: {
      "editor.background": "#ffffff",
      "editor.foreground": "#171717",
      "editorLineNumber.foreground": "#a1a1a1",
      "editorLineNumber.activeForeground": "#4d4d4d",
      "editor.lineHighlightBackground": "#fafafa",
      "editor.selectionBackground": "#d3e5ff",
      "editorCursor.foreground": "#171717",
      "editorIndentGuide.background1": "#ebebeb",
      "editorGutter.background": "#ffffff",
    },
  });
};

function FallbackEditor({ value, onChange, readOnly, examMode, onSave, onRun, onSubmit, onPasteAttempt }: Props) {
  return (
    <div className="flex h-full flex-col">
      <p role="status" className="shrink-0 border-b border-border bg-warning/10 px-3 py-1 text-body-sm text-[#8a5a00]">
        Could not load advanced code editor (CDN might be blocked). Using fallback editor.
      </p>
      <textarea
        aria-label="Source code"
        spellCheck={false}
        value={value}
        readOnly={readOnly}
        onChange={(e) => onChange?.(e.target.value)}
        onPaste={(e) => {
          if (examMode) {
            e.preventDefault();
            onPasteAttempt?.();
          }
        }}
        onDrop={(e) => {
          if (examMode) {
            e.preventDefault();
            onPasteAttempt?.();
          }
        }}
        onContextMenu={(e) => {
          if (examMode) e.preventDefault();
        }}
        onKeyDown={(e) => {
          const mod = e.ctrlKey || e.metaKey;
          if (mod && e.key.toLowerCase() === "s") {
            e.preventDefault();
            onSave?.();
          } else if (mod && e.key === "Enter") {
            e.preventDefault();
            if (e.shiftKey) onSubmit?.();
            else onRun?.();
          } else if (examMode && ((mod && e.key.toLowerCase() === "v") || (e.shiftKey && e.key === "Insert"))) {
            e.preventDefault();
            onPasteAttempt?.();
          } else if (e.key === "Tab" && !readOnly) {
            e.preventDefault();
            const t = e.currentTarget,
              s = t.selectionStart;
            onChange?.(value.slice(0, s) + "    " + value.slice(t.selectionEnd));
            requestAnimationFrame(() => {
              t.selectionStart = t.selectionEnd = s + 4;
            });
          }
        }}
        className="min-h-0 flex-1 resize-none bg-card p-3 font-mono text-[13px] leading-5 text-ink outline-none"
      />
    </div>
  );
}

export function CodeEditor(props: Props) {
  const [loadState, setLoadState] = useState<"loading" | "ready" | "failed">("loading");
  const latest = useRef(props);
  latest.current = props;

  useEffect(() => {
    let off = false;
    import("@monaco-editor/react")
      .then((m) => m.loader.init())
      .then(() => {
        if (!off) setLoadState("ready");
      })
      .catch(() => {
        if (!off) setLoadState("failed");
      });
    return () => {
      off = true;
    };
  }, []);

  const onMount: OnMount = (editor, monaco) => {
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => latest.current.onSave?.());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => latest.current.onRun?.());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => latest.current.onSubmit?.());

    if (props.examMode) {
      const block = () => latest.current.onPasteAttempt?.();
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyV, block);
      editor.addCommand(monaco.KeyMod.Shift | monaco.KeyCode.Insert, block);
      const dom = editor.getDomNode();
      dom?.addEventListener("paste", (e) => {
        e.preventDefault();
        e.stopPropagation();
        block();
      }, true);
      dom?.addEventListener("drop", (e) => {
        e.preventDefault();
        block();
      }, true);
    }
  };

  if (loadState === "failed") return <FallbackEditor {...props} />;
  return (
    <Editor
      height="100%"
      path={FILE_NAME[props.language]}
      theme="mini-light"
      language={MONACO_LANG[props.language]}
      value={props.value}
      onChange={(v) => props.onChange?.(v ?? "")}
      beforeMount={beforeMount}
      onMount={onMount}
      options={{
        readOnly: props.readOnly,
        fontSize: 13,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        automaticLayout: true,
        tabSize: 4,
        padding: { top: 12, bottom: 12 },
        contextmenu: !props.examMode,
        renderLineHighlight: "line",
        scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
      }}
    />
  );
}
