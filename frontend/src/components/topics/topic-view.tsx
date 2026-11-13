"use client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ArrowRight, Check, Clock, Lightbulb, PartyPopper } from "lucide-react";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { toast } from "@/lib/toast";
import { useLoad } from "@/hooks/use-load";
import { attemptTopicStep, fetchStudyPlan, fetchTopic, setTopicSkipped } from "@/lib/services";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import type {
  ChoiceContent, CodeContent, FillBlankContent, LearnerStep, LearnerTopic, MatchingContent, OrderingContent,
  StepAnswer, TheoryContent,
} from "@/types/api";
import { ChoiceWidget, CodeWidget, FillBlankWidget, MatchingWidget, OrderingWidget } from "./step-widgets";
import t from "./topics.module.css";

type Section = { title: string; markdown: string };

/** Gộp mọi step THEORY rồi tách section theo dòng "## ". Phần trước heading đầu tiên thành "Introduction". */
export function theorySections(steps: LearnerStep[]): Section[] {
  const sections: Section[] = [];
  for (const s of steps.filter((x) => x.type === "THEORY")) {
    let current: Section | null = null;
    for (const line of (s.content as TheoryContent).markdown.split("\n")) {
      const heading = /^##\s+(.+)$/.exec(line);
      if (heading) {
        current = { title: heading[1].trim(), markdown: "" };
        sections.push(current);
      } else {
        if (!current) {
          if (!line.trim()) continue;
          current = { title: "Introduction", markdown: "" };
          sections.push(current);
        }
        current.markdown += line + "\n";
      }
    }
  }
  return sections;
}

const KIND: Record<string, string> = {
  CHOICE: "Quiz", MATCHING: "Matching", ORDERING: "Ordering", FILL_BLANK: "Fill in the blanks", CODE: "Code challenge",
};

type Feedback = { correct: boolean; message: string; explanation?: string | null };
type Answers = { selected?: string[]; order?: string[]; values?: Record<string, string> };

function initialAnswer(step: LearnerStep): Answers {
  switch (step.type) {
    case "MATCHING": return { order: (step.content as MatchingContent).right.map((r) => r.id) };
    case "ORDERING": return { order: (step.content as OrderingContent).items.map((r) => r.id) };
    case "FILL_BLANK": return { values: {} };
    default: return { selected: [] };
  }
}

function toPayload(step: LearnerStep, a: Answers): StepAnswer {
  switch (step.type) {
    case "CHOICE": return { selected: a.selected ?? [] };
    case "MATCHING": {
      const left = (step.content as MatchingContent).left;
      return { pairs: Object.fromEntries(left.map((l, i) => [l.id, (a.order ?? [])[i]])) };
    }
    case "ORDERING": return { order: a.order ?? [] };
    case "FILL_BLANK": return { values: a.values ?? {} };
    default: return {};
  }
}

/** /topics/[slug]: lý thuyết có mục lục + luyện tập nhiều dạng (specs/topics.spec.md). */
export function TopicView({ slug, planSlug, stageNo }: { slug: string; planSlug?: string; stageNo?: number }) {
  const [attempt, setAttempt] = useState(0);
  const load = useLoad(() => fetchTopic(slug), [slug, attempt]);
  const plan = useLoad(() => (planSlug ? fetchStudyPlan(planSlug) : Promise.resolve(null)), [planSlug]);
  const [topic, setTopic] = useState<LearnerTopic | null>(null);
  useEffect(() => { if (load.state === "ok") setTopic(load.data); }, [load]);

  if (load.state === "error") {
    return (
      <div className={cn(theme.theme, p.page)}>
        <div className={p.main}>
          <div className={cn(p.table, p.empty, p.error)} role="alert">
            <h2>Couldn&apos;t load this topic</h2>
            <p>{load.message}</p>
            <button className={cn(p.toolBtn, p.centerBtn)} type="button" onClick={() => setAttempt((n) => n + 1)}>Try again</button>
          </div>
        </div>
      </div>
    );
  }
  if (!topic) {
    return <div className={cn(theme.theme, t.page)} aria-busy="true"><p>Loading topic…</p></div>;
  }

  const planTitle = plan.state === "ok" && plan.data ? plan.data.title : planSlug;
  const backHref = planSlug ? `/study-plans/${planSlug}` : "/study-plans";
  return (
    <div className={theme.theme}>
      <TopicBody key={topic.id} topic={topic} setTopic={setTopic} backHref={backHref}
        crumbs={planSlug ? [{ href: "/study-plans", label: "Study plans" }, { href: backHref, label: planTitle ?? planSlug },
          ...(stageNo ? [{ href: backHref, label: `Stage ${stageNo}` }] : [])] : [{ href: "/study-plans", label: "Study plans" }]} />
    </div>
  );
}

