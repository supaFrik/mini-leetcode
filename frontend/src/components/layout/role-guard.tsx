"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useUser } from "@/hooks/use-user";
import { USE_MOCK } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import type { Role } from "@/types/api";

const STAFF: Role[] = ["INSTRUCTOR", "ADMIN"];

/**
 * Chặn truy cập trang quản trị cho người không có quyền.
 *
 * Đây chỉ là lớp ở giao diện — backend vẫn là nơi kiểm tra thật (403). Guard
 * ở đây để không hiển thị một trang rỗng rác cho người không đủ quyền.
 */
export function RoleGuard({ allow, children }: { allow: Role[]; children: React.ReactNode }) {
  const router = useRouter();
  const { user, ready } = useUser();
  const [denied, setDenied] = useState(false);

  const permitted = !!user && allow.includes(user.role);

  useEffect(() => {
    if (ready && !permitted) {
      // Trong chế độ mock, tài khoản demo là "student" nên không vào được
      // trang quản trị — điều hướng về trang bài cho hợp lý.
      if (!USE_MOCK) router.replace("/problems");
      else setDenied(true);
    }
  }, [ready, permitted, router]);

  if (!ready) {
    return <div className="grid min-h-screen place-items-center text-body-md text-mute">Loading…</div>;
  }

  if (!permitted) {
    if (denied) {
      return (
        <div className="mx-auto grid min-h-screen max-w-container place-items-center px-6 text-center">
          <div className="max-w-[46ch] space-y-3">
            <p className="eyebrow">Restricted</p>
            <h1 className="text-heading-lg text-ink">You don&apos;t have access to this area</h1>
            <p className="text-body-md text-mute">
              This page is limited to instructors and administrators. Sign in with a staff account to continue.
            </p>
            <Button asChild variant="outline" size="sm" onClick={() => router.push("/problems")}>
              <span>Back to problems</span>
            </Button>
          </div>
        </div>
      );
    }
    return null;
  }

  return <>{children}</>;
}

/** Guard dành riêng cho khu vực quản trị. */
export function AdminGuard({ children }: { children: React.ReactNode }) {
  return (
    <RoleGuard allow={STAFF}>
      <div className={cn("min-h-full bg-background")}>{children}</div>
    </RoleGuard>
  );
}
