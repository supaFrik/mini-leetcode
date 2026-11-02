package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Empirical Adversarial Stress Test Suite for MultiFileSourceExtractor.
 * Designed to challenge Zip Slip, Path Traversal, Resource Exhaustion,
 * Target Directory Overwriting/Integrity, and Residual Leaks.
 */
class MultiFileSourceExtractorAdversarialTest {

    private MultiFileSourceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MultiFileSourceExtractor();
    }

    // =========================================================================
    // 1. NESTED TRAVERSAL ATTACK VECTORS (PASSED)
    // =========================================================================

    @ParameterizedTest(name = "Nested traversal payload: {0}")
    @ValueSource(strings = {
            "a/b/../../../../evil.txt",
            "com/example/../../../../evil.txt",
            "a/b/c/d/../../../../../../evil.txt",
            "sub/dir/../../evil.txt",
            "deep/nested/package/structure/../../../../../../../../evil.txt"
    })
    @DisplayName("Challenge 1: Nested path traversal payloads are blocked and leak zero files outside targetDir")
    void nestedTraversal_ThrowsSecurityExceptionAndLeaksZeroFiles(String evilPath, @TempDir Path tempDir) {
        byte[] evilZip = createZipArchive(List.of(
                new TestFile("safe.java", "public class Safe {}"),
                new TestFile(evilPath, "MALICIOUS PAYLOAD EXECUTED")
        ));

        Path parentDir = tempDir.getParent();
        Path evilFileInParent = parentDir.resolve("evil.txt");

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class);

        assertThat(evilFileInParent).doesNotExist();
        assertThat(parentDir.resolve("evil.txt")).doesNotExist();
        assertThat(tempDir.resolve("safe.java")).doesNotExist(); // partial cleanup check
    }

    // =========================================================================
    // 2. WINDOWS UNC AND BACKSLASH TRAVERSAL ATTACK VECTORS (PASSED)
    // =========================================================================

    @ParameterizedTest(name = "Windows UNC/Backslash payload: {0}")
    @ValueSource(strings = {
            "\\\\?\\C:\\evil.txt",
            "\\\\localhost\\c$\\evil.txt",
            "\\\\127.0.0.1\\share\\evil.txt",
            "..\\..\\evil.txt",
            "..\\..\\..\\evil.txt",
            "sub\\..\\..\\evil.txt",
            "C:\\evil.txt",
            "c:evil.txt",
            "D:\\danger\\evil.txt",
            "D:/danger/evil.txt",
            "\\\\.\\PhysicalDrive0"
    })
    @DisplayName("Challenge 2: Windows UNC and backslash traversal attacks are blocked throwing SecurityException")
    void windowsUncAndBackslashTraversal_ThrowsSecurityException(String evilPath, @TempDir Path tempDir) {
        byte[] evilZip = createZipArchive(List.of(
                new TestFile(evilPath, "MALICIOUS WINDOWS OVERWRITE")
        ));

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    // =========================================================================
    // 3. ENCODED OR TRICKY PATH TOKENS (PASSED)
    // =========================================================================

    @ParameterizedTest(name = "Tricky path token payload: {0}")
    @ValueSource(strings = {
            "././../evil.txt",
            "foo/./../../evil.txt",
            "dir/./././../../evil.txt",
            "a/./b/../../../../evil.txt",
            "dir/.. /evil.txt",
            " ..\\evil.txt",
            "foo/bar/../../../../../../etc/passwd"
    })
    @DisplayName("Challenge 3: Tricky path tokens attempting traversal throw SecurityException")
    void trickyPathTokens_ThrowsSecurityException(String evilPath, @TempDir Path tempDir) {
        byte[] evilZip = createZipArchive(List.of(
                new TestFile(evilPath, "TRICKY TOKEN CONTENT")
        ));

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    // =========================================================================
    // 4. TARGET DIRECTORY RESOLUTION AND OVERWRITE RESISTANCE (PASSED)
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Reject entry resolving directly to target directory itself (.)")
    void entryResolvingToTargetDir_ThrowsSecurityException(@TempDir Path tempDir) {
        assertThatThrownBy(() -> extractor.validateAndResolvePath(".", tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("resolves to target directory itself");
    }

    @Test
    @DisplayName("Challenge 4.2: Reject entry with empty or whitespace-only name")
    void emptyOrBlankEntry_ThrowsSecurityException(@TempDir Path tempDir) {
        assertThatThrownBy(() -> extractor.validateAndResolvePath("", tempDir))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> extractor.validateAndResolvePath("   ", tempDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Challenge 4.3: Extraction does not corrupt or overwrite pre-existing files in targetDir on failure")
    void extractionFailure_PreservesPreExistingFiles(@TempDir Path tempDir) throws IOException {
        Path existing = tempDir.resolve("pre_existing.txt");
        Files.writeString(existing, "CRITICAL DATA");

        byte[] evilZip = createZipArchive(List.of(
                new TestFile("../evil.txt", "MALICIOUS")
        ));

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class);

        assertThat(existing).exists();
        assertThat(Files.readString(existing)).isEqualTo("CRITICAL DATA");
    }

    // =========================================================================
    // 5. RESOURCE EXHAUSTION: HIGH COMPRESSION RATIO ZIP BOMB & ENTRY LIMITS
    // =========================================================================

    @Test
    @DisplayName("Challenge 5.1: High-ratio Zip Bomb expanding to 21MB is halted with SecurityException")
    void highRatioZipBomb_SingleEntryExceeding20MB_ThrowsSecurityException(@TempDir Path tempDir) {
        byte[] bombBytes = new byte[21 * 1024 * 1024];
        byte[] bombZip = createZipArchive(List.of(
                new TestFile("Bomb.txt", bombBytes)
        ));

        assertThatThrownBy(() -> extractor.extract(bombZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("20MB");
    }

    @Test
    @DisplayName("Challenge 5.2: Multi-file archive whose cumulative size exceeds 20MB is halted")
    void multiFileArchive_CumulativeSizeExceeding20MB_ThrowsSecurityException(@TempDir Path tempDir) {
        byte[] file1Bytes = new byte[12 * 1024 * 1024];
        byte[] file2Bytes = new byte[10 * 1024 * 1024];

        byte[] bombZip = createZipArchive(List.of(
                new TestFile("File1.txt", file1Bytes),
                new TestFile("File2.txt", file2Bytes)
        ));

        assertThatThrownBy(() -> extractor.extract(bombZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Archive uncompressed size exceeds maximum allowed limit of 20MB");

        assertThat(tempDir.resolve("File1.txt")).doesNotExist();
    }

    @Test
    @DisplayName("Challenge 5.3: Archive with 105 entries halts at 101 and cleans up prior 100 entries")
    void archiveWith105Entries_HaltsAndCleansUp(@TempDir Path tempDir) {
        List<TestFile> files = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            files.add(new TestFile("entry_" + i + ".java", "public class Entry" + i + " {}"));
        }
        byte[] zipBytes = createZipArchive(files);

        assertThatThrownBy(() -> extractor.extract(zipBytes, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("maximum entry count");

        for (int i = 0; i < 100; i++) {
            assertThat(tempDir.resolve("entry_" + i + ".java")).doesNotExist();
        }
    }

    // =========================================================================
    // 6. DELIMITED BUNDLE ADVERSARIAL STRESS TESTS (PASSED)
    // =========================================================================

    @ParameterizedTest(name = "Delimited bundle traversal: {0}")
    @ValueSource(strings = {
            "a/b/../../../../evil.txt",
            "\\\\?\\C:\\evil.txt",
            "..\\..\\evil.txt",
            "././../evil.txt",
            "C:/Windows/System32/cmd.exe"
    })
    @DisplayName("Challenge 6.1: Delimited bundle rejecting traversal and UNC paths")
    void delimitedBundle_MaliciousPaths_ThrowsSecurityException(String evilPath, @TempDir Path tempDir) {
        String bundle = String.format("""
                // === FILE: safe/Safe.java ===
                package safe;
                public class Safe {}
                
                // === FILE: %s ===
                package evil;
                public class Evil {}
                """, evilPath);

        assertThatThrownBy(() -> extractor.extract(bundle, tempDir))
                .isInstanceOf(SecurityException.class);

        assertThat(tempDir.resolve("safe/Safe.java")).doesNotExist();
    }

    @Test
    @DisplayName("Challenge 6.2: Delimited bundle exceeding 100 files is halted")
    void delimitedBundle_Exceeding100Files_ThrowsSecurityException(@TempDir Path tempDir) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 102; i++) {
            sb.append("// === FILE: File").append(i).append(".java ===\n");
            sb.append("public class File").append(i).append(" {}\n");
        }

        assertThatThrownBy(() -> extractor.extract(sb.toString(), tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("maximum entry count");
    }

    // =========================================================================
    // 7. SPEC DEFECT: JSON BUNDLE SWALLOWS SECURITY EXCEPTIONS
    // =========================================================================

    @ParameterizedTest(name = "JSON bundle traversal: {0}")
    @ValueSource(strings = {
            "a/b/../../../../evil.txt",
            "\\\\?\\C:\\evil.txt",
            "..\\..\\evil.txt",
            "././../evil.txt",
            "C:/evil.txt"
    })
    @DisplayName("Defect 1: JSON bundle with malicious traversal MUST throw SecurityException (Fails: catch(Exception) swallows)")
    void jsonBundleArray_MaliciousPaths_MustThrowSecurityException(String evilPath, @TempDir Path tempDir) {
        String json = String.format("""
                {
                  "files": [
                    { "path": "com/Safe.java", "content": "package com; public class Safe {}" },
                    { "path": "%s", "content": "MALICIOUS" }
                  ]
                }
                """, evilPath.replace("\\", "\\\\"));

        assertThatThrownBy(() -> extractor.extract(json, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Defect 2: JSON bundle object map format with traversal key MUST throw SecurityException")
    void jsonBundleObjectMap_TraversalKey_MustThrowSecurityException(@TempDir Path tempDir) {
        String json = """
                {
                  "Safe.java": "public class Safe {}",
                  "../../evil.txt": "MALICIOUS"
                }
                """;

        assertThatThrownBy(() -> extractor.extract(json, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Defect 3: JSON bundle exceeding 100 entries MUST throw SecurityException")
    void jsonBundle_Exceeding100Entries_MustThrowSecurityException(@TempDir Path tempDir) {
        StringBuilder sb = new StringBuilder("{\"files\": [");
        for (int i = 0; i < 102; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"path\": \"F%d.java\", \"content\": \"public class F%d {}\"}", i, i));
        }
        sb.append("]}");

        assertThatThrownBy(() -> extractor.extract(sb.toString(), tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("maximum entry count");
    }

    // =========================================================================
    // 8. SPEC DEFECT: RESIDUAL FILE ON ZIP BOMB ABORT (DISK EXHAUSTION DOS)
    // =========================================================================

    @Test
    @DisplayName("Defect 4: Zip bomb active file MUST be deleted on abort, not left on disk (Fails: 20MB file leaked)")
    void zipBomb_ActiveFileMustBeDeletedOnAbort(@TempDir Path tempDir) {
        byte[] bombBytes = new byte[21 * 1024 * 1024]; // 21MB zeros
        byte[] bombZip = createZipArchive(List.of(
                new TestFile("HugeBomb.txt", bombBytes)
        ));

        assertThatThrownBy(() -> extractor.extract(bombZip, tempDir))
                .isInstanceOf(SecurityException.class);

        Path residualBomb = tempDir.resolve("HugeBomb.txt");
        // Specification requirement: zero residual files after SecurityException abort
        assertThat(residualBomb).doesNotExist();
    }

    // =========================================================================
    // 9. SPEC DEFECT: NULL BYTE IN ENTRY NAME & STRANDED CLEANUP
    // =========================================================================

    @Test
    @DisplayName("Defect 5: Null byte entry MUST throw SecurityException and clean up preceding files")
    void nullByteInArchive_MustThrowSecurityExceptionAndCleanUpPrecedingFiles(@TempDir Path tempDir) {
        byte[] zipWithNullByte = createZipArchive(List.of(
                new TestFile("PrecedingFile.java", "public class PrecedingFile {}"),
                new TestFile("evil.txt\0.java", "MALICIOUS")
        ));

        assertThatThrownBy(() -> extractor.extract(zipWithNullByte, tempDir))
                .isInstanceOf(SecurityException.class);

        Path precedingFile = tempDir.resolve("PrecedingFile.java");
        assertThat(precedingFile).doesNotExist();
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private record TestFile(String name, byte[] content) {
        TestFile(String name, String content) {
            this(name, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] createZipArchive(List<TestFile> files) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (TestFile file : files) {
                ZipEntry entry = new ZipEntry(file.name());
                zos.putNextEntry(entry);
                zos.write(file.content());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to construct test zip archive", e);
        }
        return baos.toByteArray();
    }
}
