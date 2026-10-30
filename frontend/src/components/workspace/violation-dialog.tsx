"use client";
import Link from "next/link";
import { AlertDialog, AlertDialogAction, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

export function ViolationDialog({ warning, onClose }: { warning: { count: number; max: number } | null; onClose: () => void }) {
  return (
    <AlertDialog open={!!warning}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-destructive">Exam Regulation Warning</AlertDialogTitle>
          <AlertDialogDescription>
            You have left the exam window {warning?.count}/{warning?.max} times. Violation incident has been recorded by the server.
            {warning && warning.count >= warning.max - 1 && " You will be disqualified on your next violation."}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction onClick={onClose}>I understand and agree to continue</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function Overlay({ title, children, tone = "destructive" }: { title: string; children: React.ReactNode; tone?: "destructive" | "amber" }) {
  return (
    <div role="alertdialog" aria-modal="true" aria-label={title} className="fixed inset-0 z-50 grid place-items-center bg-background/95 p-6 text-center">
      <div className="max-w-md space-y-4">
        <h2 className={tone === "destructive" ? "text-heading-md text-destructive" : "text-heading-md text-[#8a5a00]"}>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function DisqualifiedOverlay({ contestId }: { contestId: number }) {
  return (
    <Overlay title="Exam Locked">
      <p className="text-body-md text-body">Your exam session has been locked due to exceeding allowed violations. You can no longer submit code.</p>
      <Button asChild variant="outline"><Link href={`/exam/${contestId}`}>Return to Contest</Link></Button>
    </Overlay>
  );
}

export function TimeUpOverlay({ contestId }: { contestId: number }) {
  return (
    <Overlay title="Contest Time Expired" tone="amber">
      <p className="text-body-md text-body">The contest has ended. Your latest draft has been saved.</p>
      <Button asChild variant="outline"><Link href={`/exam/${contestId}/leaderboard`}>View Leaderboard</Link></Button>
    </Overlay>
  );
}

export function FullscreenOverlay({ onEnter }: { onEnter: () => void }) {
  return (
    <Overlay title="Fullscreen Mode Exited" tone="amber">
      <p className="text-body-md text-body">Please return to fullscreen mode to continue the exam. The exam timer is still running.</p>
      <Button onClick={onEnter}>Re-enter Fullscreen</Button>
    </Overlay>
  );
}
