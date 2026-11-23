"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import "@fontsource/ibm-plex-sans/400.css";
import "@fontsource/ibm-plex-sans/500.css";
import "@fontsource/ibm-plex-sans/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import { AuthGuard, PreviewBanner, useAdminPreview } from "@/components/layout/auth-guard";
import { MaintenanceBanner } from "@/components/layout/maintenance-banner";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { SiteHeader } from "@/components/layout/site-header";
import { Toaster } from "@/components/layout/toaster";

// Khung cố định (header + footer), vùng giữa tự cuộn. Trang làm bài dùng h-full để chia 2 pane đúng chiều cao.
// Trang /problems theo bản mẫu không có footer.
function MainLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  // Khu quản trị có khung riêng (sidebar có "View site"), không dùng header/footer học viên.
  const inConsole = pathname.startsWith("/admin") || pathname.startsWith("/teaching");
  // Admin xem trước: chỉ có thanh Preview, không header/footer học viên.
  const preview = useAdminPreview();
  const showFooter = pathname !== "/problems" && !inConsole && !preview;

  return (
    <AuthGuard>
      <div className="flex h-dvh flex-col">
        <OfflineBanner />
        <MaintenanceBanner />
        <PreviewBanner />
        {!inConsole && !preview && <SiteHeader />}
        <main className="min-h-0 flex-1 overflow-auto">{children}</main>
        {showFooter && (
          <footer className="flex h-12 shrink-0 items-center border-t border-border bg-background px-6 text-body-sm font-medium text-zinc-600 dark:text-zinc-400">
            © 2026 Mini LeetCode.
            <nav aria-label="Policies" className="ml-auto flex gap-4">
              <Link href="/policies/terms" className="hover:text-ink hover:underline">Terms of use</Link>
              <Link href="/policies/rules" className="hover:text-ink hover:underline">Contest rules</Link>
            </nav>
          </footer>
        )}
        <Toaster />
      </div>
    </AuthGuard>
  );
}

export default MainLayout;
