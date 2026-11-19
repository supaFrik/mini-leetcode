"use client";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api";
import { fetchSettings, saveSettings } from "@/lib/services";
import { toast } from "@/lib/toast";
import type { SiteSettings } from "@/types/api";

type Field = { key: string; label: string; hint?: string; kind: "text" | "toggle" | "number" | "languages" };

// Chỉ banner bảo trì và bật/tắt đăng ký có hiệu lực thật; các mục khác là giá trị mặc định được lưu lại.
const GROUPS: { title: string; fields: Field[] }[] = [
  {
    title: "General",
    fields: [
      { key: "siteName", label: "Site name", kind: "text" },
      { key: "tagline", label: "Tagline", kind: "text" },
      { key: "maintenanceMode", label: "Maintenance mode", hint: "Shows the banner below on every page.", kind: "toggle" },
      { key: "bannerMessage", label: "Site-wide banner", hint: "Shown while maintenance mode is on.", kind: "text" },
    ],
  },
  {
    title: "Registration",
    fields: [{ key: "registrationOpen", label: "Allow new sign-ups", kind: "toggle" }],
  },
  {
    title: "Judge",
    fields: [
      { key: "allowedLanguages", label: "Allowed languages", kind: "languages" },
      { key: "defaultTimeLimitMs", label: "Default time limit (ms)", kind: "number" },
      { key: "defaultMemoryLimitMb", label: "Default memory limit (MB)", kind: "number" },
    ],
  },
  {
    title: "Contest",
    fields: [
      { key: "defaultContestMinutes", label: "Default duration (minutes)", kind: "number" },
      { key: "defaultMaxViolations", label: "Max proctoring violations", kind: "number" },
    ],
  },
];
const LANGUAGES = ["JAVA", "PYTHON"];

/** /admin/settings: cài đặt hệ thống (chỉ ADMIN). */
export function AdminSettingsView() {
  const [values, setValues] = useState<SiteSettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchSettings().then(setValues).catch((e) => setError(errorMessage(e)));
  }, []);

  const set = (key: string, value: string) => setValues((v) => (v ? { ...v, [key]: value } : v));

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!values) return;
    setSaving(true);
    try {
      setValues(await saveSettings(values));
      toast("Settings saved.", "success");
    } catch (err) {
      toast(errorMessage(err), "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-5 px-8 py-6">
      <div>
        <h1 className="text-heading-md text-ink">Settings</h1>
        <p className="text-body-md text-mute">Site-wide configuration. Maintenance banner and sign-up toggle apply immediately.</p>
      </div>
      {error && <p className="text-body-md text-destructive" role="alert">{error}</p>}
      {!error && !values && <p className="text-body-md text-mute">Loading settings…</p>}
      {values && (
        <form onSubmit={save} className="space-y-5">
          {GROUPS.map((g) => (
            <fieldset key={g.title} className="space-y-4 rounded-sm border border-border bg-card p-4">
              <legend className="px-1 text-heading-sm text-ink">{g.title}</legend>
              {g.fields.map((f) => (
                <SettingField key={f.key} field={f} value={values[f.key] ?? ""} onChange={(v) => set(f.key, v)} />
              ))}
            </fieldset>
          ))}
          <div className="flex justify-end">
            <Button type="submit" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
          </div>
        </form>
      )}
    </div>
  );
}

function SettingField({ field, value, onChange }: { field: Field; value: string; onChange: (v: string) => void }) {
  const id = `setting-${field.key}`;
  if (field.kind === "toggle") {
    return (
      <label htmlFor={id} className="flex items-start gap-3">
        <input id={id} type="checkbox" className="mt-1 h-4 w-4" checked={value === "true"}
          onChange={(e) => onChange(String(e.target.checked))} />
        <span>
          <span className="block text-body-md font-medium text-ink">{field.label}</span>
          {field.hint && <span className="block text-body-sm text-mute">{field.hint}</span>}
        </span>
      </label>
    );
  }
  if (field.kind === "languages") {
    const picked = value.split(",").map((l) => l.trim()).filter(Boolean);
    const toggle = (lang: string, on: boolean) =>
      onChange(LANGUAGES.filter((l) => (l === lang ? on : picked.includes(l))).join(","));
    return (
      <div>
        <p className="text-body-md font-medium text-ink">{field.label}</p>
        <div className="mt-1 flex gap-4">
          {LANGUAGES.map((lang) => (
            <label key={lang} className="flex items-center gap-2 text-body-md text-ink">
              <input type="checkbox" className="h-4 w-4" checked={picked.includes(lang)}
                disabled={picked.length === 1 && picked.includes(lang)}
                onChange={(e) => toggle(lang, e.target.checked)} />
              {lang === "JAVA" ? "Java" : "Python"}
            </label>
          ))}
        </div>
      </div>
    );
  }
  return (
    <div>
      <label htmlFor={id} className="block text-body-md font-medium text-ink">{field.label}</label>
      {field.hint && <p className="text-body-sm text-mute">{field.hint}</p>}
      <Input id={id} value={value} onChange={(e) => onChange(e.target.value)} className="mt-1 bg-card"
        type={field.kind === "number" ? "number" : "text"} min={field.kind === "number" ? 1 : undefined}
        maxLength={500} required={field.key === "siteName"} />
    </div>
  );
}
