package jsd.toiyeujava.minileetcode.plugin.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArchiveExtractor Unit & Security Tests")
class ArchiveExtractorTest {

    private ArchiveExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ArchiveExtractor();
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private static byte[] createZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String name = entry.getKey();
                ZipEntry ze = new ZipEntry(name);
                zos.putNextEntry(ze);
                if (entry.getValue() != null && !name.endsWith("/")) {
                    zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    // ==========================================
    // Happy Path Tests
    // ==========================================

    @Test
    @DisplayName("Extract normal ZIP with multiple files and nested directories")
    void testExtractNormalArchiveMultipleFilesAndDirs(@TempDir Path tempDir) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", "# Problem Description");
        files.put("src/main/java/", null); // directory entry
        files.put("src/main/java/Main.java", "public class Main { public static void main(String[] args) {} }");
        files.put("src/main/resources/app.properties", "key=value");

        byte[] zipBytes = createZip(files);
        ArchiveExtractor.ExtractionResult result = extractor.extract(zipBytes, tempDir);

        assertEquals(3, result.extractedFiles(), "Should extract 3 regular files (directory excluded)");
        assertEquals(0, result.skippedFiles(), "Should skip 0 files");
        assertEquals(3, result.totalFiles());
        assertEquals(3, result.writtenPaths().size());

        // Verify file presence and content
        assertTrue(Files.exists(tempDir.resolve("README.md")));
        assertEquals("# Problem Description", Files.readString(tempDir.resolve("README.md")));

        assertTrue(Files.exists(tempDir.resolve("src/main/java/Main.java")));
        assertEquals("public class Main { public static void main(String[] args) {} }",
                Files.readString(tempDir.resolve("src/main/java/Main.java")));

        assertTrue(Files.exists(tempDir.resolve("src/main/resources/app.properties")));
        assertEquals("key=value", Files.readString(tempDir.resolve("src/main/resources/app.properties")));
    }

