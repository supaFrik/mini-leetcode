"use client";
import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { getToken } from "@/lib/auth";
import { USE_MOCK } from "@/lib/api";
import { ensureMockSession } from "@/lib/services";

/**
 * Các trang công khai: xem danh sách bài và chi tiết bài không cần đăng nhập.
 * Backend cho phép `GET /api/v1/problems/**` (permitAll), nên tầng frontend
 * cũng phải mở — nếu không thì khách chưa đăng nhập không xem được sản phẩm.
 *
 * Gốc `/` cũng nằm trong danh sách: nó chỉ là redirect sang `/problems`,
 * nên chặn nó ở đây sẽ đẩy khách về `/login` trước khi kịp chuyển hướng.
 */
const PUBLIC_PATHS = [/^\/$/, /^\/problems(\/|$)/, /^\/login(\/|$)/, /^\/register(\/|$)/];

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
    setOk(true);
  }, [router, pathname]);
  if (!ok) return <div className="grid min-h-screen place-items-center text-body-md text-mute">Loading…</div>;
  return <>{children}</>;
}
