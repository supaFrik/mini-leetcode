import { cn } from "@/lib/utils";

const lines = (s: string) => s.replace(/\r\n/g, "\n").replace(/\n$/, "").split("\n");

function Column({ title, rows, tone }: { title: string; rows: { text?: string; diff: boolean }[]; tone: "red" | "green" }) {
  return (
    <div className="min-w-0">
      <p className="mb-1 font-mono text-[11px] uppercase text-muted-foreground">{title}</p>
      <div className="overflow-auto rounded-md border bg-black/90 py-1 font-mono text-xs text-white">
        {rows.map((r, i) => (
          <div key={i} className={cn("flex gap-2 px-2 py-0.5", r.diff && (tone === "red" ? "bg-red-500/25" : "bg-emerald-500/25"))}>
            <span className="w-4 shrink-0 select-none text-right text-white/40">{i + 1}</span>
            {r.text === undefined ? <span className="italic text-white/40">(thiếu dòng)</span> : <span className="whitespace-pre-wrap break-all">{r.text === "" ? " " : r.text}</span>}
          </div>
        ))}
      </div>
    </div>
  );
}

// So sánh từng dòng, bỏ qua khoảng trắng cuối dòng (giống cách judge so output).
export function DiffView({ expected, actual }: { expected: string; actual: string }) {
  const e = lines(expected), a = lines(actual);
  const n = Math.max(e.length, a.length);
  const rows = Array.from({ length: n }, (_, i) => ({ e: e[i], a: a[i], diff: e[i]?.trimEnd() !== a[i]?.trimEnd() }));
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
      <Column title="Output (your code)" tone="red" rows={rows.map((r) => ({ text: r.a, diff: r.diff }))} />
      <Column title="Expected output" tone="green" rows={rows.map((r) => ({ text: r.e, diff: r.diff }))} />
    </div>
  );
}
