"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { FileText, LayoutDashboard, Medal, Settings, Trophy, Users, type LucideIcon } from "lucide-react";
import { useUser } from "@/hooks/use-user";
import { cn } from "@/lib/utils";
import type { Role } from "@/types/api";

type Item = { href: string; label: string; adminOnly?: boolean };
type Group = Item & { icon: LucideIcon; tabs: Item[]; exact?: boolean };

// Mục chỉ ADMIN khớp với SecurityConfig: tags, pages, stats, users.
const GROUPS: Group[] = [
  { href: "/admin", label: "Overview", icon: LayoutDashboard, adminOnly: true, exact: true, tabs: [] },
  {
    href: "/admin/users", label: "Users", icon: Users, adminOnly: true,
    tabs: [{ href: "/admin/users", label: "Users" }, { href: "/admin/roles", label: "Roles" }],
  },
  {
    href: "/admin/problems", label: "Content", icon: FileText,
    tabs: [
      { href: "/admin/problems", label: "Problems" },
      { href: "/admin/study-plans", label: "Study plans" },
      { href: "/admin/topics", label: "Topics" },
      { href: "/admin/tags", label: "Tags", adminOnly: true },
    ],
  },
  { href: "/admin/contests", label: "Contests", icon: Trophy, tabs: [{ href: "/admin/contests", label: "Contests" }] },
  { href: "/admin/ranking", label: "Ranking", icon: Medal, tabs: [{ href: "/admin/ranking", label: "Ranking" }] },
  { href: "/admin/policies", label: "Settings", icon: Settings, adminOnly: true, tabs: [{ href: "/admin/policies", label: "Policies" }] },
];

const visible = <T extends Item>(items: T[], role?: Role) => items.filter((i) => !i.adminOnly || role === "ADMIN");
const under = (pathname: string, href: string) => pathname === href || pathname.startsWith(`${href}/`);
const groupOf = (pathname: string) =>
  GROUPS.find((g) => (g.exact ? pathname === g.href : g.tabs.some((t) => under(pathname, t.href))));

/** Sidebar trái của khu quản trị. */
export function AdminSidebar() {
  const pathname = usePathname();
  const { user } = useUser();
  const current = groupOf(pathname);
  return (
    <aside className="w-56 shrink-0 border-r border-border bg-card">
      <nav aria-label="Admin" className="sticky top-0 space-y-0.5 px-3 py-5">
        <p className="eyebrow px-3 pb-2">Admin</p>
        {visible(GROUPS, user?.role).map((g) => {
          const active = g === current;
          const Icon = g.icon;
          return (
            <Link
              key={g.href}
              href={g.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex items-center gap-2.5 rounded-sm px-3 py-2 text-body-md transition-colors",
                active ? "bg-hairline-soft font-medium text-ink" : "text-mute hover:bg-hairline-soft hover:text-ink"
              )}
            >
              <Icon className="h-4 w-4" aria-hidden />
              {g.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}

/** Tab con trong nhóm hiện tại của sidebar (vd. Content: Problems / Study plans / Topics / Tags). */
export function AdminNav() {
  const pathname = usePathname();
  const { user } = useUser();
  const tabs = visible(groupOf(pathname)?.tabs ?? [], user?.role);
  if (tabs.length < 2) return null;
  return (
    <nav aria-label="Admin sections" className="flex gap-1 border-b border-border">
      {tabs.map((s) => {
        const active = under(pathname, s.href);
        return (
          <Link
            key={s.href}
            href={s.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "-mb-px border-b-2 px-3 py-2 text-body-md transition-colors",
              active ? "border-ink text-ink" : "border-transparent text-mute hover:text-ink"
            )}
          >
            {s.label}
          </Link>
        );
      })}
    </nav>
  );
}