    @Test
    @DisplayName("Extract using InputStream overload produces identical result")
    void testExtractWithInputStreamOverload(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Map.of("data.txt", "hello stream"));
        try (ByteArrayInputStream in = new ByteArrayInputStream(zipBytes)) {
            ArchiveExtractor.ExtractionResult result = extractor.extract(in, tempDir);
            assertEquals(1, result.extractedFiles());
            assertEquals("hello stream", Files.readString(tempDir.resolve("data.txt")));
        }
    }

    @Test
    @DisplayName("Nested files automatically create missing parent directories")
    void testAutoCreateParentDirectoriesForNestedFiles(@TempDir Path tempDir) throws IOException {
        // ZIP has no explicit directory entries, only deep file path
        byte[] zipBytes = createZip(Map.of("a/b/c/d/deep.txt", "deep content"));

        ArchiveExtractor.ExtractionResult result = extractor.extract(zipBytes, tempDir);

        assertEquals(1, result.extractedFiles());
        Path deepFile = tempDir.resolve("a/b/c/d/deep.txt");
        assertTrue(Files.exists(deepFile));
        assertEquals("deep content", Files.readString(deepFile));
    }

    @Test
    @DisplayName("Directory entries alone create folders and count 0 extracted files")
    void testDirectoryEntriesAloneCreateFolders(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Collections.singletonMap("empty_dir/", null));

        ArchiveExtractor.ExtractionResult result = extractor.extract(zipBytes, tempDir);

        assertEquals(0, result.extractedFiles());
        assertEquals(0, result.skippedFiles());
        assertTrue(Files.isDirectory(tempDir.resolve("empty_dir")));
    }

    // ==========================================
    // Security & Zip Slip Tests
    // ==========================================

    @Test
    @DisplayName("Zip Slip attack with relative parent traversal throws SecurityException")
    void testZipSlipRelativeTraversalThrowsSecurityException(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Map.of("../../evil.txt", "malicious payload"));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                extractor.extract(zipBytes, tempDir));

        assertTrue(ex.getMessage().contains("Zip Slip detected"), "Exception message should identify Zip Slip");
        assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")), "File must not be written outside targetDir");
    }

    @Test
    @DisplayName("Zip Slip attack with Windows backslashes throws SecurityException")
    void testZipSlipWindowsBackslashTraversalThrowsSecurityException(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Map.of("..\\..\\evil_win.txt", "malicious payload"));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                extractor.extract(zipBytes, tempDir));

        assertTrue(ex.getMessage().contains("Zip Slip detected"));
    }

    @Test
    @DisplayName("Zip Slip attack with nested folder escape throws SecurityException")
    void testZipSlipNestedEscapeThrowsSecurityException(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Map.of("allowed/../../../evil.txt", "escape payload"));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                extractor.extract(zipBytes, tempDir));

        assertTrue(ex.getMessage().contains("Zip Slip detected"));
    }

    @Test
    @DisplayName("Zip Slip attack with absolute path throws SecurityException")
    void testZipSlipAbsolutePathThrowsSecurityException(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Map.of("/evil_root.txt", "root escape"));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                extractor.extract(zipBytes, tempDir));

        assertTrue(ex.getMessage().contains("Zip Slip detected"));
    }

    // ==========================================
    // Non-Overwrite Tests (FR-PL-002, AC-004)
    // ==========================================

    @Test
    @DisplayName("Non-overwrite policy preserves student existing files (AC-004)")
    void testNonOverwritePreservesExistingUserWork(@TempDir Path tempDir) throws IOException {
        // Given student already modified Bank.java
        Path bankFile = tempDir.resolve("Bank.java");
        Files.writeString(bankFile, "public class Bank { /* Student custom implementation */ }");

        // When extracting starter template containing Bank.java and new Account.java
        byte[] zipBytes = createZip(Map.of(
                "Bank.java", "public class Bank { /* Starter template override */ }",
                "Account.java", "public class Account { /* New stage file */ }"
        ));

        ArchiveExtractor.ExtractionResult result = extractor.extract(zipBytes, tempDir);

        // Then Bank.java is untouched, Account.java is extracted
        assertEquals(1, result.extractedFiles());
        assertEquals(1, result.skippedFiles());
        assertEquals(List.of(tempDir.resolve("Account.java")), result.writtenPaths());

        assertEquals("public class Bank { /* Student custom implementation */ }",
                Files.readString(bankFile), "Student's Bank.java must remain intact!");
        assertEquals("public class Account { /* New stage file */ }",
                Files.readString(tempDir.resolve("Account.java")));
    }

    @Test
    @DisplayName("Multiple identical entries in same archive do not overwrite first extracted")
    void testDuplicateEntriesInZipDoNotOverwrite(@TempDir Path tempDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry e1 = new ZipEntry("duplicate.txt");
            zos.putNextEntry(e1);
            zos.write("First version".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry e2 = new ZipEntry("./duplicate.txt");
            zos.putNextEntry(e2);
            zos.write("Second version".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ArchiveExtractor.ExtractionResult result = extractor.extract(baos.toByteArray(), tempDir);

        assertEquals(1, result.extractedFiles());
        assertEquals(1, result.skippedFiles());
        assertEquals("First version", Files.readString(tempDir.resolve("duplicate.txt")));
    }

    // ==========================================
    // Source Routing Tests (.java -> src/main/java/)
    // ==========================================

    @Test
    @DisplayName("Routing enabled moves bare .java files to src/main/java/ but leaves others at root")
    void testRouteJavaSourcesToSrcMainJava(@TempDir Path tempDir) throws IOException {
        Map<String, String> files = Map.of(
                "Solution.java", "public class Solution {}",
                "README.md", "# Readme"
        );
        byte[] zipBytes = createZip(files);

        ArchiveExtractor.ExtractionResult result = extractor.extractRoutingJava(zipBytes, tempDir);

        assertEquals(2, result.extractedFiles());
        assertTrue(Files.exists(tempDir.resolve("src/main/java/Solution.java")),
                "Solution.java should be routed to src/main/java/");
        assertTrue(Files.exists(tempDir.resolve("README.md")),
                "README.md should remain at project root");
        assertFalse(Files.exists(tempDir.resolve("Solution.java")),
                "Solution.java should not exist at project root");
    }

    @Test
    @DisplayName("Routing preserves files that already reside in src/ hierarchy")
    void testRouteJavaSourcesPreservesExistingSrcHierarchy(@TempDir Path tempDir) throws IOException {
        Map<String, String> files = Map.of(
                "src/test/java/SolutionTest.java", "public class SolutionTest {}"
        );
        byte[] zipBytes = createZip(files);

        ArchiveExtractor.ExtractionResult result = extractor.extractRoutingJava(zipBytes, tempDir);

        assertEquals(1, result.extractedFiles());
        assertTrue(Files.exists(tempDir.resolve("src/test/java/SolutionTest.java")));
        assertFalse(Files.exists(tempDir.resolve("src/main/java/src/test/java/SolutionTest.java")));
    }

    // ==========================================
    // Edge Cases & Robustness Tests
    // ==========================================

    @Test
    @DisplayName("Empty ZIP archive yields 0 extracted and 0 skipped")
    void testEmptyZipArchive(@TempDir Path tempDir) throws IOException {
        byte[] emptyZip = createZip(Collections.emptyMap());

        ArchiveExtractor.ExtractionResult result = extractor.extract(emptyZip, tempDir);

        assertEquals(0, result.extractedFiles());
        assertEquals(0, result.skippedFiles());
        assertTrue(result.writtenPaths().isEmpty());
    }

    @Test
    @DisplayName("Corrupt binary data throws IOException")
    void testCorruptDataThrowsIOException(@TempDir Path tempDir) {
        byte[] corrupt = new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0xDE, (byte) 0xAD};

        assertThrows(IOException.class, () -> extractor.extract(corrupt, tempDir));
    }

    @Test
    @DisplayName("Null arguments throw NullPointerException with descriptive messages")
    void testNullArgumentsValidation(@TempDir Path tempDir) {
        assertThrows(NullPointerException.class, () ->
                extractor.extract((byte[]) null, tempDir));
        assertThrows(NullPointerException.class, () ->
                extractor.extract(new byte[0], null));
    }

    @Test
    @DisplayName("ExtractionResult writtenPaths is unmodifiable")
    void testExtractionResultImmutability(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZip(Map.of("test.txt", "abc"));
        ArchiveExtractor.ExtractionResult result = extractor.extract(zipBytes, tempDir);

        assertThrows(UnsupportedOperationException.class, () ->
                result.writtenPaths().add(tempDir.resolve("other.txt")));
    }
}
