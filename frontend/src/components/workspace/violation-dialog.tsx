"use client";
import { AlertDialog, AlertDialogAction, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";

export function ViolationDialog({ warning, onClose }:
  { warning: { count: number; max: number } | null; onClose: () => void }) {
  return (
    <AlertDialog open={!!warning}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-destructive">Cảnh báo vi phạm</AlertDialogTitle>
          <AlertDialogDescription>
            Bạn đã rời khỏi giao diện thi {warning?.count}/{warning?.max} lần. Dữ liệu vi phạm đã được ghi nhận.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction onClick={onClose}>Tôi hiểu và tiếp tục</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

export function DisqualifiedOverlay() {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-background/95 p-6 text-center">
      <div className="max-w-md space-y-2">
        <h2 className="text-xl font-semibold text-destructive">Bài thi đã bị khoá</h2>
        <p className="text-sm text-muted-foreground">
          Bài thi của bạn đã bị khóa do vi phạm quy chế thi quá số lần quy định.
        </p>
      </div>
    </div>
  );
}