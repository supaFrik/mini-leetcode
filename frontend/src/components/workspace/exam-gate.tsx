"use client";
import { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useOnline } from "@/hooks/use-online";
import { fmtDateTime } from "@/lib/format";
import type { ContestInfo } from "@/types/api";

function Row({ ok, warn, children }: { ok: boolean; warn?: boolean; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-2 text-body-md">
      <span
        className={`grid h-4 w-4 place-items-center rounded-full text-white ${ok ? "bg-success" : warn ? "bg-warning" : "bg-destructive"}`}
      >
        {ok ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
      </span>
      {children}
    </li>
  );
}

export function ExamGate({ contest, onStart }: { contest: ContestInfo; onStart: () => void }) {
  const online = useOnline();
  const [agree, setAgree] = useState(false);
  const [fsOk, setFsOk] = useState(true);
  useEffect(() => setFsOk(document.fullscreenEnabled), []);

  const start = async () => {
    try {
      await document.documentElement.requestFullscreen();
    } catch {
      /* browser declined */
    }
    onStart();
  };
  return (
    <div className="mx-auto grid min-h-full max-w-xl content-center gap-5 p-6">
      <div>
        <h1 className="text-heading-lg text-ink">{contest.title}</h1>
        <p className="mt-1 text-body-md text-body">
          Ends at {fmtDateTime(contest.endTime)}. Timer is synchronized with server time.
        </p>
      </div>
      <div className="space-y-2 rounded-xl border border-border bg-card p-5 text-body-md">
        <p className="font-medium text-ink">Exam regulations</p>
        <ul className="list-disc space-y-1 pl-5 text-body">
          <li>Do not switch tabs or windows. Leaving for more than 1.5 seconds counts as 1 violation.</li>
          <li>Do not exit fullscreen mode. Exiting for more than 1.5 seconds counts as 1 violation.</li>
          <li>Do not paste code from external sources. Any paste action immediately triggers a violation.</li>
          <li>
            Exceeding {contest.maxViolationsAllowed} violations results in immediate disqualification and locks
            submissions.
          </li>
          <li>Your code is automatically saved locally and synchronized with the server every 15 seconds.</li>
        </ul>
        {(contest.myViolations ?? 0) > 0 && (
          <p className="pt-1 text-[#8a5a00]">
            You currently have {contest.myViolations}/{contest.maxViolationsAllowed} recorded violations.
          </p>
        )}
      </div>
      <ul className="space-y-1.5 rounded-xl border border-border bg-card p-5" aria-label="Pre-exam System Check">
        <Row ok={online}>{online ? "Stable internet connection" : "Offline: Please check your internet connection"}</Row>
        <Row ok={fsOk} warn>
          {fsOk ? "Browser supports fullscreen mode" : "Fullscreen mode not supported (allowed to proceed)"}
        </Row>
      </ul>
      <label className="flex items-start gap-2 text-body-md text-body">
        <input type="checkbox" className="mt-1 accent-[#171717]" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
        I have read and agree to comply with the exam regulations.
      </label>
      <Button onClick={start} disabled={!agree || !online} className="w-fit">
        Start Exam
      </Button>
    </div>
  );
}
