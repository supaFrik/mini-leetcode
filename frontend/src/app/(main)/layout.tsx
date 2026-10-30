"use client";
import { usePathname } from "next/navigation";
import { AuthGuard } from "@/components/layout/auth-guard";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { SiteHeader } from "@/components/layout/site-header";
import { Toaster } from "@/components/layout/toaster";

// Khung cố định (header + footer), vùng giữa tự cuộn. Trang làm bài dùng h-full để chia 2 pane đúng chiều cao.
//
// `/problems` tự quản lý vùng cuộn của riêng nó (cột danh sách cuộn độc lập với
// thanh rail bên trái), nên `main` ở route đó phải để chiều cao tràn xuống thay
// vì cuộn — nếu không sẽ có hai thanh cuộn chồng lên nhau.
function MainLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const selfScrolling = pathname === "/problems";

  return (
    <AuthGuard>
      <div className="flex h-dvh flex-col">
        <OfflineBanner />
        <SiteHeader />
        <main className={selfScrolling ? "min-h-0 flex-1 overflow-hidden" : "min-h-0 flex-1 overflow-auto"}>
          {children}
        </main>
        <footer className="flex h-12 shrink-0 items-center border-t border-border bg-background px-6 text-body-sm text-mute">
          © 2026 Mini LeetCode.
        </footer>
        <Toaster />
      </div>
    </AuthGuard>
  );
}

export default MainLayout;
