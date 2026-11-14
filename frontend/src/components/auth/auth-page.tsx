"use client";

import Link from "next/link";
import { motion, useReducedMotion } from "framer-motion";
import { Circle, Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/utils";

export type AuthMode = "signup" | "signin";

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
  className?: string;
}

const STEPS = [
  { title: "Solve problems right in the browser", active: true },
  { title: "Follow a study plan, stage by stage", active: false },
  { title: "Compete in proctored contests", active: false },
];

export function AuthPage({
  mode = "signin",
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
  const isSignup = mode === "signup";
  const reduceMotion = useReducedMotion();

  const container = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: reduceMotion
        ? { staggerChildren: 0, delayChildren: 0 }
        : { staggerChildren: 0.15, delayChildren: 0.2 },
    },
  };

  const item = reduceMotion
    ? { hidden: { opacity: 0 }, show: { opacity: 1 } }
    : {
        hidden: { opacity: 0, y: 10 },
        show: { opacity: 1, y: 0, transition: { duration: 0.5 } },
      };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    onSubmit();
  };

  return (
    <main
      className={cn(
        "min-h-screen w-full bg-background font-sans text-ink selection:bg-link-soft selection:text-ink",
        "lg:flex lg:h-screen lg:gap-4 lg:overflow-hidden lg:p-4",
        className
      )}
    >
      {/* ---------------- Left: hero ---------------- */}
      <section
        className="relative hidden h-full w-full flex-col items-center justify-end overflow-hidden rounded-3xl pb-32 px-12 bg-black text-white shadow-2xl lg:flex lg:w-[50%]"
        aria-label="Introduction"
      >
        <video
          className="absolute inset-0 h-full w-full object-cover"
          autoPlay
          muted
          loop
          playsInline
          preload="metadata"
          aria-hidden="true"
        >
          <source src="/aurora-bg.mp4" type="video/mp4" />
        </video>

        <motion.div
          variants={container}
          initial="hidden"
          animate="show"
          className="relative z-10 flex w-full flex-col items-center text-center"
        >
          <motion.div variants={item} className="flex items-center gap-2">
            <Circle className="size-5 fill-white text-white" aria-hidden="true" />
            <span className="text-xl font-semibold tracking-tight text-white">Mini LeetCode</span>
          </motion.div>

          <motion.div variants={item} className="mt-10 space-y-3">
            <h2 className="text-4xl font-semibold tracking-tight text-white">
              {isSignup ? "Join Mini LeetCode" : "Sign in to Mini LeetCode"}
            </h2>
            <p className="text-base text-white/70">
              Master algorithms. Compete in proctored real-time contests.
            </p>
          </motion.div>

          <motion.ul variants={item} className="mt-10 flex flex-col items-center gap-4">
            {STEPS.map((step, i) => (
              <StepItem
                key={step.title}
                index={i + 1}
                title={step.title}
                active={step.active}
              />
            ))}
          </motion.ul>
        </motion.div>
      </section>

      {/* ---------------- Right: form ---------------- */}
      <section className="flex flex-1 flex-col items-center justify-center overflow-y-auto px-4 py-12 sm:px-12 lg:overflow-hidden lg:px-16 lg:py-6 xl:px-24">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: reduceMotion ? 0 : 0.8, ease: "easeOut" }}
          className="w-full max-w-xl space-y-8 lg:space-y-6 sm:space-y-10"
        >
          {/* Header */}
          <header className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight text-ink">
              {isSignup ? "Create your account" : "Welcome back"}
            </h1>
            <p className="text-sm text-body">
              {isSignup
                ? "Pick a username and start solving problems."
                : "Enter your credentials to pick up where you left off."}
            </p>
          </header>

          <form className="space-y-5" onSubmit={handleSubmit} noValidate>
            {isSignup ? (
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <InputGroup
                  id="auth-username"
                  name="username"
                  label="Username"
                  value={username}
                  onChange={onUsernameChange}
                  placeholder="your.username"
                  icon={<AtSignGlyph />}
                  error={fieldErrors.username}
                  autoComplete="username"
                />
                <InputGroup
                  id="auth-email"
                  name="email"
                  type="email"
                  label="Email"
                  value={email}
                  onChange={onEmailChange}
                  placeholder="your.email@example.com"
                  icon={<AtSignGlyph />}
                  error={fieldErrors.email}
                  autoComplete="email"
                />
              </div>
            ) : (
              <InputGroup
                id="auth-username"
                name="username"
                label="Username"
                value={username}
                onChange={onUsernameChange}
                placeholder="your.username"
                icon={<AtSignGlyph />}
                error={fieldErrors.username}
                autoComplete="username"
              />
            )}

            <InputGroup
              id="auth-password"
              name="password"
              type={showPassword ? "text" : "password"}
              label="Password"
              value={password}
              onChange={onPasswordChange}
              placeholder="••••••••"
              icon={<LockGlyph />}
              error={fieldErrors.password}
              helper={isSignup ? "Requires at least 8 symbols." : undefined}
              autoComplete={isSignup ? "new-password" : "current-password"}
              trailing={
                <button
                  type="button"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={onToggleShowPassword}
                  className="absolute top-1/2 right-3 -translate-y-1/2 rounded p-1 text-mute transition-colors hover:text-ink"
                >
                  {showPassword ? (
                    <EyeOff className="size-4" aria-hidden="true" />
                  ) : (
                    <Eye className="size-4" aria-hidden="true" />
                  )}
                </button>
              }
            />

            {error && (
              <p
                role="alert"
                className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="mt-4 h-12 w-full rounded-xl bg-primary font-semibold text-primary-foreground shadow-sm transition-all hover:bg-primary/90 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-60"
            >
              {busy ? "Processing…" : isSignup ? "Create Account" : "Log in"}
            </button>
          </form>

          <p className="text-sm text-mute">
            {isSignup ? (
              <>
                Already have an account?{" "}
                <Link href="/login" className="font-medium text-link underline underline-offset-4 hover:text-link-deep">
                  Log in
                </Link>
              </>
            ) : (
              <>
                New here?{" "}
                <Link href="/register" className="font-medium text-link underline underline-offset-4 hover:text-link-deep">
                  Create an account
                </Link>
              </>
            )}
          </p>
        </motion.div>
      </section>
    </main>
  );
}

