"use client";
import { useEffect, useState } from "react";

// Online/offline = sự kiện của trình duyệt + kết quả thực tế của các request (net:ok / net:error từ api.ts).
// navigator.onLine chỉ biết máy có card mạng, không biết có ra được Internet, nên cần cả hai nguồn.
export function useOnline() {
  const [online, setOnline] = useState(true);
  useEffect(() => {
    const down = () => setOnline(false);
    const up = () => setOnline(navigator.onLine);
    up();
    window.addEventListener("offline", down);
    window.addEventListener("online", up);
    window.addEventListener("net:error", down);
    window.addEventListener("net:ok", up);
    return () => {
      window.removeEventListener("offline", down);
      window.removeEventListener("online", up);
      window.removeEventListener("net:error", down);
      window.removeEventListener("net:ok", up);
    };
  }, []);
  return online;
}
