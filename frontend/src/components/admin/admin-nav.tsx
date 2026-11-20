"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  BookOpen, ExternalLink, FileText, FolderTree, GraduationCap, LayoutDashboard, LogOut, Map, Medal, Megaphone, Settings,
  ShieldAlert, Tag, Trophy, Users, type LucideIcon,
} from "lucide-react";
import { useUser } from "@/hooks/use-user";
import { clearTokens, setViewingSite } from "@/lib/auth";
import { cn } from "@/lib/utils";

type Item = { href: string; label: string; icon: LucideIcon };
type Section = { heading?: string; items: Item[] };

// Menu đầy đủ của ADMIN; giảng viên dùng TEACHING bên dưới.
const SECTIONS: Section[] = [
  { items: [{ href: "/admin", label: "Overview", icon: LayoutDashboard }] },
  { heading: "People", items: [{ href: "/admin/users", label: "Users & roles", icon: Users }] },
  {
    heading: "Learning",
    items: [
      { href: "/admin/problems", label: "Problems", icon: BookOpen },
      { href: "/admin/study-plans", label: "Study plans", icon: Map },
      { href: "/admin/topics", label: "Topics", icon: FolderTree },
      { href: "/admin/tags", label: "Tags", icon: Tag },
    ],
  },
  {
    heading: "Assessment",
    items: [
      { href: "/admin/contests", label: "Contests", icon: Trophy },
      { href: "/admin/reports", label: "Reports", icon: ShieldAlert },
    ],
  },
  { heading: "Communication", items: [{ href: "/admin/announcements", label: "Announcements", icon: Megaphone }] },
  { heading: "Insights", items: [{ href: "/admin/ranking", label: "Ranking", icon: Medal }] },
  {
    heading: "System",
    items: [
      { href: "/admin/policies", label: "Policies", icon: FileText },
      { href: "/admin/settings", label: "Settings", icon: Settings },
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
      { href: "/admin/reports", label: "Reports", icon: ShieldAlert },
    ],
  },
  {
    heading: "Learners",
    items: [
      { href: "/teaching/progress", label: "Student progress", icon: GraduationCap },
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
  const router = useRouter();
  const signOut = () => { clearTokens(); router.push("/login"); };
  return (
    <aside className="flex h-full w-56 shrink-0 flex-col border-r border-border bg-card">
      <div className="flex items-center justify-between gap-2 px-6 pb-2 pt-5">
        <span className="text-body-md font-semibold text-ink">{isAdmin ? "Admin" : "Teaching"}</span>
        <Link href="/problems" onClick={() => setViewingSite(true)} className="flex items-center gap-1 text-body-sm text-mute hover:text-ink">
          View site <ExternalLink className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </div>
      <nav aria-label={isAdmin ? "Admin" : "Teaching"} className="min-h-0 flex-1 space-y-4 overflow-auto px-3 py-3">
        {sections.map((section) => {
          const items = section.items;
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
                  </Link>
                );
              })}
            </div>
          );
        })}
      </nav>
      <div className="flex items-center justify-between gap-2 border-t border-border px-6 py-3 text-body-sm">
        <span className="truncate text-mute">{user?.username}</span>
        <button type="button" onClick={signOut} className="flex items-center gap-1 text-mute hover:text-ink">
          <LogOut className="h-3.5 w-3.5" aria-hidden /> Sign out
        </button>
      </div>
    </aside>
  );
}
