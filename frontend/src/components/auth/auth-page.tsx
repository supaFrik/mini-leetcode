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
  { title: "Register your identity", active: true },
  { title: "Configure your studio", active: false },
  { title: "Finalize your profile", active: false },
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
              {isSignup ? "Create New Profile" : "Welcome back"}
            </h1>
            <p className="text-sm text-body">
              {isSignup
                ? "Input your basic details to begin your algorithm journey."
                : "Enter your credentials to pick up where you left off."}
            </p>
          </header>

          {/* Social placeholders */}
          <div className="grid grid-cols-2 gap-4">
            <SocialButton label="Google" icon={<GoogleIcon />} />
            <SocialButton label="GitHub" icon={<GitHubIcon />} />
          </div>

          {/* Divider */}
          <div className="relative flex items-center">
            <div className="h-px w-full border-t border-border" />
            <span className="bg-background px-4 text-xs font-medium uppercase tracking-widest text-mute">
              Or
            </span>
            <div className="h-px w-full border-t border-border" />
          </div>

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
                Member of the team?{" "}
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

export function SocialButton({
  label,
  icon,
}: {
  label: string;
  icon: React.ReactNode;
}) {
  return (
    <button
      type="button"
      disabled
      className="flex h-12 w-full items-center justify-center gap-3 rounded-xl border border-border bg-card text-sm font-medium text-body transition-colors hover:bg-hairline-soft disabled:cursor-not-allowed disabled:opacity-60"
    >
      {icon}
      <span>{label}</span>
      <span className="rounded-full border border-border px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-mute">
        Soon
      </span>
    </button>
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
    <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07,3.633 17.7,3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23a11.5 11.5 0 0 1 3-.405c1.02.006 2.045.138 3 .405c2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 3.873.12 5.076.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565,22.092,24,17.592,24,12.297c0-6.627-5.373-12-12-12" />
  </svg>
);
