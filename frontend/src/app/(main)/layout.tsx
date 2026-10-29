import { AuthGuard } from "@/components/layout/auth-guard";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { SiteHeader } from "@/components/layout/site-header";
import { Toaster } from "@/components/layout/toaster";

// Khung cố định (header + footer), vùng giữa tự cuộn. Trang làm bài dùng h-full để chia 2 pane đúng chiều cao.
export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="flex h-dvh flex-col">
        <OfflineBanner />
        <SiteHeader />
        <main className="min-h-0 flex-1 overflow-auto">{children}</main>
        <footer className="flex h-12 shrink-0 items-center border-t px-6 text-xs text-muted-foreground">© 2026 Mini LeetCode.</footer>
        <Toaster />
      </div>
    </AuthGuard>
  );
}
