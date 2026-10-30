"use client";
import { Button } from "@/components/ui/button";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="grid min-h-screen place-items-center bg-background p-6 text-center">
      <div className="max-w-md space-y-4">
        <h1 className="text-heading-md text-ink">An unexpected error occurred</h1>
        <p className="text-body-md text-body">
          {error.message || "Please try again. If the issue persists, contact an administrator."}
        </p>
        <Button onClick={reset}>Try Again</Button>
      </div>
    </main>
  );
}
