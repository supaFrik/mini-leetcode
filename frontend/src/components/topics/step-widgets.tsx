"use client";
import Link from "next/link";
import { Fragment } from "react";
import { Reorder } from "framer-motion";
import { ArrowDown, ArrowRight, ArrowUp, Check, GripVertical } from "lucide-react";
import { cn } from "@/lib/utils";
import { DifficultyBadge } from "@/components/problems/difficulty-badge";
import type { ChoiceContent, CodeContent, FillBlankContent, IdText } from "@/types/api";
import t from "./topics.module.css";

/* Widget luyện tập của chủ đề học (specs/topics.spec.md). Widget không tự chấm: trả giá trị qua onChange,
 * trang chủ đề gửi lên server khi bấm Submit. */

// ── Trắc nghiệm ─────────────────────────────────────────────────────────────

export function ChoiceWidget({ content, selected, onChange, disabled }: {
  content: ChoiceContent; selected: string[]; onChange: (ids: string[]) => void; disabled?: boolean;
}) {
  const multiple = !!content.multiple;
  const toggle = (id: string) => {
    if (multiple) onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);
    else onChange([id]);
  };
  return (
    <>
      <p className={t.instruction}>{multiple ? "Select all correct options" : "Select one option"}</p>
      <div className={t.options} role={multiple ? "group" : "radiogroup"}>
        {content.options.map((o) => {
          const checked = selected.includes(o.id);
          return (
            <label key={o.id} className={cn(t.option, checked && t.optionChecked)}>
              <input type={multiple ? "checkbox" : "radio"} name="choice" checked={checked} disabled={disabled}
                onChange={() => toggle(o.id)} />
              <span>{o.text}</span>
            </label>
          );
        })}
      </div>
    </>
  );
}

// ── Danh sách sắp xếp được (dùng cho ghép cặp và sắp xếp) ────────────────────

function move<T>(list: T[], from: number, to: number): T[] {
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

/** Kéo thả (chuột / cảm ứng) + nút ↑↓ (bàn phím, trình đọc màn hình). */
function SortableList({ items, order, onChange, code, label, disabled }: {
  items: IdText[]; order: string[]; onChange: (order: string[]) => void; code?: boolean; label: string; disabled?: boolean;
}) {
  const byId = new Map(items.map((i) => [i.id, i]));
  return (
    <Reorder.Group as="ol" axis="y" values={order} onReorder={disabled ? () => undefined : onChange}
      className={t.sortList} aria-label={label}>
      {order.map((id, index) => {
        const item = byId.get(id);
        if (!item) return null;
        return (
          <Reorder.Item key={id} value={id} as="li" className={t.sortItem} dragListener={!disabled}>
            <GripVertical size={16} className={t.grip} aria-hidden />
            <span className={t.sortText}>{code ? <code>{item.text}</code> : item.text}</span>
            <span className={t.arrows}>
              <button type="button" aria-label={`Move "${item.text.trim()}" up`} disabled={disabled || index === 0}
                onClick={() => onChange(move(order, index, index - 1))}><ArrowUp size={15} /></button>
              <button type="button" aria-label={`Move "${item.text.trim()}" down`} disabled={disabled || index === order.length - 1}
                onClick={() => onChange(move(order, index, index + 1))}><ArrowDown size={15} /></button>
            </span>
          </Reorder.Item>
        );
      })}
    </Reorder.Group>
  );
}

// ── Ghép cặp ────────────────────────────────────────────────────────────────

/** Cột trái cố định; kéo cột phải để dòng i của hai cột thành một cặp. */
export function MatchingWidget({ left, right, order, onChange, disabled }: {
  left: IdText[]; right: IdText[]; order: string[]; onChange: (order: string[]) => void; disabled?: boolean;
}) {
  return (
    <>
      <p className={t.instruction}>Match the items from the left and right columns — drag the right column or use the arrows</p>
      <div className={t.match}>
        <ol className={t.sortList} aria-label="Left column">
          {left.map((l) => <li key={l.id} className={t.matchLeft}>{l.text}</li>)}
        </ol>
        <SortableList items={right} order={order} onChange={onChange} label="Right column" disabled={disabled} />
      </div>
    </>
  );
}

// ── Sắp xếp ─────────────────────────────────────────────────────────────────

export function OrderingWidget({ items, order, onChange, disabled }: {
  items: IdText[]; order: string[]; onChange: (order: string[]) => void; disabled?: boolean;
}) {
  return (
    <>
      <p className={t.instruction}>Put the lines in the correct order — drag them or use the arrows</p>
      <SortableList items={items} order={order} onChange={onChange} code label="Lines to order" disabled={disabled} />
    </>
  );
}

// ── Điền chỗ trống ──────────────────────────────────────────────────────────

const BLANK = /\[\[([A-Za-z0-9_-]+)]]/g;

export function FillBlankWidget({ content, values, onChange, disabled }: {
  content: FillBlankContent; values: Record<string, string>; onChange: (values: Record<string, string>) => void; disabled?: boolean;
}) {
  const parts: Array<{ text: string } | { id: string; n: number }> = [];
  let last = 0;
  let n = 0;
  for (const m of Array.from(content.code.matchAll(BLANK))) {
    parts.push({ text: content.code.slice(last, m.index) });
    parts.push({ id: m[1], n: ++n });
    last = (m.index ?? 0) + m[0].length;
  }
  parts.push({ text: content.code.slice(last) });
  return (
    <>
      <p className={t.instruction}>Fill in the blanks</p>
      <pre className={t.codeBox}>
        {parts.map((p, i) => "text" in p ? <Fragment key={i}>{p.text}</Fragment> : (
          <input key={i} className={t.blank} aria-label={`Blank ${p.n}`} value={values[p.id] ?? ""} disabled={disabled}
            spellCheck={false} autoComplete="off" size={Math.max(4, (values[p.id] ?? "").length + 1)}
            onChange={(e) => onChange({ ...values, [p.id]: e.target.value })} />
        ))}
      </pre>
    </>
  );
}

// ── Bài code ────────────────────────────────────────────────────────────────

export function CodeWidget({ content, passed }: { content: CodeContent; passed: boolean }) {
  const open = content.problemSlug && content.published !== false;
  return (
    <div className={t.codeStep}>
      <div>
        <strong>{content.problemTitle ?? content.problemSlug ?? "Coding exercise"}</strong>
        {content.difficulty && <DifficultyBadge value={content.difficulty} />}
        <p className={t.nodeSummary}>Solve it in the workspace or IntelliJ. This step is done once a submission is accepted.</p>
      </div>
      {passed ? (
        <span className={t.solved}><Check size={16} aria-hidden /> Accepted</span>
      ) : open ? (
        <Link href={`/problems/${content.problemSlug}`} className={t.primary}>Open the exercise <ArrowRight size={15} aria-hidden /></Link>
      ) : (
        <span className={t.nodeSummary}>Being updated — try later</span>
      )}
    </div>
  );
}
