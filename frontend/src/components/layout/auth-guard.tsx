"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getToken } from "@/lib/auth";
import { USE_MOCK } from "@/lib/api";
import { ensureMockSession } from "@/lib/services";

// Chưa đăng nhập → về /login. Chế độ mock thì tự đăng nhập tài khoản "student" cho tiện demo.
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ok, setOk] = useState(false);
  useEffect(() => {
    if (!getToken()) {
      if (USE_MOCK) ensureMockSession();
      else { router.replace(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`); return; }
    }
    setOk(true);
  }, [router]);
  if (!ok) return <div className="grid min-h-screen place-items-center text-sm text-muted-foreground">Đang tải…</div>;
  return <>{children}</>;
}
