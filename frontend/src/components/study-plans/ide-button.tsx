"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Download, Loader2, Plug, SquareArrowOutUpRight } from "lucide-react";
import { errorMessage } from "@/lib/api";
import { connectIde, findIde, openInIde, type IdeStatus } from "@/lib/ide-bridge";
import { createAccessToken } from "@/lib/services";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import s from "./ide-button.module.css";

type State =
  | { kind: "checking" }
  | { kind: "missing" }
  | { kind: "ready"; ide: IdeStatus }
  | { kind: "busy"; ide: IdeStatus; label: string };

/**
 * "Open in IntelliJ" một chạm: dò IntelliJ đang chạy ⇒ chưa có thì hướng dẫn cài; có mà chưa kết nối thì
 * "Connect IntelliJ" (tự tạo token và gửi sang plugin, không copy/paste); đã kết nối thì mở đúng stage.
 */
export function IdeButton({ planSlug, itemId, stageNo, className }: {
  planSlug: string; itemId: number; stageNo: number; className?: string;
}) {
  const [state, setState] = useState<State>({ kind: "checking" });

  const detect = useCallback(async () => {
    setState({ kind: "checking" });
    const ide = await findIde();
    setState(ide ? { kind: "ready", ide } : { kind: "missing" });
  }, []);
  useEffect(() => { void detect(); }, [detect]);

  const connect = async (ide: IdeStatus) => {
    setState({ kind: "busy", ide, label: "Connecting…" });
    try {
      const { token } = await createAccessToken("IntelliJ (one-click)", 90);
      await connectIde(ide.port, window.location.origin, token);
      const next = { ...ide, connected: true };
      setState({ kind: "ready", ide: next });
      toast("IntelliJ is connected to your account.", "success");
    } catch (e) {
      setState({ kind: "ready", ide });
      toast(`Could not connect IntelliJ: ${errorMessage(e)}`, "error");
    }
  };

  const open = async (ide: IdeStatus) => {
    setState({ kind: "busy", ide, label: "Opening…" });
    try {
      await openInIde(ide.port, { planSlug, itemId, stageNo });
      toast("IntelliJ is opening the project in a new window, ready to code.", "info");
    } catch (e) {
      toast(`IntelliJ did not open the stage: ${errorMessage(e)}`, "error");
    } finally {
      setState({ kind: "ready", ide });
    }
  };

  if (state.kind === "checking") {
    return (
      <div className={cn(s.box, className)}>
        <button type="button" className={s.primary} disabled><Loader2 size={16} className={s.spin} aria-hidden /> Looking for IntelliJ…</button>
      </div>
    );
  }
  if (state.kind === "missing") {
    return (
      <div className={cn(s.box, className)}>
        <Link href="/ide" className={s.primary}><Download size={16} aria-hidden /> Install the IntelliJ plugin</Link>
        <span className={s.note}>
          Already installed? Start IntelliJ IDEA, then <button type="button" className={s.link} onClick={detect}>check again</button>.
        </span>
      </div>
    );
  }
  const { ide } = state;
  const busy = state.kind === "busy";
  return (
    <div className={cn(s.box, className)}>
      {ide.connected ? (
        <button type="button" className={s.primary} disabled={busy} onClick={() => open(ide)}>
          {busy ? <Loader2 size={16} className={s.spin} aria-hidden /> : <SquareArrowOutUpRight size={16} aria-hidden />}
          {busy ? state.label : "Open in IntelliJ"}
        </button>
      ) : (
        <button type="button" className={s.primary} disabled={busy} onClick={() => connect(ide)}>
          {busy ? <Loader2 size={16} className={s.spin} aria-hidden /> : <Plug size={16} aria-hidden />}
          {busy ? state.label : "Connect IntelliJ"}
        </button>
      )}
      <span className={s.note} role="status">
        <span className={cn(s.dot, ide.connected && s.dotOn)} aria-hidden />
        {ide.connected ? "IntelliJ connected" : "IntelliJ found — connect it to your account first"}
      </span>
    </div>
  );
}
