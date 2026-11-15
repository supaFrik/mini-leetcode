import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeRaw from "rehype-raw";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import { safeColor } from "@/lib/rich-markdown";

type HastNode = { type: string; tagName?: string; properties?: Record<string, unknown>; children?: HastNode[] };

/** Cho phép thêm <u> và <span style>; sau đó chỉ giữ lại `color:` hợp lệ trong style. */
const schema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames ?? []), "u", "span"],
  attributes: { ...defaultSchema.attributes, span: [...(defaultSchema.attributes?.span ?? []), "style"] },
};

function onlySafeColor() {
  const walk = (node: HastNode) => {
    if (node.type === "element" && node.properties && "style" in node.properties) {
      const color = safeColor(String(node.properties.style));
      if (color) node.properties.style = `color:${color}`;
      else delete node.properties.style;
    }
    node.children?.forEach(walk);
  };
  return (tree: HastNode) => walk(tree);
}

/**
 * Hiển thị Markdown soạn bằng trình soạn trực quan: GFM + HTML nội dòng đã lọc (gạch chân, màu chữ).
 * Không bao giờ chạy script hay giữ thuộc tính nguy hiểm.
 */
export function Markdown({ children }: { children: string }) {
  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw, [rehypeSanitize, schema], onlySafeColor]}>
      {children}
    </ReactMarkdown>
  );
}
