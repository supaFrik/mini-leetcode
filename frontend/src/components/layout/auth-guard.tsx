"use client";
import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { getToken } from "@/lib/auth";
import { USE_MOCK } from "@/lib/api";
import { ensureMockSession } from "@/lib/services";

// Chưa đăng nhập → về /login. Ngoại trừ /register cho phép tạo tài khoản mới.
// Chế độ mock thì tự đăng nhập tài khoản "student" cho tiện demo.
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
      else { router.replace(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`); return; }
    }
    setOk(true);
  }, [router, pathname]);
  if (!ok) return <div className="grid min-h-screen place-items-center text-sm text-muted-foreground">Loading…</div>;
  return <>{children}</>;
}
