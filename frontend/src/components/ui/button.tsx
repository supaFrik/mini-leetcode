import * as React from "react"
import { Slot } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * Buttons split into two shapes by context, and the split is a signal of
 * which surface you are on:
 *
 *   - `pill` — black ink fill, fully rounded. Marketing CTAs
 *     ("Browse Problems", "Start Deploying").
 *   - everything else — tight 6px squares. Nav and in-app controls.
 *
 * Height comes from line-height, not vertical padding, so every label sits on
 * the same optical baseline.
 */
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap font-medium transition-[color,background-color,border-color,box-shadow,transform] duration-200 ease-smooth focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 active:translate-y-px [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        /* Black ink fill — the default app button. */
        default: "rounded-sm bg-ink text-white hover:bg-ink/90",
        /* Black ink pill — marketing CTA. */
        pill: "rounded-pill bg-ink text-white hover:bg-ink/90",
        /* White pill — inverted marketing CTA. */
        "pill-outline": "rounded-pill border border-border bg-card text-ink hover:bg-hairline-soft",
        /* Square app button — nav / in-app. */
        outline: "rounded-sm border border-border bg-card text-ink hover:bg-hairline-soft",
        secondary: "rounded-sm bg-hairline-soft text-ink hover:bg-border",
        ghost: "rounded-sm text-body hover:bg-hairline-soft hover:text-ink",
        link: "rounded-sm text-link underline-offset-4 hover:underline",
        destructive: "rounded-sm bg-destructive text-destructive-foreground hover:bg-destructive/90",
      },
      size: {
        default: "h-9 px-3 text-button-md",
        sm: "h-8 px-2.5 text-button-md",
        lg: "h-11 px-6 text-button-lg",
        icon: "h-9 w-9 rounded-sm",
        "icon-sm": "h-8 w-8 rounded-sm",
        "pill-lg": "h-11 rounded-pill px-4 text-button-lg",
        "pill-md": "h-9 rounded-pill px-3.5 text-button-md",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button"
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = "Button"

export { Button, buttonVariants }
