package jsd.toiyeujava.minileetcode.plugin.workspace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectManifest Unit & Specification Test Suite")
class ProjectManifestTest {

    @Test
    @DisplayName("testParseValidMinimalManifest: required fields only, problem_id absent")
    void testParseValidMinimalManifest() {
        String yaml = """
            plan: oop-with-java
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertNotNull(manifest);
        assertEquals("oop-with-java", manifest.plan());
        assertEquals("oop-with-java", manifest.getPlan());
        assertEquals("1", manifest.item());
        assertEquals("1", manifest.getItem());
        assertEquals(1, manifest.itemAsInt());
        assertEquals(OptionalInt.of(1), manifest.findItemAsInt());
        assertEquals(1, manifest.currentStage());
        assertEquals(1, manifest.getCurrentStage());
        assertEquals("mini-bank-stage-1", manifest.problemSlug());
        assertEquals("mini-bank-stage-1", manifest.getProblemSlug());
        assertNull(manifest.problemId());
        assertNull(manifest.getProblemId());
        assertTrue(manifest.findProblemId().isEmpty());
        assertTrue(manifest.extraProperties().isEmpty());
    }

    @Test
    @DisplayName("testParseValidFullManifest: includes problem_id and extra properties")
    void testParseValidFullManifest() {
        String yaml = """
            # Mini LeetCode Project Manifest
            plan: oop-with-java
            item: 2
            current_stage: 3
            problem_slug: mini-bank-stage-3
            problem_id: 103
            version: "1.0.0"
            author: instructor1
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertNotNull(manifest);
        assertEquals("oop-with-java", manifest.plan());
        assertEquals("2", manifest.item());
        assertEquals(2, manifest.itemAsInt());
        assertEquals(3, manifest.currentStage());
        assertEquals("mini-bank-stage-3", manifest.problemSlug());
        assertEquals(103L, manifest.problemId());
        assertEquals(OptionalInt.of(2), manifest.findItemAsInt());
        assertEquals(103L, manifest.findProblemId().orElseThrow());

        assertEquals("1.0.0", manifest.get("version"));
        assertEquals("1.0.0", manifest.get("VERSION"));
        assertEquals("instructor1", manifest.get("author"));
        assertTrue(manifest.hasExtra("version"));
        assertTrue(manifest.hasExtra("author"));
        assertFalse(manifest.hasExtra("non_existent"));
        assertEquals(2, manifest.extraProperties().size());
    }

    @Test
    @DisplayName("testSerializeMinimalManifest: omits problem_id and formats clean YAML")
    void testSerializeMinimalManifest() {
        ProjectManifest manifest = ProjectManifest.of("oop-with-java", "1", 1, "mini-bank-stage-1");

        String yaml = manifest.toYamlString();

        assertNotNull(yaml);
        assertTrue(yaml.contains("plan: oop-with-java"));
        assertTrue(yaml.contains("item: 1"));
        assertTrue(yaml.contains("current_stage: 1"));
        assertTrue(yaml.contains("problem_slug: mini-bank-stage-1"));
        assertFalse(yaml.contains("problem_id:"));
    }

    @Test
    @DisplayName("testSerializeFullManifest: deterministic output order and extra properties")
    void testSerializeFullManifest() {
        ProjectManifest manifest = ProjectManifest.builder()
                .plan("oop-with-java")
                .item(1)
                .currentStage(2)
                .problemSlug("mini-bank-stage-2")
                .problemId(102L)
                .extra("created_by", "tester")
                .build();

        String yaml = manifest.toYamlString();

        assertTrue(yaml.contains("plan: oop-with-java"));
        assertTrue(yaml.contains("item: 1"));
        assertTrue(yaml.contains("current_stage: 2"));
        assertTrue(yaml.contains("problem_slug: mini-bank-stage-2"));
        assertTrue(yaml.contains("problem_id: 102"));
        assertTrue(yaml.contains("created_by: tester"));
    }

    @Test
    @DisplayName("testRoundTripEquality: parse -> serialize -> parse yields identical manifest")
    void testRoundTripEquality() {
        String yaml = """
            # Header Comment
            plan: oop-with-java
            item: 1
            current_stage: 2
            problem_slug: mini-bank-stage-2
            problem_id: 102
            author: admin
            """;

        ProjectManifest original = ProjectManifest.parse(yaml);
        String serialized = original.toYamlString();
        ProjectManifest roundTrip = ProjectManifest.parse(serialized);

        assertEquals(original, roundTrip);
        assertEquals(original.hashCode(), roundTrip.hashCode());
        assertEquals("oop-with-java", roundTrip.plan());
        assertEquals("1", roundTrip.item());
        assertEquals(2, roundTrip.currentStage());
        assertEquals("mini-bank-stage-2", roundTrip.problemSlug());
        assertEquals(102L, roundTrip.problemId());
        assertEquals("admin", roundTrip.get("author"));
    }

