"use client";
import { useEffect, useState } from "react";
import { AUTH_CHANGED, decodeUser, getToken, type AuthUser } from "@/lib/auth";

export function useUser() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const sync = () => setUser(decodeUser(getToken()));
    sync();
    setReady(true);
    // Token đổi trong tab này (refresh / đăng xuất) hoặc ở tab khác.
    window.addEventListener(AUTH_CHANGED, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(AUTH_CHANGED, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);
  return { user, ready };
}
