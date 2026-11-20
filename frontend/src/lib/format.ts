import { parseApiDate } from "./datetime";

// Dùng parseApiDate để mốc thời gian không kèm múi giờ từ server luôn được coi là UTC,
// tránh hiển thị sai lệch múi giờ so với đồng hồ đếm ngược của phòng thi.
export const fmtDateTime = (iso?: string) => {
  const ms = parseApiDate(iso);
  return Number.isNaN(ms) ? "—" : new Date(ms).toLocaleString("en-US", { hour12: false, dateStyle: "short", timeStyle: "medium" });
};

export const fmtMemory = (kb: number | null | undefined) =>
  kb == null ? "—" : kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;

export const fmtDuration = (ms: number) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, "0");
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : `${pad(m)}:${pad(s % 60)}`;
};

/** Đồng hồ đếm ngược: "HH:MM:SS" dưới một ngày; từ một ngày trở lên "7d 00h 35m". */
export const fmtClock = (ms: number) => {
  const t = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, "0");
  const d = Math.floor(t / 86400), h = Math.floor((t % 86400) / 3600), m = Math.floor((t % 3600) / 60);
  return d > 0 ? `${d}d ${pad(h)}h ${pad(m)}m` : `${pad(h)}:${pad(m)}:${pad(t % 60)}`;
};

/** Khoảng thời gian ngắn gọn: "2d 4h", "3h 12m", "8m". */
export const fmtSpan = (ms: number) => {
  const m = Math.max(0, Math.ceil(ms / 60000));
  const d = Math.floor(m / 1440), h = Math.floor((m % 1440) / 60);
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m % 60}m` : `${m}m`;
};
