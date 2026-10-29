"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { Bell, LogOut, Moon, Search, SlidersHorizontal, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { useUser } from "@/hooks/use-user";
import { clearTokens } from "@/lib/auth";

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { setTheme, resolvedTheme } = useTheme();
  const { user } = useUser();
  const [q, setQ] = useState("");

  const inExam = pathname.startsWith("/exam");
  const inExamWorkspace = /^\/exam\/[^/]+\/problems\//.test(pathname);
  const tab = (active: boolean) =>
    cn("rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
      active ? "bg-primary/15 text-foreground" : "text-muted-foreground hover:text-foreground");

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b bg-background px-3 sm:gap-4 sm:px-4">
      <Link href="/" className="flex items-center gap-2 whitespace-nowrap font-semibold" aria-label="Trang chủ Mini LeetCode">
        <span className="grid h-8 w-8 place-items-center rounded-full bg-primary text-[10px] text-primary-foreground">mini</span>
        <span className="hidden sm:inline">Mini LeetCode</span>
      </Link>

      <nav className="flex rounded-lg bg-secondary p-1">
        {/* Đang trong phòng thi thì khoá tab Practice để không rời bài thi */}
        {inExamWorkspace
          ? <span className={cn(tab(false), "cursor-not-allowed opacity-50")} title="Không thể rời phòng thi khi đang làm bài">Practice</span>
          : <Link href="/problems" className={tab(pathname.startsWith("/problems"))}>Practice</Link>}
        <Link href="/exam" className={tab(inExam)}>Exam</Link>
      </nav>

      {!inExamWorkspace && (
        <form className="relative mx-auto hidden w-full max-w-xl md:block"
          onSubmit={(e) => { e.preventDefault(); router.push(q ? `/problems?q=${encodeURIComponent(q)}` : "/problems"); }}>
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} className="bg-card pl-9 pr-9"
            placeholder="Search by title, #slug, or keyword (e.g. dynamic programming)..." aria-label="Tìm bài tập" />
          <SlidersHorizontal className="absolute right-3 top-2.5 h-4 w-4 text-muted-foreground" />
        </form>
      )}

      <div className="ml-auto flex items-center gap-1">
        <Link href="/submissions" className="mr-2 hidden whitespace-nowrap text-sm text-muted-foreground hover:text-foreground xl:inline">Bài đã nộp</Link>
        <Button variant="ghost" size="icon" className="hidden sm:inline-flex" aria-label="Thông báo"><Bell className="h-5 w-5" /></Button>
        <Button variant="ghost" size="icon" aria-label="Đổi giao diện sáng/tối" onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}>
          <Moon className="hidden h-5 w-5 dark:block" /><Sun className="h-5 w-5 dark:hidden" />
        </Button>
        <div className="ml-1 flex items-center gap-2 rounded-lg bg-card px-2 py-1">
          <Avatar className="h-8 w-8"><AvatarFallback>{user?.username?.[0]?.toUpperCase() ?? "?"}</AvatarFallback></Avatar>
          <div className="hidden text-xs leading-tight sm:block">
            <div>{user?.username ?? "Guest"}</div>
            <div className="font-mono text-primary">#{user?.role ?? "—"}</div>
          </div>
          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Đăng xuất" onClick={() => { clearTokens(); router.push("/login"); }}>
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </header>
  );
}
