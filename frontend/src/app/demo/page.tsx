"use client";

import { useState } from "react";
import { AuthPage } from "@/components/auth/auth-page";

/**
 * Standalone demo of the `auth-page` block.
 *
 * Public route (outside the `(main)` group) so it can be previewed without
 * authenticating.
 */
export default function AuthPageDemo() {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  return (
    <AuthPage
      mode="signup"
      username={username}
      email={email}
      password={password}
      showPassword={showPassword}
      onUsernameChange={setUsername}
      onEmailChange={setEmail}
      onPasswordChange={setPassword}
      onToggleShowPassword={() => setShowPassword(!showPassword)}
      onSubmit={() => {
        /* Demo only: no network call. */
      }}
    />
  );
}
