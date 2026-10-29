"use client";
import Link from "next/link";
import { AlertDialog, AlertDialogAction, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

// AlertDialog (không phải Dialog): không đóng khi bấm ra ngoài / nhấn Esc, thí sinh BẮT BUỘC bấm xác nhận.
export function ViolationDialog({ warning, onClose }: { warning: { count: number; max: number } | null; onClose: () => void }) {
  return (
    <AlertDialog open={!!warning}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-destructive">Cảnh báo vi phạm quy chế thi</AlertDialogTitle>
          <AlertDialogDescription>
            Bạn đã rời khỏi giao diện thi {warning?.count}/{warning?.max} lần. Dữ liệu vi phạm đã được ghi nhận.
            {warning && warning.count >= warning.max - 1 && " Lần vi phạm tiếp theo bạn sẽ bị loại khỏi kỳ thi."}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction onClick={onClose}>Tôi hiểu và tiếp tục</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function Overlay({ title, children, tone = "destructive" }: { title: string; children: React.ReactNode; tone?: "destructive" | "amber" }) {
  return (
    <div role="alertdialog" aria-modal="true" aria-label={title} className="fixed inset-0 z-50 grid place-items-center bg-background/95 p-6 text-center">
      <div className="max-w-md space-y-3">
        <h2 className={tone === "destructive" ? "text-xl font-semibold text-destructive" : "text-xl font-semibold text-amber-500"}>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function DisqualifiedOverlay({ contestId }: { contestId: number }) {
  return (
    <Overlay title="Bài thi đã bị khoá">
      <p className="text-sm text-muted-foreground">Bài thi của bạn đã bị khóa do vi phạm quy chế thi quá số lần quy định. Bạn không thể nộp thêm bài.</p>
      <Button asChild variant="outline"><Link href={`/exam/${contestId}`}>Về trang kỳ thi</Link></Button>
    </Overlay>
  );
}

export function TimeUpOverlay({ contestId }: { contestId: number }) {
  return (
    <Overlay title="Đã hết giờ làm bài" tone="amber">
      <p className="text-sm text-muted-foreground">Hệ thống đã khoá việc nộp bài. Bản nháp mới nhất của bạn đã được lưu.</p>
      <Button asChild variant="outline"><Link href={`/exam/${contestId}/leaderboard`}>Xem bảng xếp hạng</Link></Button>
    </Overlay>
  );
}

export function FullscreenOverlay({ onEnter }: { onEnter: () => void }) {
  return (
    <Overlay title="Bạn đã thoát chế độ toàn màn hình" tone="amber">
      <p className="text-sm text-muted-foreground">Vui lòng quay lại toàn màn hình để tiếp tục làm bài. Đồng hồ vẫn đang chạy.</p>
      <Button onClick={onEnter}>Vào lại toàn màn hình</Button>
    </Overlay>
  );
}
