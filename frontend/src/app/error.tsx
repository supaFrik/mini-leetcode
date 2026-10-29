"use client";
import { Button } from "@/components/ui/button";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="grid min-h-screen place-items-center p-6 text-center">
      <div className="max-w-md space-y-3">
        <h1 className="text-xl font-semibold">Đã có lỗi xảy ra</h1>
        <p className="text-sm text-muted-foreground">{error.message || "Vui lòng thử lại. Nếu lỗi lặp lại, hãy báo cho quản trị viên."}</p>
        <Button onClick={reset}>Thử lại</Button>
      </div>
    </main>
  );
}
