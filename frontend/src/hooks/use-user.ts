"use client";
import { useEffect, useState } from "react";
import { decodeUser, getToken, type AuthUser } from "@/lib/auth";

export function useUser() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => { setUser(decodeUser(getToken())); setReady(true); }, []);
  return { user, ready };
}
