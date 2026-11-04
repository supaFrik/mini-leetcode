import type { Language } from "@/types/api";

const INDENT = "    ";

/**
 * Format nhẹ không cần thư viện. Java: thụt lề lại theo độ sâu `{}` (bỏ qua chuỗi, ký tự và comment).
 * Python: thụt lề mang nghĩa cú pháp nên chỉ đổi tab thành 4 dấu cách và xoá khoảng trắng cuối dòng.
 * shortcut: không căn dòng nối tiếp (chuỗi `.method()` xuống dòng); cần đầy đủ thì dùng google-java-format ở backend.
 */
export function formatCode(code: string, lang: Language): string {
  const lines = code.replace(/\r\n?/g, "\n").replace(/\t/g, INDENT).split("\n").map((l) => l.trimEnd());
  const out = lang === "JAVA" ? reindentJava(lines) : lines;
  return out.join("\n").replace(/\n{3,}/g, "\n\n").replace(/\n*$/, "\n");
}

function reindentJava(lines: string[]): string[] {
  let depth = 0;
  let inBlockComment = false;
  return lines.map((raw) => {
    const line = raw.trim();
    if (!line) return "";
    if (inBlockComment) {
      const text = INDENT.repeat(depth) + (line.startsWith("*") ? " " : "") + line;
      if (line.includes("*/")) inBlockComment = false;
      return text;
    }
    // Đếm ngoặc thật trên dòng, bỏ qua chuỗi, ký tự và comment.
    let open = 0;
    let leadingClose = 0;
    let seenCode = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === "/" && line[i + 1] === "/") break;
      if (ch === "/" && line[i + 1] === "*") {
        const end = line.indexOf("*/", i + 2);
        if (end < 0) {
          inBlockComment = true;
          break;
        }
        i = end + 1;
        continue;
      }
      if (ch === '"' || ch === "'") {
        for (i++; i < line.length && line[i] !== ch; i++) if (line[i] === "\\") i++;
        seenCode = true;
        continue;
      }
      if (ch === "{" || ch === "(") open++;
      else if (ch === "}" || ch === ")") {
        if (!seenCode) leadingClose++;
        open--;
      } else if (ch !== " ") seenCode = true;
    }
    const text = INDENT.repeat(Math.max(0, depth - leadingClose)) + line;
    depth = Math.max(0, depth + open);
    return text;
  });
}
