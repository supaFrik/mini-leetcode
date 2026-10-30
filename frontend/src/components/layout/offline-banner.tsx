"use client";
import { WifiOff } from "lucide-react";
import { useOnline } from "@/hooks/use-online";

export function OfflineBanner() {
  const online = useOnline();
  if (online) return null;
  return (
    <div
      role="alert"
      className="flex items-center justify-center gap-2 bg-warning px-4 py-1.5 text-center text-body-md font-medium text-ink"
    >
      <WifiOff className="h-4 w-4 shrink-0" />
      You are currently offline. Your work is being saved locally. Please check your internet connection.
    </div>
  );
}
