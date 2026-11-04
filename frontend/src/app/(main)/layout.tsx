"use client";
import { usePathname } from "next/navigation";
import "@fontsource/ibm-plex-sans/400.css";
import "@fontsource/ibm-plex-sans/500.css";
import "@fontsource/ibm-plex-sans/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import { AuthGuard } from "@/components/layout/auth-guard";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { SiteHeader } from "@/components/layout/site-header";
import { Toaster } from "@/components/layout/toaster";

// Khung cố định (header + footer), vùng giữa tự cuộn. Trang làm bài dùng h-full để chia 2 pane đúng chiều cao.
// Trang /problems theo bản mẫu không có footer.
function MainLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const showFooter = pathname !== "/problems";

  return (
    <AuthGuard>
      <div className="flex h-dvh flex-col">
        <OfflineBanner />
        <SiteHeader />
        <main className="min-h-0 flex-1 overflow-auto">{children}</main>
        {showFooter && (
          <footer className="flex h-12 shrink-0 items-center border-t border-border bg-background px-6 text-body-sm text-mute">
            © 2026 Mini LeetCode.
          </footer>
        )}
        <Toaster />
      </div>
    </AuthGuard>
  );
}

export default MainLayout;
