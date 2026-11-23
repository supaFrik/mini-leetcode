"use client";
import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { fetchAdminTags, fetchProblemTags } from "@/lib/services";

export type TagOption = { name: string; category: string };

/** Mọi tag đã biết. Không có TAG_MANAGE (403) thì lấy danh sách công khai (chỉ tag của bài đã publish). */
export function useTagOptions(): TagOption[] {
  const [options, setOptions] = useState<TagOption[]>([]);
  useEffect(() => {
    fetchAdminTags()
      .then((t) => t.map((x) => ({ name: x.name, category: x.category })))
      .catch(() => fetchProblemTags().then((t) => t.map((x) => ({ name: x.tag, category: x.category }))))
      .then(setOptions)
      .catch(() => setOptions([]));
  }, []);
  return options;
}

const has = (list: string[], name: string) => list.some((t) => t.toLowerCase() === name.toLowerCase());

/** Chọn nhiều tag: tìm kiếm, gom theo category; gõ tên mới + Enter để thêm (tag mới nằm ở "Other"). */
export function TagPicker({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const options = useTagOptions();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const q = query.trim();

  const groups = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const o of options) {
      if (has(value, o.name) || (q && !o.name.toLowerCase().includes(q.toLowerCase()))) continue;
      map.set(o.category, [...(map.get(o.category) ?? []), o.name]);
    }
    return Array.from(map.entries()).sort(([a], [b]) => (a === "Other" ? 1 : b === "Other" ? -1 : a.localeCompare(b)));
  }, [options, value, q]);

  const add = (name: string) => {
    const n = name.trim().slice(0, 50);
    if (n && !has(value, n)) onChange([...value, options.find((o) => o.name.toLowerCase() === n.toLowerCase())?.name ?? n]);
    setQuery("");
  };

  return (
    <div className="space-y-1 text-body-sm text-mute">
      <span id="tags-label">Tags</span>
      <div className="relative">
        <div className="flex flex-wrap items-center gap-1 rounded-sm border border-border bg-card px-2 py-1">
          {value.map((t) => (
            <span key={t} className="flex items-center gap-0.5 rounded-sm bg-muted px-1.5 py-0.5 text-body-sm text-ink">
              {t}
              <button type="button" aria-label={`Remove tag ${t}`} className="text-mute hover:text-ink" onClick={() => onChange(value.filter((x) => x !== t))}>
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
          <Input
            aria-labelledby="tags-label"
            value={query}
            placeholder={value.length ? "" : "Search or type a new tag"}
            className="h-7 min-w-[140px] flex-1 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0"
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            onBlur={() => setTimeout(() => setOpen(false), 150)}
            onKeyDown={(e) => {
              if (e.key === "Enter") { e.preventDefault(); add(query); }
              else if (e.key === "Backspace" && !query && value.length) onChange(value.slice(0, -1));
              else if (e.key === "Escape") setOpen(false);
            }}
          />
        </div>
        {open && (groups.length > 0 || q) && (
          <div className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-sm bg-background py-1 shadow-lg" role="listbox" aria-label="Tag suggestions">
            {groups.map(([cat, names]) => (
              <div key={cat}>
                <div className="px-3 pt-1.5 text-[11px] uppercase tracking-wider text-mute">{cat}</div>
                {names.map((n) => (
                  <button key={n} type="button" role="option" aria-selected={false} className="block w-full px-3 py-1 text-left text-body-md text-ink hover:bg-muted/60"
                    onMouseDown={(e) => e.preventDefault()} onClick={() => add(n)}>{n}</button>
                ))}
              </div>
            ))}
            {q && !options.some((o) => o.name.toLowerCase() === q.toLowerCase()) && !has(value, q) && (
              <button type="button" className="block w-full px-3 py-1 text-left text-body-md text-link hover:bg-muted/60"
                onMouseDown={(e) => e.preventDefault()} onClick={() => add(q)}>Add &quot;{q}&quot;</button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
