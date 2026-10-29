"use client";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/login-form";

export default function RegisterPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm initialMode="register" />
    </Suspense>
  );
}
