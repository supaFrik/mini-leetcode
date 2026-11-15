"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { EditorContent, useEditor, useEditorState, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Color, TextStyle } from "@tiptap/extension-text-style";
import { Placeholder } from "@tiptap/extensions";
import {
  Bold, ChevronDown, Code, Code2, Heading1, Heading2, Heading3, Italic, Link2, List, ListOrdered, Palette, Quote,
  Type, Underline,
} from "lucide-react";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { htmlToMarkdown, markdownToHtml } from "@/lib/rich-markdown";
import r from "./rich-text-editor.module.css";

/*
 * Trình soạn kiểu Notion cho admin (specs/practice-ide-rich-editor.spec.md §1).
 * Nhận và trả Markdown; người soạn không phải gõ ký tự Markdown.
 */

type Block = {
  id: string;
  label: string;
  hint: string;
  icon: typeof Type;
  /** Chỉ có ở cỡ full (lý thuyết, đề bài). */
  fullOnly?: boolean;
  isActive: (e: Editor) => boolean;
  apply: (e: Editor) => void;
};

const BLOCKS: Block[] = [
  { id: "text", label: "Text", hint: "Plain paragraph", icon: Type,
    isActive: (e) => e.isActive("paragraph"), apply: (e) => e.chain().focus().setParagraph().run() },
  { id: "h1", label: "Heading 1", hint: "Big section title", icon: Heading1, fullOnly: true,
    isActive: (e) => e.isActive("heading", { level: 1 }), apply: (e) => e.chain().focus().setHeading({ level: 1 }).run() },
  { id: "h2", label: "Heading 2", hint: "Section title", icon: Heading2, fullOnly: true,
    isActive: (e) => e.isActive("heading", { level: 2 }), apply: (e) => e.chain().focus().setHeading({ level: 2 }).run() },
  { id: "h3", label: "Heading 3", hint: "Small title", icon: Heading3, fullOnly: true,
    isActive: (e) => e.isActive("heading", { level: 3 }), apply: (e) => e.chain().focus().setHeading({ level: 3 }).run() },
  { id: "bullet", label: "Bulleted list", hint: "Simple list", icon: List,
    isActive: (e) => e.isActive("bulletList"), apply: (e) => e.chain().focus().toggleBulletList().run() },
  { id: "ordered", label: "Numbered list", hint: "List with numbers", icon: ListOrdered,
    isActive: (e) => e.isActive("orderedList"), apply: (e) => e.chain().focus().toggleOrderedList().run() },
  { id: "quote", label: "Quote", hint: "Highlighted note", icon: Quote,
    isActive: (e) => e.isActive("blockquote"), apply: (e) => e.chain().focus().toggleBlockquote().run() },
  { id: "code", label: "Code block", hint: "Code sample", icon: Code2,
    isActive: (e) => e.isActive("codeBlock"), apply: (e) => e.chain().focus().toggleCodeBlock().run() },
];

const COLORS: { label: string; value: string | null }[] = [
  { label: "Default", value: null },
  { label: "Gray", value: "#6b7280" },
  { label: "Red", value: "#dc2626" },
  { label: "Orange", value: "#ea580c" },
  { label: "Green", value: "#16a34a" },
  { label: "Blue", value: "#2563eb" },
  { label: "Purple", value: "#7c3aed" },
  { label: "Pink", value: "#db2777" },
];

/** Menu "/" đang mở: vị trí "/" trong tài liệu và chữ gõ sau nó. */
type Slash = { from: number; query: string; top: number; left: number };

