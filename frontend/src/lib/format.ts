export const fmtDateTime = (iso?: string) =>
  iso ? new Date(iso).toLocaleString("vi-VN", { hour12: false, dateStyle: "short", timeStyle: "medium" }) : "—";

export const fmtMemory = (kb: number | null | undefined) =>
  kb == null ? "—" : kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;

export const fmtDuration = (ms: number) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, "0");
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : `${pad(m)}:${pad(s % 60)}`;
};

export const fmtLong = (ms: number) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  return d > 0 ? `${d} ngày ${h} giờ` : h > 0 ? `${h} giờ ${m} phút` : fmtDuration(ms);
};
