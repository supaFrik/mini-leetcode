"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { BookOpen, Check, Circle, Filter, Lock, Plus, Search, Star, Swords, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { useUrlState } from "@/hooks/use-url-state";
import { errorMessage } from "@/lib/api";
import { fetchProblems } from "@/lib/services";
import { DifficultyBadge } from "./difficulty-badge";
import { Pagination } from "./pagination";
import { LANG_PREF_KEY } from "@/lib/templates";
import type { Language, Page, ProblemSummary } from "@/types/api";

// Chỉ các ngôn ngữ backend hỗ trợ (enum JAVA | PYTHON | CPP)
const LANGS: [string, Language][] = [["Python", "PYTHON"], ["Java", "JAVA"], ["C++", "CPP"]];
const TAGS = ["Array", "Two Pointers", "Hash Table", "Math", "Dynamic Programming", "Sorting", "Graph", "Design"];

function Segmented({ value, onChange, options }: { value: string; onChange: (v: string) => void; options: [string, string][] }) {
  return (
    <div className="flex flex-wrap gap-1 rounded-md bg-secondary p-1">
      {options.map(([v, label]) => (
        <button key={v} onClick={() => onChange(v)} aria-pressed={value === v}
          className={cn("rounded px-2.5 py-1 text-xs", value === v ? "bg-primary text-primary-foreground" : "hover:bg-accent")}>{label}</button>
      ))}
    </div>
  );
}

function Removable({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-secondary px-2.5 py-1">
      {label}
      <button aria-label={`Bỏ lọc ${label}`} onClick={onRemove} className="text-muted-foreground hover:text-foreground"><X className="h-3 w-3" /></button>
    </span>
  );
}

// Sidebar tĩnh theo Figma (Library / My Lists). Backend chưa có API cho các mục này.
function Sidebar() {
  const item = "flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-accent";
  return (
    <aside className="hidden w-56 shrink-0 space-y-1 border-r p-3 lg:block">
      <div className={cn(item, "bg-secondary")}><BookOpen className="h-4 w-4" /><span className="font-mono">Placeholder</span></div>
      <div className={item}><Swords className="h-4 w-4" /><span className="font-mono">Placeholder</span></div>
      <hr className="my-3" />
      <div className="flex items-center justify-between px-3 text-xs text-muted-foreground"><span className="font-mono">My Lists</span><Plus className="h-3.5 w-3.5" /></div>
      <div className={item}><Star className="h-4 w-4 text-amber-500" /><span className="flex-1">Favorite</span><Lock className="h-3.5 w-3.5 text-muted-foreground" /></div>
      <div className={item}><Star className="h-4 w-4 text-amber-500" /><span>I hate these problems</span></div>
      <hr className="my-3" />
      <p className="px-3 font-mono text-xs text-muted-foreground">Saved by me</p>
      <div className={item}><span className="h-3 w-3 rounded-sm bg-blue-500" />Binary Search</div>
      <div className={item}><span className="h-3 w-3 rounded-sm bg-emerald-500" />Dynamic Programming</div>
      <div className={cn(item, "text-muted-foreground")}><Plus className="h-3.5 w-3.5" />New List</div>
    </aside>
  );
}

