"use client";
import { useCallback, useEffect, useState } from "react";
import { Trash2, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { addContestProctor, fetchContestProctors, removeContestProctor } from "@/lib/services";
import { toast } from "@/lib/toast";
import type { ContestProctor } from "@/types/api";

/** Tab Proctors: chủ kỳ thi/ADMIN bổ nhiệm giảng viên hoặc ADMIN khác làm giám thị (backend chặn quyền). */
export function ProctorsPanel({ contestId }: { contestId: number }) {
  const [list, setList] = useState<ContestProctor[] | null>(null);
  const [username, setUsername] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    fetchContestProctors(contestId).then(setList).catch((e) => { setList([]); toast(errorMessage(e), "error"); });
  }, [contestId]);
  useEffect(() => { load(); }, [load]);

  const add = async (e: React.FormEvent) => {
    e.preventDefault();
    const name = username.trim();
    if (!name) return;
    setBusy(true);
    try {
      await addContestProctor(contestId, name);
      toast(`${name} is now a proctor.`, "success");
      setUsername("");
      load();
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (p: ContestProctor) => {
    try {
      await removeContestProctor(contestId, p.userId);
      load();
    } catch (err) {
      toast(errorMessage(err), "error");
    }
  };

  return (
    <div className="max-w-xl space-y-4">
      <p className="text-sm text-mute">Proctors can watch the live view and read the violation and plagiarism reports.</p>
      <form onSubmit={add} className="flex gap-2" aria-label="Add proctor">
        <Input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Instructor or admin username" aria-label="Proctor username" />
        <Button type="submit" disabled={busy || !username.trim()}><UserPlus className="h-4 w-4" />Add</Button>
      </form>
      {list === null ? (
        <p className="text-sm text-mute">Loading proctors…</p>
      ) : list.length === 0 ? (
        <p className="text-sm text-mute">No proctors yet.</p>
      ) : (
        <ul className="divide-y divide-border rounded-sm border border-border" aria-label="Proctors">
          {list.map((p) => (
            <li key={p.userId} className="flex items-center justify-between px-3 py-2 text-sm">
              <span className="text-ink">{p.username} <span className="text-mute">· {p.role === "ADMIN" ? "Admin" : "Instructor"}</span></span>
              <Button variant="ghost" size="icon-sm" onClick={() => remove(p)} aria-label={`Remove proctor ${p.username}`}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
