"use client";
import { Check, Minus } from "lucide-react";
import type { Role } from "@/types/api";
import { AdminNav } from "./admin-nav";

const ROLES: { value: Role; label: string }[] = [
  { value: "USER", label: "User" },
  { value: "INSTRUCTOR", label: "Instructor" },
  { value: "ADMIN", label: "Admin" },
];

// Khớp với SecurityConfig. Sửa quyền ở backend thì sửa bảng này theo.
const PERMISSIONS: { label: string; roles: Role[] }[] = [
  { label: "Solve problems and view own submissions", roles: ["USER", "INSTRUCTOR", "ADMIN"] },
  { label: "Join contests and study plans", roles: ["USER", "INSTRUCTOR", "ADMIN"] },
  { label: "Create and edit problems and test cases", roles: ["INSTRUCTOR", "ADMIN"] },
  { label: "Manage contests, study plans and topics", roles: ["INSTRUCTOR", "ADMIN"] },
  { label: "Delete problems and contests", roles: ["ADMIN"] },
  { label: "Manage tags and policy pages", roles: ["ADMIN"] },
  { label: "View overview, manage users and roles", roles: ["ADMIN"] },
];

/** Bảng quyền theo role, chỉ để xem. */
export function AdminRolesView() {
  return (
    <div className="mx-auto w-full max-w-container space-y-5 px-8 py-6">
      <AdminNav />
      <div>
        <h1 className="text-heading-md text-ink">Roles</h1>
        <p className="text-body-md text-mute">What each role can do. Change a user&apos;s role on the Users tab.</p>
      </div>
      <div className="overflow-hidden rounded-sm border border-border bg-card">
        <table className="w-full text-left text-body-md">
          <thead className="border-b border-border text-body-sm text-mute">
            <tr>
              <th className="px-4 py-2 font-medium">Permission</th>
              {ROLES.map((r) => <th key={r.value} className="w-32 px-4 py-2 text-center font-medium">{r.label}</th>)}
            </tr>
          </thead>
          <tbody>
            {PERMISSIONS.map((p) => (
              <tr key={p.label} className="border-b border-border last:border-0">
                <td className="px-4 py-2 text-ink">{p.label}</td>
                {ROLES.map((r) => (
                  <td key={r.value} className="px-4 py-2 text-center">
                    {p.roles.includes(r.value)
                      ? <Check className="mx-auto h-4 w-4 text-success" aria-label="Allowed" />
                      : <Minus className="mx-auto h-4 w-4 text-zinc-300" aria-label="Not allowed" />}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