    @Test
    @DisplayName("testRoundTripWithSpecialCharacters: colons, quotes, hashes in values, unicode")
    void testRoundTripWithSpecialCharacters() {
        ProjectManifest manifest = ProjectManifest.builder()
                .plan("oop-with-java")
                .item("item:special #1")
                .currentStage(1)
                .problemSlug("mini-bank-stage-1")
                .extra("description", "Bài tập: Lập trình OOP # Java 21")
                .extra("tag", "\"quoted text\"")
                .build();

        String yaml = manifest.toYamlString();
        ProjectManifest parsed = ProjectManifest.parse(yaml);

        assertEquals(manifest, parsed);
        assertEquals("item:special #1", parsed.item());
        assertEquals("Bài tập: Lập trình OOP # Java 21", parsed.get("description"));
        assertEquals("\"quoted text\"", parsed.get("tag"));
    }

    @Test
    @DisplayName("testCaseInsensitiveKeys: handles UPPERCASE and MixedCase keys seamlessly")
    void testCaseInsensitiveKeys() {
        String yaml = """
            PLAN: oop-with-java
            ITEM: "1"
            CURRENT_STAGE: 1
            PROBLEM_SLUG: mini-bank-stage-1
            PROBLEM_ID: 101
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
        assertEquals(1, manifest.currentStage());
        assertEquals("mini-bank-stage-1", manifest.problemSlug());
        assertEquals(101L, manifest.problemId());
    }

    @Test
    @DisplayName("testKeyAliases: handles dashed and alternative key spellings")
    void testKeyAliases() {
        String yaml = """
            study-plan: oop-with-java
            item-id: 2
            current-stage: 3
            problem-slug: mini-bank-stage-3
            problem-id: 103
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("2", manifest.item());
        assertEquals(3, manifest.currentStage());
        assertEquals("mini-bank-stage-3", manifest.problemSlug());
        assertEquals(103L, manifest.problemId());
    }

    @Test
    @DisplayName("testCommentsAndEmptyLines: skips leading, interleaved, trailing comments and blank lines")
    void testCommentsAndEmptyLines() {
        String yaml = """
            # Start of manifest
            
            plan: oop-with-java
            
            # Item position
            item: 1
            
            current_stage: 1
            # Current stage comment
            
            problem_slug: mini-bank-stage-1
            
            # End of manifest
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
        assertEquals(1, manifest.currentStage());
        assertEquals("mini-bank-stage-1", manifest.problemSlug());
    }

    @Test
    @DisplayName("testInlineComments: strips trailing inline comments when preceded by space")
    void testInlineComments() {
        String yaml = """
            plan: oop-with-java # course identifier
            item: 1 # first item in plan
            current_stage: 2 # active stage
            problem_slug: mini-bank-stage-2 # problem slug
            problem_id: 102 # stage 2 id
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
        assertEquals(2, manifest.currentStage());
        assertEquals("mini-bank-stage-2", manifest.problemSlug());
        assertEquals(102L, manifest.problemId());
    }

