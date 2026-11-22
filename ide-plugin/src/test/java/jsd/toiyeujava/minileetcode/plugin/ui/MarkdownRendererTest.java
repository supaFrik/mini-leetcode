package jsd.toiyeujava.minileetcode.plugin.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarkdownRenderer Unit Tests")
class MarkdownRendererTest {

    @Test
    @DisplayName("Renders null or blank markdown with placeholder notice")
    void rendersNullOrBlank() {
        String htmlNull = MarkdownRenderer.renderToHtml(null);
        assertTrue(htmlNull.contains("No description provided for this stage"));

        String htmlBlank = MarkdownRenderer.renderToHtml("   \n  ");
        assertTrue(htmlBlank.contains("No description provided for this stage"));
    }

    @Test
    @DisplayName("Renders headers, paragraphs, and inline formatting")
    void rendersBasicFormatting() {
        String md = """
                # Stage 1: Mini Bank
                ## Overview
                Create an **Account** class with `balance` and *interestRate*.
                """;
        String html = MarkdownRenderer.renderToHtml(md);

        assertTrue(html.contains("<h1>Stage 1: Mini Bank</h1>"));
        assertTrue(html.contains("<h2>Overview</h2>"));
        assertTrue(html.contains("<strong>Account</strong>"));
        assertTrue(html.contains("<code>balance</code>"));
        assertTrue(html.contains("<em>interestRate</em>"));
    }

    @Test
    @DisplayName("Renders GFM tables with thead and tbody")
    void rendersGfmTables() {
        String md = """
                | Command | Output |
                |---|---|
                | DEPOSIT 100 | New balance: 100 |
                | WITHDRAW 50 | New balance: 50 |
                """;
        String html = MarkdownRenderer.renderToHtml(md);

        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<thead><tr><th>Command</th><th>Output</th></tr></thead>"));
        assertTrue(html.contains("<tbody>"));
        assertTrue(html.contains("<tr><td>DEPOSIT 100</td><td>New balance: 100</td></tr>"));
        assertTrue(html.contains("<tr><td>WITHDRAW 50</td><td>New balance: 50</td></tr>"));
        assertTrue(html.contains("</table>"));
    }

    @Test
    @DisplayName("Renders code blocks and preserves indentation")
    void rendersCodeBlocks() {
        String md = """
                ```java
                public class Account {
                    private double balance;
                }
                ```
                """;
        String html = MarkdownRenderer.renderToHtml(md);

        assertTrue(html.contains("<pre><code>"));
        assertTrue(html.contains("public class Account {"));
        assertTrue(html.contains("    private double balance;"));
        assertTrue(html.contains("}"));
        assertTrue(html.contains("</code></pre>"));
    }

    @Test
    @DisplayName("Escapes HTML tags and scripts inside markdown for safety")
    void escapesMaliciousHtml() {
        String md = "Test <script>alert('hack')</script> and <div>box</div>";
        String html = MarkdownRenderer.renderToHtml(md);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(&#39;hack&#39;)&lt;/script&gt;"));
    }
}
