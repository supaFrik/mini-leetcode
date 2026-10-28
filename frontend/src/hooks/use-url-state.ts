"use client";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

export function useUrlState() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();

  const set = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(sp.toString());
    for (const [k, v] of Object.entries(patch)) (v ? next.set(k, v) : next.delete(k));
    if (!("page" in patch)) next.delete("page");        // đổi bộ lọc thì quay về trang 1
    router.push(`${pathname}?${next.toString()}`);
  };
  return { sp, set };
}