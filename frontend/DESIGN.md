# Mini LeetCode — Design System

**"Ink & Mesh"** — a Vercel/Geist-inspired system for the Mini LeetCode app.

The premise: a near-white canvas carrying near-black ink. Headings, body copy,
primary buttons, and the 1px borders that define every card all draw from the
same ink-and-grey ladder. The one place colour is allowed to exist is the home
hero, where a soft multi-stop **mesh gradient** blooms as the brand's entire
decorative system. Everywhere else, restraint.

---

## Decision log

These eight decisions were made during the design-system grill and are binding.
Changing any of them is a design change, not a refactor.

| # | Decision | Choice |
|---|---|---|
| 1 | Scope | Full rewrite — every component and page |
| 2 | Dark mode | **Light only.** No dark twin; the theme toggle is removed, not hidden |
| 3 | Fonts | **Geist Sans + Geist Mono**, self-hosted from `src/app/fonts/` |
| 4 | Accent colour | **Vercel blue `#0070f3`** as the single chromatic voice |
| 5 | Difficulty colours | **Kept semantic** (green / amber / red) — folded into ink would cost the signal |
| 6 | Page coverage | All pages, not just the app shell |
| 7 | Mesh gradient | **Home hero only** — never a page background, never a card fill |
| 8 | Code editor | **Light editor theme**, custom Monaco preset matched to the canvas |

---

## Colour

### Surface

| Token | Value | Use |
|---|---|---|
| `--background` | `#fafafa` | The canvas. Everything sits on it. |
| `--card` | `#ffffff` | Elevated surfaces: cards, buttons, inputs, code blocks. |
| `--hairline-soft` | `#f2f2f2` | The faintest grey fill — inset wells, alternating panels. |
| `--border` | `#ebebeb` | The 1px hairline. The structural workhorse. |

### Text ladder

Step deliberately: **ink → body → mute → faint**.

| Token | Value | Use |
|---|---|---|
| `--ink` | `#171717` | Headings, high-emphasis text, icon fills. Never pure `#000`. |
| `--body` | `#4d4d4d` | Paragraphs, nav links, secondary copy. |
| `--mute` | `#8f8f8f` | Captions, metadata, eyebrow labels. |
| `--faint` | `#a1a1a1` | Placeholders, disabled labels, lowest tier. |

### Accent

| Token | Value | Use |
|---|---|---|
| `--link` | `#0070f3` | Links, focus rings, and — doubling as the spec allows — the positive/active signal. |
| `--link-deep` | `#0761d1` | Pressed link tone. |
| `--link-soft` | `#d3e5ff` | Pale wash: selection, "you" row highlight. |

### Semantic

| Token | Value | Use |
|---|---|---|
| `--destructive` | `#ee0000` | Validation, destructive actions, failed verdicts. |
| `--warning` | `#f5a623` | Caution states (offline banner, violations). |
| `--success` | `#0070f3` | Success — deliberately the same blue as `--link`. |

### Difficulty

Kept semantic by decision, desaturated to sit on the near-white canvas without shouting.

| Token | Value | Use |
|---|---|---|
| `--easy` | emerald | Easy |
| `--medium` | amber | Medium |
| `--hard` | red | Hard |

### Gradient

The mesh gradient is the **entire** decorative system and is confined to the
home hero (`.mesh-hero` in `globals.css`). Its five stops are the brand
accent family blended together: cyan `#50e3c2` → blue `#007cf0` → violet
`#7928ca` → magenta `#ff0080` → amber `#f9cb28`.

The legacy two-stop Vercel gradients survive as named accents for illustration
use only — develop (blue→cyan), preview (violet→pink), ship (red→amber).

---

## Typography

Geist Sans sets all UI and prose. Geist Mono is reserved for exactly two roles:
**code**, and **small uppercase eyebrows** that label sections like a spec sheet.

| Token | Size | Weight | Tracking | Use |
|---|---|---|---|---|
| `display-xl` | 48px | 600 | -2.4px | Hero headline |
| `heading-lg` | 32px | 600 | -1.28px | Major section headings |
| `heading-md` | 20px | 600 | -0.4px | Sub-section / card headings |
| `label-sm` | 14px | 500 | -0.28px | Strong labels, nav emphasis |
| `body-lg` | 16px | 400 | 0 | Lead paragraphs |
| `body-md` | 14px | 400 | 0 | Default body, nav, table cells |
| `body-sm` | 12px | 400 | 0 | Captions, footnotes, metadata |
| `button-lg` | 16px | 500 | 0 | Marketing pill labels |
| `button-md` | 14px | 500 | 0 | Nav / app button labels |
| `code` | 14px | 400 | 0 | Code blocks, inline code |
| `mono-eyebrow` | 12px | 500 | 0 | Uppercase Geist Mono eyebrows |

**Principles.** Display type is defined by tight negative tracking — the larger
the heading, the tighter. Weight is binary: 600 for headings, 500 for buttons
and labels, 400 for everything else. No light weight, no black weight, no
italic (except in diff views where "missing line" is set in italic on purpose).

The eyebrow style is available as the `.eyebrow` class.

---

## Layout