export function StepItem({
  index,
  title,
  active = false,
}: {
  index: number;
  title: string;
  active?: boolean;
}) {
  return (
    <li
      className={cn(
        "flex w-72 items-center gap-4 rounded-2xl border px-4 py-3 text-sm backdrop-blur-md transition-colors",
        active
          ? "border-white/30 bg-white/15 text-white shadow-lg"
          : "border-white/10 bg-white/5 text-white/70"
      )}
    >
      <span
        className={cn(
          "grid size-7 place-items-center rounded-full text-xs font-semibold",
          active ? "bg-white text-black" : "bg-white/10 text-white/70"
        )}
      >
        {index}
      </span>
      <span className="font-medium text-white">{title}</span>
    </li>
  );
}

export function InputGroup({
  id,
  name,
  label,
  type = "text",
  value,
  onChange,
  placeholder,
  icon,
  error,
  helper,
  autoComplete,
  trailing,
}: {
  id: string;
  name: string;
  label: string;
  type?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  icon?: React.ReactNode;
  error?: string;
  helper?: string;
  autoComplete?: string;
  trailing?: React.ReactNode;
}) {
  return (
    <div className="space-y-2">
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          name={name}
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          autoComplete={autoComplete}
          aria-invalid={!!error}
          className={cn(
            "h-12 w-full rounded-xl border bg-card px-4 text-sm text-ink transition-colors",
            "placeholder:text-faint focus:outline-none focus:ring-2",
            error
              ? "border-destructive/60 focus:border-destructive focus:ring-destructive/20"
              : "border-border focus:border-ring focus:ring-ring/20",
            icon ? "ps-11" : "",
            trailing ? "pe-11" : ""
          )}
        />
        {icon && (
          <span
            className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-mute"
            aria-hidden="true"
          >
            {icon}
          </span>
        )}
        {trailing}
      </div>
      {error ? (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : helper ? (
        <p className="text-xs text-mute">{helper}</p>
      ) : null}
    </div>
  );
}

function AtSignGlyph() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8" />
    </svg>
  );
}

function LockGlyph() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
      aria-hidden="true"
    >
      <rect width="18" height="11" x="3" y="11" rx="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

