"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { decodeUser, getToken } from "@/lib/auth";
import { USE_MOCK } from "@/lib/api";
import { ensureMockSession } from "@/lib/services";
import { useUser } from "@/hooks/use-user";

/**
 * Các trang công khai: xem danh sách bài và chi tiết bài không cần đăng nhập.
 * Backend cho phép `GET /api/v1/problems/**` (permitAll), nên tầng frontend
 * cũng phải mở — nếu không thì khách chưa đăng nhập không xem được sản phẩm.
 *
 * Gốc `/` cũng nằm trong danh sách: nó chỉ là redirect sang `/problems`,
 * nên chặn nó ở đây sẽ đẩy khách về `/login` trước khi kịp chuyển hướng.
 */
const PUBLIC_PATHS = [/^\/$/, /^\/problems(\/|$)/, /^\/login(\/|$)/, /^\/register(\/|$)/, /^\/ranking$/, /^\/policies\//];

/** Trang học viên ADMIN được mở để xem trước (nút View / Preview trong khu quản trị). */
const PREVIEW_PATHS = [/^\/problems\/[^/]+$/, /^\/study-plans\/[^/]+(\/[^/]+\/[^/]+)?$/, /^\/topics\/[^/]+$/];
const isPreviewPath = (pathname: string) => PREVIEW_PATHS.some((p) => p.test(pathname));

/** ADMIN đang xem trước trang học viên. */
export function useAdminPreview() {
  const pathname = usePathname();
  const { user } = useUser();
  return user?.role === "ADMIN" && isPreviewPath(pathname);
}

/** Thanh báo đang xem trước, thay cho header học viên. */
export function PreviewBanner() {
  if (!useAdminPreview()) return null;
  return (
    <div role="status" className="flex shrink-0 items-center justify-center gap-3 bg-primary px-4 py-2 text-body-sm font-medium text-primary-foreground">
      Preview mode — this is what students see
      <Link href="/admin" className="underline underline-offset-2 hover:opacity-80">Back to admin</Link>
    </div>
  );
}

/**
 * Chưa đăng nhập → về /login. Ngoại trừ /register cho phép tạo tài khoản mới.
 * Chế độ mock thì tự đăng nhập tài khoản "student" cho tiện demo.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ok, setOk] = useState(false);
  useEffect(() => {
    if (pathname === "/register" || pathname.startsWith("/register")) {
      setOk(true);
      return;
    }
    if (!getToken()) {
      if (USE_MOCK) ensureMockSession();
      else if (!PUBLIC_PATHS.some((p) => p.test(pathname))) {
        router.replace(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`);
        return;
      }
    }
    const inConsole = pathname.startsWith("/admin") || pathname.startsWith("/teaching");
    // ADMIN chỉ ở trong khu quản trị, kể cả khi gõ thẳng URL trang học viên; trừ các trang xem trước.
    if (!inConsole && !isPreviewPath(pathname) && decodeUser(getToken())?.role === "ADMIN") {
      router.replace("/admin");
      return;
    }
    setOk(true);
  }, [router, pathname]);
  if (!ok) return <div className="grid min-h-screen place-items-center text-body-md text-mute">Loading…</div>;
  return <>{children}</>;
}
