package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiFileSourceExtractorTest {

    private MultiFileSourceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MultiFileSourceExtractor();
    }

    @Test
    @DisplayName("Test 1: Extract normal ZIP and preserve package directory hierarchy")
    void extract_NormalZip_PreservesPackageStructure(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZipArchive(List.of(
                new TestFile("com/example/model/Shape.java", "package com.example.model;\npublic interface Shape { double area(); }"),
                new TestFile("com/example/model/Circle.java", "package com.example.model;\npublic class Circle implements Shape { private double r; public Circle(double r){this.r=r;} public double area(){return Math.PI*r*r;} }"),
                new TestFile("com/example/Main.java", "package com.example;\nimport com.example.model.*;\npublic class Main { public static void main(String[] args){ Shape s = new Circle(5); } }")
        ));

        List<Path> extracted = extractor.extract(zipBytes, tempDir);

        assertThat(extracted).hasSize(3);
        Path shapePath = tempDir.resolve("com/example/model/Shape.java");
        Path circlePath = tempDir.resolve("com/example/model/Circle.java");
        Path mainPath = tempDir.resolve("com/example/Main.java");

        assertThat(shapePath).exists().isRegularFile();
        assertThat(circlePath).exists().isRegularFile();
        assertThat(mainPath).exists().isRegularFile();

        assertThat(Files.readString(shapePath)).contains("interface Shape");
        assertThat(Files.readString(circlePath)).contains("class Circle");
        assertThat(Files.readString(mainPath)).contains("class Main");
    }

    @Test
    @DisplayName("Test 2: Reject ZIP with relative path traversal (../) throwing SecurityException")
    void extract_ZipSlipRelativeTraversal_ThrowsSecurityException(@TempDir Path tempDir) {
        byte[] evilZip = createZipArchive(List.of(
                new TestFile("../evil.txt", "MALICIOUS CONTENT")
        ));

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Zip Slip");

        assertThat(tempDir.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    @DisplayName("Test 3: Reject ZIP with backslash traversal (..\\) throwing SecurityException")
    void extract_ZipSlipBackslashTraversal_ThrowsSecurityException(@TempDir Path tempDir) {
        byte[] evilZip = createZipArchive(List.of(
                new TestFile("sub\\..\\..\\evil_win.txt", "MALICIOUS CONTENT")
        ));

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Zip Slip");

        assertThat(tempDir.getParent().resolve("evil_win.txt")).doesNotExist();
    }

    @Test
    @DisplayName("Test 4: Reject ZIP with leading absolute slash (/ or \\) throwing SecurityException")
    void extract_ZipSlipLeadingSlash_ThrowsSecurityException(@TempDir Path tempDir) {
        byte[] evilZip = createZipArchive(List.of(
                new TestFile("/etc/shadow", "ROOT ACCESS")
        ));

        assertThatThrownBy(() -> extractor.extract(evilZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Illegal absolute path");
    }

    @Test
    @DisplayName("Test 5: Reject ZIP bomb exceeding 20MB uncompressed limit throwing SecurityException")
    void extract_ZipBombExceeding20MB_ThrowsSecurityException(@TempDir Path tempDir) {
        // Create highly compressible entry that uncompresses to 21MB
        byte[] largeContent = new byte[21 * 1024 * 1024]; // 21MB of zeroes
        byte[] bombZip = createZipArchive(List.of(
                new TestFile("HugeFile.txt", largeContent)
        ));

        assertThatThrownBy(() -> extractor.extract(bombZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("20MB");
    }

    @Test
    @DisplayName("Test 6: Reject archive exceeding 100 entries throwing SecurityException")
    void extract_EntryCountExceeds100_ThrowsSecurityException(@TempDir Path tempDir) {
        List<TestFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            files.add(new TestFile("file" + i + ".java", "public class F" + i + " {}"));
        }
        byte[] hugeEntryZip = createZipArchive(files);

        assertThatThrownBy(() -> extractor.extract(hugeEntryZip, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("maximum entry count");
    }

    @Test
    @DisplayName("Test 7: Extract delimited text bundle format (// === FILE: ... ===)")
    void extract_DelimitedBundle_ExtractsMultipleFiles(@TempDir Path tempDir) throws IOException {
        String bundle = """
                // === FILE: com/example/A.java ===
                package com.example;
                public class A {}
                
                // === FILE: com/example/B.java ===
                package com.example;
                public class B {}
                """;

        List<Path> extracted = extractor.extract(bundle, tempDir);

        assertThat(extracted).hasSize(2);
        assertThat(tempDir.resolve("com/example/A.java")).exists().content().contains("class A");
        assertThat(tempDir.resolve("com/example/B.java")).exists().content().contains("class B");
    }

    @Test
    @DisplayName("Test 8: Extract JSON bundle with files array")
    void extract_JsonBundle_ArrayFormat_ExtractsMultipleFiles(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  "files": [
                    { "path": "model/Shape.java", "content": "package model; public interface Shape {}" },
                    { "path": "model/Circle.java", "content": "package model; public class Circle implements Shape {}" }
                  ]
                }
                """;

        List<Path> extracted = extractor.extract(json, tempDir);

        assertThat(extracted).hasSize(2);
        assertThat(tempDir.resolve("model/Shape.java")).exists().content().contains("interface Shape");
        assertThat(tempDir.resolve("model/Circle.java")).exists().content().contains("class Circle");
    }

    @Test
    @DisplayName("Test 9: Extract JSON bundle with object key-value map")
    void extract_JsonBundle_ObjectMapFormat_ExtractsMultipleFiles(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  "util/MathHelper.java": "package util; public class MathHelper {}",
                  "util/StringHelper.java": "package util; public class StringHelper {}"
                }
                """;

        List<Path> extracted = extractor.extract(json, tempDir);

        assertThat(extracted).hasSize(2);
        assertThat(tempDir.resolve("util/MathHelper.java")).exists().content().contains("class MathHelper");
        assertThat(tempDir.resolve("util/StringHelper.java")).exists().content().contains("class StringHelper");
    }

    @Test
    @DisplayName("Test 10: Extract Base64-encoded ZIP string")
    void extract_Base64Zip_ExtractsMultipleFiles(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZipArchive(List.of(
                new TestFile("Solution.java", "public class Solution {}")
        ));
        String base64 = Base64.getEncoder().encodeToString(zipBytes);

        List<Path> extracted = extractor.extract(base64, tempDir);

        assertThat(extracted).hasSize(1);
        assertThat(tempDir.resolve("Solution.java")).exists().content().contains("class Solution");
    }

    @Test
    @DisplayName("Test 11: Fallback single Java source string derives class name")
    void extract_SingleJavaFileFallback_ExtractsToClassName(@TempDir Path tempDir) throws IOException {
        String singleCode = """
                package com.demo;
                public class Calculator {
                    public int add(int a, int b) { return a + b; }
                }
                """;

        List<Path> extracted = extractor.extract(singleCode, tempDir);

        assertThat(extracted).hasSize(1);
        assertThat(tempDir.resolve("Calculator.java")).exists().content().contains("class Calculator");
    }

    @Test
    @DisplayName("Test 12: Reject forbidden executable extensions (.exe, .sh, .bat)")
    void extract_ForbiddenExtension_ThrowsSecurityException(@TempDir Path tempDir) {
        byte[] zipBytes = createZipArchive(List.of(
                new TestFile("hack.sh", "rm -rf /")
        ));

        assertThatThrownBy(() -> extractor.extract(zipBytes, tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Forbidden executable file type");
    }

    @Test
    @DisplayName("Test 13: macOS __MACOSX metadata and .DS_Store are safely ignored")
    void extract_MacOsMetadata_SafelySkipped(@TempDir Path tempDir) throws IOException {
        byte[] zipBytes = createZipArchive(List.of(
                new TestFile("__MACOSX/._Shape.java", "METADATA"),
                new TestFile(".DS_Store", "DS_STORE"),
                new TestFile("Shape.java", "public class Shape {}")
        ));

        List<Path> extracted = extractor.extract(zipBytes, tempDir);

        assertThat(extracted).hasSize(1);
        assertThat(tempDir.resolve("Shape.java")).exists();
        assertThat(tempDir.resolve("__MACOSX")).doesNotExist();
        assertThat(tempDir.resolve(".DS_Store")).doesNotExist();
    }

    @Test
    @DisplayName("Test 14: Corrupted ZIP byte stream throws IOException")
    void extract_CorruptedZipBytes_ThrowsIOException(@TempDir Path tempDir) {
        byte[] corrupted = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x12, 0x34}; // truncated invalid header

        assertThatThrownBy(() -> extractor.extract(corrupted, tempDir))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Test 15: Null, empty, or blank string inputs throw IllegalArgumentException")
    void extract_EmptyOrBlankString_ThrowsIllegalArgumentException(@TempDir Path tempDir) {
        assertThatThrownBy(() -> extractor.extract((String) null, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");

        assertThatThrownBy(() -> extractor.extract("", tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");

        assertThatThrownBy(() -> extractor.extract("   \t\n  ", tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("Test 16: Empty ZIP archive with no files throws IllegalArgumentException")
    void extract_EmptyZipArchive_ThrowsIllegalArgumentException(@TempDir Path tempDir) {
        byte[] emptyZip = createZipArchive(List.of());

        assertThatThrownBy(() -> extractor.extract(emptyZip, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archive contains no regular files");
    }

    // Helper utilities for ZIP creation in tests
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
