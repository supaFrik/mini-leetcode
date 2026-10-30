"use client";
import { ThemeProvider } from "next-themes";

export function Providers({ children }: { children: React.ReactNode }) {
  // Light-only by design decision: the ink-and-mesh system is a near-white
  // canvas with near-black ink. There is no dark twin, so the theme is pinned
  // and the toggle is absent rather than hidden.
  return (
    <ThemeProvider attribute="class" defaultTheme="light" enableSystem={false} forcedTheme="light">
      {children}
    </ThemeProvider>
  );
}
