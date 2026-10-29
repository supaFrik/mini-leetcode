"use client";
import { WifiOff } from "lucide-react";
import { useOnline } from "@/hooks/use-online";

export function OfflineBanner() {
  const online = useOnline();
  if (online) return null;
  return (
    <div role="alert" className="flex items-center justify-center gap-2 bg-amber-500 px-4 py-1.5 text-center text-sm font-medium text-black">
      <WifiOff className="h-4 w-4 shrink-0" />
      Mất kết nối mạng. Bài làm đang được lưu cục bộ. Vui lòng kiểm tra đường truyền.
    </div>
  );
}
