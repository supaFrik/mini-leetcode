"use client";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

// Bộ lọc nằm trên URL: F5 không mất, nút Back hoạt động, gửi link được.
export function useUrlState() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();

  const set = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(sp.toString());
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("page" in patch)) next.delete("page"); // đổi bộ lọc thì về trang 1
    const qs = next.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname);
  };
  return { sp, set };
}
