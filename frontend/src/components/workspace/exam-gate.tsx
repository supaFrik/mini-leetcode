"use client";
import { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useOnline } from "@/hooks/use-online";
import { fmtDateTime } from "@/lib/format";
import type { ContestInfo } from "@/types/api";

function Row({ ok, warn, children }: { ok: boolean; warn?: boolean; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-2">
      <span className={`grid h-4 w-4 place-items-center rounded-full text-white ${ok ? "bg-primary" : warn ? "bg-amber-500" : "bg-destructive"}`}>{ok ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}</span>
      {children}
    </li>
  );
}

// requestFullscreen() chỉ được phép khi gọi trực tiếp từ một cú click → cần màn hình "Bắt đầu làm bài".
export function ExamGate({ contest, onStart }: { contest: ContestInfo; onStart: () => void }) {
  const online = useOnline();
  const [agree, setAgree] = useState(false);
  const [fsOk, setFsOk] = useState(true);
  useEffect(() => setFsOk(document.fullscreenEnabled), []);

  const start = async () => {
    try { await document.documentElement.requestFullscreen(); } catch { /* trình duyệt từ chối: giao diện sẽ yêu cầu vào lại */ }
    onStart();
  };
  return (
    <div className="mx-auto grid min-h-full max-w-xl content-center gap-4 p-6">
      <h1 className="text-2xl font-semibold">{contest.title}</h1>
      <p className="text-sm text-muted-foreground">Kết thúc lúc {fmtDateTime(contest.endTime)}. Đồng hồ tính theo giờ máy chủ.</p>
      <div className="space-y-2 rounded-lg border bg-card p-4 text-sm">
        <p className="font-medium">Quy chế phòng thi</p>
        <ul className="list-disc space-y-1 pl-5 text-muted-foreground">
          <li>Không rời tab hoặc đổi cửa sổ. Rời quá 1,5 giây tính là 1 lần vi phạm.</li>
          <li>Không thoát chế độ toàn màn hình. Thoát quá 1,5 giây tính là 1 lần vi phạm.</li>
          <li>Không dán (paste) code từ bên ngoài. Mỗi lần dán tính ngay 1 lần vi phạm.</li>
          <li>Vi phạm {contest.maxViolationsAllowed} lần sẽ bị loại khỏi kỳ thi và không thể nộp bài.</li>
          <li>Bài làm được tự động lưu cục bộ và đồng bộ lên máy chủ mỗi 15 giây.</li>
        </ul>
        {(contest.myViolations ?? 0) > 0 && <p className="pt-1 text-amber-600 dark:text-amber-400">Bạn đã có {contest.myViolations}/{contest.maxViolationsAllowed} lần vi phạm.</p>}
      </div>
      <ul className="space-y-1.5 rounded-lg border bg-card p-4 text-sm" aria-label="Kiểm tra trước khi thi">
        <Row ok={online}>{online ? "Kết nối mạng ổn định" : "Đang mất mạng, hãy kiểm tra đường truyền"}</Row>
        <Row ok={fsOk} warn>{fsOk ? "Trình duyệt hỗ trợ toàn màn hình" : "Trình duyệt không hỗ trợ toàn màn hình (vẫn được vào thi)"}</Row>
      </ul>
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" className="mt-1" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
        Tôi đã đọc và đồng ý tuân thủ quy chế phòng thi.
      </label>
      <Button onClick={start} disabled={!agree || !online}>Bắt đầu làm bài</Button>
    </div>
  );
}
