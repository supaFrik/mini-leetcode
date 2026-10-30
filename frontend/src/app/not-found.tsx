import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="grid min-h-screen place-items-center bg-background p-6 text-center">
      <div className="space-y-4">
        <p className="font-mono text-5xl font-semibold tracking-tight text-ink">404</p>
        <h1 className="text-heading-md text-ink">Page not found</h1>
        <p className="text-body-md text-body">
          The page you are looking for does not exist or has been moved.
        </p>
        <Button asChild className="mt-1">
          <Link href="/">Back to Home</Link>
        </Button>
      </div>
    </main>
  );
}
