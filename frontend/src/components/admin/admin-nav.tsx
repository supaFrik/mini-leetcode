"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useUser } from "@/hooks/use-user";
import { cn } from "@/lib/utils";

const SECTIONS = [
  { href: "/admin/problems", label: "Problems" },
  { href: "/admin/contests", label: "Contests" },
  { href: "/admin/study-plans", label: "Study plans" },
  { href: "/admin/topics", label: "Topics" },
];
// Chỉ ADMIN (backend cũng chặn: /api/v1/admin/tags).
const ADMIN_ONLY = [
  { href: "/admin/tags", label: "Tags" },
];

/** Chuyển giữa các trang quản trị. */
export function AdminNav() {
  const pathname = usePathname();
  const { user } = useUser();
  const sections = user?.role === "ADMIN" ? [...SECTIONS, ...ADMIN_ONLY] : SECTIONS;
  return (
    <nav aria-label="Admin sections" className="flex gap-1 border-b border-border">
      {sections.map((s) => {
        const active = pathname.startsWith(s.href);
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
