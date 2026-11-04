"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bell, Compass, GraduationCap, History, List, LogOut, Trophy, type LucideIcon } from "lucide-react";
import { useUser } from "@/hooks/use-user";
import { clearTokens } from "@/lib/auth";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import theme from "./plex-theme.module.css";
import s from "./site-header.module.css";

interface NavEntry {
  label: string;
  icon?: LucideIcon;
  href?: string;
  active?: boolean;
  /** Lý do không bấm được (hiện ở tooltip). */
  disabledReason?: string;
}

function NavItem({ label, icon: Icon, href, active, disabledReason }: NavEntry) {
  const body = (
    <>
      {Icon && <Icon size={16} aria-hidden="true" />}
      {label}
    </>
  );
  if (!href || disabledReason) {
    return (
      <span className={s.navItem} aria-disabled="true" title={disabledReason}>
        {body}
      </span>
    );
  }
  return (
    <Link href={href} className={s.navItem} aria-current={active ? "page" : undefined}>
      {body}
    </Link>
  );
}

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, ready } = useUser();

  const inExamWorkspace = /^\/exam\/[^\/]+\/problems\//.test(pathname);
  const isStaff = user?.role === "ADMIN" || user?.role === "INSTRUCTOR";
  const lockReason = inExamWorkspace ? "Cannot leave the exam room while in progress" : undefined;

  const entries: NavEntry[] = [
    { label: "Problems", icon: List, href: "/problems", active: pathname.startsWith("/problems"), disabledReason: lockReason },
    { label: "Contest", icon: Trophy, href: "/exam", active: pathname.startsWith("/exam") },
    { label: "Explore", icon: Compass, href: "/explore", active: pathname.startsWith("/explore") },
    { label: "Study Plan", icon: GraduationCap, href: "/study-plan", active: pathname.startsWith("/study-plan") },
    { label: "Submissions", icon: History, href: "/submissions", active: pathname.startsWith("/submissions") },
  ];
  if (isStaff) entries.push({ label: "Admin", href: "/admin/problems", active: pathname.startsWith("/admin") });

  const signOut = () => {
    clearTokens();
    router.push("/login");
  };

  return (
    <header className={cn(theme.theme, s.header)}>
      <div className={s.bar}>
        <Link href="/" className={s.brand} aria-label="Mini LeetCode Home">
          <span aria-hidden="true">&lt;/&gt;</span>
          Mini LeetCode
        </Link>

        <nav aria-label="Main" className={s.nav}>
          {entries.map((e) => (
            <NavItem key={e.label} {...e} />
          ))}
        </nav>

        <div className={s.user}>
          {!ready ? null : user ? (
            <>
              <button className={s.ghost} type="button" aria-label="Notifications">
                <Bell size={20} aria-hidden="true" />
              </button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className={cn(s.ghost, s.account)} type="button" aria-label={`Account: ${user.username}`}>
                    <span className={s.avatar} aria-hidden="true">
                      {user.username?.[0]?.toUpperCase() ?? "?"}
                    </span>
                    <span>{user.username}</span>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="min-w-44">
                  <DropdownMenuLabel>
                    <div>{user.username}</div>
                    <div className={s.menuRole}>{user.role ?? "USER"}</div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={signOut}>
                    <LogOut className="mr-2 h-4 w-4" aria-hidden="true" />
                    Sign out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <>
              <Link href="/login" className={s.ghost} style={{ padding: "0 12px" }}>
                Log in
              </Link>
              <Link href="/register" className={s.solid}>
                Register
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
