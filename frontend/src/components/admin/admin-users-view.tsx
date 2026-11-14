"use client";
import { useCallback, useEffect, useState } from "react";
import { KeyRound, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Pagination } from "@/components/problems/pagination";
import { AdminTabs } from "@/components/admin/admin-tabs";
import { useUser } from "@/hooks/use-user";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { fetchAdminUsers, updateAdminUser, type AdminUser } from "@/lib/services";
import type { Page, Role } from "@/types/api";

const ROLES: Role[] = ["USER", "INSTRUCTOR", "ADMIN"];

function UserRow({ u, self, onSaved }: { u: AdminUser; self: boolean; onSaved: (u: AdminUser) => void }) {
  const [username, setUsername] = useState(u.username);
  const [email, setEmail] = useState(u.email);
  const [busy, setBusy] = useState(false);

  const patch = async (p: Parameters<typeof updateAdminUser>[1], ok = "User updated.") => {
    setBusy(true);
    try {
      onSaved(await updateAdminUser(u.id, p));
      toast(ok, "success");
    } catch (e) {
      toast(errorMessage(e), "error");
      setUsername(u.username);
      setEmail(u.email);
    } finally {
      setBusy(false);
    }
  };

  const resetPassword = () => {
    const pw = window.prompt(`New password for ${u.username} (min 8 characters):`);
    if (pw) patch({ password: pw }, "Password reset. The user's sessions were signed out.");
  };

  const select = "h-8 rounded-sm border border-border bg-card px-2 text-body-sm text-ink";
  return (
    <div className="grid grid-cols-1 items-center gap-2 border-t border-border px-5 py-3 first:border-t-0 md:grid-cols-[48px_1fr_1.4fr_120px_110px_40px]">
      <span className="font-mono text-body-sm text-mute">{u.id}</span>
      <Input aria-label="Username" value={username} disabled={busy} onChange={(e) => setUsername(e.target.value)}
        onBlur={() => username !== u.username && patch({ username })} className="h-8 rounded-sm bg-card text-body-sm" />
      <Input aria-label="Email" type="email" value={email} disabled={busy} onChange={(e) => setEmail(e.target.value)}
        onBlur={() => email !== u.email && patch({ email })} className="h-8 rounded-sm bg-card text-body-sm" />
      <select aria-label="Role" value={u.role} disabled={busy || self} onChange={(e) => patch({ role: e.target.value as Role })} className={select}>
        {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
      </select>
      <select aria-label="Status" value={u.status} disabled={busy || self} onChange={(e) => patch({ status: e.target.value as AdminUser["status"] })} className={select}>
        <option value="ACTIVE">ACTIVE</option>
        <option value="BLOCKED">BLOCKED</option>
      </select>
      <Button size="icon-sm" variant="ghost" disabled={busy} onClick={resetPassword} title="Reset password" aria-label={`Reset password for ${u.username}`}>
        <KeyRound className="h-3.5 w-3.5" />
      </Button>
    </div>
  );
}

export function AdminUsersView() {
  const { user } = useUser();
  const [res, setRes] = useState<Page<AdminUser> | null>(null);
  const [page, setPage] = useState(1);
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");

  const load = useCallback(async () => {
    try {
      setRes(await fetchAdminUsers(page, search));
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  }, [page, search]);

  useEffect(() => {
    load();
  }, [load]);

  const replace = (u: AdminUser) =>
    setRes((r) => (r ? { ...r, content: r.content.map((x) => (x.id === u.id ? u : x)) } : r));

  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <header>
        <AdminTabs current="users" />
        <h1 className="mt-1 text-heading-lg text-ink">Users</h1>
        <p className="mt-1 text-body-md text-mute">Edit accounts, roles and access. Changes save as soon as a field loses focus.</p>
      </header>

      <div className="relative max-w-md">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              setPage(1);
              setSearch(q);
            }
          }}
          placeholder="Search username or email, press Enter"
          aria-label="Search users"
          className="h-10 rounded-sm bg-card pl-9"
        />
      </div>

      <section aria-label="User administration" className="overflow-hidden rounded-xl border border-border bg-card">
        {res?.content.map((u) => (
          <UserRow key={`${u.id}:${u.username}:${u.email}`} u={u} self={u.username === user?.username} onSaved={replace} />
        ))}
        {res?.content.length === 0 && <p className="p-12 text-center text-body-md text-mute">No users match.</p>}
        {res && res.totalPages > 1 && (
          <div className="flex justify-end border-t border-border bg-hairline-soft px-5 py-3">
            <Pagination page={page} totalPages={res.totalPages} onChange={setPage} />
          </div>
        )}
      </section>
    </div>
  );
}
