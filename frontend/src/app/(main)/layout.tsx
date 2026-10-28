import { SiteHeader } from "@/components/layout/site-header";

export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="flex-1">{children}</main>
      <footer className="flex h-12 items-center border-t px-6 text-xs text-muted-foreground">
        © 2026 Mini LeetCode.
      </footer>
    </div>
  );
}