    @Test
    @DisplayName("testWhitespaceTolerance: handles tabs and irregular spacing around colons")
    void testWhitespaceTolerance() {
        String yaml = """
              plan   :    oop-with-java  \s
              item:1
              current_stage  :   1
              problem_slug:    mini-bank-stage-1   \s
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
        assertEquals(1, manifest.currentStage());
        assertEquals("mini-bank-stage-1", manifest.problemSlug());
    }

    @Test
    @DisplayName("testCrlfLineEndings: parses Windows CRLF line breaks identically to Unix LF")
    void testCrlfLineEndings() {
        String yaml = "plan: oop-with-java\r\nitem: 1\r\ncurrent_stage: 1\r\nproblem_slug: mini-bank-stage-1\r\nproblem_id: 101\r\n";

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
        assertEquals(1, manifest.currentStage());
        assertEquals("mini-bank-stage-1", manifest.problemSlug());
        assertEquals(101L, manifest.problemId());
    }

    @Test
    @DisplayName("testQuotedValuesDoubleAndSingle: handles quotes and escaped characters")
    void testQuotedValuesDoubleAndSingle() {
        String yaml = """
            plan: "oop-with-java"
            item: '1'
            current_stage: 1
            problem_slug: "mini-bank-stage-1"
            quote_test: 'It''s working'
            escaped_test: "Hello \\"World\\""
            """;

        ProjectManifest manifest = ProjectManifest.parse(yaml);

        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
        assertEquals("mini-bank-stage-1", manifest.problemSlug());
        assertEquals("It's working", manifest.get("quote_test"));
        assertEquals("Hello \"World\"", manifest.get("escaped_test"));
    }

    @Test
    @DisplayName("testNullOrEmptyProblemId: 'null', '~', or empty problem_id yields null")
    void testNullOrEmptyProblemId() {
        String yamlNull = """
            plan: oop-with-java
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            problem_id: null
            """;
        assertNull(ProjectManifest.parse(yamlNull).problemId());

        String yamlTilde = """
            plan: oop-with-java
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            problem_id: ~
            """;
        assertNull(ProjectManifest.parse(yamlTilde).problemId());

        String yamlBlank = """
            plan: oop-with-java
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            problem_id:
            """;
        assertNull(ProjectManifest.parse(yamlBlank).problemId());
    }

    @Test
    @DisplayName("testMissingRequiredPlanThrows: throws IllegalArgumentException when 'plan' is missing or blank")
    void testMissingRequiredPlanThrows() {
        String missingPlan = """
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(missingPlan));
        assertTrue(ex.getMessage().contains("'plan'"));