function TopicBody({ topic, setTopic, crumbs, backHref }: {
  topic: LearnerTopic; setTopic: (t: LearnerTopic) => void; crumbs: { href: string; label: string }[]; backHref: string;
}) {
  const sections = useMemo(() => theorySections(topic.steps), [topic.steps]);
  const theorySteps = topic.steps.filter((s) => s.type === "THEORY");
  const practice = topic.steps.filter((s) => s.type !== "THEORY");
  const theoryRead = theorySteps.every((s) => s.passed);
  const [mode, setMode] = useState<"theory" | "practice">(theoryRead && practice.length > 0 ? "practice" : sections.length ? "theory" : "practice");
  const [revealed, setRevealed] = useState(theoryRead ? sections.length : Math.min(1, sections.length));
  const [index, setIndex] = useState(() => Math.max(0, practice.findIndex((s) => !s.passed)));
  const [answers, setAnswers] = useState<Record<number, Answers>>({});
  const [feedback, setFeedback] = useState<Record<number, Feedback>>({});
  const [hint, setHint] = useState<Record<number, boolean>>({});
  const [busy, setBusy] = useState(false);
  const sectionRefs = useRef<Array<HTMLElement | null>>([]);
  const words = sections.reduce((n, s) => n + s.markdown.split(/\s+/).length, 0);

  const patchStep = (stepId: number, passed: boolean, attempts: number, progress: LearnerTopic["progress"], doneSteps: number) =>
    setTopic({ ...topic, progress, doneSteps, steps: topic.steps.map((s) => (s.id === stepId ? { ...s, passed: s.passed || passed, attempts } : s)) });

  /** Đọc tới section cuối ⇒ đánh dấu mọi step THEORY đã đọc. */
  const markTheoryRead = async () => {
    let current = topic;
    for (const s of theorySteps.filter((x) => !x.passed)) {
      try {
        const r = await attemptTopicStep(topic.slug, s.id, {});
        current = { ...current, progress: r.progress, doneSteps: r.doneSteps,
          steps: current.steps.map((x) => (x.id === s.id ? { ...x, passed: true, attempts: r.attempts } : x)) };
      } catch (e) {
        toast(errorMessage(e), "error");
        return;
      }
    }
    setTopic(current);
  };

  const reveal = (count: number) => {
    const next = Math.min(sections.length, Math.max(revealed, count));
    setRevealed(next);
    if (next === sections.length && !theoryRead) void markTheoryRead();
    requestAnimationFrame(() => sectionRefs.current[count - 1]?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  const goPractice = () => {
    if (!theoryRead) void markTheoryRead();
    setRevealed(sections.length);
    setMode("practice");
  };

  const toggleSkip = async () => {
    const skip = topic.progress !== "SKIPPED";
    try {
      await setTopicSkipped(topic.slug, skip);
      setTopic({ ...topic, progress: skip ? "SKIPPED" : topic.doneSteps > 0 ? "IN_PROGRESS" : "NEW" });
      toast(skip ? "Topic skipped — you can come back any time." : "Topic restored", "info");
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const step = practice[index];
  const answer = step ? answers[step.id] ?? initialAnswer(step) : null;
  const setAnswer = (a: Answers) => step && setAnswers({ ...answers, [step.id]: a });
  const fb = step ? feedback[step.id] : undefined;
  const allDone = topic.progress === "DONE";

  const submit = async () => {
    if (!step || !answer) return;
    setBusy(true);
    try {
      const r = await attemptTopicStep(topic.slug, step.id, toPayload(step, answer));
      setFeedback({ ...feedback, [step.id]: { correct: r.correct, message: r.message, explanation: r.explanation } });
      patchStep(step.id, r.stepPassed, r.attempts, r.progress, r.doneSteps);
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  const nextStep = () => {
    const after = practice.findIndex((s, i) => i > index && !s.passed);
    const before = practice.findIndex((s) => !s.passed && s.id !== step?.id);
    setIndex(after >= 0 ? after : before >= 0 ? before : Math.min(index + 1, practice.length - 1));
  };

  return (
    <div className={t.page}>
      <nav className={t.crumbs} aria-label="Breadcrumb">
        {crumbs.map((c, i) => <span key={i}>{i > 0 && " → "}<Link href={c.href}>{c.label}</Link></span>)}
      </nav>
      <div className={t.head}>
        <div>
          <h1>{topic.title}</h1>
          {topic.progress === "SKIPPED" && <span className={t.tag}>Skipped</span>}
        </div>
        <div className={t.headActions}>
          <button type="button" className={t.secondary} onClick={toggleSkip}>
            {topic.progress === "SKIPPED" ? "Undo skip" : "Skip topic"}
          </button>
          {sections.length > 0 && practice.length > 0 && (mode === "theory" ? (
            <button type="button" className={cn(t.primary, t.dark)} onClick={goPractice}>Go to practice</button>
          ) : (
            <button type="button" className={cn(t.primary, t.dark)} onClick={() => setMode("theory")}>Theory</button>
          ))}
        </div>
      </div>

      <div className={t.segments} aria-label={`${topic.doneSteps} of ${topic.stepCount} steps done`}>
        {topic.steps.map((s) => (
          <button key={s.id} type="button" title={s.title}
            aria-label={`${s.type === "THEORY" ? "Theory" : KIND[s.type]}: ${s.title}${s.passed ? " (done)" : ""}`}
            className={cn(t.segment, s.passed && t.segmentDone, mode === "practice" && s.id === step?.id && t.segmentActive)}
            onClick={() => {
              if (s.type === "THEORY") setMode("theory");
              else { setMode("practice"); setIndex(practice.indexOf(s)); }
            }} />
        ))}
      </div>

      {mode === "theory" ? (
        <section className={cn(t.card, t.theory)} aria-label="Theory">
          <nav className={t.toc} aria-label="Table of contents">
            <h2>Table of contents</h2>
            <ol>
              {sections.map((s, i) => (
                <li key={i}>
                  <button type="button" className={cn(i < revealed ? i === revealed - 1 && t.tocActive : t.tocLocked)}
                    onClick={() => reveal(i + 1)}>{s.title}</button>
                </li>
              ))}
            </ol>
          </nav>
          <div>
            <span className={t.readTime}><Clock size={14} aria-hidden /> {Math.max(1, Math.round(words / 200))} minutes read</span>
            <div className={t.prose}>
              {sections.slice(0, revealed).map((s, i) => (
                <section key={i} ref={(el) => { sectionRefs.current[i] = el; }} aria-label={s.title}>
                  {s.title !== "Introduction" && <h2>{s.title}</h2>}
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{s.markdown}</ReactMarkdown>
                </section>
              ))}
            </div>
            <div className={t.more}>
              {revealed < sections.length ? (
                <>
                  <button type="button" className={cn(t.primary, t.moreButton)} onClick={() => reveal(revealed + 1)}>Read more</button>
                  <button type="button" className={t.linkButton} onClick={() => reveal(sections.length)}>
                    Expand all {sections.length} sections
                  </button>
                </>
              ) : practice.length > 0 ? (
                <button type="button" className={cn(t.primary, t.moreButton)} onClick={goPractice}>
                  Go to practice <ArrowRight size={16} aria-hidden />
                </button>
              ) : (
                <Link href={backHref} className={cn(t.primary, t.moreButton)}>Back to the plan</Link>
              )}
            </div>
          </div>
        </section>
      ) : allDone && !fb ? (
        <section className={cn(t.card, t.complete)} aria-label="Topic completed">
          <PartyPopper size={36} aria-hidden />
          <h2>Topic completed</h2>
          <p>You finished every step of “{topic.title}”. Keep going with the next topic or the project stage.</p>
          <Link href={backHref} className={t.primary}>Back to the plan <ArrowRight size={15} aria-hidden /></Link>
        </section>
      ) : step && answer ? (
        <section className={t.card} aria-label={`Step ${index + 1} of ${practice.length}`}>
          <div className={t.stepHead}>
            <span className={t.stepKind}>{KIND[step.type]} · {index + 1}/{practice.length}</span>
            {step.passed && <span className={t.solved}><Check size={15} aria-hidden /> Solved</span>}
          </div>
          <div className={t.stepHead}><h2>{step.title}</h2></div>
          {"prompt" in step.content && (
            <div className={t.prose}><ReactMarkdown remarkPlugins={[remarkGfm]}>{(step.content as ChoiceContent).prompt}</ReactMarkdown></div>
          )}
          {step.type === "CODE" && (step.content as CodeContent).intro && (
            <div className={t.prose}><p>{(step.content as CodeContent).intro}</p></div>
          )}
          {"hint" in step.content && (step.content as ChoiceContent).hint && (
            hint[step.id] ? <p className={t.hint}><Lightbulb size={14} aria-hidden /> {(step.content as ChoiceContent).hint}</p>
              : <button type="button" className={t.secondary} onClick={() => setHint({ ...hint, [step.id]: true })}>
                <Lightbulb size={14} aria-hidden /> See hint</button>
          )}

          {step.type === "CHOICE" && (
            <ChoiceWidget content={step.content as ChoiceContent} selected={answer.selected ?? []} disabled={busy}
              onChange={(selected) => setAnswer({ selected })} />
          )}
          {step.type === "MATCHING" && (
            <MatchingWidget left={(step.content as MatchingContent).left} right={(step.content as MatchingContent).right}
              order={answer.order ?? []} disabled={busy} onChange={(order) => setAnswer({ order })} />
          )}
          {step.type === "ORDERING" && (
            <OrderingWidget items={(step.content as OrderingContent).items} order={answer.order ?? []} disabled={busy}
              onChange={(order) => setAnswer({ order })} />
          )}
          {step.type === "FILL_BLANK" && (
            <FillBlankWidget content={step.content as FillBlankContent} values={answer.values ?? {}} disabled={busy}
              onChange={(values) => setAnswer({ values })} />
          )}
          {step.type === "CODE" && <CodeWidget content={step.content as CodeContent} passed={step.passed} />}

          {fb && (
            <div className={cn(t.feedback, fb.correct ? t.feedbackOk : t.feedbackBad)} role="status" aria-live="polite">
              <strong>{fb.correct ? "Correct!" : "Wrong answer"}</strong>
              {!fb.correct && fb.message}
              {fb.correct && fb.explanation && <div>{fb.explanation}</div>}
            </div>
          )}

          <div className={t.footer}>
            {step.type !== "CODE" && !(fb?.correct) && (
              <button type="button" className={t.primary} disabled={busy || (step.type === "CHOICE" && !(answer.selected ?? []).length)}
                onClick={submit}>{busy ? "Checking…" : fb ? "Try again" : "Submit"}</button>
            )}
            {(fb?.correct || (step.passed && step.type === "CODE")) && (
              allDone ? (
                <button type="button" className={t.primary} onClick={() => setFeedback({})}>Finish topic <ArrowRight size={15} aria-hidden /></button>
              ) : (
                <button type="button" className={t.primary} onClick={nextStep}>Continue <ArrowRight size={15} aria-hidden /></button>
              )
            )}
            {practice.length > 1 && !(fb?.correct) && (
              <button type="button" className={t.linkButton} onClick={nextStep}>Skip problem</button>
            )}
          </div>
        </section>
      ) : (
        <section className={t.card}><p>This topic has no practice steps yet.</p></section>
      )}
    </div>
  );
}
