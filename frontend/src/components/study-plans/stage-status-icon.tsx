import { Check, CircleDot, Circle, Lock, Wrench } from "lucide-react";
import type { StudyPlanStage } from "@/types/api";
import s from "./study-plans.module.css";

/** Status icon of a stage, shared by the plan page rows and the topic timeline. */
export function StageIcon({ stage }: { stage: StudyPlanStage }) {
  if (!stage.published) return <Wrench size={18} className={s.locked} aria-label="Under maintenance" />;
  switch (stage.status) {
    case "DONE": return <Check size={18} strokeWidth={2.4} className={s.iconDone} aria-label="Done" />;
    case "IN_PROGRESS": return <CircleDot size={18} className={s.iconProgress} aria-label="In progress" />;
    case "LOCKED": return <Lock size={16} className={s.locked} aria-label="Locked" />;
    default: return <Circle size={18} className={s.locked} aria-label="Not started" />;
  }
}
