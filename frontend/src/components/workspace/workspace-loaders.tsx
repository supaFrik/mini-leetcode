"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { useUser } from "@/hooks/use-user";
import { errorMessage } from "@/lib/api";
import { fetchContest, fetchProblem, fetchTestCases } from "@/lib/services";
import { ExamGate } from "./exam-gate";
import { DisqualifiedOverlay } from "./violation-dialog";
import { Workspace } from "./workspace";
import type { ContestInfo, ProblemFull, TestCase } from "@/types/api";

// Next.js tạo lại cây component khi tham số route đổi (chuyển đề A → B), nên state `started` trong component sẽ mất
// và cổng quy chế hiện lại. Biến module sống qua điều hướng trong app nhưng mất khi F5 (F5 phải qua cổng lại).
const startedContests = new Set<number>();

function Message({ title, children }: { title: string; children?: React.ReactNode }) {
  return <div className="mx-auto grid h-full max-w-md place-content-center gap-3 p-6 text-center"><p className="font-medium">{title}</p>{children}</div>;
}

function useLoaded<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let off = false;
    setData(null); setError(null);
    load().then((d) => { if (!off) setData(d); }).catch((e) => { if (!off) setError(errorMessage(e)); });
    return () => { off = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);
  return { data, error, retry: () => setAttempt((a) => a + 1) };
}

export function PracticeLoader({ slug }: { slug: string }) {
  const sp = useSearchParams();
  const { user } = useUser();
  const { data, error, retry } = useLoaded(async () => {
    const problem = await fetchProblem(slug);
    return { problem, cases: await fetchTestCases(problem.id) };
  }, [slug]);

  if (error) return <Message title={error}><Button onClick={retry} variant="outline">Thử lại</Button></Message>;
  if (!data || !user) return <Message title="Đang tải đề bài…" />;
  // Giảng viên/Admin thấy giao diện soạn đề, trừ khi xem thử bằng ?as=student
  const mode = user.role !== "USER" && sp.get("as") !== "student" ? "instructor" : "student";
  return <Workspace key={`${data.problem.id}-${mode}`} problem={data.problem} testCases={data.cases} mode={mode} role={user.role} />;
}

export function ExamLoader({ contestId, problemId }: { contestId: number; problemId: string }) {
  const [started, setStarted] = useState(() => startedContests.has(contestId));
  const { user } = useUser();
  const { data, error, retry } = useLoaded<{ contest: ContestInfo; problem: ProblemFull; cases: TestCase[] }>(async () => {
    const contest = await fetchContest(contestId);
    const problem = await fetchProblem(problemId);
    return { contest, problem, cases: await fetchTestCases(problem.id) };
  }, [contestId, problemId]);

  if (error) return <Message title={error}><Button onClick={retry} variant="outline">Thử lại</Button></Message>;
  if (!data || !user) return <Message title="Đang tải phòng thi…" />;
  const { contest, problem, cases } = data;

  if (!contest.myStatus) {
    return (
      <Message title="Bạn chưa đăng ký kỳ thi này">
        <Button asChild variant="outline"><Link href={`/exam/${contest.id}`}>Về trang kỳ thi để đăng ký</Link></Button>
      </Message>
    );
  }
  if (contest.myStatus === "DISQUALIFIED") return <DisqualifiedOverlay contestId={contest.id} />;
  if (contest.status !== "RUNNING") {
    return (
      <Message title={contest.status === "SCHEDULED" ? "Kỳ thi chưa bắt đầu" : "Kỳ thi đã kết thúc"}>
        <Button asChild variant="outline"><Link href={`/exam/${contest.id}`}>Về trang kỳ thi</Link></Button>
      </Message>
    );
  }
  if (!contest.problems.some((p) => p.id === problem.id)) return <Message title="Đề bài này không thuộc kỳ thi" />;
  if (!started) return <ExamGate contest={contest} onStart={() => { startedContests.add(contestId); setStarted(true); }} />;
  return <Workspace key={`${contest.id}-${problem.id}`} problem={problem} testCases={cases} mode="exam" contest={contest} role={user.role} />;
}
