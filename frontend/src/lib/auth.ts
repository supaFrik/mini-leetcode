const KEY = "access_token";
export const getToken = () => (typeof window === "undefined" ? null : localStorage.getItem(KEY));
export const setToken = (t: string) => localStorage.setItem(KEY, t);
export const clearToken = () => localStorage.removeItem(KEY);

export type Role = "USER" | "INSTRUCTOR" | "ADMIN";
// Chỉ để HIỂN THỊ tên/role. Quyền thật sự do server kiểm tra.
export function decodeUser(token: string | null) {
  try {
    const b64 = token!.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const p = JSON.parse(atob(b64));
    return { id: p.userId as number, username: p.username as string, role: p.role as Role };
  } catch { return null; }
}