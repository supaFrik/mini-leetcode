"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  AppleIcon,
  AtSignIcon,
  ChevronLeftIcon,
  EyeIcon,
  EyeOffIcon,
  LockKeyholeIcon,
  UserIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export type AuthMode = "login" | "register";

export interface AuthPageProps {
  mode?: AuthMode;
  username: string;
  email: string;
  password: string;
  showPassword: boolean;
  busy?: boolean;
  error?: string | null;
  fieldErrors?: Record<string, string>;
  onUsernameChange: (value: string) => void;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onToggleShowPassword: () => void;
  onSubmit: () => void;
  /** Disable the "coming soon" social buttons (they are inert by default). */
  className?: string;
}

/**
 * Split-screen authentication page.
 *
 * Left panel: the mesh bloom on the canvas with the product promise.
 * Right panel: brand, social sign-in placeholders, and the credentials form.
 *
 * The component is presentational: all state and network calls live in the
 * consumer (see `components/auth/login-form.tsx`).
 */
export function AuthPage({
  mode = "login",
  username,
  email,
  password,
  showPassword,
  busy = false,
  error = null,
  fieldErrors = {},
  onUsernameChange,
  onEmailChange,
  onPasswordChange,
  onToggleShowPassword,
  onSubmit,
  className,
}: AuthPageProps) {
  const isRegister = mode === "register";
  const reduceMotion = usePrefersReducedMotion();

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    onSubmit();
  };

  const fieldError = (key: string) =>
    fieldErrors[key] ? (
      <p role="alert" className="text-body-sm text-destructive">
        {fieldErrors[key]}
      </p>
    ) : null;

  return (
    <main className={cn("relative lg:grid lg:grid-cols-2", className)}>
      {/* ---------------- Left panel ---------------- */}
      <div className="bg-background relative hidden h-full flex-col overflow-hidden border-r border-border p-10 lg:flex">
        {/* The single decorative system, used here as the brand bloom. */}
        <div className="mesh-hero absolute inset-0" aria-hidden="true" />

        {/* Brand */}
        <div className="relative z-20 flex items-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-md bg-ink font-mono text-[11px] font-bold text-white">
            &lt;/&gt;
          </span>
          <p className="text-body-lg font-semibold tracking-tight text-ink">Mini LeetCode</p>
        </div>

        {/* Promise */}
        <div className="relative z-20 mt-auto max-w-md">
          <p className="text-heading-lg leading-snug text-ink">
            A focused forty-minute algorithm session, with verdicts you can trust.
          </p>
          <p className="mt-4 text-body-md text-body">
            Every submission runs in a disposable sandbox. Every contest is server-timed.
          </p>
        </div>

        <div className="pointer-events-none absolute inset-0 z-10">
          <FloatingPaths reduceMotion={reduceMotion} />
        </div>
      </div>

      {/* ---------------- Right panel ---------------- */}
      <div className="relative flex min-h-screen flex-col justify-center p-4">
        <Button variant="ghost" size="sm" className="absolute top-5 left-5" asChild>
          <Link href="/">
            <ChevronLeftIcon className="size-4" />
            Home
          </Link>
        </Button>

        <div className="relative z-10 mx-auto w-full space-y-5 sm:w-sm">
          {/* Brand (mobile only) */}
          <div className="flex items-center gap-2 lg:hidden">
            <span className="grid h-8 w-8 place-items-center rounded-md bg-ink font-mono text-[11px] font-bold text-white">
              &lt;/&gt;
            </span>
            <p className="text-body-lg font-semibold tracking-tight text-ink">Mini LeetCode</p>
          </div>

          <div className="flex flex-col space-y-2">
            <h1 className="text-heading-md text-ink">
              {isRegister ? "Create your account" : "Sign in to your account"}
            </h1>
            <p className="text-body-md text-body">
              {isRegister
                ? "Register to start practicing algorithms and joining proctored contests."
                : "Welcome back. Continue your algorithm journey."}
            </p>
          </div>

          {/* Social sign-in placeholders */}
          <div className="space-y-2">
            <SocialButton label="Continue with Google" icon={<GoogleIcon />} />
            <SocialButton label="Continue with Apple" icon={<AppleIcon className="size-4" />} />
            <SocialButton label="Continue with GitHub" icon={<GitHubIcon />} />
          </div>

          <AuthSeparator />

          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            <div className="space-y-1.5">
              <label htmlFor="auth-username" className="text-label-sm text-ink">
                Username
              </label>
              <div className="relative h-max">
                <Input
                  id="auth-username"
                  name="username"
                  value={username}
                  onChange={(e) => onUsernameChange(e.target.value)}
                  placeholder="your.username"
                  className="peer ps-9"
                  autoComplete="username"
                  aria-invalid={!!fieldErrors.username}
                />
                <div className="pointer-events-none absolute inset-y-0 start-0 flex items-center justify-center ps-3 text-faint peer-disabled:opacity-50">
                  <UserIcon className="size-4" aria-hidden="true" />
                </div>
              </div>
              {fieldError("username")}
            </div>

            {isRegister && (
              <div className="space-y-1.5">
                <label htmlFor="auth-email" className="text-label-sm text-ink">
                  Email
                </label>
                <div className="relative h-max">
                  <Input
                    id="auth-email"
                    name="email"
                    type="email"
                    value={email}
                    onChange={(e) => onEmailChange(e.target.value)}
                    placeholder="your.email@example.com"
                    className="peer ps-9"
                    autoComplete="email"
                    aria-invalid={!!fieldErrors.email}
                  />
                  <div className="pointer-events-none absolute inset-y-0 start-0 flex items-center justify-center ps-3 text-faint peer-disabled:opacity-50">
                    <AtSignIcon className="size-4" aria-hidden="true" />
                  </div>
                </div>
                {fieldError("email")}
              </div>
            )}

            <div className="space-y-1.5">
              <label htmlFor="auth-password" className="text-label-sm text-ink">
                Password
              </label>
              <div className="relative">
                <Input
                  id="auth-password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => onPasswordChange(e.target.value)}
                  placeholder="••••••••"
                  className="pe-10 ps-9"
                  autoComplete={isRegister ? "new-password" : "current-password"}
                  aria-invalid={!!fieldErrors.password}
                />
                <div className="pointer-events-none absolute inset-y-0 start-0 flex items-center justify-center ps-3 text-faint peer-disabled:opacity-50">
                  <LockKeyholeIcon className="size-4" aria-hidden="true" />
                </div>
                <button
                  type="button"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={onToggleShowPassword}
                  className="absolute top-1/2 right-3 -translate-y-1/2 text-faint transition-colors hover:text-ink"
                >
                  {showPassword ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
                </button>
              </div>
              {fieldError("password")}
            </div>

            {error && (
              <p role="alert" className="rounded-sm border border-destructive/30 bg-destructive/5 p-2.5 text-body-md text-destructive">
                {error}
              </p>
            )}

            <Button type="submit" className="w-full" size="lg" disabled={busy}>
              {busy ? "Processing…" : isRegister ? "Sign Up" : "Sign In"}
            </Button>
          </form>

          <p className="text-body-md text-body">
            {isRegister ? (
              <>
                Already have an account?{" "}
                <Link href="/login" className="font-medium text-link hover:underline">
                  Sign in
                </Link>
              </>
            ) : (
              <>
                Don&apos;t have an account?{" "}
                <Link href="/register" className="font-medium text-link hover:underline">
                  Sign up
                </Link>
              </>
            )}
          </p>

          <p className="mt-1 text-body-sm text-mute">
            By continuing, you agree to our{" "}
            <a href="#" className="underline underline-offset-4 hover:text-ink">
              Terms of Service
            </a>{" "}
            and{" "}
            <a href="#" className="underline underline-offset-4 hover:text-ink">
              Privacy Policy
            </a>
            .
          </p>
        </div>
      </div>
    </main>
  );
}

