"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  AlertTriangle,
  Code2,
  Copy,
  Download,
  FolderPlus,
  Layers,
  Play,
  Settings,
} from "lucide-react";
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
 * /ide — Complete setup and user guide for the Mini LeetCode IntelliJ IDEA plugin.
 * Features 5 standardized steps:
 * 1. Download Plugin Binary & Source
 * 2. Install from Disk
 * 3. Server Connection & Personal Access Token
 * 4. Dedicated Workspace Directory Setup
 * 5. Tool Window Workflow (Study Plans, Problem View, Run & Submit)
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
      toast("Token copied to clipboard", "success");
    } catch {
      toast("Copy failed — select the token and copy it manually", "error");
    }
  };

  return (
    <div className={cn(theme.theme, p.page)}>
      <div className={p.main}>
        <h1 className={s.title}>IntelliJ IDEA Plugin Setup & User Guide</h1>
        <p className={s.lead}>
          Bridge Mini LeetCode study plans directly into your local IntelliJ IDEA environment. Unpack stages into
          isolated Maven projects, view problem descriptions, run public test cases, and submit code for real-time
          grading without ever leaving your IDE.
        </p>

        <ol className={s.steps}>
          {/* STEP 1 */}
          <li className={s.step}>
            <h2>1. Download Plugin Binary & Source</h2>
            <p>
              Compatible with <strong>IntelliJ IDEA 2024.1 – 2025.3+</strong> (both Community and Ultimate editions).
            </p>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center", margin: "12px 0" }}>
              <a
                href={PLUGIN_DOWNLOAD_URL}
                className={cn(p.toolBtn)}
                style={{ display: "inline-flex", gap: 8, alignItems: "center", fontWeight: 600 }}
                download
              >
                <Download size={16} aria-hidden="true" /> mini-leetcode-intellij.zip (v0.1.0)
              </a>
            </div>
            <div className={s.callout}>
              <Code2 size={18} className={s.calloutIcon} aria-hidden="true" />
              <div>
                <strong>Plugin Source Code:</strong> The full open-source plugin code is available in the repository at{" "}
                <code>/ide-plugin</code>. Developers can inspect the source or build custom packages via Gradle:
                <div style={{ marginTop: 6 }}>
                  <code>cd ide-plugin &amp;&amp; ./gradlew buildPlugin</code>
                </div>
              </div>
            </div>
          </li>

          {/* STEP 2 */}
          <li className={s.step}>
            <h2>2. Install Plugin in IntelliJ IDEA</h2>
            <p>Install the downloaded ZIP distribution directly into your IDE:</p>
            <ol style={{ paddingLeft: 20, margin: "8px 0", lineHeight: 1.6 }}>
              <li>
                Open IntelliJ IDEA and navigate to <code>Settings</code> (Windows/Linux:{" "}
                <span className={s.shortcut}>Ctrl+Alt+S</span>, macOS: <span className={s.shortcut}>Cmd+,</span>).
              </li>
              <li>
                Select <strong>Plugins</strong> from the left navigation tree.
              </li>
              <li>
                Click the gear icon ⚙ at the top right of the marketplace header and choose{" "}
                <strong>Install Plugin from Disk…</strong>
              </li>
              <li>
                Select the downloaded <code>mini-leetcode-intellij.zip</code> file (<strong>do not unzip it</strong>).
              </li>
              <li>
                Click <strong>Apply</strong> and then <strong>Restart IDE</strong> to activate the plugin.
              </li>
            </ol>
          </li>

          {/* STEP 3 */}
          <li className={s.step}>
            <h2>3. Server Connection &amp; Personal Access Token</h2>
            <p>
              Authenticate the plugin so it can fetch study plan roadmaps, download starter files, and submit solutions
              to the grading engine:
            </p>
            <div className={s.callout}>
              <Settings size={18} className={s.calloutIcon} aria-hidden="true" />
              <div>
                <strong>IntelliJ Settings Path:</strong> Open <code>Settings › Tools › Mini LeetCode</code>. Set{" "}
                <strong>Server URL</strong> to <code>http://localhost:8088</code> (or{" "}
                <code>{origin || "http://localhost:3100"}</code>), paste your token, and click{" "}
                <strong>Test Connection</strong>.
              </div>
            </div>

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
                <button
                  className={p.toolBtn}
                  type="button"
                  onClick={copy}
                  style={{ display: "inline-flex", gap: 6, alignItems: "center" }}
                >
                  <Copy size={15} aria-hidden="true" /> Copy
                </button>
              </div>
            )}

            {tokens.state === "error" && <p role="alert" style={{ color: "var(--error)" }}>{tokens.message}</p>}
            {tokens.state === "ok" && tokens.data.length > 0 && (
              <div aria-label="Your tokens" style={{ marginTop: 12 }}>
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

          {/* STEP 4 */}
          <li className={s.step}>
            <h2>4. Configure a Dedicated Workspace Directory</h2>
            <div className={s.calloutWarning}>
              <FolderPlus size={22} className={s.calloutIcon} aria-hidden="true" />
              <div>
                <strong>Crucial Requirement — Create a Dedicated Folder:</strong>
                <br />
                When you click <strong>Open Stage</strong>, IntelliJ will prompt you with a Directory Chooser. Always{" "}
                <strong>create a clean, dedicated empty folder</strong> (for example:{" "}
                <code>C:\Users\Username\Projects\MiniLeetCode\OOP-Stage-1</code> or <code>~/Projects/MiniLeetCode/Stage-1</code>
                ) and select that folder.
              </div>
            </div>

            <p style={{ marginTop: 10 }}>
              <strong>Why a dedicated folder is required:</strong> The plugin initializes a clean, self-contained Maven project
              within the chosen directory:
            </p>

            <div className={s.fileTree}>
              <div>📁 &lt;Selected Dedicated Folder&gt;/</div>
              <div>&nbsp;&nbsp;├── 📄 pom.xml&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(Maven configuration &amp; JUnit 5 dependencies)</div>
              <div>&nbsp;&nbsp;├── 📁 src/main/java/&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(Java starter code, domain classes &amp; Main.java)</div>
              <div>&nbsp;&nbsp;├── 📁 stages/</div>
              <div>&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;└── 📄 stage-1.md&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(Offline copy of problem statement &amp; invariants)</div>
              <div>&nbsp;&nbsp;└── 📁 .mlc/</div>
              <div>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└── 📄 project.yaml&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(Manifest binding this project to the stage)</div>
            </div>

            <p style={{ fontSize: 13, color: "var(--muted)" }}>
              <em>
                Warning: Do not select an existing project root, personal home folder, or multi-project repository. Doing so would
                unpack files directly into that existing structure.
              </em>
            </p>
          </li>

          {/* STEP 5 */}
          <li className={s.step}>
            <h2>5. Tool Window Workflow: Solve, Run &amp; Submit</h2>
            <p>
              Open the <strong>Mini LeetCode</strong> tool window docked on the right tool window stripe:
            </p>

            <div className={s.featureBox}>
              <div className={s.featureCard}>
                <h3>
                  <Layers size={16} aria-hidden="true" /> [📁 Study Plans] Tab
                </h3>
                <p>
                  Browse all active <Link href="/study-plans" style={{ color: "var(--link)", textDecoration: "underline" }}>study plans</Link>, modules, and stages in a hierarchical tree.
                </p>
                <p style={{ marginTop: 6 }}>
                  <strong>Sequential Stage Gate:</strong> Stages must be solved in sequence. Locked stages (🔒) cannot be opened
                  until all previous stages have been completed with an Accepted score.
                </p>
              </div>

              <div className={s.featureCard}>
                <h3>
                  <Play size={16} aria-hidden="true" /> [📄 Problem View] Tab
                </h3>
                <p>
                  Read full problem requirements, domain invariants, and input/output formats right next to your editor.
                </p>
                <div style={{ marginTop: 8, fontSize: 12.5, lineHeight: 1.5 }}>
                  <div>
                    • <strong>Run</strong> (<span className={s.shortcut}>Ctrl+Alt+R</span>): Executes your code against public
                    test cases. Fast feedback without submitting.
                  </div>
                  <div style={{ marginTop: 4 }}>
                    • <strong>Submit</strong> (<span className={s.shortcut}>Ctrl+Alt+Enter</span>): Submits your project for
                    official grading (hidden edge cases &amp; OOP invariants). Unlocks the next stage upon passing!
                  </div>
                </div>
              </div>
            </div>

            <div className={s.callout}>
              <AlertTriangle size={18} className={s.calloutIcon} aria-hidden="true" />
              <div>
                <strong>Mismatched Project Guard:</strong> The <strong>Run</strong> and <strong>Submit</strong> buttons are
                automatically disabled if your active IntelliJ project window does not match the stage shown in the Problem
                View. Always open and edit the specific stage project you wish to run or submit.
              </div>
            </div>
          </li>
        </ol>
      </div>
    </div>
  );
}
