"use client";
import { useEffect, useState } from "react";
import { Bell } from "lucide-react";
import { DropdownMenu, DropdownMenuContent, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { formatWhen } from "@/components/admin/admin-overview";
import { fetchAnnouncements } from "@/lib/services";
import type { Announcement } from "@/types/api";
import s from "./site-header.module.css";

const SEEN_KEY = "announcements:lastSeenId";

// Bộ nhớ trình duyệt có thể bị chặn (chế độ riêng tư): coi như chưa đọc gì.
function readSeen(): number {
  try { return Number(localStorage.getItem(SEEN_KEY)) || 0; } catch { return 0; }
}
function writeSeen(id: number) {
  try { localStorage.setItem(SEEN_KEY, String(id)); } catch { /* bỏ qua */ }
}

/** Chuông thông báo trên header: liệt kê thông báo chung, chấm đỏ khi có thông báo mới hơn lần mở trước. */
export function NotificationBell() {
  const [items, setItems] = useState<Announcement[] | null>(null);
  const [seen, setSeen] = useState(0);

  useEffect(() => {
    setSeen(readSeen());
    fetchAnnouncements().then(setItems).catch(() => setItems([]));
  }, []);

  const newest = items?.[0]?.id ?? 0;
  const unread = newest > seen;

  return (
    <DropdownMenu
      onOpenChange={(open) => {
        if (open && unread) { writeSeen(newest); setSeen(newest); }
      }}
    >
      <DropdownMenuTrigger asChild>
        <button className={s.ghost} type="button" aria-label={unread ? "Notifications, new announcements" : "Notifications"}>
          <span className="relative inline-flex">
            <Bell size={20} aria-hidden="true" />
            {unread && <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-destructive" aria-hidden="true" />}
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuLabel>Announcements</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {items === null && <p className="px-2 py-3 text-body-sm text-mute">Loading…</p>}
        {items?.length === 0 && <p className="px-2 py-3 text-body-sm text-mute">No announcements yet.</p>}
        {items && items.length > 0 && (
          <ul className="max-h-96 overflow-y-auto">
            {items.map((a) => (
              <li key={a.id} className="px-2 py-2">
                <p className="font-medium text-ink">{a.title}</p>
                <p className="whitespace-pre-line text-body-sm text-body">{a.body}</p>
                <p className="mt-0.5 text-body-sm text-mute">{formatWhen(a.createdAt)}</p>
              </li>
            ))}
          </ul>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
