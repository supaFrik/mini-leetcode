"use client";
import { Reorder, useDragControls } from "framer-motion";
import { BookOpen, ChevronDown, ChevronRight, Code2, FolderKanban, GripVertical, Plus, X } from "lucide-react";
import { useState } from "react";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { shift, type EntryNode, type NodeId, type PlanModel, type SessionNode } from "./model";

/* Outline của Course Builder (kiểu curriculum): session cách nhau bằng đường kẻ mảnh, gập / mở, nhấp đúp đổi tên, kéo thả trong cùng cấp (tay nắm), Alt+↑/↓ cho nút đang chọn. */

const ENTRY_KIND = {
  LESSON: { icon: BookOpen, label: "Lesson", color: "text-blue-600" },
  PROBLEM: { icon: Code2, label: "Problem", color: "text-green-600" },
  STAGE: { icon: FolderKanban, label: "Project stage", color: "text-orange-600" },
} as const;

// Nằm ở lề trái (absolute) để không đẩy số / icon lệch cột.
function Grip({ controls, label }: { controls: ReturnType<typeof useDragControls>; label: string }) {
  return (
    <span role="button" tabIndex={-1} aria-label={`Drag ${label}`}
      className="absolute -left-4 top-1.5 cursor-grab touch-none text-mute/60 opacity-0 hover:text-ink group-hover:opacity-100"
      onPointerDown={(e) => controls.start(e)}>
      <GripVertical className="h-3.5 w-3.5" />
    </span>
  );
}

function RemoveButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick}
      className="rounded-sm p-0.5 text-mute opacity-0 hover:text-ink focus:opacity-100 group-hover:opacity-100">
      <X className="h-3.5 w-3.5" />
    </button>
  );
}

export interface OutlineProps {
  model: PlanModel;
  selected: NodeId;
  /** Nút có lỗi thật → nhãn ngắn màu cam, vd "No tests". */
  issues: Map<NodeId, string>;
  canEdit: boolean;
  onSelect: (id: NodeId) => void;
  onChange: (m: PlanModel) => void;
  onAddSession: () => void;
  onRemove: (id: NodeId) => void;
}

export const entryLabel = (e: EntryNode) =>
  e.topic?.title ?? e.problem?.title ?? (e.problem ? `Problem #${e.problem.problemId}` : "");

function Warn({ text }: { text?: string }) {
  return text ? <span className="shrink-0 text-body-sm text-orange-600">{text}</span> : null;
}

function EntryRow({ entry, props }: { entry: EntryNode; props: OutlineProps }) {
  const controls = useDragControls();
  const id = `entry:${entry.key}`;
  const label = entryLabel(entry);
  const selected = props.selected === id;
  const { icon: Icon, label: kind, color } = ENTRY_KIND[entry.type];
  return (
    <Reorder.Item as="li" value={entry} dragListener={false} dragControls={controls}
      className={cn("group relative flex items-center gap-2 text-[13px] leading-5", selected ? "text-link" : "text-ink")}>
      {props.canEdit && <Grip controls={controls} label={label} />}
      <button type="button" className="flex min-w-0 flex-1 items-center gap-1.5 py-1 text-left" aria-current={selected ? "true" : undefined}
        data-node={id} onClick={() => props.onSelect(id)}>
        <Icon className={cn("h-4 w-4 shrink-0", color)} aria-label={kind} />
        <span className="min-w-0 break-words">{label}</span>
      </button>
      <Warn text={props.issues.get(id)} />
      {props.canEdit && <RemoveButton label={`Remove ${label}`} onClick={() => props.onRemove(id)} />}
    </Reorder.Item>
  );
}

/** Phần lặp ở đầu tên mọi session ("Mini Bank Console · Stage 1" → "Stage 1"). */
function sharedPrefix(sessions: SessionNode[]): string {
  if (sessions.length < 2) return "";
  const first = sessions[0].title.match(/^(.+?·\s*)/)?.[1] ?? "";
  return first && sessions.every((s) => s.title.startsWith(first) && s.title.length > first.length) ? first : "";
}

