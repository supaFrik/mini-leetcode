"use client";
import { Fragment, useEffect, useState } from "react";
import { Check, Lock, Minus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api";
import { fetchRolePermissions, saveInstructorPermissions } from "@/lib/services";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import type { Permission } from "@/types/api";

type Row = { key: Permission; label: string; hint?: string };

// Admin luôn đủ quyền, User không có quyền nào; chỉ cột Instructor bật/tắt được (trừ SYSTEM_MANAGE).
const GROUPS: { heading: string; rows: Row[] }[] = [
  {
    heading: "Problems",
    rows: [
      { key: "PROBLEM_VIEW_ALL", label: "View all problems", hint: "Includes other instructors' unpublished problems, read only." },
      { key: "PROBLEM_EDIT", label: "Create and edit problems", hint: "Own problems only, with test cases and grading." },
      { key: "PROBLEM_PUBLISH", label: "Publish problems", hint: "Submit for review, publish and archive." },
      { key: "PROBLEM_DELETE", label: "Delete problems" },
    ],
  },
  {
    heading: "Contests",
    rows: [
      { key: "CONTEST_EDIT", label: "Create and edit contests" },
      { key: "CONTEST_PARTICIPANTS", label: "Manage participants and proctoring" },
      { key: "CONTEST_REPORTS", label: "View reports and plagiarism", hint: "Includes the submissions report." },
      { key: "CONTEST_DELETE", label: "Delete contests" },
    ],
  },
  {
    heading: "Study plans and topics",
    rows: [
      { key: "COURSE_EDIT", label: "Create and edit", hint: "Includes publishing." },
      { key: "COURSE_DELETE", label: "Delete" },
    ],
  },
  {
    heading: "Tags and pages",
    rows: [
      { key: "TAG_MANAGE", label: "Manage tags" },
      { key: "PAGE_MANAGE", label: "Manage policy pages" },
    ],
  },
  { heading: "Communication", rows: [{ key: "ANNOUNCEMENT_SEND", label: "Send announcements" }] },
  {
    heading: "System",
    rows: [{ key: "SYSTEM_MANAGE", label: "Manage users, roles, permissions and settings", hint: "Admin only. Cannot be granted to instructors." }],
  },
];

// Giống V24__fine_grained_permissions.sql.
const DEFAULTS: Permission[] = [
  "PROBLEM_EDIT", "PROBLEM_PUBLISH", "CONTEST_EDIT", "CONTEST_PARTICIPANTS", "CONTEST_REPORTS",
  "COURSE_EDIT", "COURSE_DELETE", "ANNOUNCEMENT_SEND",
];

const sameSet = (a: Permission[], b: Permission[]) => a.length === b.length && a.every((p) => b.includes(p));

function Switch({ on, label, disabled, onToggle }: { on: boolean; label: string; disabled?: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={onToggle}
      className={cn(
        "relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50",
        on ? "bg-primary" : "bg-border"
      )}
    >
      <span className={cn("inline-block h-4 w-4 rounded-full bg-card shadow transition-transform", on ? "translate-x-[18px]" : "translate-x-0.5")} />
    </button>
  );
}

/** Khung cạnh bảng Users: mỗi role làm được gì; ADMIN bật/tắt quyền của Instructor. */
export function RolesGuide() {
  const [saved, setSaved] = useState<Permission[] | null>(null);
  const [draft, setDraft] = useState<Permission[]>([]);
  const [saving, setSaving] = useState(false);
  const [justSaved, setJustSaved] = useState(false);

  useEffect(() => {
    fetchRolePermissions()
      .then((p) => { setSaved(p.INSTRUCTOR); setDraft(p.INSTRUCTOR); })
      .catch((err) => toast(errorMessage(err), "error"));
  }, []);

  const dirty = saved !== null && !sameSet(saved, draft);
  const change = (next: Permission[]) => { setDraft(next); setJustSaved(false); };
  const toggle = (key: Permission) => change(draft.includes(key) ? draft.filter((k) => k !== key) : [...draft, key]);

  async function save() {
    setSaving(true);
    try {
      const p = await saveInstructorPermissions(draft);
      setSaved(p.INSTRUCTOR);
      setDraft(p.INSTRUCTOR);
      setJustSaved(true);
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <details className="rounded-sm border border-border bg-card text-body-sm" open>
      <summary className="cursor-pointer px-4 py-2 font-medium text-ink">What each role can do</summary>
      <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border px-4 py-2">
        <span className="mr-auto text-mute" role="status" aria-live="polite">
          {dirty ? "Unsaved changes" : justSaved ? "Permissions saved" : ""}
        </span>
        <Button type="button" variant="outline" size="sm" disabled={!saved || saving || sameSet(draft, DEFAULTS)}
          onClick={() => change(DEFAULTS)}>
          Reset to default
        </Button>
        <Button type="button" size="sm" onClick={save} disabled={!dirty || saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
      <table className="w-full text-left">
        <thead className="border-y border-border text-mute">
          <tr>
            <th className="px-4 py-1.5 font-medium">Permission</th>
            <th className="w-24 px-2 py-1.5 text-center font-medium">User</th>
            <th className="w-24 px-2 py-1.5 text-center font-medium">Instructor</th>
            <th className="w-24 px-2 py-1.5 text-center font-medium">Admin</th>
          </tr>
        </thead>
        <tbody>
          {GROUPS.map((group) => (
            <Fragment key={group.heading}>
              <tr className="border-b border-border bg-hairline-soft">
                <th colSpan={4} scope="colgroup" className="px-4 py-1 text-left text-xs font-semibold uppercase tracking-wide text-mute">
                  {group.heading}
                </th>
              </tr>
              {group.rows.map((row) => (
                <tr key={row.key} className="border-b border-border last:border-0">
                  <td className="px-4 py-2">
                    <p className="text-ink">{row.label}</p>
                    {row.hint && <p className="text-xs text-mute">{row.hint}</p>}
                  </td>
                  <td className="px-2 py-2 text-center">
                    <Minus className="mx-auto h-4 w-4 text-mute" aria-label="Not allowed" />
                  </td>
                  <td className="px-2 py-2 text-center">
                    {row.key === "SYSTEM_MANAGE" ? (
                      <Lock className="mx-auto h-4 w-4 text-mute" aria-label="Admin only" />
                    ) : (
                      <Switch on={draft.includes(row.key)} label={`Instructor: ${row.label}`} disabled={!saved || saving}
                        onToggle={() => toggle(row.key)} />
                    )}
                  </td>
                  <td className="px-2 py-2 text-center">
                    <Check className="mx-auto h-4 w-4 text-ink" aria-label="Allowed" />
                  </td>
                </tr>
              ))}
            </Fragment>
          ))}
        </tbody>
      </table>
    </details>
  );
}
