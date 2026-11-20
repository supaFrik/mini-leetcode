"use client";
import { useEffect, useState } from "react";
import { fetchPublicSettings } from "@/lib/services";
import type { PublicSettings } from "@/types/api";

/** Banner toàn site khi ADMIN bật maintenance mode ở /admin/settings. */
export function MaintenanceBanner() {
  const [settings, setSettings] = useState<PublicSettings | null>(null);
  useEffect(() => {
    fetchPublicSettings().then(setSettings).catch(() => setSettings(null));
  }, []);
  if (!settings?.maintenanceMode) return null;
  return (
    <div role="status" className="shrink-0 bg-amber-100 px-6 py-2 text-center text-body-sm font-medium text-amber-900 dark:bg-amber-950 dark:text-amber-200">
      {settings.bannerMessage || "The site is under maintenance. Some features may be unavailable."}
    </div>
  );
}
