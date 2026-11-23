"use client";
import { useState } from "react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export const PRESET_COLORS = [
  "#2563eb", "#16a34a", "#dc2626", "#ca8a04", "#ea580c",
  "#9333ea", "#db2777", "#0891b2", "#4b5563", "#64748b",
];
const HEX = /^#[0-9a-fA-F]{6}$/;

/** Chấm màu; bấm mở bảng 10 màu + ô hex. Chọn là gọi onChange ngay (người gọi tự lưu). */
export function ColorPicker({ value, onChange, label, children }: {
  value: string;
  onChange: (color: string) => void;
  label: string;
  /** Nội dung thêm dưới bảng màu, vd nút "Use category color". */
  children?: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [hex, setHex] = useState(value);
  const pick = (c: string) => { onChange(c.toLowerCase()); setOpen(false); };
  return (
    <Popover open={open} onOpenChange={(o) => { setOpen(o); if (o) setHex(value); }}>
      <PopoverTrigger asChild>
        <button type="button" aria-label={label} className="grid h-5 w-5 shrink-0 place-items-center rounded-full hover:bg-muted">
          <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: value }} />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto space-y-2 p-3">
        <div className="grid grid-cols-5 gap-1.5">
          {PRESET_COLORS.map((c) => (
            <button key={c} type="button" aria-label={c} onClick={() => pick(c)}
              className={cn("h-6 w-6 rounded-full", c === value.toLowerCase() && "ring-2 ring-ink ring-offset-2 ring-offset-background")}
              style={{ backgroundColor: c }} />
          ))}
        </div>
        <form className="flex items-center gap-1.5" onSubmit={(e) => { e.preventDefault(); if (HEX.test(hex.trim())) pick(hex.trim()); }}>
          <Input value={hex} onChange={(e) => setHex(e.target.value)} aria-label="Hex color" maxLength={7}
            aria-invalid={!!hex && !HEX.test(hex.trim())} className="h-8 w-28 font-mono text-body-sm" />
          <button type="submit" disabled={!HEX.test(hex.trim())} className="text-body-sm text-link disabled:text-mute">Apply</button>
        </form>
        {children}
      </PopoverContent>
    </Popover>
  );
}
