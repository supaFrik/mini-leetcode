"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Copy, Download } from "lucide-react";
import { cn } from "@/lib/utils";
import { errorMessage } from "@/lib/api";
import { parseApiDate } from "@/lib/datetime";
import { toast } from "@/lib/toast";
import { useLoad } from "@/hooks/use-load";
import { createAccessToken, fetchAccessTokens, PLUGIN_DOWNLOAD_URL, revokeAccessToken } from "@/lib/services";
import theme from "@/components/layout/plex-theme.module.css";
import p from "@/components/problems/problems.module.css";
import s from "./study-plans.module.css";
import type { AccessToken } from "@/types/api";

const fmtDate = (iso?: string | null) => {
  const t = parseApiDate(iso ?? undefined);
  return Number.isNaN(t) ? "—" : new Date(t).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
};
const isActive = (t: AccessToken) => !t.revoked && parseApiDate(t.expiresAt) > Date.now();

/**
 * /ide — 4 bước dùng plugin IntelliJ (specs/study-plan.spec.md, slice 6): tải, cài, tạo token, kết nối.
 * Token chỉ hiện một lần ngay khi tạo; server chỉ lưu SHA-256.
 */
export function IdeSetup() {
  const [attempt, setAttempt] = useState(0);
  const tokens = useLoad(fetchAccessTokens, [attempt]);
  const [name, setName] = useState("IntelliJ IDEA");
  const [days, setDays] = useState(90);
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<string | null>(null);
  const [origin, setOrigin] = useState("");
  useEffect(() => setOrigin(window.location.origin), []);

  const create = async () => {
    setBusy(true);
    try {
      const r = await createAccessToken(name.trim() || "IntelliJ IDEA", days);
      setCreated(r.token);
      setAttempt((n) => n + 1);
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (t: AccessToken) => {
    try {
      await revokeAccessToken(t.id);
      toast(`Revoked "${t.name}". The plugin will ask for a new token.`, "success");
      setAttempt((n) => n + 1);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const copy = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created);
      toast("Token copied", "success");
    } catch {
      toast("Copy failed — select the token and copy it manually", "error");
    }
  };

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <h1 className={s.title}>IntelliJ plugin</h1>
        <p className={s.lead}>
          Open study plan stages as Maven projects, read the statement next to your code and press <b>Check</b> to have
          the server judge it — without leaving IntelliJ IDEA.
        </p>

        <ol className={s.steps}>
          <li className={s.step}>
            <h2>1. Download the plugin</h2>
            <p>Requires IntelliJ IDEA 2025.3 or newer (Community or Ultimate).</p>
            <a href={PLUGIN_DOWNLOAD_URL} className={cn(p.toolBtn)} style={{ display: "inline-flex", gap: 8, alignItems: "center" }} download>
              <Download size={16} aria-hidden="true" /> mini-leetcode-intellij.zip
            </a>
          </li>

          <li className={s.step}>
            <h2>2. Install it</h2>
            <p>
              In IntelliJ: <code>Settings › Plugins › ⚙ › Install Plugin from Disk…</code>, choose the ZIP (do not unzip it),
              then restart the IDE.
            </p>
          </li>

          <li className={s.step}>
            <h2>3. Create a personal access token</h2>
            <p>The plugin signs in with this token instead of your password. It is shown <b>once</b> — copy it now.</p>
            <div className={s.form}>
              <label>
                Name
                <input className={p.input} value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
              </label>
              <label>
                Expires in
                <select className={p.select} value={days} onChange={(e) => setDays(Number(e.target.value))}>
                  <option value={30}>30 days</option>
                  <option value={90}>90 days</option>
                  <option value={365}>1 year</option>
                </select>
              </label>
              <button className={p.toolBtn} type="button" onClick={create} disabled={busy}>
                {busy ? "Creating…" : "Create token"}
              </button>
            </div>

            {created && (
              <div className={s.secret} role="status">
                <code data-testid="new-token">{created}</code>
                <button className={p.toolBtn} type="button" onClick={copy} style={{ display: "inline-flex", gap: 6, alignItems: "center" }}>
                  <Copy size={15} aria-hidden="true" /> Copy
                </button>
              </div>
            )}

            {tokens.state === "error" && <p role="alert" style={{ color: "var(--error)" }}>{tokens.message}</p>}
            {tokens.state === "ok" && tokens.data.length > 0 && (
              <div aria-label="Your tokens">
                {tokens.data.map((t) => (
                  <div key={t.id} className={s.tokenRow}>
                    <span>{t.name}</span>
                    <span>created {fmtDate(t.createdAt)}</span>
                    <span>{t.revoked ? "revoked" : isActive(t) ? `expires ${fmtDate(t.expiresAt)}` : "expired"}</span>
                    <span>
                      {isActive(t) && (
                        <button className={s.danger} type="button" onClick={() => revoke(t)} aria-label={`Revoke ${t.name}`}>
                          Revoke
                        </button>
                      )}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </li>

          <li className={s.step}>
            <h2>4. Connect and start</h2>
            <p>
              <code>Settings › Tools › Mini LeetCode</code>: Server URL <code>{origin || "this site's address"}</code>, paste
              the token, press <b>Test connection</b>. Then open the <b>Mini LeetCode</b> tool window, pick a stage of a{" "}
              <Link href="/study-plans">study plan</Link>, code, and use <b>Check Stage</b> / <b>Next Stage</b> on the toolbar.
            </p>
          </li>
        </ol>
      </div>
    </div>
  );
}
