import { marked } from "marked";
import TurndownService from "turndown";
import { gfm } from "@joplin/turndown-plugin-gfm";

/*
 * Nội dung soạn trực quan vẫn lưu dạng Markdown (specs/practice-ide-rich-editor.spec.md, A1).
 * Gạch chân và màu chữ không có trong Markdown nên lưu bằng HTML nội dòng trong danh sách cho phép:
 * <u>…</u> và <span style="color:#hex">…</span>.
 */

const HEX = /^#[0-9a-f]{3}([0-9a-f]{3})?$/i;
const RGB = /^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*(,\s*(0|1|0?\.\d+)\s*)?\)$/i;

/** Màu hợp lệ lấy từ thuộc tính style, chuẩn hoá về #rrggbb (trình duyệt hay đổi sang rgb()); ngược lại null. */
export function safeColor(style: string | null | undefined): string | null {
  const m = /(?:^|;)\s*color\s*:\s*([^;]+)/i.exec(style ?? "");
  const value = m?.[1].trim() ?? "";
  if (HEX.test(value)) return value.toLowerCase();
  if (!RGB.test(value)) return null;
  const [r, g, b] = value.match(/\d+(\.\d+)?/g)!.slice(0, 3).map((n) => Math.min(255, Number(n)));
  return "#" + [r, g, b].map((n) => Math.round(n).toString(16).padStart(2, "0")).join("");
}

export function markdownToHtml(markdown: string): string {
  return marked.parse(markdown ?? "", { async: false, gfm: true, breaks: false }) as string;
}

const turndown = new TurndownService({
  headingStyle: "atx",
  codeBlockStyle: "fenced",
  bulletListMarker: "-",
  emDelimiter: "*",
});
turndown.use(gfm);
turndown.addRule("underline", {
  filter: ["u"],
  replacement: (content) => (content.trim() ? `<u>${content}</u>` : content),
});
turndown.addRule("color", {
  filter: (node) => node.nodeName === "SPAN" && !!safeColor(node.getAttribute("style")),
  replacement: (content, node) => {
    const color = safeColor((node as HTMLElement).getAttribute("style"));
    return content.trim() && color ? `<span style="color:${color}">${content}</span>` : content;
  },
});

export function htmlToMarkdown(html: string): string {
  return turndown.turndown(html ?? "").replace(/\n{3,}/g, "\n\n").trim();
}
