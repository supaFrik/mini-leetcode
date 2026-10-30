import { mergeProps } from "@base-ui/react/merge-props";
import { useRender } from "@base-ui/react/use-render";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/**
 * Badge — shadcn base-nova, adapted to the Ink & Mesh system (Tailwind v3).
 *
 * The `render` prop renders the badge as another element (e.g. a link) while
 * keeping badge styling and semantics:
 *
 * ```tsx
 * import { Badge } from "@/components/ui/badge";
 *
 * <Badge>Default</Badge>
 * <Badge variant="outline">Outline</Badge>
 * <Badge variant="secondary">Secondary</Badge>
 * <Badge variant="destructive">Destructive</Badge>
 * <Badge variant="success">Published</Badge>
 * <Badge variant="warning">Reviewing</Badge>
 * <Badge variant="ghost">Ghost</Badge>
 * <Badge variant="link">Link</Badge>
 * <Badge render={<a href="/problems" />}>Problems</Badge>
 * ```
 *
 * Icons and spinners slot in with `data-icon` controlling the inset padding:
 *
 * ```tsx
 * <Badge variant="outline"><Check data-icon="inline-start" /> Done</Badge>
 * <Badge variant="secondary"><Loader2 data-icon="inline-start" className="animate-spin" /> Syncing</Badge>
 * ```
 *
 * Light-only by design decision — no `dark:` variants. `success` / `warning`
 * are local additions (shadcn ships without them) so lifecycle and verdict
 * surfaces keep their signal colours.
 */
const badgeVariants = cva(
  "group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-colors focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/50 [&:has([data-icon=inline-end])]:pr-1.5 [&:has([data-icon=inline-start])]:pl-1.5 aria-[invalid=true]:border-destructive aria-[invalid=true]:ring-2 aria-[invalid=true]:ring-destructive/20 [&>svg]:pointer-events-none [&>svg]:size-3",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground [a]:hover:bg-primary/80",
        secondary:
          "bg-secondary text-secondary-foreground [a]:hover:bg-secondary/80",
        destructive:
          "bg-destructive/10 text-destructive focus-visible:ring-destructive/20 [a]:hover:bg-destructive/20",
        outline:
          "border-border text-ink [a]:hover:bg-hairline-soft [a]:hover:text-ink",
        ghost: "text-body hover:bg-hairline-soft hover:text-ink",
        link: "text-link underline-offset-4 hover:underline",
        success: "bg-success/10 text-success [a]:hover:bg-success/20",
        warning:
          "bg-warning/10 text-[#8a5a00] [a]:hover:bg-warning/20",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

function Badge({
  className,
  variant = "default",
  render,
  ...props
}: useRender.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: "span",
    props: mergeProps<"span">(
      {
        className: cn(badgeVariants({ variant }), className),
      },
      props
    ),
    render,
    state: {
      slot: "badge",
      variant,
    },
  });
}

export { Badge, badgeVariants };
