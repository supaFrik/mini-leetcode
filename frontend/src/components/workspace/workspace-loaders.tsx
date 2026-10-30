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

const startedContests = new Set<number>();

function isContestStarted(id: number): boolean {
  if (startedContests.has(id)) return true;
  if (typeof window !== "undefined") {
    try { return sessionStorage.getItem(`exam_started_${id}`) === "true"; }
    catch { return false; }
  }
  return false;
}

function markContestStarted(id: number): void {
  startedContests.add(id);
  if (typeof window !== "undefined") {
    try { sessionStorage.setItem(`exam_started_${id}`, "true"); }
    catch { /* ignore */ }
  }
}

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

  if (error) return <Message title={error}><Button onClick={retry} variant="outline">Try Again</Button></Message>;
  if (!data || !user) return <Message title="Loading problem…" />;
  const mode = user.role !== "USER" && sp.get("as") !== "student" ? "instructor" : "student";
  return <Workspace key={`${data.problem.id}-${mode}`} problem={data.problem} testCases={data.cases} mode={mode} role={user.role} />;
}

export function ExamLoader({ contestId, problemId }: { contestId: number; problemId: string }) {
  const [started, setStarted] = useState(() => isContestStarted(contestId));
  const { user } = useUser();
  const { data, error, retry } = useLoaded<{ contest: ContestInfo; problem: ProblemFull; cases: TestCase[] }>(async () => {
    const contest = await fetchContest(contestId);
    const problem = await fetchProblem(problemId);
    return { contest, problem, cases: await fetchTestCases(problem.id) };
  }, [contestId, problemId]);

  if (error) return <Message title={error}><Button onClick={retry} variant="outline">Try Again</Button></Message>;
  if (!data || !user) return <Message title="Loading exam room…" />;
  const { contest, problem, cases } = data;

  if (!contest.myStatus) {
    return (
      <Message title="You are not registered for this contest">
        <Button asChild variant="outline"><Link href={`/exam/${contest.id}`}>Go to contest page to register</Link></Button>
      </Message>
    );
  }
  if (contest.myStatus === "DISQUALIFIED") return <DisqualifiedOverlay contestId={contest.id} />;
  if (contest.status !== "RUNNING") {
    return (
      <Message title={contest.status === "SCHEDULED" ? "Contest has not started yet" : "Contest has ended"}>
        <Button asChild variant="outline"><Link href={`/exam/${contest.id}`}>Back to contest</Link></Button>
      </Message>
    );
  }
  if (!contest.problems.some((p) => p.id === problem.id)) return <Message title="This problem does not belong to the contest" />;
  if (!started) return <ExamGate contest={contest} onStart={() => { markContestStarted(contestId); setStarted(true); }} />;
  return <Workspace key={`${contest.id}-${problem.id}`} problem={problem} testCases={cases} mode="exam" contest={contest} role={user.role} />;
}