**Spacing.** Base unit 4px. Scale: 4 → 8 → 12 → 16 → 24 → 32 → 40 → 64 → 96 → 128px.

**Container.** Centered `max-w-container` (1200px) with comfortable gutters.
Feature grids run 2-up, 3-up, 4-up and collapse to 1-up.

**Whitespace is structural.** The near-white canvas and generous section
padding do the separating work; cards are grouped by thin hairlines rather
than heavy backgrounds.

**Breakpoints.** Mobile ≤ 640px (single column, nav collapses) · Tablet 768px
(2-up grids) · Laptop 1024px (3–4-up grids) · Desktop 1200px+ (full container).

---

## Elevation & depth

Depth is deliberately minimal. The system prefers a crisp 1px hairline plus the
near-white-on-white surface step to a shadow.

| Level | Treatment | Use |
|---|---|---|
| 0 — Flat | 1px hairline, no shadow | Default cards, inputs, dividers, the canvas |
| 1 — Whisper | `shadow-hairline` — `0 1px 1px rgba(0,0,0,0.04)` | Lightly-raised cards |
| 2 — Floating | `shadow-floating` — layered low-alpha stack + inset hairline | Menus, modals, toasts, tooltips |

**Never** a single heavy drop shadow.

---

## Shapes

The radius language is bimodal: tight 6px squares for functional chrome, full
pills for marketing CTAs, 12–16px on content cards in between.

| Token | Value | Use |
|---|---|---|
| `none` | 0px | Full-bleed bands, dividers |
| `sm` | 6px | Nav / app buttons, inputs, tabs |
| `DEFAULT` | 8px | General purpose |
| `md` | 12px | Feature cards, code blocks, popovers |
| `lg` | 16px | Larger panels |
| `pill-category` | 64px | Topic pills |
| `pill` | 100px | Marketing CTA pills |
| `full` | 9999px | Circular icon buttons, avatars, status dots |

---

## Components

### Buttons — two shapes by context

The split is a **signal of which surface you're on**. Never mix shapes within
one context.

**`variant="pill"` / `variant="pill-outline"`** — marketing CTAs. Black ink
fill, or white with a hairline. Fully rounded. Sizes `pill-lg` (44px) and
`pill-md` (36px).

**Everything else** — nav and in-app controls. Tight 6px squares.

| Variant | Treatment |
|---|---|
| `default` | Black ink fill, white text |
| `pill` | Black ink fill, fully rounded |
| `pill-outline` | White fill, hairline border, fully rounded |
| `outline` | White fill, hairline border, 6px square |
| `secondary` | Hairline-soft fill |
| `ghost` | Transparent until hovered |
| `link` | Blue text with underline offset |
| `destructive` | Red fill |

Height comes from line-height, not vertical padding, so every label sits on the
same optical baseline.

### Other primitives

- **Badge** — hairline chip. `default` (ink fill), `secondary`, `outline`,
  `ghost`, `destructive`, `success`, `warning`, `link`.
- **Input / Textarea** — white fill, hairline border, 6px radius, blue focus
  ring, `text-faint` placeholders.
- **Tabs** — underline tabs, not a filled pill row. A hairline rail with an
  ink bar under the active trigger.
- **Popover / DropdownMenu / AlertDialog** — white fill, hairline border,
  `shadow-floating`.
- **Tooltip** — ink fill, white text, 6px radius.

### Feature surfaces

- **Hairline card** — `.hairline-card`: white on canvas, 1px hairline, 12px
  radius. The workhorse container.
- **Eyebrow** — `.eyebrow`: uppercase Geist Mono section label.
- **Mesh hero** — `.mesh-hero`: the multi-stop bloom. Home hero only.
- **Difficulty badge** — semantic green/amber/red, full radius.
- **Verdict badge** — blue for Accepted, red for failures.

---

## Do's and Don'ts

### Do

- Keep the canvas near-white and let near-black ink carry headings, CTAs, and
  borders.
- Confine colour to the hero mesh and small semantic accents; reserve
  `--link` for links and focus.
- Use the two button shapes by context: black pill for marketing CTAs, 6px
  square for nav/app.
- Define cards and inputs with a 1px hairline **before** any shadow.
- Set display headings in Geist Sans 600 with tight negative tracking; label
  sections with uppercase Geist Mono eyebrows.
- Step the grey ladder deliberately: ink → body → mute → faint.

### Don't

- Don't fill large surfaces with accent colours.
- Don't mix button shapes within one context.
- Don't pile on shadows.
- Don't set body copy in pure black — the brand's ink is `#171717`.
- Don't add a second decorative system — the mesh gradient is the only flourish.
- Don't loosen the display tracking.
- Don't add dark-mode overrides. The system is light-only by decision.

---

## Implementation map

| Concern | Location |
|---|---|
| Tokens | `src/app/globals.css` (`@layer base :root`) |
| Tailwind theme | `tailwind.config.ts` |
| Fonts | `src/app/layout.tsx` — self-hosted Geist via `next/font/local` |
| Theme pinning | `src/components/providers.tsx` — `forcedTheme="light"` |
| Editor theme | `src/components/workspace/code-editor.tsx` — `mini-light` Monaco preset |
| Mesh gradient | `src/app/globals.css` — `.mesh-hero` |
