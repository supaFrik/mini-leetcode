import Link from "next/link";
import { AdminStudyPlansView } from "@/components/admin/admin-study-plans";
import { AdminTopicsView } from "@/components/admin/admin-topics";
import { AdminGuard } from "@/components/layout/role-guard";
import { cn } from "@/lib/utils";

const TABS = [
  { key: "plans", label: "Plans", href: "/admin/study-plans" },
  { key: "lessons", label: "Lesson library", href: "/admin/study-plans?tab=lessons" },
];

export default function AdminStudyPlansPage({ searchParams }: { searchParams: { tab?: string } }) {
  const tab = searchParams.tab === "lessons" ? "lessons" : "plans";
  return (
    <AdminGuard>
      <nav aria-label="Study plans sections" className="mx-auto flex w-full max-w-container gap-5 px-4 pt-6 sm:px-6 lg:px-8">
        {TABS.map((t) => (
          <Link key={t.key} href={t.href} aria-current={tab === t.key ? "page" : undefined}
            className={cn("text-body-md", tab === t.key ? "font-semibold text-ink" : "text-mute hover:text-ink")}>
            {t.label}
          </Link>
        ))}
      </nav>
      {tab === "lessons" ? <AdminTopicsView /> : <AdminStudyPlansView />}
    </AdminGuard>
  );
}
