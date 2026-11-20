"use client";
import { useEffect, useState } from "react";
import { Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useConfirm } from "@/components/ui/confirm";
import { useUser } from "@/hooks/use-user";
import { errorMessage } from "@/lib/api";
import { fetchAdminUsers, updateAdminUser } from "@/lib/services";
import { toast } from "@/lib/toast";
import type { AdminUserList, AdminUserRow, Role } from "@/types/api";
import { formatWhen } from "./admin-overview";
import { RolesGuide } from "./admin-roles";

const ROLES: { value: Role; label: string }[] = [
  { value: "USER", label: "User" },
  { value: "INSTRUCTOR", label: "Instructor" },
  { value: "ADMIN", label: "Admin" },
];
const roleLabel = (r: Role) => ROLES.find((x) => x.value === r)?.label ?? r;
const selectClass = "h-9 rounded-sm border border-border bg-card px-2 text-body-md text-ink disabled:opacity-60";
const SELF = "You cannot change your own account";

/** Tìm người dùng, khoá/mở khoá, đổi role. Chỉ ADMIN; backend chặn tự khoá và tự đổi role. */
export function AdminUsersView() {
  const { user: me } = useUser();
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminUserList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<number | null>(null);
  const confirm = useConfirm();

  useEffect(() => {
    // Gõ tìm kiếm: chờ 300ms rồi mới gọi API.
    const t = setTimeout(() => {
      fetchAdminUsers(q.trim(), page)
        .then((d) => { setData(d); setError(null); })
        .catch((e) => setError(errorMessage(e)));
    }, 300);
    return () => clearTimeout(t);
  }, [q, page]);

  const apply = async (u: AdminUserRow, change: Partial<Pick<AdminUserRow, "role" | "status">>, title: string, action: string, destructive = false) => {
    if (!(await confirm({ title, action, destructive }))) return;
    setPending(u.id);
    try {
      const saved = await updateAdminUser(u.id, change);
      setData((d) => d && { ...d, items: d.items.map((x) => (x.id === saved.id ? saved : x)) });
      toast(`${saved.username} updated.`, "success");
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setPending(null);
    }
  };

  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <div className="flex justify-end">
        <div className="relative w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" aria-hidden />
          <Input
            value={q}
            onChange={(e) => { setQ(e.target.value); setPage(0); }}
            placeholder="Search username or email"
            aria-label="Search users"
            className="h-10 bg-card pl-9"
          />
        </div>
      </div>

      <div className="overflow-hidden rounded-sm border border-border bg-card" role="region" aria-label="Users">
        {error && <p className="p-6 text-body-md text-destructive" role="alert">{error}</p>}
        {!error && !data && <p className="p-6 text-body-md text-mute">Loading users…</p>}
        {data?.items.length === 0 && <p className="p-6 text-body-md text-mute">No users match “{q.trim()}”.</p>}
        {data && data.items.length > 0 && (
          <table className="w-full text-left text-body-md">
            <thead className="border-b border-border text-body-sm text-mute">
              <tr>
                <th className="px-4 py-2 font-medium">User</th>
                <th className="px-4 py-2 font-medium">Role</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="px-4 py-2 font-medium">Joined</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {data.items.map((u) => {
                const self = u.id === me?.id;
                const busy = pending === u.id;
                const blocked = u.status === "BLOCKED";
                return (
                  <tr key={u.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-2">
                      <p className="font-medium text-ink">{u.username}{self && <span className="ml-2 text-body-sm font-normal text-mute">(you)</span>}</p>
                      <p className="text-body-sm text-mute">{u.email}</p>
                    </td>
                    <td className="px-4 py-2">
                      <select
                        value={u.role}
                        disabled={self || busy}
                        title={self ? SELF : undefined}
                        aria-label={`Role for ${u.username}`}
                        className={selectClass}
                        onChange={(e) => {
                          const role = e.target.value as Role;
                          void apply(u, { role }, `Change ${u.username} from ${roleLabel(u.role)} to ${roleLabel(role)}?`, "Change role");
                        }}
                      >
                        {ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                      </select>
                    </td>
                    <td className="px-4 py-2">
                      <Badge variant={blocked ? "destructive" : "success"}>{blocked ? "Blocked" : "Active"}</Badge>
                    </td>
                    <td className="px-4 py-2 text-body-sm text-mute">{formatWhen(u.createdAt)}</td>
                    <td className="px-4 py-2 text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={self || busy}
                        title={self ? SELF : undefined}
                        onClick={() => blocked
                          ? apply(u, { status: "ACTIVE" }, `Unblock ${u.username}?`, "Unblock")
                          : apply(u, { status: "BLOCKED" }, `Block ${u.username}? They will be signed out and cannot sign in.`, "Block", true)}
                      >
                        {blocked ? "Unblock" : "Block"}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {data && data.total > data.size && (
        <div className="flex items-center justify-end gap-3 text-body-sm text-mute">
          <span>Page {page + 1} of {pages} · {data.total} users</span>
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</Button>
        </div>
      )}
      <RolesGuide />
    </div>
  );
}
