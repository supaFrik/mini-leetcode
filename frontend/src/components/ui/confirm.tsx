"use client";

import * as React from "react";
import { AlertTriangle } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogMedia,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

export interface ConfirmOptions {
  title: string;
  description?: React.ReactNode;
  /** Label of the confirming button. Defaults to "Continue". */
  action?: string;
  cancel?: string;
  destructive?: boolean;
}

type Ask = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = React.createContext<Ask | null>(null);

/**
 * Mounts one alert dialog for the app and exposes `useConfirm()`, an awaitable replacement for
 * `window.confirm` that renders with the design system.
 */
export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = React.useState<(ConfirmOptions & { open: boolean }) | null>(null);
  const resolver = React.useRef<((ok: boolean) => void) | null>(null);

  const settle = React.useCallback((ok: boolean) => {
    resolver.current?.(ok);
    resolver.current = null;
    setState((s) => (s ? { ...s, open: false } : s));
  }, []);

  const ask = React.useCallback<Ask>((options) => {
    resolver.current?.(false);
    setState({ ...options, open: true });
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  return (
    <ConfirmContext.Provider value={ask}>
      {children}
      <AlertDialog open={!!state?.open} onOpenChange={(open) => !open && settle(false)}>
        <AlertDialogContent size={state?.destructive ? "sm" : "default"}>
          <AlertDialogHeader>
            {state?.destructive && (
              <AlertDialogMedia className="bg-destructive/10 text-destructive">
                <AlertTriangle aria-hidden />
              </AlertDialogMedia>
            )}
            <AlertDialogTitle>{state?.title}</AlertDialogTitle>
            {state?.description && <AlertDialogDescription>{state.description}</AlertDialogDescription>}
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => settle(false)}>{state?.cancel ?? "Cancel"}</AlertDialogCancel>
            <AlertDialogAction variant={state?.destructive ? "destructive" : "default"} onClick={() => settle(true)}>
              {state?.action ?? "Continue"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): Ask {
  const ask = React.useContext(ConfirmContext);
  if (!ask) throw new Error("useConfirm must be used inside <ConfirmProvider>");
  return ask;
}