export function RichTextEditor({ value, onChange, label, placeholder, variant = "full", disabled = false, className }: {
  value: string;
  onChange: (markdown: string) => void;
  /** Tên truy cập của vùng soạn (aria-label). */
  label: string;
  placeholder?: string;
  variant?: "full" | "compact";
  disabled?: boolean;
  className?: string;
}) {
  const blocks = useMemo(() => BLOCKS.filter((b) => variant === "full" || !b.fullOnly), [variant]);
  const emitted = useRef(value);
  const [slash, setSlash] = useState<Slash | null>(null);
  const [active, setActive] = useState(0);
  const wrap = useRef<HTMLDivElement>(null);
  // ProseMirror xử lý phím trước React ⇒ phím của menu "/" đi qua editorProps.handleKeyDown, đọc state qua ref.
  const keys = useRef<(e: KeyboardEvent) => boolean>(() => false);

  const editor = useEditor({
    immediatelyRender: false,
    editable: !disabled,
    extensions: [
      StarterKit.configure({
        heading: variant === "full" ? { levels: [1, 2, 3] } : false,
        link: { openOnClick: false, autolink: true },
      }),
      TextStyle,
      Color,
      Placeholder.configure({ placeholder: placeholder ?? (variant === "full" ? "Write, or type / for blocks…" : "Write…") }),
    ],
    content: markdownToHtml(value),
    editorProps: {
      attributes: { "aria-label": label, role: "textbox", "aria-multiline": "true", class: r.content },
      handleKeyDown: (_view, event) => keys.current(event),
    },
    onUpdate: ({ editor: e }) => {
      const md = htmlToMarkdown(e.getHTML());
      if (md !== emitted.current) {
        emitted.current = md;
        onChange(md);
      }
      setSlash(findSlash(e, wrap.current));
    },
    onSelectionUpdate: ({ editor: e }) => setSlash(findSlash(e, wrap.current)),
    onBlur: () => setTimeout(() => setSlash(null), 150),
  });

  // Giá trị đổi từ ngoài (tải xong, hoàn tác…): nạp lại vào editor.
  useEffect(() => {
    if (!editor || value === emitted.current) return;
    emitted.current = value;
    editor.commands.setContent(markdownToHtml(value), { emitUpdate: false });
  }, [editor, value]);

  useEffect(() => { editor?.setEditable(!disabled); }, [editor, disabled]);

  const matches = slash ? blocks.filter((b) => `${b.label} ${b.hint}`.toLowerCase().includes(slash.query.toLowerCase())) : [];
  useEffect(() => setActive(0), [slash?.query]);

  const choose = (b: Block) => {
    if (!editor || !slash) return;
    editor.chain().focus().deleteRange({ from: slash.from, to: editor.state.selection.from }).run();
    b.apply(editor);
    setSlash(null);
  };

  keys.current = (e) => {
    if (!slash || matches.length === 0) return false;
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      setActive((i) => (i + (e.key === "ArrowDown" ? 1 : matches.length - 1)) % matches.length);
    } else if (e.key === "Enter" || e.key === "Tab") {
      choose(matches[Math.min(active, matches.length - 1)]);
    } else if (e.key === "Escape") {
      setSlash(null);
    } else {
      return false;
    }
    return true;
  };

  return (
    <div ref={wrap} className={cn(r.editor, variant === "compact" && r.compact, disabled && r.disabled, className)}>
      {editor && !disabled && <Toolbar editor={editor} blocks={blocks} />}
      <EditorContent editor={editor} />
      {slash && matches.length > 0 && (
        <ul className={r.slash} role="listbox" aria-label="Insert block" style={{ top: slash.top, left: slash.left }}>
          {matches.map((b, i) => (
            <li key={b.id} role="option" aria-selected={i === active}>
              <button type="button" className={cn(r.slashItem, i === active && r.slashActive)}
                onMouseDown={(e) => { e.preventDefault(); choose(b); }} onMouseEnter={() => setActive(i)}>
                <b.icon size={16} aria-hidden />
                <span><strong>{b.label}</strong><small>{b.hint}</small></span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** "/" ở đầu một đoạn văn trống, con trỏ đứng sau nó ⇒ mở menu chèn block. */
function findSlash(editor: Editor, wrap: HTMLElement | null): Slash | null {
  const { $from, empty } = editor.state.selection;
  if (!empty || $from.parent.type.name !== "paragraph") return null;
  const text = $from.parent.textBetween(0, $from.parentOffset, undefined, "￼");
  const m = /^\/([\w ]{0,20})$/.exec(text);
  if (!m || !wrap) return null;
  const coords = editor.view.coordsAtPos($from.pos);
  const box = wrap.getBoundingClientRect();
  return { from: $from.start(), query: m[1], top: coords.bottom - box.top + 4, left: coords.left - box.left };
}

function Toolbar({ editor, blocks }: { editor: Editor; blocks: Block[] }) {
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      block: blocks.find((b) => b.id !== "text" && b.isActive(e)) ?? blocks[0],
      bold: e.isActive("bold"),
      italic: e.isActive("italic"),
      underline: e.isActive("underline"),
      code: e.isActive("code"),
      link: e.isActive("link"),
      color: (e.getAttributes("textStyle").color as string | undefined) ?? null,
    }),
  });
  const mark = (name: string, on: boolean, run: () => void, Icon: typeof Bold, title: string) => (
    <button key={name} type="button" className={cn(r.tool, on && r.toolOn)} aria-pressed={on} aria-label={title} title={title}
      onMouseDown={(e) => e.preventDefault()} onClick={run}>
      <Icon size={15} aria-hidden />
    </button>
  );
  /** Đóng menu xong thì con trỏ quay lại chỗ đang soạn (Radix mặc định trả focus về nút mở menu). */
  const backToText = (e: Event) => {
    e.preventDefault();
    editor.commands.focus();
  };
  const setLink = () => {
    const previous = editor.getAttributes("link").href as string | undefined;
    const href = window.prompt("Link URL (leave empty to remove)", previous ?? "https://");
    if (href === null) return;
    if (!href.trim()) editor.chain().focus().extendMarkRange("link").unsetLink().run();
    else editor.chain().focus().extendMarkRange("link").setLink({ href: href.trim() }).run();
  };

  return (
    <div className={r.toolbar} role="toolbar" aria-label="Formatting">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button type="button" className={r.blockPicker} aria-label="Text style">
            <state.block.icon size={15} aria-hidden /> {state.block.label} <ChevronDown size={14} aria-hidden />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-56" onCloseAutoFocus={backToText}>
          {blocks.map((b) => (
            <DropdownMenuItem key={b.id} onSelect={() => b.apply(editor)} className="gap-2">
              <b.icon size={16} aria-hidden /> <span className="flex-1">{b.label}</span>
              {b.id === state.block.id && <span aria-hidden>✓</span>}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
      <span className={r.sep} aria-hidden />
      {mark("bold", state.bold, () => editor.chain().focus().toggleBold().run(), Bold, "Bold")}
      {mark("italic", state.italic, () => editor.chain().focus().toggleItalic().run(), Italic, "Italic")}
      {mark("underline", state.underline, () => editor.chain().focus().toggleUnderline().run(), Underline, "Underline")}
      {mark("code", state.code, () => editor.chain().focus().toggleCode().run(), Code, "Inline code")}
      {mark("link", state.link, setLink, Link2, "Link")}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button type="button" className={r.tool} aria-label="Text color" title="Text color">
            <Palette size={15} aria-hidden style={state.color ? { color: state.color } : undefined} />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-44" onCloseAutoFocus={backToText}>
          {COLORS.map((c) => (
            <DropdownMenuItem key={c.label} className="gap-2"
              onSelect={() => (c.value ? editor.chain().focus().setColor(c.value).run() : editor.chain().focus().unsetColor().run())}>
              <span className={r.swatch} style={{ background: c.value ?? "transparent" }} aria-hidden />
              <span style={c.value ? { color: c.value } : undefined}>{c.label}</span>
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
