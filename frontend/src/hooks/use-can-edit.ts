import { useUser } from "@/hooks/use-user";

/**
 * Plan / chủ đề chỉ tác giả hoặc ADMIN sửa được (server cũng kiểm tra, trả 403).
 * `createdById` chưa có (đang tạo mới) ⇒ được sửa.
 */
export function useCanEdit() {
  const { user } = useUser();
  return (createdById?: number) => !!user && (user.role === "ADMIN" || createdById === undefined || createdById === user.id);
}
