"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Bell, Moon, Sun, Search, SlidersHorizontal } from "lucide-react";
import { useTheme } from "next-themes";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { decodeUser, getToken } from "@/lib/auth";

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { setTheme, resolvedTheme } = useTheme();
  const [q, setQ] = useState("");
  const [user, setUser] = useState<ReturnType<typeof decodeUser>>(null);

  useEffect(() => setUser(decodeUser(getToken())), []);   // đọc localStorage chỉ được làm trong effect

  const inExam = pathname.startsWith("/exam");
  const tab = (active: boolean) =>
    cn("rounded-md px-4 py-1.5 text-sm font-medium transition-colors",
       active ? "bg-primary/15 text-foreground" : "text-muted-foreground hover:text-foreground");

  return (
    <header className="flex h-14 items-center gap-4 border-b bg-background px-4">
      <Link href="/problems" className="flex items-center gap-2 font-semibold">
        {/* thay bằng <Image src="/logo.svg" .../> khi export logo từ Figma */}
        <span className="grid h-8 w-8 place-items-center rounded-full bg-primary text-primary-foreground text-xs">mini</span>
        Mini LeetCode
      </Link>

      <nav className="flex rounded-lg bg-secondary p-1">
        <Link href="/problems" className={tab(!inExam)}>Practice</Link>
        <Link href="/exam" className={tab(inExam)}>Exam</Link>
      </nav>

      {!inExam && (
        <form className="relative mx-auto w-full max-w-xl"
          onSubmit={(e) => { e.preventDefault(); router.push(`/problems?q=${encodeURIComponent(q)}`); }}>
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input value={q} onChange={(e) => setQ(e.target.value)} className="bg-card pl-9 pr-9"
            placeholder="Search by title, #slug, or keyword (e.g. dynamic programming)..." />
          <SlidersHorizontal className="absolute right-3 top-2.5 h-4 w-4 text-muted-foreground" />
        </form>
      )}

      <div className="ml-auto flex items-center gap-2">
        <Button variant="ghost" size="icon"><Bell className="h-5 w-5" /></Button>
        <Button variant="ghost" size="icon" onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}>
          <Moon className="hidden h-5 w-5 dark:block" /><Sun className="h-5 w-5 dark:hidden" />
        </Button>
        <div className="flex items-center gap-2 rounded-lg bg-card px-2 py-1">
          <Avatar className="h-8 w-8"><AvatarFallback>{user?.username?.[0]?.toUpperCase() ?? "?"}</AvatarFallback></Avatar>
          <div className="text-xs leading-tight">
            <div>{user?.username ?? "Guest"}</div>
            <div className="font-mono text-primary">#{user?.role ?? "—"}</div>
          </div>
        </div>
      </div>
    </header>
  );
}