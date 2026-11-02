package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionArchiveServiceSecurityTest {

    private SubmissionArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new SubmissionArchiveServiceImpl();
    }

    // =========================================================================
    // 1. ZIP SLIP & PATH TRAVERSAL VECTORS
    // =========================================================================

    @ParameterizedTest(name = "Zip Slip payload: {0}")
    @ValueSource(strings = {
            "../../etc/passwd",
            "..\\..\\windows\\system32\\cmd.exe",
            "com/example/../../../../etc/shadow",
            "sub/dir/../../evil.txt",
            "././../evil.txt",
            "dir/.. /evil.txt",
            " ..\\evil.txt"
    })
    @DisplayName("SEC-01 - SEC-04: Path traversal attacks must be rejected with 400 mapping exception")
    void zipSlip_TraversalVectors_ThrowsInvalidSubmissionPathException(String evilPath) {
        List<SubmissionFileDto> files = List.of(
                new SubmissionFileDto("Safe.java", "public class Safe {}"),
                new SubmissionFileDto(evilPath, "MALICIOUS CONTENT")
        );

        assertThatThrownBy(() -> archiveService.packageFiles(files))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class, SecurityException.class)
                .hasMessageMatching(".*(?i)(Zip Slip|path traversal|traversal|invalid path|illegal).*");
    }

    // =========================================================================
    // 2. NULL BYTE INJECTION
    // =========================================================================

    @ParameterizedTest(name = "Null byte payload: {0}")
    @ValueSource(strings = {
            "Test.java\0.txt",
            "evil.txt\0.java",
            "com/\0/Bad.java"
    })
    @DisplayName("SEC-05: Null byte injections in filename must be rejected")
    void nullByte_InFileName_ThrowsInvalidSubmissionPathException(String evilPath) {
        List<SubmissionFileDto> files = List.of(new SubmissionFileDto(evilPath, "CODE"));

        assertThatThrownBy(() -> archiveService.packageFiles(files))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class, SecurityException.class);
    }

    // =========================================================================
    // 3. ABSOLUTE, UNC & DEVICE PATHS
    // =========================================================================

    @ParameterizedTest(name = "Absolute / UNC / Device payload: {0}")
    @ValueSource(strings = {
            "/root/Exploit.java",
            "/etc/shadow",
            "C:\\Windows\\hack.dll",
            "c:evil.txt",
            "D:/danger/evil.txt",
            "\\\\10.0.0.1\\share\\virus.bat",
            "\\\\localhost\\c$\\evil.txt",
            "\\\\?\\C:\\evil.txt",
            "\\\\.\\PhysicalDrive0",
            "CON",
            "PRN",
            "AUX",
            "NUL",
            "CON.txt",
            "COM1.java"
    })
    @DisplayName("SEC-06 - SEC-09: Absolute, UNC and device paths must be rejected")
    void absoluteAndUncPaths_ThrowsInvalidSubmissionPathException(String evilPath) {
        List<SubmissionFileDto> files = List.of(new SubmissionFileDto(evilPath, "CODE"));

        assertThatThrownBy(() -> archiveService.packageFiles(files))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class, SecurityException.class);
    }

    // =========================================================================
    // 4. PROHIBITED EXECUTABLE EXTENSIONS
    // =========================================================================

    @ParameterizedTest(name = "Forbidden extension: {0}")
    @ValueSource(strings = {
            "exploit.exe", "MALWARE.EXE",
            "inject.dll", "SYSTEM.DLL",
            "rootkit.so", "HOOK.SO",
            "clean.sh", "RUN.SH",
            "attack.bat", "START.BAT",
            "launch.cmd", "KILL.CMD",
            "backdoor.jar", "AGENT.JAR",
            "Solution.java.exe",
            "Main.java.bat"
    })
    @DisplayName("SEC-10 - SEC-17: Prohibited file extensions must be rejected")
    void prohibitedExtensions_ThrowsInvalidSubmissionPathException(String forbiddenFile) {
        List<SubmissionFileDto> files = List.of(new SubmissionFileDto(forbiddenFile, "EXECUTABLE"));

        assertThatThrownBy(() -> archiveService.packageFiles(files))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class, SecurityException.class)
                .hasMessageMatching(".*(?i)(Forbidden|prohibited|executable|extension).*");
    }

    // =========================================================================
    // 5. BOUNDARY LIMITS (100 ENTRIES & 20MB)
    // =========================================================================

    @ParameterizedTest(name = "Self or empty path: {0}")
    @ValueSource(strings = {".", "", "   "})
    @DisplayName("SEC-18: Self or empty paths must be rejected")
    void selfOrEmptyPath_ThrowsInvalidSubmissionPathException(String emptyPath) {
        List<SubmissionFileDto> files = List.of(new SubmissionFileDto(emptyPath, "CODE"));

        assertThatThrownBy(() -> archiveService.packageFiles(files))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class, SecurityException.class);
    }

    @Test
    @DisplayName("SEC-19: Empty ZIP archive must throw IllegalArgumentException (400 Bad Request)")
    void boundary_EmptyZip_ThrowsIllegalArgumentException() throws IOException {
        byte[] emptyZip = createZip(List.of());

        assertThatThrownBy(() -> archiveService.validateAndSanitizeZip(new ByteArrayInputStream(emptyZip)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archive contains no regular files");
    }

    @Test
    @DisplayName("SEC-20: Delimited marker with path traversal must be rejected")
    void delimitedMarker_WithPathTraversal_ThrowsInvalidSubmissionPathException() {
        String delimited = """
                // === FILE: ../../etc/shadow ===
                root:x:0:0:root:/root:/bin/bash
                """;

        assertThatThrownBy(() -> archiveService.packageDelimited(delimited))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SEC-21: Exactly 100 entries must succeed")
    void boundary_Exactly100Entries_Succeeds() {
        List<SubmissionFileDto> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(new SubmissionFileDto("File" + i + ".java", "public class File" + i + " {}"));
        }

        assertThatCode(() -> archiveService.packageFiles(files)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SEC-22: 101 entries must throw InvalidSubmissionPathException / IllegalArgumentException")
    void boundary_101Entries_ThrowsIllegalArgumentException() {
        List<SubmissionFileDto> files = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            files.add(new SubmissionFileDto("File" + i + ".java", "public class File" + i + " {}"));
        }

        assertThatThrownBy(() -> archiveService.packageFiles(files))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class, SecurityException.class)
                .hasMessageContaining("maximum entry count");
    }

    @Test
    @DisplayName("SEC-24: Total uncompressed size exceeding 20MB must throw PayloadTooLargeException (413)")
    void boundary_CumulativeSizeExceeding20MB_ThrowsPayloadTooLargeException() throws IOException {
        byte[] chunk1 = new byte[11 * 1024 * 1024]; // 11MB
        byte[] chunk2 = new byte[10 * 1024 * 1024]; // 10MB (Total: 21MB)

        byte[] bombZip = createZip(List.of(
                new RawEntry("Chunk1.txt", chunk1),
                new RawEntry("Chunk2.txt", chunk2)
        ));

        assertThatThrownBy(() -> archiveService.validateAndSanitizeZip(new ByteArrayInputStream(bombZip)))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("20MB");
    }

    @Test
    @DisplayName("SEC-25: Single file exceeding 20MB must throw PayloadTooLargeException (413)")
    void boundary_SingleFileExceeding20MB_ThrowsPayloadTooLargeException() throws IOException {
        byte[] largeChunk = new byte[21 * 1024 * 1024]; // 21MB
        byte[] zip = createZip(List.of(new RawEntry("Large.txt", largeChunk)));

        assertThatThrownBy(() -> archiveService.validateAndSanitizeZip(new ByteArrayInputStream(zip)))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("20MB");
    }

    @Test
    @DisplayName("SEC-27: High-ratio Zip Bomb must halt stream immediately with PayloadTooLargeException")
    void boundary_ZipBomb_HaltsStreamImmediatelyWithPayloadTooLargeException() throws IOException {
        byte[] bombPayload = new byte[21 * 1024 * 1024]; // 21MB of zeros compresses to ~20KB
        byte[] bombZip = createZip(List.of(new RawEntry("Bomb.txt", bombPayload)));

        assertThatThrownBy(() -> archiveService.validateAndSanitizeZip(new ByteArrayInputStream(bombZip)))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("20MB");
    }

    @Test
    @DisplayName("SEC-28: Delimited markers exceeding 100 entries must throw exception")
    void boundary_DelimitedMarkersOver100_ThrowsException() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            sb.append("// === FILE: File").append(i).append(".java ===\npublic class File").append(i).append(" {}\n");
        }

        assertThatThrownBy(() -> archiveService.packageDelimited(sb.toString()))
                .isInstanceOfAny(InvalidSubmissionPathException.class, IllegalArgumentException.class)
                .hasMessageContaining("maximum entry count");
    }

    // --- Helpers ---
    private byte[] createZip(List<RawEntry> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (RawEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.name()));
                zos.write(entry.data());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private record RawEntry(String name, byte[] data) {
    }
}
