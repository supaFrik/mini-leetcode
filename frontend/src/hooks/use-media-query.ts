"use client";
import { useEffect, useState } from "react";

export function useMediaQuery(query: string, initial = true) {
  const [matches, setMatches] = useState(initial);
  useEffect(() => {
    const mq = window.matchMedia(query);
    const f = () => setMatches(mq.matches);
    f();
    mq.addEventListener("change", f);
    return () => mq.removeEventListener("change", f);
  }, [query]);
  return matches;
}
