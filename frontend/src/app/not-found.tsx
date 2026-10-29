import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="grid min-h-screen place-items-center p-6 text-center">
      <div className="space-y-3">
        <p className="font-mono text-5xl font-semibold text-primary">404</p>
        <h1 className="text-xl font-semibold">Không tìm thấy trang</h1>
        <p className="text-sm text-muted-foreground">Đường dẫn không tồn tại hoặc đã bị di chuyển.</p>
        <Button asChild><Link href="/">Về trang chủ</Link></Button>
      </div>
    </main>
  );
}
