"use client";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Workspace } from "@/components/workspace/workspace";
// Workspace ở mode="exam" gọi useProctoring(contestId, true) bên trong,
// truyền reportPaste vào <CodeEditor onPasteAttempt>, và render <ViolationDialog/> + <DisqualifiedOverlay/>.

export default function ExamPage({ params }: { params: { contestId: string; problemId: string } }) {
  const [started, setStarted] = useState(false);
  const start = async () => {
    await document.documentElement.requestFullscreen().catch(() => {});   // phải gọi từ cú click
    setStarted(true);
  };

  if (!started) return (
    <div className="grid h-[60vh] place-items-center text-center">
      <div className="space-y-3">
        <h1 className="text-xl font-semibold">Quy chế phòng thi</h1>
        <p className="max-w-md text-sm text-muted-foreground">
          Không rời tab, không dán (paste) code. Vi phạm 3 lần sẽ bị loại. Bấm bắt đầu để vào chế độ toàn màn hình.
        </p>
        <Button onClick={start}>Bắt đầu làm bài</Button>
      </div>
    </div>
  );
  const problem = { id: Number(params.problemId), title: "Two Sum", difficulty: "EASY" as const, descriptionMd: "..." };
  return <Workspace problem={problem} mode="exam" contestId={Number(params.contestId)} />;
}