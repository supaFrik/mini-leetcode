"use client";
import { Button } from "@/components/ui/button";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="grid min-h-screen place-items-center p-6 text-center">
      <div className="max-w-md space-y-3">
        <h1 className="text-xl font-semibold">An unexpected error occurred</h1>
        <p className="text-sm text-muted-foreground">{error.message || "Please try again. If the issue persists, contact an administrator."}</p>
        <Button onClick={reset}>Try Again</Button>
      </div>
    </main>
  );
}