export function ProblemsView() {
  const { sp, set } = useUrlState();
  const page = Math.max(1, Number(sp.get("page") ?? 1) || 1);
  const q = sp.get("q") ?? "";
  const difficulty = sp.get("difficulty") ?? "";
  const progress = sp.get("progress") ?? "";
  // "Language" là NGÔN NGỮ LÀM BÀI MẶC ĐỊNH (backend không lọc đề theo ngôn ngữ), được workspace đọc lại
  const [lang, setLang] = useState("");
  useEffect(() => { try { setLang(localStorage.getItem(LANG_PREF_KEY) ?? ""); } catch { /* bỏ qua */ } }, []);
  const pickLang = (v: string) => { setLang(v); try { if (v) localStorage.setItem(LANG_PREF_KEY, v); else localStorage.removeItem(LANG_PREF_KEY); } catch { /* bỏ qua */ } };
  const searchRef = useRef<HTMLInputElement>(null);
  useEffect(() => { // phím "/" nhảy vào ô tìm kiếm
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (e.key === "/" && !["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName)) { e.preventDefault(); searchRef.current?.focus(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  const tagsParam = sp.get("tags") ?? "";
  const tags = tagsParam.split(",").filter(Boolean);

  const [res, setRes] = useState<Page<ProblemSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false; // bỏ kết quả cũ nếu người dùng đổi lọc quá nhanh
    setLoading(true); setError(null);
    fetchProblems({ page, q, difficulty, progress, tags: tagsParam.split(",").filter(Boolean) })
      .then((r) => { if (!ignore) setRes(r); })
      .catch((e) => { if (!ignore) setError(errorMessage(e)); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [page, q, difficulty, progress, tagsParam]);

  // Popover giữ lựa chọn NHÁP, chỉ ghi lên URL khi bấm Apply
  const [open, setOpen] = useState(false);
  const [dProgress, setDProgress] = useState(progress);
  const [dDiff, setDDiff] = useState(difficulty);
  const activeCount = (progress ? 1 : 0) + (difficulty ? 1 : 0);
  const chip = (active: boolean) =>
    cn("rounded-md border px-3 py-1 text-xs transition-colors", active ? "border-primary bg-primary text-primary-foreground" : "bg-card hover:bg-accent");

  return (
    <div className="flex">
      <Sidebar />
      <div className="mx-auto w-full max-w-5xl space-y-4 p-6">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="w-full text-xs text-muted-foreground">Ngôn ngữ làm bài mặc định</span>
            <button className={chip(!lang)} onClick={() => pickLang("")}>All languages</button>
            {LANGS.map(([label, v]) => <button key={v} className={chip(lang === v)} onClick={() => pickLang(v)}>{label}</button>)}
            <Popover open={open} onOpenChange={(o) => { setOpen(o); if (o) { setDProgress(progress); setDDiff(difficulty); } }}>
              <PopoverTrigger asChild>
                <Button size="sm" className="ml-auto gap-1"><Filter className="h-3.5 w-3.5" />Filter
                  {activeCount > 0 && <Badge className="ml-1 h-4 bg-background px-1 text-[10px] text-foreground">{activeCount}</Badge>}
                </Button>
              </PopoverTrigger>
              <PopoverContent align="end" className="w-80 space-y-4">
                <div>
                  <p className="mb-1 font-mono text-xs text-muted-foreground">Status</p>
                  <Segmented value={dProgress} onChange={setDProgress} options={[["", "All"], ["DONE", "Done"], ["IN_PROGRESS", "In progress"], ["NEVER", "Never"]]} />
                </div>
                <div>
                  <p className="mb-1 font-mono text-xs text-muted-foreground">Difficulty</p>
                  <Segmented value={dDiff} onChange={setDDiff} options={[["", "All"], ["EASY", "Easy"], ["MEDIUM", "Medium"], ["HARD", "Hard"]]} />
                </div>
                <div className="flex items-center justify-between">
                  <button className="text-xs text-muted-foreground hover:text-foreground" onClick={() => { setDProgress(""); setDDiff(""); }}>Reset</button>
                  <Button size="sm" onClick={() => { set({ progress: dProgress || null, difficulty: dDiff || null }); setOpen(false); }}>Apply</Button>
                </div>
              </PopoverContent>
            </Popover>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="w-full text-xs text-muted-foreground">Tags</span>
            <button className={chip(tags.length === 0)} onClick={() => set({ tags: null })}>All Tags</button>
            {TAGS.map((t) => {
              const on = tags.includes(t);
              const next = on ? tags.filter((x) => x !== t) : [...tags, t];
              return <button key={t} className={chip(on)} onClick={() => set({ tags: next.join(",") || null })}>{t}{on && <span className="ml-1 text-[10px]">{tags.length}</span>}</button>;
            })}
          </div>
        </div>

        <div className="relative">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input ref={searchRef} key={q} defaultValue={q} className="bg-card pl-9" aria-label="Tìm trong danh sách"
            placeholder="Search by title, #slug, or keyword (e.g. dynamic programming)...  (nhấn / để tìm)"
            onKeyDown={(e) => { if (e.key === "Enter") set({ q: e.currentTarget.value || null }); }} />
        </div>

        {(q || difficulty || progress || tags.length > 0) && (
          <div className="flex flex-wrap items-center gap-2 text-xs" aria-label="Bộ lọc đang áp dụng">
            <span className="text-muted-foreground">Đang lọc:</span>
            {q && <Removable label={`Từ khoá: ${q}`} onRemove={() => set({ q: null })} />}
            {difficulty && <Removable label={`Độ khó: ${difficulty}`} onRemove={() => set({ difficulty: null })} />}
            {progress && <Removable label={`Trạng thái: ${progress}`} onRemove={() => set({ progress: null })} />}
            {tags.map((t) => <Removable key={t} label={t} onRemove={() => set({ tags: tags.filter((x) => x !== t).join(",") || null })} />)}
            <button className="text-muted-foreground underline" onClick={() => set({ q: null, difficulty: null, progress: null, tags: null })}>Xoá tất cả</button>
          </div>
        )}

        <div className="overflow-hidden rounded-lg border bg-card">
          <div className="grid grid-cols-[72px_1fr_110px] px-4 py-3 font-mono text-xs text-muted-foreground">
            <span>Status</span><span>Problem title</span><span>Difficulty</span>
          </div>
          {error && (
            <div role="alert" className="border-t p-6 text-sm text-destructive">
              {error} <button className="ml-2 underline" onClick={() => set({ page: String(page) })}>Thử lại</button>
            </div>
          )}
          {loading && !res && !error && Array.from({ length: 5 }, (_, i) => <div key={i} className="h-[74px] animate-pulse border-t bg-muted/30" />)}
          {res?.content.map((p) => (
            <Link key={p.id} href={`/problems/${p.slug}`} className="grid grid-cols-[72px_1fr_110px] items-center border-t px-4 py-4 hover:bg-accent/40">
              <span aria-label={p.progress === "DONE" ? "Đã làm xong" : "Chưa xong"}>
                {p.progress === "DONE"
                  ? <span className="grid h-5 w-5 place-items-center rounded-full bg-primary text-primary-foreground"><Check className="h-3 w-3" /></span>
                  : <Circle className={p.progress === "IN_PROGRESS" ? "h-5 w-5 text-amber-500" : "h-5 w-5 text-muted-foreground"} />}
              </span>
              <span className="min-w-0">
                <span className="font-semibold">{p.id}. {p.title}</span>
                <span className="mt-1 flex flex-wrap gap-1">{p.tags.map((t) => <Badge key={t} variant="secondary" className="font-mono text-[10px] font-normal">{t}</Badge>)}</span>
              </span>
              <span><DifficultyBadge value={p.difficulty} /></span>
            </Link>
          ))}
          {res && res.content.length === 0 && (
            <div className="border-t p-8 text-center text-sm text-muted-foreground">
              Không có bài nào khớp bộ lọc. <button className="underline" onClick={() => set({ q: null, difficulty: null, progress: null, tags: null })}>Xoá bộ lọc</button>
            </div>
          )}
          {res && (
            <div className="flex flex-wrap items-center justify-between gap-2 border-t px-4 py-3 font-mono text-xs text-muted-foreground">
              <span>Showing {res.totalElements === 0 ? 0 : res.number * res.size + 1}-{res.number * res.size + res.content.length} of {res.totalElements} Problems</span>
              <Pagination page={page} totalPages={res.totalPages} onChange={(p) => set({ page: String(p) })} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
