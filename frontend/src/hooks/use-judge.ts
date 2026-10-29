"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, errorMessage } from "@/lib/api";
import { pollSubmission } from "@/lib/polling";
import { fetchSubmissionResults, pollSubmission as fetchPoll, submitCode } from "@/lib/services";
import type { CaseResult, Language, Submission } from "@/types/api";

// Nộp bài → nhận 202 → polling thích ứng (backoff + jitter) → lấy chi tiết từng testcase.
export function useJudge(problemId: number, contestId?: number) {
  const [result, setResult] = useState<Submission | null>(null);
  const [cases, setCases] = useState<CaseResult[] | null>(null);
  const [judging, setJudging] = useState(false);
  const [timedOut, setTimedOut] = useState(false);
  const [error, setError] = useState<{ status?: number; message: string } | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  useEffect(() => () => abortRef.current?.abort(), []); // rời trang thì dừng polling

  const submit = useCallback(async (language: Language, code: string, opts?: { run?: boolean }) => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setJudging(true); setResult(null); setCases(null); setError(null); setTimedOut(false);
    try {
      const created = await submitCode(problemId, { language, code, ...(contestId ? { contestId } : {}) }, opts?.run);
      if (ctrl.signal.aborted) return;
      setResult(created);
      const final = await pollSubmission((signal) => fetchPoll(created.id, signal), { signal: ctrl.signal, onUpdate: (s) => !ctrl.signal.aborted && setResult(s) });
      if (!final) { if (!ctrl.signal.aborted) setTimedOut(true); return; }
      setResult(final);
      fetchSubmissionResults(final.id).then((c) => { if (!ctrl.signal.aborted) setCases(c); }).catch(() => undefined);
    } catch (e) {
      setError({ status: e instanceof ApiError ? e.status : undefined, message: errorMessage(e) });
    } finally {
      if (abortRef.current === ctrl) setJudging(false);
    }
  }, [problemId, contestId]);

  return { result, cases, judging, timedOut, error, submit };
}
