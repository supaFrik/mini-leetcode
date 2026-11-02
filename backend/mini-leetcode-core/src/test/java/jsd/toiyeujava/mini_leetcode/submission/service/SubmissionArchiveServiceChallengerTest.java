package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionArchiveServiceChallengerTest {

    private SubmissionArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new SubmissionArchiveServiceImpl();
    }

    // =========================================================================
    // 1. Boundary Verification: Exactly 100 vs 101 Entries across All Ingestion Channels
    // =========================================================================
    @Nested
    @DisplayName("Boundary 1: Entry Limits (1, 100, 101 entries)")
    class EntryLimitBoundaries {

        @Test
        @DisplayName("Channel 1 (JSON Files): Exactly 1 entry succeeds")
        void packageFiles_SingleEntry_Succeeds() {
            List<SubmissionFileDto> files = List.of(new SubmissionFileDto("Main.java", "public class Main {}"));
            SubmissionArchiveResult result = archiveService.packageFiles(files);
            assertThat(result.entryCount()).isEqualTo(1);
            assertThat(result.entryPaths()).containsExactly("Main.java");
        }

        @Test
        @DisplayName("Channel 1 (JSON Files): Exactly 100 entries succeeds")
        void packageFiles_Exactly100Entries_Succeeds() {
            List<SubmissionFileDto> files = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                files.add(new SubmissionFileDto("pkg/Class" + i + ".java", "package pkg; public class Class" + i + " {}"));
            }
            SubmissionArchiveResult result = archiveService.packageFiles(files);
            assertThat(result.entryCount()).isEqualTo(100);
            assertThat(result.entryPaths()).hasSize(100);
        }

        @Test
        @DisplayName("Channel 1 (JSON Files): 101 entries throws InvalidSubmissionPathException")
        void packageFiles_101Entries_ThrowsException() {
            List<SubmissionFileDto> files = new ArrayList<>();
            for (int i = 1; i <= 101; i++) {
                files.add(new SubmissionFileDto("Class" + i + ".java", "public class Class" + i + " {}"));
            }
            assertThatThrownBy(() -> archiveService.packageFiles(files))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("maximum entry count");
        }

        @Test
        @DisplayName("Channel 2 (ZIP Stream): Exactly 100 entries succeeds and repackages cleanly")
        void sanitizeZip_Exactly100Entries_Succeeds() throws IOException {
            byte[] zip = createZipWithEntries(100);
            SubmissionArchiveResult result = archiveService.sanitizeAndRepackageZip(new ByteArrayInputStream(zip));
            assertThat(result.entryCount()).isEqualTo(100);
            assertThat(result.entryPaths()).hasSize(100);
        }

        @Test
        @DisplayName("Channel 2 (ZIP Stream): 101 entries throws InvalidSubmissionPathException")
        void sanitizeZip_101Entries_ThrowsException() throws IOException {
            byte[] zip = createZipWithEntries(101);
            assertThatThrownBy(() -> archiveService.sanitizeAndRepackageZip(new ByteArrayInputStream(zip)))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("maximum entry count");
        }

        @Test
        @DisplayName("Channel 2 (ZIP Stream): 10 directories + 100 files = 100 entries (directories are ignored)")
        void sanitizeZip_DirectoriesIgnored_100FilesAllowed() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Add 10 directory entries
                for (int i = 1; i <= 10; i++) {
                    ZipEntry dirEntry = new ZipEntry("dir" + i + "/");
                    zos.putNextEntry(dirEntry);
                    zos.closeEntry();
                }
                // Add 100 regular file entries
                for (int i = 1; i <= 100; i++) {
                    ZipEntry fileEntry = new ZipEntry("dir1/File" + i + ".java");
                    zos.putNextEntry(fileEntry);
                    zos.write(("public class File" + i + " {}").getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }

            SubmissionArchiveResult result = archiveService.sanitizeAndRepackageZip(new ByteArrayInputStream(baos.toByteArray()));
            assertThat(result.entryCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("Channel 3 (Delimited Markers): Exactly 100 markers succeeds")
        void packageDelimited_Exactly100Markers_Succeeds() {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 100; i++) {
                sb.append("// === FILE: model/Entity").append(i).append(".java ===\n");
                sb.append("package model;\npublic class Entity").append(i).append(" {}\n\n");
            }

            SubmissionArchiveResult result = archiveService.packageDelimitedCode(sb.toString(), SubmissionLanguage.JAVA);
            assertThat(result.entryCount()).isEqualTo(100);
            assertThat(result.entryPaths()).hasSize(100);
        }

        @Test
        @DisplayName("Channel 3 (Delimited Markers): 101 markers throws InvalidSubmissionPathException")
        void packageDelimited_101Markers_ThrowsException() {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 101; i++) {
                sb.append("// === FILE: Entity").append(i).append(".java ===\n");
                sb.append("public class Entity").append(i).append(" {}\n\n");
            }

            assertThatThrownBy(() -> archiveService.packageDelimitedCode(sb.toString(), SubmissionLanguage.JAVA))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("maximum entry count");
        }
    }

    // =========================================================================
    // 2. Boundary Verification: Uncompressed Size Limits (20MB vs > 20MB)
    // =========================================================================
    @Nested
    @DisplayName("Boundary 2: Uncompressed Size Limits (20MB vs > 20MB)")
    class SizeLimitBoundaries {

        @Test
        @DisplayName("Cumulative size exactly 20MB succeeds")
        void packageFiles_Exactly20MB_Succeeds() {
            // Two files: 10MB each = exactly 20MB (20 * 1024 * 1024 bytes)
            int tenMB = 10 * 1024 * 1024;
            byte[] tenMBBytes = new byte[tenMB];
            Arrays.fill(tenMBBytes, (byte) 'A');
            String content = new String(tenMBBytes, StandardCharsets.ISO_8859_1);

            List<SubmissionFileDto> files = List.of(
                    new SubmissionFileDto("File1.txt", content),
                    new SubmissionFileDto("File2.txt", content)
            );

            SubmissionArchiveResult result = archiveService.packageFiles(files);
            assertThat(result.totalUncompressedSize()).isEqualTo(20 * 1024 * 1024L);
        }

        @Test
        @DisplayName("Cumulative size 20MB + 1 byte throws PayloadTooLargeException")
        void packageFiles_20MBPlusOneByte_ThrowsPayloadTooLarge() {
            int tenMB = 10 * 1024 * 1024;
            byte[] tenMBBytes = new byte[tenMB];
            Arrays.fill(tenMBBytes, (byte) 'A');
            String content1 = new String(tenMBBytes, StandardCharsets.ISO_8859_1);

            byte[] tenMBPlusOne = new byte[tenMB + 1];
            Arrays.fill(tenMBPlusOne, (byte) 'B');
            String content2 = new String(tenMBPlusOne, StandardCharsets.ISO_8859_1);

            List<SubmissionFileDto> files = List.of(
                    new SubmissionFileDto("File1.txt", content1),
                    new SubmissionFileDto("File2.txt", content2)
            );

            assertThatThrownBy(() -> archiveService.packageFiles(files))
                    .isInstanceOf(PayloadTooLargeException.class)
                    .hasMessageContaining("20MB");
        }

        @Test
        @DisplayName("Single file exactly 20MB in ZIP stream succeeds")
        void sanitizeZip_SingleFileExactly20MB_Succeeds() throws IOException {
            int exact20MB = 20 * 1024 * 1024;
            byte[] data = new byte[exact20MB];

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("Large.dat"));
                zos.write(data);
                zos.closeEntry();
            }

            SubmissionArchiveResult result = archiveService.sanitizeAndRepackageZip(new ByteArrayInputStream(baos.toByteArray()));
            assertThat(result.totalUncompressedSize()).isEqualTo(exact20MB);
        }

        @Test
        @DisplayName("Single file 20MB + 1 byte in ZIP stream throws PayloadTooLargeException")
        void sanitizeZip_SingleFile20MBPlusOne_ThrowsPayloadTooLarge() throws IOException {
            int size = 20 * 1024 * 1024 + 1;
            byte[] data = new byte[size];

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("TooLarge.dat"));
                zos.write(data);
                zos.closeEntry();
            }

            assertThatThrownBy(() -> archiveService.sanitizeAndRepackageZip(new ByteArrayInputStream(baos.toByteArray())))
                    .isInstanceOf(PayloadTooLargeException.class)
                    .hasMessageContaining("20MB");
        }
    }

    // =========================================================================
    // 3. Boundary Verification: Path Parsing & Traversal Hardening
    // =========================================================================
    @Nested
    @DisplayName("Boundary 3: Path Parsing & Traversal Hardening")
    class PathParsingAndSecurityBoundaries {

        @ParameterizedTest(name = "Relative traversal attack: {0}")
        @ValueSource(strings = {
                "../Hack.java",
                "../../Hack.java",
                "a/../../Hack.java",
                "a/b/../../../Hack.java",
                "dir/..",
                "dir/../",
                "dir/.. /file.java",
                "dir/ .. /file.java",
                "..\\windows\\system32\\cmd.exe",
                "sub/..\\..\\secret.key"
        })
        void traversalVectors_ThrowInvalidSubmissionPathException(String path) {
            assertThatThrownBy(() -> SourcePathValidator.sanitizeAndValidatePath(path))
                    .isInstanceOf(InvalidSubmissionPathException.class);
        }

        @ParameterizedTest(name = "Null character attack: {0}")
        @ValueSource(strings = {
                "Solution.java\0",
                "\0Solution.java",
                "pkg/\0/Solution.java",
                "pkg/Solution\0.java",
                "test\0case.txt"
        })
        void nullCharacterVectors_ThrowInvalidSubmissionPathException(String path) {
            assertThatThrownBy(() -> SourcePathValidator.sanitizeAndValidatePath(path))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("null byte");
        }

        @ParameterizedTest(name = "Absolute / Drive / UNC attack: {0}")
        @ValueSource(strings = {
                "/Solution.java",
                "/etc/passwd",
                "\\Solution.java",
                "\\windows\\system32",
                "C:/Solution.java",
                "C:\\Solution.java",
                "c:Solution.java",
                "D:/path/to/file.java",
                "d:\\path\\to\\file.java",
                "\\\\server\\share\\hack.java",
                "//server/share/hack.java",
                "\\\\?\\C:\\file.java",
                "\\\\.\\PhysicalDrive0",
                "//?/C:/file.java",
                "//./file.java"
        })
        void absoluteAndDrivePrefixVectors_ThrowInvalidSubmissionPathException(String path) {
            assertThatThrownBy(() -> SourcePathValidator.sanitizeAndValidatePath(path))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("absolute or UNC path");
        }

        @ParameterizedTest(name = "Windows reserved device names: {0}")
        @ValueSource(strings = {
                "CON", "con", "CON.java", "con.txt",
                "PRN", "PRN.java",
                "AUX", "aux.java",
                "NUL", "nul.cpp",
                "COM1", "com1.java", "COM9.txt",
                "LPT1", "lpt1.java", "LPT9.dat",
                "sub/dir/CON.java",
                "model/aux.json"
        })
        void windowsReservedDevices_ThrowInvalidSubmissionPathException(String path) {
            assertThatThrownBy(() -> SourcePathValidator.sanitizeAndValidatePath(path))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("Reserved device name");
        }

        @ParameterizedTest(name = "Legitimate safe path: {0}")
        @ValueSource(strings = {
                "Solution.java",
                "src/Solution.java",
                "src/main/java/com/example/MyClass.java",
                "model/user/UserProfile.java",
                "./Solution.java",
                "src/./Solution.java",
                "src//Solution.java",
                "src\\Solution.java",
                "src\\model\\User.java"
        })
        void legitimateSafePaths_SanitizedSuccessfully(String path) {
            String sanitized = SourcePathValidator.sanitizeAndValidatePath(path);
            assertThat(sanitized).doesNotStartWith("/");
            assertThat(sanitized).doesNotContain("\\");
            assertThat(sanitized).doesNotContain("..");
            assertThat(sanitized).endsWith(".java");
        }

        @Test
        @DisplayName("Duplicate entry paths in submission throw InvalidSubmissionPathException")
        void duplicatePaths_ThrowInvalidSubmissionPathException() {
            List<SubmissionFileDto> files = List.of(
                    new SubmissionFileDto("src/Solution.java", "public class Solution {}"),
                    new SubmissionFileDto("src\\Solution.java", "public class Solution {}") // normalized to src/Solution.java
            );

            assertThatThrownBy(() -> archiveService.packageFiles(files))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("Duplicate entry path");
        }
    }

    // =========================================================================
    // 4. Boundary Verification: File Extension Policy
    // =========================================================================
    @Nested
    @DisplayName("Boundary 4: File Extension Policy")
    class FileExtensionPolicyBoundaries {

        @ParameterizedTest(name = "Allowed source & resource extensions: {0}")
        @ValueSource(strings = {
                "Solution.java",
                "Model.JAVA",
                "solution.py",
                "solution.cpp",
                "solution.c",
                "config.json",
                "schema.sql",
                "README.md",
                "application.properties",
                "data.xml",
                "input.txt"
        })
        void allowedExtensions_Succeed(String filename) {
            assertThatCode(() -> SourcePathValidator.sanitizeAndValidatePath(filename))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Forbidden executable/binary extensions: {0}")
        @ValueSource(strings = {
                "malware.exe", "MALWARE.EXE", "malware.Exe",
                "library.dll", "SYSTEM.DLL", "library.Dll",
                "hook.so", "HOOK.SO",
                "script.sh", "RUN.SH", "script.Sh",
                "batch.bat", "START.BAT", "batch.Bat",
                "command.cmd", "KILL.CMD", "command.Cmd",
                "archive.jar", "BACKDOOR.JAR", "archive.Jar",
                "exploit.class", "EXPLOIT.CLASS", "exploit.Class",
                "nested/Exploit.class",
                "nested/exploit.java.exe",
                "nested/trojan.bat"
        })
        void forbiddenExtensions_ThrowInvalidSubmissionPathException(String filename) {
            assertThatThrownBy(() -> SourcePathValidator.sanitizeAndValidatePath(filename))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("Forbidden executable file type");
        }

        @Test
        @DisplayName("Challenger: Delimited marker syntax with Exploit.class must throw InvalidSubmissionPathException")
        void delimitedCode_WithClassFile_ThrowsInvalidSubmissionPathException() {
            String maliciousDelimited = "// === FILE: Exploit.class ===\n" +
                    "\u00CA\u00FE\u00BA\u00BE\u0000\u0000\u0000\u0037\n";

            assertThatThrownBy(() ->
                    archiveService.packageDelimitedCode(maliciousDelimited, SubmissionLanguage.JAVA)
            )
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("Forbidden executable file type in archive");
        }
    }

    // =========================================================================
    // 5. Boundary Verification: Delimited Marker Syntax Parsing & Line Endings
    // =========================================================================
    @Nested
    @DisplayName("Boundary 5: Delimited Marker Syntax & Line Endings")
    class DelimitedMarkerParsingBoundaries {

        @Test
        @DisplayName("Standard triple equals markers with LF (\\n)")
        void parseDelimited_StandardTripleEquals_LF() throws IOException {
            String code = "// === FILE: model/Shape.java ===\n" +
                    "package model;\n" +
                    "public interface Shape { double area(); }\n\n" +
                    "// === FILE: model/Circle.java ===\n" +
                    "package model;\n" +
                    "public class Circle implements Shape {\n" +
                    "    public double area() { return 3.14; }\n" +
                    "}\n";

            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactlyInAnyOrder("model/Shape.java", "model/Circle.java");
            assertThat(result.entryCount()).isEqualTo(2);

            String circleContent = getZipEntryContent(result.zipBytes(), "model/Circle.java");
            assertThat(circleContent).contains("public class Circle implements Shape");
            assertThat(circleContent).doesNotContain("// === FILE:");
        }

        @Test
        @DisplayName("Delimited markers with Windows CRLF (\\r\\n) line endings")
        void parseDelimited_CRLF_LineEndings() throws IOException {
            String code = "// === FILE: A.java ===\r\n" +
                    "public class A {\r\n" +
                    "    private int x = 1;\r\n" +
                    "}\r\n" +
                    "// === FILE: B.java ===\r\n" +
                    "public class B {\r\n" +
                    "    private int y = 2;\r\n" +
                    "}\r\n";

            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactlyInAnyOrder("A.java", "B.java");

            String aContent = getZipEntryContent(result.zipBytes(), "A.java");
            assertThat(aContent).startsWith("public class A");
            assertThat(aContent).contains("private int x = 1;");
        }

        @Test
        @DisplayName("Fence variations: triple hyphen (---), triple hash (###), and no fence")
        void parseDelimited_FenceVariations() {
            String code = "// --- FILE: Service.java ---\n" +
                    "public class Service {}\n\n" +
                    "// ### FILE: Repository.java ###\n" +
                    "public interface Repository {}\n\n" +
                    "// FILE: Controller.java\n" +
                    "public class Controller {}\n";

            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactlyInAnyOrder(
                    "Service.java",
                    "Repository.java",
                    "Controller.java"
            );
        }

        @Test
        @DisplayName("Excessive leading/trailing whitespace and tabs in markers")
        void parseDelimited_WhitespaceAndTabsInMarkers() {
            String code = "   \t   //   ===   FILE:   pkg/TabClass.java   ===   \t   \n" +
                    "package pkg;\npublic class TabClass {}\n\n" +
                    "\t//   FILE:   pkg/AnotherClass.java   \n" +
                    "package pkg;\npublic class AnotherClass {}\n";

            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactlyInAnyOrder(
                    "pkg/TabClass.java",
                    "pkg/AnotherClass.java"
            );
        }

        @Test
        @DisplayName("Single source without markers extracts public record name")
        void parseDelimited_SingleSource_PublicRecord() {
            String code = "package dto;\npublic record UserDto(String name, int age) {}\n";
            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactly("UserDto.java");
        }

        @Test
        @DisplayName("Single source without markers extracts public enum name")
        void parseDelimited_SingleSource_PublicEnum() {
            String code = "package entity;\npublic enum OrderStatus { PENDING, COMPLETED, CANCELLED }\n";
            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactly("OrderStatus.java");
        }

        @Test
        @DisplayName("Single source without markers extracts public interface name")
        void parseDelimited_SingleSource_PublicInterface() {
            String code = "package service;\npublic interface PaymentGateway { void pay(); }\n";
            SubmissionArchiveResult result = archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactly("PaymentGateway.java");
        }

        @Test
        @DisplayName("Delimited marker containing path traversal is rejected")
        void parseDelimited_MarkerWithPathTraversal_Rejected() {
            String code = "// === FILE: ../../../etc/shadow ===\nroot:x:0:0:...\n";
            assertThatThrownBy(() -> archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA))
                    .isInstanceOf(InvalidSubmissionPathException.class);
        }

        @Test
        @DisplayName("Delimited marker containing forbidden extension is rejected")
        void parseDelimited_MarkerWithForbiddenExtension_Rejected() {
            String code = "// === FILE: exploit.sh ===\n#!/bin/bash\nrm -rf /\n";
            assertThatThrownBy(() -> archiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("Forbidden executable");
        }
    }

    // --- Helpers ---

    private byte[] createZipWithEntries(int count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 1; i <= count; i++) {
                ZipEntry entry = new ZipEntry("src/Class" + i + ".java");
                zos.putNextEntry(entry);
                zos.write(("public class Class" + i + " {}").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private String getZipEntryContent(byte[] zipBytes, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buf = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    return out.toString(StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }
        return null;
    }
}
