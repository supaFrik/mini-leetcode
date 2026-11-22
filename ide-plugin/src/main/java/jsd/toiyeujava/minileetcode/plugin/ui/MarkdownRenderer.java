package jsd.toiyeujava.minileetcode.plugin.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, robust Markdown to HTML renderer for problem statements.
 * Supports GFM tables, code blocks, inline code, headers, lists, and theme-adaptive CSS.
 */
public final class MarkdownRenderer {

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");

    private MarkdownRenderer() {}

    /**
     * Converts a raw markdown problem statement into a complete HTML document with embedded CSS.
     */
    @NotNull
    public static String renderToHtml(@Nullable String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return wrapWithHtmlStructure("<p class='empty-text'>No description provided for this stage.</p>");
        }

        StringBuilder html = new StringBuilder();
        String[] lines = markdown.replace("\r\n", "\n").split("\n");

        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();

        boolean inTable = false;
        List<String> tableLines = new ArrayList<>();

        boolean inList = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 1. Code blocks (``` or ~~~)
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (inCodeBlock) {
                    html.append("<pre><code>")
                            .append(escapeHtml(codeBlockContent.toString()))
                            .append("</code></pre>\n");
                    codeBlockContent.setLength(0);
                    inCodeBlock = false;
                } else {
                    // Close any open table or list
                    if (inTable) {
                        html.append(renderTable(tableLines));
                        tableLines.clear();
                        inTable = false;
                    }
                    if (inList) {
                        html.append("</ul>\n");
                        inList = false;
                    }
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n");
                continue;
            }

            // 2. GFM Tables (| col1 | col2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (inList) {
                    html.append("</ul>\n");
                    inList = false;
                }
                inTable = true;
                tableLines.add(trimmed);
                continue;
            } else if (inTable) {
                html.append(renderTable(tableLines));
                tableLines.clear();
                inTable = false;
            }

            // 3. Bullet lists (- item or * item)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                if (!inList) {
                    html.append("<ul>\n");
                    inList = true;
                }
                String content = trimmed.substring(2).trim();
                html.append("<li>").append(renderInline(content)).append("</li>\n");
                continue;
            } else if (inList && !trimmed.isEmpty()) {
                html.append("</ul>\n");
                inList = false;
            }

            // 4. Headers (#, ##, ###, ####)
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && trimmed.length() > level && trimmed.charAt(level) == ' ') {
                    String headerText = trimmed.substring(level + 1).trim();
                    html.append("<h").append(level).append(">")
                            .append(renderInline(headerText))
                            .append("</h").append(level).append(">\n");
                    continue;
                }
            }

            // 5. Empty lines
            if (trimmed.isEmpty()) {
                if (inList) {
                    html.append("</ul>\n");
                    inList = false;
                }
                continue;
            }

            // 6. Regular paragraphs
            html.append("<p>").append(renderInline(trimmed)).append("</p>\n");
        }

        // Final cleanup of open states
        if (inCodeBlock) {
            html.append("<pre><code>")
                    .append(escapeHtml(codeBlockContent.toString()))
                    .append("</code></pre>\n");
        }
        if (inTable) {
            html.append(renderTable(tableLines));
        }
        if (inList) {
            html.append("</ul>\n");
        }

        return wrapWithHtmlStructure(html.toString());
    }

    private static String renderTable(List<String> lines) {
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<table>\n");

        boolean hasHeader = false;
        int startRow = 0;

        // Check if row 1 is a delimiter (|---|---|)
        if (lines.size() >= 2 && isTableDelimiter(lines.get(1))) {
            hasHeader = true;
            sb.append("<thead><tr>");
            for (String cell : parseTableRow(lines.get(0))) {
                sb.append("<th>").append(renderInline(cell)).append("</th>");
            }
            sb.append("</tr></thead>\n");
            startRow = 2;
        }

        sb.append("<tbody>\n");
        for (int i = startRow; i < lines.size(); i++) {
            if (isTableDelimiter(lines.get(i))) continue;
            sb.append("<tr>");
            for (String cell : parseTableRow(lines.get(i))) {
                sb.append("<td>").append(renderInline(cell)).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table>\n");
        return sb.toString();
    }

    private static boolean isTableDelimiter(String line) {
        return line.matches("^\\|[\\s\\-:\\|]+$");
    }

    private static List<String> parseTableRow(String line) {
        List<String> cells = new ArrayList<>();
        String[] parts = line.split("\\|");
        for (int i = 1; i < parts.length; i++) {
            cells.add(parts[i].trim());
        }
        return cells;
    }

    private static String renderInline(String text) {
        String s = escapeHtml(text);
        // Inline code `code`
        s = INLINE_CODE_PATTERN.matcher(s).replaceAll("<code>$1</code>");
        // Bold **text**
        s = BOLD_PATTERN.matcher(s).replaceAll("<strong>$1</strong>");
        // Italic *text*
        s = ITALIC_PATTERN.matcher(s).replaceAll("<em>$1</em>");
        // Links [text](url)
        s = LINK_PATTERN.matcher(s).replaceAll("<a href='$2'>$1</a>");
        return s;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String wrapWithHtmlStructure(String bodyContent) {
        boolean isDark = !JBColor.isBright();

        String bg = isDark ? "#1e1f22" : "#ffffff";
        String fg = isDark ? "#bcbec4" : "#1f2328";
        String border = isDark ? "#393b40" : "#d0d7de";
        String codeBg = isDark ? "#2b2d30" : "#f6f8fa";
        String tableHeaderBg = isDark ? "#2b2d30" : "#f6f8fa";
        String linkColor = isDark ? "#569cd6" : "#0969da";
        String headerColor = isDark ? "#dfe1e5" : "#1f2328";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                            font-size: 13px;
                            line-height: 1.6;
                            color: %s;
                            background-color: %s;
                            margin: 0;
                            padding: 16px;
                            box-sizing: border-box;
                        }
                        h1, h2, h3, h4 {
                            color: %s;
                            font-weight: 600;
                            margin-top: 18px;
                            margin-bottom: 8px;
                            line-height: 1.3;
                        }
                        h1 { font-size: 18px; border-bottom: 1px solid %s; padding-bottom: 6px; }
                        h2 { font-size: 15px; border-bottom: 1px solid %s; padding-bottom: 4px; }
                        h3 { font-size: 14px; }
                        p { margin: 8px 0; }
                        ul, ol { padding-left: 20px; margin: 8px 0; }
                        li { margin: 4px 0; }
                        code {
                            font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
                            font-size: 12px;
                            background-color: %s;
                            padding: 2px 5px;
                            border-radius: 4px;
                            border: 1px solid %s;
                        }
                        pre {
                            background-color: %s;
                            border: 1px solid %s;
                            border-radius: 6px;
                            padding: 12px;
                            overflow-x: auto;
                            margin: 12px 0;
                        }
                        pre code {
                            background-color: transparent;
                            padding: 0;
                            border: none;
                            display: block;
                            line-height: 1.5;
                        }
                        table {
                            border-collapse: collapse;
                            width: 100%%;
                            margin: 14px 0;
                            font-size: 12px;
                        }
                        th, td {
                            border: 1px solid %s;
                            padding: 7px 10px;
                            text-align: left;
                        }
                        th {
                            background-color: %s;
                            font-weight: 600;
                            color: %s;
                        }
                        tr:nth-child(even) {
                            background-color: %s;
                        }
                        a {
                            color: %s;
                            text-decoration: none;
                        }
                        a:hover {
                            text-decoration: underline;
                        }
                        .empty-text {
                            color: #888888;
                            font-style: italic;
                            text-align: center;
                            padding: 30px 0;
                        }
                    </style>
                </head>
                <body>
                    %s
                </body>
                </html>
                """.formatted(
                fg, bg, headerColor, border, border,
                codeBg, border, codeBg, border,
                border, tableHeaderBg, headerColor, codeBg,
                linkColor, bodyContent
        );
    }
}
