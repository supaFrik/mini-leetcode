"use client";
import { useEffect, useState } from "react";
import { useUser } from "@/hooks/use-user";
import { fetchMyPermissions } from "@/lib/services";
import type { Permission } from "@/types/api";

/** Quyền thật của user đang đăng nhập (từ server); lỗi hoặc chưa tải xong thì coi như không có quyền nào. */
export function usePermissions(): Permission[] {
  const { user } = useUser();
  const [perms, setPerms] = useState<Permission[]>([]);
  useEffect(() => {
    if (!user || user.role === "USER") { setPerms([]); return; } // USER không có quyền nào, khỏi gọi API
    fetchMyPermissions().then(setPerms).catch(() => setPerms([]));
  }, [user]);
  return perms;
}
