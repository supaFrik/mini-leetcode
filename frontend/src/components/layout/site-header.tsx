"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { Bell, LogOut, Search } from "lucide-react";
import { useUser } from "@/hooks/use-user";
import { clearTokens } from "@/lib/auth";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { user } = useUser();
  const [q, setQ] = useState("");

  const inExam = pathname.startsWith("/exam");
  const inExamWorkspace = /^\/exam\/[^\/]+\/problems\//.test(pathname);
  const onProblems = pathname.startsWith("/problems");
  const tab = (active: boolean) =>
    cn(
      "rounded-full px-3 py-1.5 text-body-md font-medium transition-colors",
      active ? "bg-hairline-soft text-ink" : "text-body hover:bg-hairline-soft hover:text-ink"
    );

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-border bg-background px-3 text-body sm:gap-4 sm:px-5">
      <Link href="/" className="flex items-center gap-2 whitespace-nowrap" aria-label="Mini LeetCode Home">
        <span className="grid h-7 w-7 place-items-center rounded-md bg-ink font-mono text-[11px] font-bold text-white">
          &lt;/&gt;
        </span>
        <span className="hidden text-[15px] font-semibold tracking-tight text-ink sm:inline">Mini LeetCode</span>
      </Link>

      <nav aria-label="Primary" className="flex items-center gap-0.5 sm:gap-1">
        {inExamWorkspace ? (
          <span className={cn(tab(false), "cursor-not-allowed opacity-50")} title="Cannot leave the exam room while in progress">
            Problems
          </span>
        ) : (
          <Link href="/problems" aria-current={onProblems ? "page" : undefined} className={tab(onProblems)}>
            Problems
          </Link>
        )}
        <Link href="/exam" aria-current={inExam ? "page" : undefined} className={tab(inExam)}>
          Contest
        </Link>
      </nav>

      {!inExamWorkspace && (
        <form
          className="relative mx-auto hidden w-full max-w-xl md:block"
          onSubmit={(e) => {
            e.preventDefault();
            router.push(q ? `/problems?q=${encodeURIComponent(q)}` : "/problems");
          }}
        >
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="h-9 rounded-pill border-border bg-card pl-9 pr-3 text-body-md text-ink placeholder:text-faint"
            placeholder="Search"
            aria-label="Search problems"
          />
        </form>
      )}

      <div className="ml-auto flex items-center gap-1.5">
        <Link href="/submissions" className="mr-1 hidden whitespace-nowrap text-body-md text-body transition-colors hover:text-ink xl:inline">
          Submissions
        </Link>
        <Button variant="ghost" size="icon" className="text-body hover:text-ink" aria-label="Notifications">
          <span className="relative">
            <Bell className="h-4 w-4" />
            <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-destructive" />
          </span>
        </Button>
        <div className="ml-1 flex items-center gap-2 rounded-full border border-border bg-card py-1 pl-1 pr-1">
          <Avatar className="h-7 w-7">
            <AvatarFallback className="bg-hairline-soft text-[11px] font-medium text-ink">
              {user?.username?.[0]?.toUpperCase() ?? "?"}
            </AvatarFallback>
          </Avatar>
          <div className="hidden leading-tight sm:block">
            <div className="text-body-md text-ink">{user?.username ?? "Guest"}</div>
            <div className="font-mono text-[10px] text-mute">{user?.role ?? "—"}</div>
          </div>
          <Button
            variant="ghost"
            size="icon-sm"
            className="text-body hover:text-ink"
            aria-label="Sign out"
            onClick={() => {
              clearTokens();
              router.push("/login");
            }}
          >
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
        <Link
          href="/problems"
          className="ml-1 hidden rounded-sm bg-ink px-3.5 py-1.5 text-body-md font-medium text-white transition-colors hover:bg-ink/90 sm:inline-block"
        >
          Start Solving
        </Link>
      </div>
    </header>
  );
}