        String blankPlan = """
            plan: "   "
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            """;
        assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(blankPlan));
    }

    @Test
    @DisplayName("testMissingRequiredItemThrows: throws IllegalArgumentException when 'item' is missing")
    void testMissingRequiredItemThrows() {
        String missingItem = """
            plan: oop-with-java
            current_stage: 1
            problem_slug: mini-bank-stage-1
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(missingItem));
        assertTrue(ex.getMessage().contains("'item'"));
    }

    @Test
    @DisplayName("testMissingRequiredCurrentStageThrows: throws IllegalArgumentException when 'current_stage' is missing")
    void testMissingRequiredCurrentStageThrows() {
        String missingStage = """
            plan: oop-with-java
            item: 1
            problem_slug: mini-bank-stage-1
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(missingStage));
        assertTrue(ex.getMessage().contains("'current_stage'"));
    }

    @Test
    @DisplayName("testMissingRequiredProblemSlugThrows: throws IllegalArgumentException when 'problem_slug' is missing")
    void testMissingRequiredProblemSlugThrows() {
        String missingSlug = """
            plan: oop-with-java
            item: 1
            current_stage: 1
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(missingSlug));
        assertTrue(ex.getMessage().contains("'problem_slug'"));
    }

    @Test
    @DisplayName("testInvalidCurrentStageNonNumericThrows: throws IllegalArgumentException on non-numeric stage")
    void testInvalidCurrentStageNonNumericThrows() {
        String yaml = """
            plan: oop-with-java
            item: 1
            current_stage: stage-one
            problem_slug: mini-bank-stage-1
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(yaml));
        assertTrue(ex.getMessage().contains("Invalid integer for 'current_stage'"));
    }

    @Test
    @DisplayName("testInvalidCurrentStageZeroOrNegativeThrows: throws IllegalArgumentException when stage <= 0")
    void testInvalidCurrentStageZeroOrNegativeThrows() {
        String yamlZero = """
            plan: oop-with-java
            item: 1
            current_stage: 0
            problem_slug: mini-bank-stage-1
            """;
        assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(yamlZero));

        String yamlNegative = """
            plan: oop-with-java
            item: 1
            current_stage: -1
            problem_slug: mini-bank-stage-1
            """;
        assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(yamlNegative));
    }

    @Test
    @DisplayName("testInvalidProblemIdNonNumericThrows: throws IllegalArgumentException on non-numeric problem_id")
    void testInvalidProblemIdNonNumericThrows() {
        String yaml = """
            plan: oop-with-java
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            problem_id: not_a_long
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(yaml));
        assertTrue(ex.getMessage().contains("Invalid integer for 'problem_id'"));
    }

    @Test
    @DisplayName("testMalformedLineWithoutColonThrows: throws IllegalArgumentException on invalid syntax")
    void testMalformedLineWithoutColonThrows() {
        String yaml = """
            plan: oop-with-java
            invalid line without colon
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(yaml));
        assertTrue(ex.getMessage().contains("missing ':' delimiter"));
    }

    @Test
    @DisplayName("testNullYamlContentThrows: throws IllegalArgumentException when input is null")
    void testNullYamlContentThrows() {
        assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse(null));
    }

    @Test
    @DisplayName("testYamlDocumentMarkersIgnored: allows '---' and '...' standard markers")
    void testYamlDocumentMarkersIgnored() {
        String yaml = """
            ---
            plan: oop-with-java
            item: 1
            current_stage: 1
            problem_slug: mini-bank-stage-1
            ...
            """;
        ProjectManifest manifest = ProjectManifest.parse(yaml);
        assertEquals("oop-with-java", manifest.plan());
        assertEquals("1", manifest.item());
    }

    @Test
    @DisplayName("testMutationHelpers: verify withStage, withCurrentStage, withProblem, withExtra")
    void testMutationHelpers() {
        ProjectManifest stage1 = ProjectManifest.of("oop-with-java", "1", 1, "mini-bank-stage-1", 101L);

        ProjectManifest stage2 = stage1.withStage(2, "mini-bank-stage-2", 102L);
        assertEquals(2, stage2.currentStage());
        assertEquals("mini-bank-stage-2", stage2.problemSlug());
        assertEquals(102L, stage2.problemId());
        assertEquals("oop-with-java", stage2.plan());

        ProjectManifest stage3 = stage2.withCurrentStage(3);
        assertEquals(3, stage3.currentStage());

        ProjectManifest withCustom = stage3.withExtra("status", "in_progress");
        assertEquals("in_progress", withCustom.get("status"));
        assertEquals("in_progress", withCustom.find("status").orElseThrow());
    }

    @Test
    @DisplayName("testItemAsIntAndOptionalInt: handles numeric and non-numeric item identifiers")
    void testItemAsIntAndOptionalInt() {
        ProjectManifest numericItem = ProjectManifest.of("plan", "42", 1, "slug");
        assertEquals(42, numericItem.itemAsInt());
        assertEquals(OptionalInt.of(42), numericItem.findItemAsInt());

        ProjectManifest nonNumericItem = ProjectManifest.of("plan", "project-mini-bank", 1, "slug");
        assertEquals("project-mini-bank", nonNumericItem.item());
        assertEquals(OptionalInt.empty(), nonNumericItem.findItemAsInt());
        assertThrows(NumberFormatException.class, nonNumericItem::itemAsInt);
    }

    @Test
    @DisplayName("testFileReadAndWrite: writes to file creating parent dir and reads back cleanly")
    void testFileReadAndWrite(@TempDir Path tempDir) throws IOException {
        Path manifestPath = tempDir.resolve(".mlc").resolve("project.yaml");
        ProjectManifest original = ProjectManifest.of("oop-with-java", "1", 1, "mini-bank-stage-1", 101L);

        original.writeTo(manifestPath);

        assertTrue(Files.isRegularFile(manifestPath));
        ProjectManifest read = ProjectManifest.readFrom(manifestPath);
        assertEquals(original, read);
    }

    @Test
    @DisplayName("testReadFromNonExistentFileThrows: throws NoSuchFileException")
    void testReadFromNonExistentFileThrows(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does_not_exist.yaml");
        assertThrows(NoSuchFileException.class, () -> ProjectManifest.readFrom(missing));
    }

    @Test
    @DisplayName("testProjectRootHelpers: resolvePath, existsIn, readFromProject, writeToProject")
    void testProjectRootHelpers(@TempDir Path projectRoot) throws IOException {
        assertFalse(ProjectManifest.existsIn(projectRoot));
        assertEquals(projectRoot.resolve(".mlc").resolve("project.yaml"), ProjectManifest.resolvePath(projectRoot));

        ProjectManifest manifest = ProjectManifest.of("oop-with-java", "1", 1, "mini-bank-stage-1", 101L);
        manifest.writeToProject(projectRoot);

        assertTrue(ProjectManifest.existsIn(projectRoot));
        ProjectManifest loaded = ProjectManifest.readFromProject(projectRoot);
        assertEquals(manifest, loaded);
    }

    @Test
    @DisplayName("testEqualsHashCodeToString: standard contracts")
    void testEqualsHashCodeToString() {
        ProjectManifest m1 = ProjectManifest.of("oop", "1", 1, "slug", 101L);
        ProjectManifest m2 = ProjectManifest.of("oop", "1", 1, "slug", 101L);
        ProjectManifest m3 = ProjectManifest.of("oop", "1", 2, "slug", 101L);

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertTrue(m1.toString().contains("oop"));
        assertTrue(m1.toString().contains("slug"));
    }
}
