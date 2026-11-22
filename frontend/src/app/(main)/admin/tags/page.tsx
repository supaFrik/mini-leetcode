"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AdminTagsView } from "@/components/admin/admin-tags";
import { RoleGuard } from "@/components/layout/role-guard";
import { useUser } from "@/hooks/use-user";
import { fetchMyPermissions } from "@/lib/services";

/** Cần TAG_MANAGE (backend cũng chặn 403); không có thì về /admin. */
function TagManageGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user } = useUser();
  const [allowed, setAllowed] = useState<boolean | null>(null);
  useEffect(() => {
    if (!user) return;
    fetchMyPermissions()
      .then((p) => setAllowed(p.includes("TAG_MANAGE")))
      .catch(() => setAllowed(false));
  }, [user]);
  useEffect(() => {
    if (allowed === false) router.replace("/admin");
  }, [allowed, router]);
  if (!allowed) return <div className="grid min-h-[50vh] place-items-center text-body-md text-mute">Loading…</div>;
  return <>{children}</>;
}

export default function AdminTagsPage() {
  return (
    <RoleGuard allow={["INSTRUCTOR", "ADMIN"]}>
      <TagManageGuard>
        <AdminTagsView />
      </TagManageGuard>
    </RoleGuard>
  );
}
