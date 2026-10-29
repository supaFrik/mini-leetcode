"use client";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Eye, EyeOff, Shield, Timer, Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError, USE_MOCK, errorMessage } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { login, register } from "@/lib/services";

// Chỉ cho quay lại đường dẫn nội bộ (chặn open-redirect như //evil.com)
const safeNext = (n: string | null) => (n && n.startsWith("/") && !n.startsWith("//") && !n.startsWith("/login") ? n : "/");

function LoginForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (getToken()) router.replace(next); }, [router, next]);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true); setError(null); setFields({});
    try { await fn(); router.replace(next); }
    catch (err) {
      setError(errorMessage(err, "Đăng nhập thất bại."));
      if (err instanceof ApiError && err.problem?.invalid_params) setFields(err.problem.invalid_params); // lỗi theo từng ô nhập (RFC 7807)
    } finally { setBusy(false); }
  };
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (username.trim().length < 3) { setFields({ username: "Tên đăng nhập tối thiểu 3 ký tự." }); return; }
    if (!USE_MOCK && password.length < 6) { setFields({ password: "Mật khẩu tối thiểu 6 ký tự." }); return; }
    void run(() => (mode === "login" ? login(username.trim(), password) : register(username.trim(), email.trim(), password)));
  };
  const err = (k: string) => fields[k] && <p role="alert" className="text-xs text-destructive">{fields[k]}</p>;

  return (
    <main className="grid min-h-screen md:grid-cols-[1fr_1.1fr]">
      <aside className="hidden flex-col justify-between bg-gradient-to-br from-primary/20 via-card to-background p-10 md:flex">
        <div className="flex items-center gap-2 font-semibold"><span className="grid h-8 w-8 place-items-center rounded-full bg-primary text-[10px] text-primary-foreground">mini</span>Mini LeetCode</div>
        <div className="space-y-6">
          <h2 className="text-3xl font-semibold leading-tight">Luyện thuật toán.<br />Thi công bằng.</h2>
          <ul className="space-y-3 text-sm text-muted-foreground">
            <li className="flex items-center gap-3"><Shield className="h-5 w-5 text-primary" />Chấm bài cô lập, kết quả trong vài giây</li>
            <li className="flex items-center gap-3"><Timer className="h-5 w-5 text-primary" />Phòng thi có đồng hồ và giám sát từ máy chủ</li>
            <li className="flex items-center gap-3"><Trophy className="h-5 w-5 text-primary" />Bảng xếp hạng ACM/ICPC theo thời gian thực</li>
          </ul>
        </div>
        <p className="text-xs text-muted-foreground">© 2026 Mini LeetCode.</p>
      </aside>

      <section className="grid place-items-center p-6">
        <form onSubmit={submit} className="w-full max-w-sm space-y-4" noValidate>
          <div className="flex items-center gap-2 font-semibold md:hidden"><span className="grid h-8 w-8 place-items-center rounded-full bg-primary text-[10px] text-primary-foreground">mini</span>Mini LeetCode</div>
          <div>
            <h1 className="text-2xl font-semibold">{mode === "login" ? "Đăng nhập" : "Tạo tài khoản"}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{mode === "login" ? "Chào mừng quay lại. Tiếp tục hành trình giải thuật toán." : "Tài khoản mới có vai trò học viên."}</p>
          </div>
          <label className="block space-y-1 text-sm">Tên đăng nhập
            <Input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" aria-invalid={!!fields.username} />
            {err("username")}
          </label>
          {mode === "register" && (
            <label className="block space-y-1 text-sm">Email
              <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" aria-invalid={!!fields.email} />
              {err("email")}
            </label>
          )}
          <label className="block space-y-1 text-sm">Mật khẩu
            <div className="relative">
              <Input type={show ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)} className="pr-10" autoComplete={mode === "login" ? "current-password" : "new-password"} aria-invalid={!!fields.password} />
              <button type="button" aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"} onClick={() => setShow(!show)} className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground">{show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button>
            </div>
            {err("password")}
          </label>
          {error && <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 p-2.5 text-sm text-destructive">{error}</p>}
          <Button type="submit" className="w-full" disabled={busy}>{busy ? "Đang xử lý…" : mode === "login" ? "Đăng nhập" : "Đăng ký"}</Button>
          <button type="button" className="text-sm text-muted-foreground underline" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(null); setFields({}); }}>
            {mode === "login" ? "Chưa có tài khoản? Đăng ký" : "Đã có tài khoản? Đăng nhập"}
          </button>

          {USE_MOCK && (
            <div className="space-y-2 rounded-lg border bg-card p-3">
              <p className="text-xs text-muted-foreground">Chế độ mock, vào nhanh bằng tài khoản mẫu:</p>
              <div className="flex flex-wrap gap-2">
                {[["Học viên", "student"], ["Giảng viên", "teacher1"], ["Admin", "admin"]].map(([label, u]) => (
                  <Button key={u} type="button" size="sm" variant="outline" disabled={busy} onClick={() => void run(() => login(u, ""))}>{label}</Button>
                ))}
              </div>
            </div>
          )}
        </form>
      </section>
    </main>
  );
}

export default function LoginPage() {
  return <Suspense fallback={null}><LoginForm /></Suspense>;
}
