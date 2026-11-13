"use client";
import { Reorder, useDragControls } from "framer-motion";
import { AlertTriangle, ChevronDown, ChevronRight, GripVertical, Plus, X } from "lucide-react";
import { useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { STATUS_MARK, shift, type ItemNode, type NodeId, type PlanModel, type StageNode, type TopicNode } from "./model";

/* Outline của Course Builder: chọn nút, kéo thả trong cùng cấp (tay nắm), Alt+↑/↓ cho nút đang chọn. */

function Row({ id, depth, selected, onSelect, label, mark, warn, grip, actions, toggle }: {
  id: NodeId; depth: number; selected: boolean; onSelect: (id: NodeId) => void; label: ReactNode;
  mark?: string; warn?: boolean; grip?: ReactNode; actions?: ReactNode; toggle?: ReactNode;
}) {
  return (
    <div className={cn("group flex items-center gap-1 rounded-sm pr-1 text-body-sm",
      selected ? "bg-hairline-soft text-ink" : "text-mute hover:bg-hairline-soft/60 hover:text-ink")}
      style={{ paddingLeft: depth * 14 + 4 }}>
      {grip}
      {toggle}
      <button type="button" className="min-w-0 flex-1 truncate py-1.5 text-left" aria-current={selected ? "true" : undefined}
        data-node={id} onClick={() => onSelect(id)}>
        {label}
      </button>
      {warn && <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-destructive" aria-label="Has publish errors" />}
      {mark && <span className="w-3 shrink-0 text-center text-mute" aria-hidden>{mark}</span>}
      <span className="flex shrink-0 opacity-0 group-hover:opacity-100 focus-within:opacity-100">{actions}</span>
    </div>
  );
}

function Grip({ controls, label }: { controls: ReturnType<typeof useDragControls>; label: string }) {
  return (
    <span role="button" tabIndex={-1} aria-label={`Drag ${label}`} className="cursor-grab touch-none text-mute/60 hover:text-ink"
      onPointerDown={(e) => controls.start(e)}>
      <GripVertical className="h-3.5 w-3.5" />
    </span>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick}
      className="rounded-sm p-0.5 text-mute hover:bg-border hover:text-ink">{children}</button>
  );
}

export interface OutlineProps {
  model: PlanModel;
  selected: NodeId;
  issues: Set<NodeId>;
  onSelect: (id: NodeId) => void;
  onChange: (m: PlanModel) => void;
  onAddItem: (kind: ItemNode["kind"]) => void;
  onAddStage: (item: ItemNode) => void;
  onAddTopic: (stage: StageNode) => void;
  onRemove: (id: NodeId) => void;
}

function TopicRow({ topic, stage, depth, props }: { topic: TopicNode; stage: StageNode; depth: number; props: OutlineProps }) {
  const controls = useDragControls();
  const id = `topic:${topic.id}@${stage.problemId}`;
  return (
    <Reorder.Item as="li" value={topic} dragListener={false} dragControls={controls}>
      <Row id={id} depth={depth} selected={props.selected === id} onSelect={props.onSelect} label={topic.title}
        mark={STATUS_MARK[topic.status ?? "PUBLISHED"]} warn={props.issues.has(id)}
        grip={<Grip controls={controls} label={topic.title} />}
        actions={<IconButton label={`Remove topic ${topic.title}`} onClick={() => props.onRemove(id)}><X className="h-3.5 w-3.5" /></IconButton>} />
    </Reorder.Item>
  );
}

function StageRow({ stage, index, item, props }: { stage: StageNode; index: number; item: ItemNode; props: OutlineProps }) {
  const controls = useDragControls();
  const [open, setOpen] = useState(true);
  const id = `stage:${stage.problemId}`;
  const project = item.kind === "PROJECT";
  const label = `${project ? `${index + 1} ` : ""}${stage.title ?? `Problem #${stage.problemId}`}`;
  const setTopics = (topics: TopicNode[]) => props.onChange({
    ...props.model,
    items: props.model.items.map((it) => it.key !== item.key ? it
      : { ...it, stages: it.stages.map((s) => (s.problemId === stage.problemId ? { ...s, topics } : s)) }),
  });
  return (
    <Reorder.Item as="li" value={stage} dragListener={false} dragControls={controls}>
      <Row id={id} depth={1} selected={props.selected === id} onSelect={props.onSelect} label={label}
        mark={STATUS_MARK[stage.status ?? "DRAFT"]} warn={props.issues.has(id)}
        grip={project ? <Grip controls={controls} label={label} /> : undefined}
        toggle={<button type="button" aria-label={open ? `Collapse ${label}` : `Expand ${label}`} onClick={() => setOpen(!open)}
          className="text-mute">{open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}</button>}
        actions={<IconButton label={`Remove ${label}`} onClick={() => props.onRemove(id)}><X className="h-3.5 w-3.5" /></IconButton>} />
      {open && (
        <>
          <Reorder.Group as="ol" axis="y" values={stage.topics} onReorder={setTopics} aria-label={`${label} topics`}>
            {stage.topics.map((t) => <TopicRow key={t.id} topic={t} stage={stage} depth={3} props={props} />)}
          </Reorder.Group>
          <button type="button" onClick={() => props.onAddTopic(stage)} style={{ paddingLeft: 3 * 14 + 4 }}
            className="flex w-full items-center gap-1 py-1 text-body-sm text-mute hover:text-ink">
            <Plus className="h-3.5 w-3.5" /> topic
          </button>
        </>
      )}
    </Reorder.Item>
  );
}

function ItemRow({ item, props }: { item: ItemNode; props: OutlineProps }) {
  const controls = useDragControls();
  const id = `item:${item.key}`;
  const setStages = (stages: StageNode[]) => props.onChange({
    ...props.model, items: props.model.items.map((it) => (it.key === item.key ? { ...it, stages } : it)),
  });
  const title = item.title || (item.kind === "PROJECT" ? "Untitled project" : "Untitled practice");
  const canAdd = item.kind === "PROJECT" || item.stages.length === 0;
  return (
    <Reorder.Item as="li" value={item} dragListener={false} dragControls={controls}>
      <Row id={id} depth={0} selected={props.selected === id} onSelect={props.onSelect}
        label={<><span className="mr-1 font-mono text-[11px] uppercase text-mute">{item.kind === "PROJECT" ? "Project" : "Practice"}</span>{title}</>}
        warn={props.issues.has(id)} grip={<Grip controls={controls} label={title} />}
        actions={<IconButton label={`Remove ${title}`} onClick={() => props.onRemove(id)}><X className="h-3.5 w-3.5" /></IconButton>} />
      <Reorder.Group as="ol" axis="y" values={item.stages} onReorder={setStages} aria-label={`${title} stages`}>
        {item.stages.map((s, k) => <StageRow key={s.problemId} stage={s} index={k} item={item} props={props} />)}
      </Reorder.Group>
      {canAdd && (
        <button type="button" onClick={() => props.onAddStage(item)} style={{ paddingLeft: 14 + 4 }}
          className="flex w-full items-center gap-1 py-1 text-body-sm text-mute hover:text-ink">
          <Plus className="h-3.5 w-3.5" /> {item.kind === "PROJECT" ? "stage" : "problem"}
        </button>
      )}
    </Reorder.Item>
  );
}

export function Outline(props: OutlineProps) {
  const { model, selected, onSelect, onChange } = props;
  return (
    <nav aria-label="Course outline" className="flex flex-col gap-1 text-body-sm"
      onKeyDown={(e) => {
        if (!e.altKey || (e.key !== "ArrowUp" && e.key !== "ArrowDown")) return;
        e.preventDefault();
        onChange(shift(model, selected, e.key === "ArrowUp" ? -1 : 1));
        requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-node="${selected}"]`)?.focus());
      }}>
      <Row id="plan" depth={0} selected={selected === "plan"} onSelect={onSelect}
        label={<span className="font-medium text-ink">{model.title || "Untitled plan"}</span>}
        mark={STATUS_MARK[model.status]} warn={props.issues.has("plan")} />
      <Reorder.Group as="ol" axis="y" values={model.items} onReorder={(items) => onChange({ ...model, items })} aria-label="Items">
        {model.items.map((it) => <ItemRow key={it.key} item={it} props={props} />)}
      </Reorder.Group>
      <div className="flex gap-3 px-1 pt-1">
        <button type="button" onClick={() => props.onAddItem("PROJECT")} className="flex items-center gap-1 text-mute hover:text-ink">
          <Plus className="h-3.5 w-3.5" /> project
        </button>
        <button type="button" onClick={() => props.onAddItem("PROBLEM")} className="flex items-center gap-1 text-mute hover:text-ink">
          <Plus className="h-3.5 w-3.5" /> practice
        </button>
      </div>
      <p className="px-1 pt-2 text-[11px] text-mute">Drag the handles or press Alt+↑ / Alt+↓ to reorder.</p>
    </nav>
  );
}