/* -------------------------------------------------------------------------- */
/*                                  Sub-parts                                  */
/* -------------------------------------------------------------------------- */

/**
 * Tracks the OS "reduce motion" preference.
 */
function usePrefersReducedMotion() {
  const [reduce, setReduce] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduce(mq.matches);
    update();
    mq.addEventListener("change", update);
    return () => mq.removeEventListener("change", update);
  }, []);
  return reduce;
}

function SocialButton({ label, icon }: { label: string; icon: React.ReactNode }) {
  return (
    <Button type="button" size="lg" variant="outline" className="w-full" disabled aria-disabled="true">
      {icon}
      <span>{label}</span>
      <span className="rounded-full bg-hairline-soft px-2 py-0.5 font-mono text-[10px] font-medium uppercase tracking-wider text-mute">
        Soon
      </span>
    </Button>
  );
}

function AuthSeparator() {
  return (
    <div className="flex w-full items-center justify-center">
      <div className="h-px w-full bg-border" />
      <span className="px-2 font-mono text-body-sm text-mute">OR</span>
      <div className="h-px w-full bg-border" />
    </div>
  );
}

function FloatingPaths({ reduceMotion }: { reduceMotion: boolean }) {
  const paths = Array.from({ length: 28 }, (_, i) => ({
    id: i,
    d: `M-${380 - i * 5} -${189 + i * 6}C-${380 - i * 5} -${189 + i * 6} -${312 - i * 5} ${216 - i * 6} ${
      152 - i * 5
    } ${343 - i * 6}C${616 - i * 5} ${470 - i * 6} ${684 - i * 5} ${875 - i * 6} ${684 - i * 5} ${875 - i * 6}`,
    width: 0.5 + i * 0.03,
  }));

  return (
    <div className="pointer-events-none absolute inset-0">
      <svg className="h-full w-full text-ink" viewBox="0 0 696 316" fill="none" preserveAspectRatio="xMidYMid slice">
        <title>Background Paths</title>
        {paths.map((path) => (
          <path
            key={path.id}
            d={path.d}
            stroke="currentColor"
            strokeWidth={path.width}
            strokeOpacity={reduceMotion ? 0.06 : 0.05 + path.id * 0.012}
          />
        ))}
      </svg>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                                   Icons                                     */
/* -------------------------------------------------------------------------- */

const GoogleIcon = (props: React.ComponentProps<"svg">) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="currentColor"
    className="size-4"
    aria-hidden="true"
    {...props}
  >
    <path d="M12.479,14.265v-3.279h11.049c0.108,0.571,0.164,1.247,0.164,1.979c0,2.46-0.672,5.502-2.84,7.669C18.744,22.829,16.051,24,12.483,24C5.869,24,0.308,18.613,0.308,12S5.869,0,12.483,0c3.659,0,6.265,1.436,8.223,3.307L18.392,5.62c-1.404-1.317-3.307-2.341-5.913-2.341C7.65,3.279,3.873,7.171,3.873,12s3.777,8.721,8.606,8.721c3.132,0,4.916-1.258,6.059-2.401c0.927-0.927,1.537-2.251,1.777-4.059L12.479,14.265z" />
  </svg>
);

const GitHubIcon = (props: React.ComponentProps<"svg">) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="currentColor"
    className="size-4"
    aria-hidden="true"
    {...props}
  >
    <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07,3.633 17.7,3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084,1.838 1.236,1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23a11.5 11.5 0 0 1 3-.405c1.02.006 2.045.138 3 .405c2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 3.873.12 5.076.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565,22.092,24,17.592,24,12.297c0-6.627-5.373-12-12-12" />
  </svg>
);
