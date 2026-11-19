"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BookOpen, ClipboardList, FileText, FolderTree, GraduationCap, LayoutDashboard, Map, Medal, Megaphone, Settings,
  ShieldAlert, Tag, Trophy, UserCog, Users, type LucideIcon,
} from "lucide-react";
import { useUser } from "@/hooks/use-user";
import { cn } from "@/lib/utils";

type Item = { href: string; label: string; icon: LucideIcon; adminOnly?: boolean };
type Section = { heading?: string; items: Item[] };

// Mục chỉ ADMIN khớp với SecurityConfig: stats, users, tags, pages, settings.
const SECTIONS: Section[] = [
  { items: [{ href: "/admin", label: "Overview", icon: LayoutDashboard, adminOnly: true }] },
  {
    heading: "People",
    items: [
      { href: "/admin/users", label: "Users", icon: Users, adminOnly: true },
      { href: "/admin/roles", label: "Roles", icon: UserCog, adminOnly: true },
    ],
  },
  {
    heading: "Learning",
    items: [
      { href: "/admin/problems", label: "Problems", icon: BookOpen },
      { href: "/admin/study-plans", label: "Study plans", icon: Map },
      { href: "/admin/topics", label: "Topics", icon: FolderTree },
      { href: "/admin/tags", label: "Tags", icon: Tag, adminOnly: true },
    ],
  },
  {
    heading: "Assessment",
    items: [
      { href: "/admin/contests", label: "Contests", icon: Trophy },
      { href: "/admin/submissions", label: "Submissions", icon: ClipboardList },
      { href: "/admin/reports", label: "Reports", icon: ShieldAlert },
    ],
  },
  { heading: "Communication", items: [{ href: "/admin/announcements", label: "Announcements", icon: Megaphone }] },
  { heading: "Insights", items: [{ href: "/admin/ranking", label: "Ranking", icon: Medal }] },
  {
    heading: "System",
    items: [
      { href: "/admin/policies", label: "Policies", icon: FileText, adminOnly: true },
      { href: "/admin/settings", label: "Settings", icon: Settings, adminOnly: true },
    ],
  },
];

// Giảng viên thấy menu "Teaching" riêng; các trang /admin/* tự lọc theo nội dung của họ.
const TEACHING: Section[] = [
  { items: [{ href: "/teaching", label: "Dashboard", icon: LayoutDashboard }] },
  {
    heading: "My content",
    items: [
      { href: "/admin/problems", label: "My problems", icon: BookOpen },
      { href: "/admin/study-plans", label: "My study plans", icon: Map },
      { href: "/admin/contests", label: "My contests", icon: Trophy },
    ],
  },
  {
    heading: "Learners",
    items: [
      { href: "/teaching/progress", label: "Student progress", icon: GraduationCap },
      { href: "/admin/submissions", label: "Submissions", icon: ClipboardList },
      { href: "/admin/announcements", label: "Announcements", icon: Megaphone },
    ],
  },
];

/** Chỉ một mục sáng: href dài nhất khớp pathname ("/admin", "/teaching" chỉ khớp đúng chính nó). */
export function activeHref(pathname: string, sections: Section[] = SECTIONS): string | undefined {
  return sections.flatMap((s) => s.items).map((i) => i.href)
    .filter((h) => pathname === h || (h !== "/admin" && h !== "/teaching" && pathname.startsWith(`${h}/`)))
    .sort((a, b) => b.length - a.length)[0];
}

/** Sidebar trái: ADMIN thấy khu quản trị đầy đủ, giảng viên thấy menu Teaching. */
export function AdminSidebar() {
  const pathname = usePathname();
  const { user } = useUser();
  const isAdmin = user?.role === "ADMIN";
  const sections = isAdmin ? SECTIONS : TEACHING;
  const active = activeHref(pathname, sections);
  return (
    <aside className="w-56 shrink-0 border-r border-border bg-card">
      <nav aria-label={isAdmin ? "Admin" : "Teaching"} className="sticky top-0 space-y-4 px-3 py-5">
        {sections.map((section) => {
          const items = section.items.filter((i) => !i.adminOnly || isAdmin);
          if (items.length === 0) return null;
          return (
            <div key={section.heading ?? "top"} className="space-y-0.5">
              {section.heading && <p className="eyebrow px-3 pb-1">{section.heading}</p>}
              {items.map((item) => {
                const Icon = item.icon;
                const on = item.href === active;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={on ? "page" : undefined}
                    className={cn(
                      "flex items-center gap-2.5 rounded-sm px-3 py-2 text-body-md transition-colors",
                      on ? "bg-hairline-soft font-medium text-ink" : "text-mute hover:bg-hairline-soft hover:text-ink"
                    )}
                  >
                    <Icon className="h-4 w-4" aria-hidden />
                    {item.label}
                    {item.adminOnly && (
                      <span className="ml-auto rounded-sm bg-ink px-1 py-px text-[10px] font-semibold uppercase tracking-wide text-white"
                        title="Admin only">Admin only</span>
                    )}
                  </Link>
                );
              })}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