function SessionBox({ session, index, prefix, props }: { session: SessionNode; index: number; prefix: string; props: OutlineProps }) {
  const controls = useDragControls();
  const [open, setOpen] = useState(true);
  const [editing, setEditing] = useState<string | null>(null);
  const id = `session:${session.key}`;
  const full = session.title || `Session ${index + 1}`;
  const title = prefix ? full.slice(prefix.length) : full;
  const count = session.entries.length;
  const selected = props.selected === id;
  const patch = (p: Partial<SessionNode>) => props.onChange({
    ...props.model, sessions: props.model.sessions.map((s) => (s.key === session.key ? { ...s, ...p } : s)),
  });
  const commit = () => {
    if (editing?.trim()) patch({ title: editing.trim() });
    setEditing(null);
  };
  return (
    <Reorder.Item as="li" value={session} dragListener={false} dragControls={controls}
      className="border-t border-border pt-1 first:border-t-0 first:pt-0">
      <div className="group relative flex items-start gap-2">
        {props.canEdit && <Grip controls={controls} label={full} />}
        <button type="button" aria-label={open ? `Collapse ${full}` : `Expand ${full}`} onClick={() => setOpen(!open)}
          className="w-4 shrink-0 pt-0.5 text-left text-[13px] leading-5 text-mute hover:text-ink">
          <span className="group-hover:hidden">{index + 1}</span>
          <span className="hidden group-hover:inline">{open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}</span>
        </button>
        {editing !== null ? (
          <input autoFocus value={editing} aria-label="Session title" onChange={(e) => setEditing(e.target.value)} onBlur={commit}
            onKeyDown={(e) => { if (e.key === "Enter") commit(); if (e.key === "Escape") setEditing(null); }}
            className="min-w-0 flex-1 border-b border-border bg-transparent py-0.5 text-body-md font-semibold text-ink outline-none" />
        ) : (
          <button type="button" className={cn("min-w-0 flex-1 break-words py-0.5 text-left text-body-md font-semibold", selected ? "text-link" : "text-ink")}
            aria-current={selected ? "true" : undefined} data-node={id} title={props.canEdit ? "Double-click to rename" : full}
            onClick={() => props.onSelect(id)} onDoubleClick={() => props.canEdit && setEditing(full)}>
            {title}
          </button>
        )}
        <span className="shrink-0 pt-1 text-body-sm text-mute">{count ? `${count} item${count === 1 ? "" : "s"}` : "Empty"}</span>
        {props.canEdit && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button type="button" aria-label={`Add to ${full}`} title="Add lesson or problem"
                className={cn("shrink-0 pt-1 hover:text-ink focus:opacity-100", selected ? "text-link opacity-100" : "text-mute opacity-0 group-hover:opacity-100")}>
                <Plus className="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => props.onSelect(`add:${session.key}:lesson`)}>
                <BookOpen className="mr-2 h-4 w-4" aria-hidden="true" />Add lesson
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => props.onSelect(`add:${session.key}:problem`)}>
                <Code2 className="mr-2 h-4 w-4" aria-hidden="true" />Add problem
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        {props.canEdit && <span className="pt-0.5"><RemoveButton label={`Remove ${full}`} onClick={() => props.onRemove(id)} /></span>}
      </div>
      {open && count > 0 && (
        <Reorder.Group as="ol" axis="y" values={session.entries} onReorder={(entries) => patch({ entries })} aria-label={`${full} items`} className="pl-6">
          {session.entries.map((e) => <EntryRow key={e.key} entry={e} props={props} />)}
        </Reorder.Group>
      )}
    </Reorder.Item>
  );
}

export function Outline(props: OutlineProps) {
  const { model, selected, onChange } = props;
  const prefix = sharedPrefix(model.sessions);
  return (
    <nav aria-label="Course outline" className="flex flex-col gap-3 text-body-sm"
      onKeyDown={(e) => {
        if ((e.target as HTMLElement).tagName === "INPUT" || !props.canEdit || !e.altKey || (e.key !== "ArrowUp" && e.key !== "ArrowDown")) return;
        e.preventDefault();
        onChange(shift(model, selected, e.key === "ArrowUp" ? -1 : 1));
        requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-node="${selected}"]`)?.focus());
      }}>
      <p className="mb-2 text-[11px] uppercase tracking-wider text-mute">Curriculum</p>
      <Reorder.Group as="ol" axis="y" values={model.sessions} onReorder={(sessions) => onChange({ ...model, sessions })}
        aria-label="Sessions" className="flex flex-col gap-1">
        {model.sessions.map((s, i) => <SessionBox key={s.key} session={s} index={i} prefix={prefix} props={props} />)}
      </Reorder.Group>
      {model.sessions.length === 0 && <p className="px-1 text-mute">No sessions yet.</p>}
      {props.canEdit && (
        <button type="button" onClick={props.onAddSession}
          className="flex items-center gap-1 self-start py-1.5 text-mute hover:text-ink">
          <Plus className="h-4 w-4" /> Add session
        </button>
      )}
    </nav>
  );
}
