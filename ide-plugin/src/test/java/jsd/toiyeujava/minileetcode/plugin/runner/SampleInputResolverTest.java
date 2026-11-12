package jsd.toiyeujava.minileetcode.plugin.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit tests for SampleInputResolver.
 */
class SampleInputResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Extracts sample input from fenced code block under ### Sample Input")
    void testExtractSectionCodeBlock() {
        String md = """
                # Problem Title
                
                Some problem description.
                
                ### Sample Input
                ```text
                3
                CIRCLE 2
                RECTANGLE 3 4
                TRIANGLE 3 4 5
                ```
                
                ### Sample Output
                ```text
                12.57
                ```
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertEquals("3\nCIRCLE 2\nRECTANGLE 3 4\nTRIANGLE 3 4 5", extracted);
    }

    @Test
    @DisplayName("Extracts sample input from code block containing Input: up to Output:")
    void testExtractCodeBlockInputOutput() {
        String md = """
                **Example 1:**
                
                ```
                Input:
                2
                Nguyễn Văn A
                S
                100000
                
                Output:
                OK
                ```
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertTrue(extracted.contains("Nguyễn Văn A"));
        assertFalse(extracted.contains("Output:"));
    }

    @Test
    @DisplayName("Extracts single line Input:")
    void testExtractSingleLineInput() {
        String md = """
                Example 1:
                Input: nums = [2,7,11,15], target = 9
                Output: [0,1]
                """;
        String extracted = SampleInputResolver.extractSampleInputFromMarkdown(md);
        assertEquals("nums = [2,7,11,15], target = 9", extracted);
    }

    @Test
    @DisplayName("stages/sample-input.txt takes precedence over root and markdown")
    void testStagesSampleInputPrecedence() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);
        Files.writeString(stagesDir.resolve("sample-input.txt"), "STAGE_SAMPLE\n");
        Files.writeString(tempDir.resolve("sample-input.txt"), "ROOT_SAMPLE\n");
        Files.writeString(stagesDir.resolve("stage-1.md"), "### Sample Input\n```\nMD_SAMPLE\n```");

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals("STAGE_SAMPLE\n", resolved);
    }

    @Test
    @DisplayName("root sample-input.txt takes precedence over markdown if stages/sample-input.txt is absent")
    void testRootSampleInputPrecedence() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);
        Files.writeString(tempDir.resolve("sample-input.txt"), "ROOT_SAMPLE\n");
        Files.writeString(stagesDir.resolve("stage-1.md"), "### Sample Input\n```\nMD_SAMPLE\n```");

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 1);
        assertEquals("ROOT_SAMPLE\n", resolved);
    }

    @Test
    @DisplayName("Resolves from stage markdown when dedicated files are absent")
    void testMarkdownResolution() throws IOException {
        Path stagesDir = tempDir.resolve("stages");
        Files.createDirectories(stagesDir);
        Files.writeString(stagesDir.resolve("stage-2.md"), "### Sample Input\n```\nFROM_MD_STAGE_2\n```");

        String resolved = SampleInputResolver.resolveSampleInput(tempDir, 2);
        assertEquals("FROM_MD_STAGE_2\n", resolved);
    }

    @Test
    @DisplayName("Returns empty string when no sample input sources exist or projectDir is null")
    void testFallbackEmpty() {
        assertEquals("", SampleInputResolver.resolveSampleInput(null, 1));
        assertEquals("", SampleInputResolver.resolveSampleInput(tempDir, 1));
        assertEquals("", SampleInputResolver.extractSampleInputFromMarkdown(""));
        assertEquals("", SampleInputResolver.extractSampleInputFromMarkdown(null));
    }

    @Test
    @DisplayName("Extracts markdown single-line and codeblock input across markdown formatting variations")
    void testMarkdownFormattingVariations() {
        // Bold with colon inside
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("**Input:** val = true\n**Output:** false"));

        // Bold with colon outside
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("**Input**: val = true\n**Output**: false"));

        // Bold sample input
        assertEquals("100 200", SampleInputResolver.extractSampleInputFromMarkdown("**Sample Input:** 100 200\n**Sample Output:** 300"));

        // Italic variations
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("*Input:* val = true\n*Output:* false"));
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("*Input*: val = true\n*Output*: false"));

        // List bullet items
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("- **Input:** val = true\n- **Output:** false"));
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("* **Input:** val = true\n* **Output:** false"));

        // Output on same line
        assertEquals("val = true", SampleInputResolver.extractSampleInputFromMarkdown("**Input:** val = true, **Output:** false"));

        // Heading with blank line before code fence
        String blankLineHeading = """
                ### Sample Input
                
                ```text
                3
                CIRCLE 2
                ```
                """;
        assertEquals("3\nCIRCLE 2", SampleInputResolver.extractSampleInputFromMarkdown(blankLineHeading));

        // Bold heading before code fence
        String boldHeading = """
                ## **Sample Input**:
                ```
                42
                ```
                """;
        assertEquals("42", SampleInputResolver.extractSampleInputFromMarkdown(boldHeading));
    }
}
