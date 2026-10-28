"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { CheckCircle2, Circle, Filter, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";
import { useUrlState } from "@/hooks/use-url-state";
import { fetchProblems } from "@/lib/problems";
import { DifficultyBadge } from "./difficulty-badge";
import { Pagination } from "./pagination";
import type { Language, Page, ProblemSummary } from "@/types/api";

const LANGS = ["Python", "Java", "C++"];                      // chỉ những gì backend hỗ trợ
const SUPPORTED_LANGUAGES: { value: Language; label: string }[] = [
  { value: "JAVA", label: "Java" },
  { value: "PYTHON", label: "Python" },
  { value: "CPP", label: "C++" },
];
const TAGS = ["Array", "Two Pointers", "Hash Table", "Math", "Dynamic Programming", "Sorting", "Graph"];

export function ProblemsView() {
  const { sp, set } = useUrlState();
  const page = Number(sp.get("page") ?? 1);
  const q = sp.get("q") ?? "";
  const difficulty = sp.get("difficulty") ?? "";
  const status = sp.get("status") ?? "";
  const tags = sp.get("tags")?.split(",").filter(Boolean) ?? [];
  const lang = sp.get("lang") ?? "";

  const [res, setRes] = useState<Page<ProblemSummary> | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    fetchProblems({ page, q, difficulty, status, tags })
      .then((r) => !ignore && setRes(r))
      .finally(() => !ignore && setLoading(false));
    return () => { ignore = true; };           // bỏ kết quả cũ nếu người dùng đổi lọc quá nhanh
  }, [page, q, difficulty, status, sp.get("tags")]);

  // trạng thái nháp của popover: chỉ áp dụng khi bấm Apply
  const [open, setOpen] = useState(false);
  const [dStatus, setDStatus] = useState(status);
  const [dDiff, setDDiff] = useState(difficulty);
  const activeCount = (status ? 1 : 0) + (difficulty ? 1 : 0);

  const chip = (active: boolean) =>
    cn("rounded-md border px-3 py-1 text-xs", active ? "border-primary bg-primary text-primary-foreground" : "bg-card hover:bg-accent");

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      {/* Hàng chip Language + Tags + nút Filter (Figma 5) */}
      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="w-full text-xs text-muted-foreground">Language</span>
          <button className={chip(!lang)} onClick={() => set({ lang: null })}>All languages</button>
          {LANGS.map((l) => <button key={l} className={chip(lang === l)} onClick={() => set({ lang: l })}>{l}</button>)}
          <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
              <Button size="sm" className="ml-auto gap-1"><Filter className="h-3.5 w-3.5" />Filter
                {activeCount > 0 && <Badge className="ml-1 h-4 px-1 text-[10px]">{activeCount}</Badge>}
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-72 space-y-4">
              <div>
                <p className="mb-1 font-mono text-xs text-muted-foreground">STATUS</p>
                <ToggleGroup type="single" value={dStatus} onValueChange={setDStatus} className="justify-start">
                  <ToggleGroupItem value="">All</ToggleGroupItem>
                  <ToggleGroupItem value="DONE">Done</ToggleGroupItem>
                  <ToggleGroupItem value="IN_PROGRESS">In progress</ToggleGroupItem>
                  <ToggleGroupItem value="NEVER">Never</ToggleGroupItem>
                </ToggleGroup>
              </div>
              <div>
                <p className="mb-1 font-mono text-xs text-muted-foreground">DIFFICULTY</p>
                <ToggleGroup type="single" value={dDiff} onValueChange={setDDiff} className="justify-start">
                  <ToggleGroupItem value="">All</ToggleGroupItem>
                  <ToggleGroupItem value="EASY">Easy</ToggleGroupItem>
                  <ToggleGroupItem value="MEDIUM">Medium</ToggleGroupItem>
                  <ToggleGroupItem value="HARD">Hard</ToggleGroupItem>
                </ToggleGroup>
              </div>
              <div className="flex items-center justify-between">
                <button className="text-xs text-muted-foreground" onClick={() => { setDStatus(""); setDDiff(""); }}>Reset</button>
                <Button size="sm" onClick={() => { set({ status: dStatus || null, difficulty: dDiff || null }); setOpen(false); }}>Apply</Button>
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
            return <button key={t} className={chip(on)} onClick={() => set({ tags: next.join(",") || null })}>{t}</button>;
          })}
        </div>
      </div>

      {/* Ô search trong trang */}
      <div className="relative">
        <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input defaultValue={q} className="bg-card pl-9" placeholder="Search by title, #slug, or keyword (e.g. dynamic programming)..."
          onKeyDown={(e) => e.key === "Enter" && set({ q: e.currentTarget.value || null })} />
      </div>

      {/* Bảng */}
      <div className="overflow-hidden rounded-lg border bg-card">
        <div className="grid grid-cols-[36px_minmax(0,1fr)_96px_78px] gap-2 px-3 py-3 font-mono text-xs text-muted-foreground sm:grid-cols-[48px_minmax(0,1fr)_140px_100px] sm:gap-3 sm:px-4 md:grid-cols-[64px_minmax(0,1fr)_180px_120px]">
          <span>STATUS</span><span>PROBLEM TITLE</span><span>LANGUAGES</span><span>DIFFICULTY</span>
        </div>
        {loading && !res && <p className="p-6 text-sm text-muted-foreground">Loading…</p>}
        {res?.content.map((p) => (
          <Link key={p.id} href={`/problems/${p.slug}`}
            className="grid grid-cols-[36px_minmax(0,1fr)_96px_78px] items-center gap-2 border-t px-3 py-4 hover:bg-accent/40 sm:grid-cols-[48px_minmax(0,1fr)_140px_100px] sm:gap-3 sm:px-4 md:grid-cols-[64px_minmax(0,1fr)_180px_120px]">
            <span>{p.status === "DONE" ? <CheckCircle2 className="h-5 w-5 text-primary" /> : <Circle className="h-5 w-5 text-muted-foreground" />}</span>
            <span>
              <span className="font-semibold">{p.id}. {p.title}</span>
              <span className="mt-1 flex gap-1">{p.tags.map((t) => <Badge key={t} variant="secondary" className="font-mono text-[10px]">{t}</Badge>)}</span>
            </span>
            <span className="flex flex-wrap gap-1">
              {SUPPORTED_LANGUAGES.map(({ value, label }) => (
                <Badge key={value} variant="outline" className="font-mono text-[10px]">{label}</Badge>
              ))}
            </span>
            <span><DifficultyBadge value={p.difficulty} /></span>
          </Link>
        ))}
        {res && res.content.length === 0 && <p className="p-6 text-sm text-muted-foreground">Không có bài nào khớp bộ lọc.</p>}

        {res && (
          <div className="flex items-center justify-between border-t px-4 py-3 font-mono text-xs text-muted-foreground">
            <span>Showing {res.totalElements === 0 ? 0 : res.number * res.size + 1}-{res.number * res.size + res.content.length} of {res.totalElements} Problems</span>
            <Pagination page={page} totalPages={res.totalPages} onChange={(p) => set({ page: String(p) })} />
          </div>
        )}
      </div>
    </div>
  );
}