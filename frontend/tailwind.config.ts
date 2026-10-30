import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      fontFamily: {
        // DESIGN.md decision #3: Geist Sans + Geist Mono across the app.
        sans: ["Geist", "'Inter Variable'", "system-ui", "sans-serif"],
        mono: ["'Geist Mono'", "'JetBrains Mono Variable'", "ui-monospace", "SFMono-Regular", "monospace"],
        // The "Aurora" auth surface is a self-contained dark island with its
        // own type. Scoped to that surface only — never the app default.
        aurora: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
      },
      colors: {
        brandGray: "var(--brand-gray)",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: { DEFAULT: "hsl(var(--card))", foreground: "hsl(var(--card-foreground))" },
        popover: { DEFAULT: "hsl(var(--popover))", foreground: "hsl(var(--popover-foreground))" },
        primary: { DEFAULT: "hsl(var(--primary))", foreground: "hsl(var(--primary-foreground))" },
        secondary: { DEFAULT: "hsl(var(--secondary))", foreground: "hsl(var(--secondary-foreground))" },
        muted: { DEFAULT: "hsl(var(--muted))", foreground: "hsl(var(--muted-foreground))" },
        accent: { DEFAULT: "hsl(var(--accent))", foreground: "hsl(var(--accent-foreground))" },
        destructive: { DEFAULT: "hsl(var(--destructive))", foreground: "hsl(var(--destructive-foreground))" },
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",

        /* Ink & grey ladder */
        ink: "hsl(var(--ink))",
        body: "hsl(var(--body))",
        mute: "hsl(var(--mute))",
        faint: "hsl(var(--faint))",
        hairline: "hsl(var(--border))",
        "hairline-soft": "hsl(var(--hairline-soft))",

        /* Accent family */
        link: "hsl(var(--link))",
        "link-deep": "hsl(var(--link-deep))",
        "link-soft": "hsl(var(--link-soft))",
        success: "hsl(var(--success))",
        warning: "hsl(var(--warning))",

        /* Difficulty */
        easy: "hsl(var(--easy))",
        medium: "hsl(var(--medium))",
        hard: "hsl(var(--hard))",
      },
      borderRadius: {
        none: "0px",
        sm: "6px",
        DEFAULT: "8px",
        md: "12px",
        lg: "16px",
        xl: "20px",
        "pill-category": "64px",
        pill: "100px",
        full: "9999px",
      },
      fontSize: {
        "display-xl": ["48px", { lineHeight: "48px", letterSpacing: "-2.4px", fontWeight: "600" }],
        "heading-lg": ["32px", { lineHeight: "40px", letterSpacing: "-1.28px", fontWeight: "600" }],
        "heading-md": ["20px", { lineHeight: "28px", letterSpacing: "-0.4px", fontWeight: "600" }],
        "label-sm": ["14px", { lineHeight: "20px", letterSpacing: "-0.28px", fontWeight: "500" }],
        "body-lg": ["16px", { lineHeight: "24px", fontWeight: "400" }],
        "body-md": ["14px", { lineHeight: "20px", fontWeight: "400" }],
        "body-sm": ["12px", { lineHeight: "16px", fontWeight: "400" }],
        "button-lg": ["16px", { lineHeight: "20px", fontWeight: "500" }],
        "button-md": ["14px", { lineHeight: "20px", fontWeight: "500" }],
        code: ["14px", { lineHeight: "20px", fontWeight: "400" }],
      },
      boxShadow: {
        /* Depth is a 1px hairline; shadows are whisper-thin and layered. */
        hairline: "0 1px 1px rgba(0,0,0,0.04)",
        whisper: "0 1px 1px rgba(0,0,0,0.04), 0 8px 16px -4px rgba(0,0,0,0.06)",
        floating:
          "0 2px 2px rgba(0,0,0,0.04), 0 8px 16px -4px rgba(0,0,0,0.08), inset 0 0 0 1px rgba(0,0,0,0.04)",
      },
      maxWidth: {
        container: "1200px",
      },
      transitionTimingFunction: {
        smooth: "cubic-bezier(0.16, 1, 0.3, 1)",
      },
    },
  },
  plugins: [require("tailwindcss-animate"), require("@tailwindcss/typography")],
};
export default config;
