import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";
import { Providers } from "@/components/providers";

const geistSans = localFont({
  src: "./fonts/GeistVF.woff",
  variable: "--font-geist-sans",
  display: "swap",
  weight: "100 900",
});

const geistMono = localFont({
  src: "./fonts/GeistMonoVF.woff",
  variable: "--font-geist-mono",
  display: "swap",
  weight: "100 900",
});

/**
 * Inter — used by the "Aurora" auth surface. Self-hosted (variable woff2, no
 * third-party request at runtime). Weights 300–700 are covered by the
 * variable axis.
 */
const inter = localFont({
  src: [
    { path: "./fonts/InterVF.woff2", weight: "100 900", style: "normal" },
    { path: "./fonts/InterVF-ext.woff2", weight: "100 900", style: "normal" },
  ],
  variable: "--font-inter",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Mini LeetCode",
  description: "Algorithm Practice & Proctored Online Contests",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${geistSans.variable} ${geistMono.variable} ${inter.variable} font-sans antialiased`}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
