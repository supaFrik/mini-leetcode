"use client";
import { Check, Minus } from "lucide-react";
import type { Role } from "@/types/api";

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
  { label: "View overview, manage users, roles and settings", roles: ["ADMIN"] },
];

/** Khung nhỏ cạnh bảng Users: mỗi role làm được gì. */
export function RolesGuide() {
  return (
    <details className="rounded-sm border border-border bg-card text-body-sm" open>
      <summary className="cursor-pointer px-4 py-2 font-medium text-ink">What each role can do</summary>
      <table className="w-full text-left">
        <thead className="border-y border-border text-mute">
          <tr>
            <th className="px-4 py-1.5 font-medium">Permission</th>
            {ROLES.map((r) => <th key={r.value} className="w-24 px-2 py-1.5 text-center font-medium">{r.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {PERMISSIONS.map((p) => (
            <tr key={p.label} className="border-b border-border last:border-0">
              <td className="px-4 py-1.5 text-ink">{p.label}</td>
              {ROLES.map((r) => (
                <td key={r.value} className="px-2 py-1.5 text-center">
                  {p.roles.includes(r.value)
                    ? <Check className="mx-auto h-3.5 w-3.5 text-success" aria-label="Allowed" />
                    : <Minus className="mx-auto h-3.5 w-3.5 text-zinc-300" aria-label="Not allowed" />}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
