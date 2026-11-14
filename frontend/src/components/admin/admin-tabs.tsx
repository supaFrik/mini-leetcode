"use client";
import Link from "next/link";
import { useUser } from "@/hooks/use-user";
import { cn } from "@/lib/utils";

const TABS = [
  { key: "problems", label: "Problems", href: "/admin/problems", adminOnly: false },
  { key: "contests", label: "Contests", href: "/admin/contests", adminOnly: false },
  { key: "users", label: "Users", href: "/admin/users", adminOnly: true },
] as const;

/** Điều hướng giữa các khu quản trị. Tab Users chỉ hiện với ADMIN (server cũng chặn 403). */
export function AdminTabs({ current }: { current: (typeof TABS)[number]["key"] }) {
  const { user } = useUser();
  return (
    <nav aria-label="Admin sections" className="flex gap-1">
      {TABS.filter((t) => !t.adminOnly || user?.role === "ADMIN").map((t) => (
        <Link
          key={t.key}
          href={t.href}
          aria-current={t.key === current ? "page" : undefined}
          className={cn(
            "eyebrow rounded-full px-2.5 py-0.5 transition-colors hover:text-ink",
            t.key === current ? "bg-ink text-white hover:text-white" : "text-mute"
          )}
        >
          {t.label}
        </Link>
      ))}
    </nav>
  );
}
