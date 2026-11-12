package jsd.toiyeujava.minileetcode.plugin.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves sample input for local sanity execution of main().
 * <p>
 * Resolution precedence:
 * 1. File {@code stages/sample-input.txt} in project directory.
 * 2. File {@code sample-input.txt} in project directory root.
 * 3. Parsed sample input from Markdown file {@code stages/stage-N.md}.
 * 4. Fallback: empty string {@code ""}.
 */
public final class SampleInputResolver {

    public static final String STAGES_SAMPLE_INPUT = "stages/sample-input.txt";
    public static final String ROOT_SAMPLE_INPUT = "sample-input.txt";

    // Matches Input block inside code blocks: Input:\n... up to Output:, closing ``` or end of text
    private static final Pattern CODEBLOCK_INPUT_PATTERN = Pattern.compile(
            "(?is)(?:[-*+]\\s+)?(?:[*_]{1,3})?\\s*(?:sample\\s+)?input\\s*(?:[*_]{1,3})?\\s*:\\s*(?:[*_]{1,3})?\\s*\\r?\\n(.*?)(?=\\r?\\n\\s*(?:```|(?:[-*+]\\s+)?(?:[*_]{1,3})?\\s*(?:sample\\s+)?output(?:[*_]{1,3})?\\s*:)|\\z)"
    );

    // Matches Section headers: ### Sample Input followed by optional blank lines and fenced code block ``` ... ```
    private static final Pattern SECTION_CODEBLOCK_PATTERN = Pattern.compile(
            "(?is)(?:#{1,6}\\s*)?(?:[-*+]\\s+)?(?:[*_]{1,3})?\\s*(?:sample\\s+)?input\\s*(?:[*_]{1,3})?\\s*:?\\s*(?:[*_]{1,3})?\\s*\\r?\\n\\s*```[^\\r\\n]*\\r?\\n(.*?)```"
    );

    // Matches single line input: Input: nums = [1, 2], **Input:** val = true, etc.
    private static final Pattern SINGLE_LINE_INPUT_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:[-*+]\\s+)?(?:[*_]{1,3})?\\s*(?:sample\\s+)?input\\s*(?:[*_]{1,3})?\\s*:\\s*(?:[*_]{1,3})?\\s*(.*?)(?:\\s*,?\\s*(?:[*_]{1,3})?(?:sample\\s+)?output(?:[*_]{1,3})?:.*)?$"
    );

    private SampleInputResolver() {
    }

    /**
     * Resolves the sample input for the specified project directory and current stage number.
     *
     * @param projectDir  root path of the project
     * @param stageNumber current stage number (e.g. 1)
     * @return resolved sample input string, or empty string if none found
     */
    public static String resolveSampleInput(Path projectDir, int stageNumber) {
        if (projectDir == null) {
            return "";
        }

        // 1. Check stages/sample-input.txt
        Path stagesInputFile = projectDir.resolve(STAGES_SAMPLE_INPUT);
        if (Files.isRegularFile(stagesInputFile)) {
            try {
                String content = Files.readString(stagesInputFile, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return content + "\n";
                }
            } catch (IOException ignored) {}
        }

        // 2. Check root sample-input.txt
        Path rootInputFile = projectDir.resolve(ROOT_SAMPLE_INPUT);
        if (Files.isRegularFile(rootInputFile)) {
            try {
                String content = Files.readString(rootInputFile, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return content + "\n";
                }
            } catch (IOException ignored) {}
        }

        // 3. Check stages/stage-N.md
        Path stageMdPath = projectDir.resolve("stages/stage-" + stageNumber + ".md");
        if (!Files.isRegularFile(stageMdPath)) {
            // Fallback: look for any stage-*.md
            Path stagesDir = projectDir.resolve("stages");
            if (Files.isDirectory(stagesDir)) {
                try (Stream<Path> stream = Files.list(stagesDir)) {
                    stageMdPath = stream.filter(p -> p.getFileName().toString().matches("stage-\\d+\\.md"))
                            .findFirst()
                            .orElse(null);
                } catch (IOException ignored) {}
            }
        }

        if (stageMdPath != null && Files.isRegularFile(stageMdPath)) {
            try {
                String mdContent = Files.readString(stageMdPath, StandardCharsets.UTF_8);
                String extracted = extractSampleInputFromMarkdown(mdContent);
                if (extracted != null && !extracted.isBlank()) {
                    return extracted.trim() + "\n";
                }
            } catch (IOException ignored) {}
        }

        // 4. Default fallback
        return "";
    }

    /**
     * Extracts sample input text from markdown content using pattern matching.
     *
     * @param markdown markdown text of problem statement
     * @return extracted input text, or empty string if none found
     */
    public static String extractSampleInputFromMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        // Strategy A: Section header followed by code block
        Matcher sectionMatcher = SECTION_CODEBLOCK_PATTERN.matcher(markdown);
        if (sectionMatcher.find()) {
            String val = sectionMatcher.group(1).trim();
            if (!val.isEmpty()) return val;
        }

        // Strategy B: Code block containing Input: ... Output:
        Matcher codeBlockMatcher = CODEBLOCK_INPUT_PATTERN.matcher(markdown);
        if (codeBlockMatcher.find()) {
            String val = codeBlockMatcher.group(1).trim();
            if (!val.isEmpty()) return val;
        }

        // Strategy C: Single line Input: ...
        Matcher singleLineMatcher = SINGLE_LINE_INPUT_PATTERN.matcher(markdown);
        if (singleLineMatcher.find()) {
            String val = singleLineMatcher.group(1).trim();
            val = val.replaceAll("[*_]+$", "").trim();
            if (!val.isEmpty()) return val;
        }

        return "";
    }
}
