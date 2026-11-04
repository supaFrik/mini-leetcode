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
  // Mỗi dòng đã trimEnd nên trimEnd ở đây chỉ bỏ các dòng trống cuối, tránh regex /\n*$/ (backtracking).
  return out.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd() + "\n";
}

/** Vị trí ngay sau dấu đóng của chuỗi hoặc ký tự mở tại `start` (bỏ qua ký tự đứng sau `\`). */
function skipQuoted(line: string, start: number): number {
  const quote = line[start];
  let j = start + 1;
  while (j < line.length && line[j] !== quote) j += line[j] === "\\" ? 2 : 1;
  return j + 1;
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
    let i = 0;
    while (i < line.length) {
      const ch = line[i];
      if (ch === "/" && line[i + 1] === "/") break;
      if (ch === "/" && line[i + 1] === "*") {
        const end = line.indexOf("*/", i + 2);
        if (end < 0) {
          inBlockComment = true;
          break;
        }
        i = end + 2;
        continue;
      }
      if (ch === '"' || ch === "'") {
        i = skipQuoted(line, i);
        seenCode = true;
        continue;
      }
      if (ch === "{" || ch === "(") open++;
      else if (ch === "}" || ch === ")") {
        if (!seenCode) leadingClose++;
        open--;
      } else if (ch !== " ") seenCode = true;
      i++;
    }
    const text = INDENT.repeat(Math.max(0, depth - leadingClose)) + line;
    depth = Math.max(0, depth + open);
    return text;
  });
}
