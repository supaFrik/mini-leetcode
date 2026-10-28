"use client";
import dynamic from "next/dynamic";
import { useRef } from "react";
import type { OnMount } from "@monaco-editor/react";
import type { Language } from "@/types/api";
import { MONACO_LANG } from "@/lib/templates";

const Editor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => <div className="p-4 text-sm text-muted-foreground">Loading editor…</div>,
});

interface Props {
  language: Language; value: string; onChange: (v: string) => void;
  readOnly?: boolean; examMode?: boolean;
  onSave: () => void; onRun?: () => void; onSubmit: () => void;
  onPasteAttempt?: () => void;           // chỉ dùng ở Exam
}

export function CodeEditor(props: Props) {
  // "ref chứa props mới nhất": giải thích ngay bên dưới
  const latest = useRef(props);
  latest.current = props;

  const onMount: OnMount = (editor, monaco) => {
    // Ctrl (Windows/Linux) hoặc Cmd (Mac) + S = Save
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => latest.current.onSave());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => latest.current.onRun?.());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => latest.current.onSubmit());

    if (props.examMode) {
      const block = () => latest.current.onPasteAttempt?.();
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyV, block);
      editor.addCommand(monaco.KeyMod.Shift | monaco.KeyCode.Insert, block);
      const dom = editor.getDomNode();
      // capture = true: chặn sự kiện trước khi Monaco kịp xử lý
      dom?.addEventListener("paste", (e) => { e.preventDefault(); e.stopPropagation(); block(); }, true);
      dom?.addEventListener("drop", (e) => { e.preventDefault(); block(); }, true); // kéo-thả chữ vào
    }
  };

  return (
    <Editor
      height="100%"
      theme="vs-dark"
      language={MONACO_LANG[props.language]}
      value={props.value}
      onChange={(v) => props.onChange(v ?? "")}
      onMount={onMount}
      options={{
        readOnly: props.readOnly,
        fontSize: 13, minimap: { enabled: false }, scrollBeyondLastLine: false,
        automaticLayout: true, tabSize: 4,
        contextmenu: !props.examMode,          // tắt chuột phải khi thi
      }}
    />
  );
}