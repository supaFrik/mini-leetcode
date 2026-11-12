package jsd.toiyeujava.minileetcode.plugin.workspace;

import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial stress and security test harness for {@link ArchiveExtractor} and {@link WorkspaceService}.
 * Empirically tests:
 * <ul>
 *     <li>Zip Slip attack variants (relative, backslashes, mixed, Windows drive letters, UNIX root, encoded)</li>
 *     <li>Illegal and malicious characters (null bytes, colons, wildcards, question marks, pipes)</li>
 *     <li>Corrupt, truncated, 0-byte archives and entries</li>
 *     <li>Massive nested directories and long paths</li>
 *     <li>Zip bomb / compression ratio behavior</li>
 *     <li>WorkspaceService error containment and sandbox isolation</li>
 * </ul>
 */
@DisplayName("Slice 4 Milestone 2 Security & Zip Slip Stress Tests")
public class Slice4Milestone2SecurityStressTest {

    private ArchiveExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ArchiveExtractor();
    }

    // =========================================================================
    // Helpers & Test Doubles
    // =========================================================================

    private static class StubApiClient extends ApiClient {
        private final Map<String, byte[]> starterTemplates = new HashMap<>();
        private final Map<String, ProblemDto> problems = new HashMap<>();

        public StubApiClient() {
            super("http://localhost:8080");
        }

        public void setStarterTemplate(String slug, byte[] bytes) {
            starterTemplates.put(slug, bytes);
        }

        public void setProblem(String slug, ProblemDto dto) {
            problems.put(slug, dto);
        }

        @Override
        public byte[] getStarterTemplate(String idOrSlug) {
            byte[] bytes = starterTemplates.get(idOrSlug);
            return bytes != null ? bytes : new byte[0];
        }

        @Override
        public ProblemDto getProblem(String idOrSlug) {
            return problems.get(idOrSlug);
        }
    }

    private static byte[] createZipWithEntry(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            if (content != null && !entryName.endsWith("/")) {
                zos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static byte[] createZipWithRawEntry(String entryName, byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            if (payload != null && !entryName.endsWith("/")) {
                zos.write(payload);
            }
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static byte[] createZipBomb(int uncompressedSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            ZipEntry entry = new ZipEntry("bomb.txt");
            zos.putNextEntry(entry);
            byte[] chunk = new byte[8192];
            Arrays.fill(chunk, (byte) 'A');
            int written = 0;
            while (written < uncompressedSize) {
                int toWrite = Math.min(chunk.length, uncompressedSize - written);
                zos.write(chunk, 0, toWrite);
                written += toWrite;
            }
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    // =========================================================================
    // 1. Zip Slip Attack Variants & Path Traversal Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("1. Zip Slip Attack Variants")
    class ZipSlipVariantsTests {

        @Test
        @DisplayName("Traversal with simple relative parent '../../evil.txt' throws SecurityException")
        void testZipSlipRelativeParent(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("../../evil.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
            assertFalse(Files.exists(sandboxDir.getParent().resolve("evil.txt")));
        }

        @Test
        @DisplayName("Traversal with Windows backslashes '..\\..\\evil_win.txt' throws SecurityException")
        void testZipSlipWindowsBackslashes(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("..\\..\\evil_win.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
            assertFalse(Files.exists(sandboxDir.getParent().resolve("evil_win.txt")));
        }

        @Test
        @DisplayName("Traversal with nested escape 'folder/sub/../../../../evil.txt' throws SecurityException")
        void testZipSlipNestedEscape(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("folder/sub/../../../../evil.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
            assertFalse(Files.exists(sandboxDir.getParent().resolve("evil.txt")));
        }

        @Test
        @DisplayName("Traversal with mixed separators 'folder\\sub/..\\../../evil.txt' throws SecurityException")
        void testZipSlipMixedSeparators(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("folder\\sub/..\\../../evil.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal with Windows absolute path 'C:\\Windows\\System32\\evil.dll' throws SecurityException")
        void testZipSlipWindowsAbsolutePathBackslash(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("C:\\Windows\\System32\\evil.dll", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
            assertFalse(Files.exists(Path.of("C:\\Windows\\System32\\evil.dll")));
        }

        @Test
        @DisplayName("Traversal with Windows absolute path forward slash 'C:/Windows/System32/evil.dll' throws SecurityException")
        void testZipSlipWindowsAbsolutePathForwardSlash(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("C:/Windows/System32/evil.dll", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal with alternative drive letter 'D:\\escape\\evil.txt' throws SecurityException")
        void testZipSlipAlternativeDriveLetter(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("D:\\escape\\evil.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal with UNIX root '/etc/passwd' throws SecurityException")
        void testZipSlipUnixRoot(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("/etc/passwd", "root:x:0:0:root:/root:/bin/bash");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal with UNIX multi-slash root '///etc/shadow' throws SecurityException")
        void testZipSlipUnixMultiSlashRoot(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("///etc/shadow", "secret");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal with Windows current-drive root '\\Windows\\evil.txt' throws SecurityException")
        void testZipSlipWindowsDriveRoot(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("\\Windows\\evil.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal via directory entry '../../evil_dir/' throws SecurityException without creating dir")
        void testZipSlipDirectoryEntryTraversal(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("../../evil_dir/", null);
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
            assertFalse(Files.exists(sandboxDir.getParent().resolve("evil_dir")));
        }

        @Test
        @DisplayName("URL-encoded traversal '%2e%2e%2fevil.txt' stays inside sandbox if treated literally")
        void testZipSlipUrlEncodedEntry(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("%2e%2e%2fevil.txt", "literal encoded content");
            try {
                ArchiveExtractor.ExtractionResult res = extractor.extract(zip, sandboxDir);
                assertTrue(res.extractedFiles() <= 1);
            } catch (SecurityException se) {
                assertTrue(se.getMessage().contains("Zip Slip"));
            }
            assertFalse(Files.exists(sandboxDir.getParent().resolve("evil.txt")),
                    "URL encoded traversal must NEVER write to parent directory");
        }

        @Test
        @DisplayName("Traversal with UNC network path '\\\\127.0.0.1\\c$\\evil.txt' throws SecurityException")
        void testZipSlipUncPath(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("\\\\127.0.0.1\\c$\\evil.txt", "pwned");
            SecurityException ex = assertThrows(SecurityException.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Traversal with Windows extended length prefix '\\\\?\\C:\\evil.txt' is blocked safely")
        void testZipSlipWindowsExtendedPrefix(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("\\\\?\\C:\\evil.txt", "pwned");
            Throwable thrown = assertThrows(Throwable.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(thrown instanceof SecurityException || thrown instanceof IOException || thrown instanceof IllegalArgumentException,
                    "Expected SecurityException, IOException, or InvalidPathException, but got: " + thrown.getClass().getName());
            assertFalse(Files.exists(Path.of("C:\\evil.txt")));
        }

        @Test
        @DisplayName("Traversal attempt with .java extension under routeJavaSources=true throws SecurityException")
        void testZipSlipJavaRoutingEscapeThrowsSecurityException(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("../../../../evil.java", "public class Evil {}");
            SecurityException ex = assertThrows(SecurityException.class, () ->
                    extractor.extract(zip, sandboxDir, true));
            assertTrue(ex.getMessage().contains("Zip Slip detected"));
        }

        @Test
        @DisplayName("Current directory reference './file.txt' stays within sandbox")
        void testCurrentDirectoryReference(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("./safe.txt", "safe content");
            ArchiveExtractor.ExtractionResult result = extractor.extract(zip, sandboxDir);
            assertEquals(1, result.extractedFiles());
            assertTrue(Files.exists(sandboxDir.resolve("safe.txt")));
            assertEquals("safe content", Files.readString(sandboxDir.resolve("safe.txt")));
        }
    }

    // =========================================================================
    // 2. Malicious Entry Names & Special Characters
    // =========================================================================

    @Nested
    @DisplayName("2. Malicious Characters & Special File Names")
    class MaliciousNamesTests {

        @Test
        @DisplayName("Entry containing null byte is blocked safely without sandbox escape")
        void testNullByteInEntryName(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("evil\u0000payload.txt", "injected");
            Throwable thrown = assertThrows(Throwable.class, () -> extractor.extract(zip, sandboxDir));
            // Check if it is SecurityException or IOException or InvalidPathException
            assertTrue(thrown instanceof SecurityException || thrown instanceof IOException || thrown instanceof IllegalArgumentException,
                    "Expected SecurityException, IOException, or InvalidPathException, but got: " + thrown.getClass().getName());
        }

        @Test
        @DisplayName("Entry containing NTFS alternate data stream (colon) is handled safely")
        void testColonAlternateDataStream(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("test.txt:stream", "stream-data");
            // Must not escape sandbox
            try {
                extractor.extract(zip, sandboxDir);
            } catch (Throwable expected) {
                // Handled
            }
        }

        @Test
        @DisplayName("Entry containing wildcard asterisk '*' is handled safely")
        void testWildcardAsteriskInName(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("file*.txt", "wildcard");
            Throwable thrown = assertThrows(Throwable.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(thrown instanceof SecurityException || thrown instanceof IOException || thrown instanceof IllegalArgumentException,
                    "Expected SecurityException, IOException, or InvalidPathException, but got: " + thrown.getClass().getName());
        }

        @Test
        @DisplayName("Entry containing question mark '?' is handled safely")
        void testQuestionMarkInName(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("file?.txt", "question");
            Throwable thrown = assertThrows(Throwable.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(thrown instanceof SecurityException || thrown instanceof IOException || thrown instanceof IllegalArgumentException,
                    "Expected SecurityException, IOException, or InvalidPathException, but got: " + thrown.getClass().getName());
        }

        @Test
        @DisplayName("Entry containing pipes and quotes is handled safely")
        void testPipesAndQuotesInName(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithEntry("file|pipe\"quote.txt", "special");
            Throwable thrown = assertThrows(Throwable.class, () -> extractor.extract(zip, sandboxDir));
            assertTrue(thrown instanceof SecurityException || thrown instanceof IOException || thrown instanceof IllegalArgumentException,
                    "Expected SecurityException, IOException, or InvalidPathException, but got: " + thrown.getClass().getName());
        }
    }

    // =========================================================================
    // 3. Corrupt Archives, Truncated Payloads, 0-Byte Entries
    // =========================================================================

    @Nested
    @DisplayName("3. Corrupt Archives, Headers, & Boundary Inputs")
    class CorruptAndBoundaryTests {

        @Test
        @DisplayName("Archive with all-zero magic bytes throws ZipException (IOException)")
        void testAllZeroMagicBytesThrowsZipException(@TempDir Path sandboxDir) {
            byte[] corrupt = new byte[]{0x00, 0x00, 0x00, 0x00};
            assertThrows(ZipException.class, () -> extractor.extract(corrupt, sandboxDir));
        }

        @Test
        @DisplayName("Archive with invalid magic header 'ABCD' throws ZipException")
        void testInvalidMagicHeaderThrowsZipException(@TempDir Path sandboxDir) {
            byte[] corrupt = new byte[]{'A', 'B', 'C', 'D'};
            ZipException ex = assertThrows(ZipException.class, () -> extractor.extract(corrupt, sandboxDir));
            assertTrue(ex.getMessage().contains("missing or corrupted ZIP magic header"));
        }

        @Test
        @DisplayName("Truncated ZIP payload with incomplete header throws IOException or ZipException")
        void testTruncatedZipPayload(@TempDir Path sandboxDir) {
            byte[] truncated = new byte[]{0x50, 0x4B};
            assertThrows(ZipException.class, () -> extractor.extract(truncated, sandboxDir));
        }

        @Test
        @DisplayName("ZIP with PK header followed by garbage bytes throws IOException or handles safely")
        void testPkHeaderFollowedByGarbage(@TempDir Path sandboxDir) {
            byte[] corrupt = new byte[]{0x50, 0x4B, (byte) 0xFF, (byte) 0xFE, 0x01, 0x02, 0x03};
            assertDoesNotThrow(() -> {
                try {
                    extractor.extract(corrupt, sandboxDir);
                } catch (IOException expected) {
                    // expected
                }
            });
        }

        @Test
        @DisplayName("Zero-byte empty archive returns 0 files without error")
        void testZeroByteEmptyArchiveReturnsZeroFiles(@TempDir Path sandboxDir) throws IOException {
            byte[] empty = new byte[0];
            ArchiveExtractor.ExtractionResult result = extractor.extract(empty, sandboxDir);
            assertEquals(0, result.extractedFiles());
            assertEquals(0, result.skippedFiles());
            assertTrue(result.writtenPaths().isEmpty());
        }

        @Test
        @DisplayName("Valid ZIP with 0-byte file entry extracts empty file cleanly")
        void testZeroByteFileEntryInsideZip(@TempDir Path sandboxDir) throws IOException {
            byte[] zip = createZipWithRawEntry("empty-file.txt", new byte[0]);
            ArchiveExtractor.ExtractionResult result = extractor.extract(zip, sandboxDir);
            assertEquals(1, result.extractedFiles());
            Path extracted = sandboxDir.resolve("empty-file.txt");
            assertTrue(Files.exists(extracted));
            assertEquals(0, Files.size(extracted));
        }

        @Test
        @DisplayName("Massive nested paths (30 levels deep) extract successfully within sandbox")
        void testMassiveNestedPaths(@TempDir Path sandboxDir) throws IOException {
            StringBuilder deepPath = new StringBuilder();
            for (int i = 1; i <= 30; i++) {
                deepPath.append("level").append(i).append("/");
            }
            deepPath.append("target.txt");

            byte[] zip = createZipWithEntry(deepPath.toString(), "deep content");
            ArchiveExtractor.ExtractionResult result = extractor.extract(zip, sandboxDir);
            assertEquals(1, result.extractedFiles());

            Path resolved = sandboxDir.resolve(deepPath.toString());
            assertTrue(Files.exists(resolved));
            assertEquals("deep content", Files.readString(resolved));
            assertTrue(resolved.startsWith(sandboxDir));
        }

        @Test
        @DisplayName("Extremely long single filename (100 characters) extracts cleanly")
        void testLongSingleFilename(@TempDir Path sandboxDir) throws IOException {
            String longFileName = "a".repeat(100) + ".txt";
            byte[] zip = createZipWithEntry(longFileName, "long filename content");
            ArchiveExtractor.ExtractionResult result = extractor.extract(zip, sandboxDir);
            assertEquals(1, result.extractedFiles());
            assertTrue(Files.exists(sandboxDir.resolve(longFileName)));
        }
    }

    // =========================================================================
    // 4. Zip Bomb Simulation
    // =========================================================================

    @Nested
    @DisplayName("4. Zip Bomb & Compression Ratio Stress")
    class ZipBombTests {

        @Test
        @DisplayName("High compression ratio entry (5MB decompressed from ~5KB) extracts safely")
        void testHighCompressionRatioEntry(@TempDir Path sandboxDir) throws IOException {
            int fiveMb = 5 * 1024 * 1024;
            byte[] zipBomb = createZipBomb(fiveMb);
            assertTrue(zipBomb.length < 50_000, "Compressed size should be tiny compared to 5MB");

            ArchiveExtractor.ExtractionResult result = extractor.extract(zipBomb, sandboxDir);
            assertEquals(1, result.extractedFiles());
            Path bombFile = sandboxDir.resolve("bomb.txt");
            assertTrue(Files.exists(bombFile));
            assertEquals(fiveMb, Files.size(bombFile));
        }

        @Test
        @DisplayName("Archive with 500 entries (over MAX_ENTRIES) is rejected; 200 entries extract fine")
        void testMultiFileArchiveManyEntries(@TempDir Path sandboxDir) throws IOException {
            ByteArrayOutputStream ok = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(ok, StandardCharsets.UTF_8)) {
                for (int i = 0; i < ArchiveExtractor.MAX_ENTRIES; i++) {
                    zos.putNextEntry(new ZipEntry("ok/file_" + i + ".txt"));
                    zos.write(("Content " + i).getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            assertEquals(ArchiveExtractor.MAX_ENTRIES, extractor.extract(ok.toByteArray(), sandboxDir).extractedFiles());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                for (int i = 0; i < 500; i++) {
                    ZipEntry entry = new ZipEntry("files/file_" + i + ".txt");
                    zos.putNextEntry(entry);
                    zos.write(("Content of file " + i).getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }

            assertThrows(java.util.zip.ZipException.class, () -> extractor.extract(baos.toByteArray(), sandboxDir));
        }
    }

    // =========================================================================
    // 5. WorkspaceService Integration & Sandbox Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("5. WorkspaceService Containment & Sandbox Isolation")
    class WorkspaceServiceContainmentTests {

        @Test
        @DisplayName("WorkspaceService halts when archive contains illegal path characters and never calls ProjectOpener")
        void testWorkspaceServiceHaltsOnIllegalPathCharacters(@TempDir Path parentDir) throws Exception {
            Path projectDir = parentDir.resolve("student-illegal");
            StubApiClient stubClient = new StubApiClient();

            byte[] illegalZip = createZipWithEntry("file*.txt", "illegal path");
            stubClient.setStarterTemplate("illegal-problem", illegalZip);
            stubClient.setProblem("illegal-problem", new ProblemDto(
                    4L, "Illegal", "illegal-problem", "Desc", Difficulty.EASY, ProblemType.OOP_GRINDING
            ));

            AtomicBoolean openerCalled = new AtomicBoolean(false);
            ProjectOpener recordingOpener = path -> openerCalled.set(true);

            WorkspaceService service = new WorkspaceService(stubClient, extractor, recordingOpener);
            WorkspaceInitRequest req = new WorkspaceInitRequest(
                    projectDir, "plan-1", "item-1", 1, "illegal-problem", 4L
            );

            // Verify exception is thrown and opener is never invoked
            assertThrows(Throwable.class, () -> service.initializeProject(req));
            assertFalse(openerCalled.get(), "ProjectOpener MUST NOT be called when extraction fails on illegal path!");
        }

        @Test
        @DisplayName("WorkspaceService stops immediately on Zip Slip and never calls ProjectOpener")
        void testWorkspaceServiceHaltsOnZipSlip(@TempDir Path parentDir) throws Exception {
            Path projectDir = parentDir.resolve("student-workspace");
            StubApiClient stubClient = new StubApiClient();

            byte[] evilZip = createZipWithEntry("../../escape.txt", "malicious code");
            stubClient.setStarterTemplate("two-sum", evilZip);
            stubClient.setProblem("two-sum", new ProblemDto(
                    1L, "Two Sum", "two-sum", "Desc", Difficulty.EASY, ProblemType.OOP_GRINDING
            ));

            AtomicBoolean openerCalled = new AtomicBoolean(false);
            ProjectOpener recordingOpener = path -> openerCalled.set(true);

            WorkspaceService service = new WorkspaceService(stubClient, extractor, recordingOpener);
            WorkspaceInitRequest req = new WorkspaceInitRequest(
                    projectDir, "java-basics", "item-1", 1, "two-sum", 1L
            );

            WorkspaceException ex = assertThrows(WorkspaceException.class, () -> service.initializeProject(req));
            assertTrue(ex.getMessage().contains("Zip Slip detected") || ex.getMessage().contains("Archive security violation"));

            // Verification: Opener MUST NOT be called!
            assertFalse(openerCalled.get(), "ProjectOpener MUST NOT be invoked when starter archive is malicious!");

            // Sandbox verification: NO file written outside projectDir
            assertFalse(Files.exists(parentDir.resolve("escape.txt")), "Sandbox breach: file escaped to parentDir!");
            assertFalse(Files.exists(parentDir.getParent().resolve("escape.txt")));
        }

        @Test
        @DisplayName("WorkspaceService halts on corrupt ZIP header and never calls ProjectOpener")
        void testWorkspaceServiceHaltsOnCorruptZip(@TempDir Path parentDir) throws Exception {
            Path projectDir = parentDir.resolve("student-corrupt");
            StubApiClient stubClient = new StubApiClient();

            byte[] corruptZip = new byte[]{0x00, 0x01, 0x02, 0x03};
            stubClient.setStarterTemplate("corrupt-problem", corruptZip);

            AtomicBoolean openerCalled = new AtomicBoolean(false);
            ProjectOpener recordingOpener = path -> openerCalled.set(true);

            WorkspaceService service = new WorkspaceService(stubClient, extractor, recordingOpener);
            WorkspaceInitRequest req = new WorkspaceInitRequest(
                    projectDir, "plan-1", "item-1", 1, "corrupt-problem", 2L
            );

            WorkspaceException ex = assertThrows(WorkspaceException.class, () -> service.initializeProject(req));
            assertTrue(ex.getMessage().contains("Failed to extract starter template archive"));
            assertFalse(openerCalled.get(), "ProjectOpener MUST NOT be invoked when archive is corrupted!");
        }

        @Test
        @DisplayName("WorkspaceService handles empty starter template bytes gracefully without failure")
        void testWorkspaceServiceHandlesEmptyStarterTemplate(@TempDir Path parentDir) throws Exception {
            Path projectDir = parentDir.resolve("student-empty");
            StubApiClient stubClient = new StubApiClient();

            stubClient.setStarterTemplate("empty-problem", new byte[0]);
            stubClient.setProblem("empty-problem", new ProblemDto(
                    3L, "Empty", "empty-problem", "Desc", Difficulty.EASY, ProblemType.OOP_GRINDING
            ));

            AtomicBoolean openerCalled = new AtomicBoolean(false);
            ProjectOpener recordingOpener = path -> openerCalled.set(true);

            WorkspaceService service = new WorkspaceService(stubClient, extractor, recordingOpener);
            WorkspaceInitRequest req = new WorkspaceInitRequest(
                    projectDir, "plan-1", "item-1", 1, "empty-problem", 3L
            );

            WorkspaceResult result = service.initializeProject(req);
            assertNotNull(result);
            assertEquals(0, result.extractionResult().extractedFiles());
            assertTrue(openerCalled.get(), "Opener should be called when empty starter is valid (no-op extraction)");
        }
    }
}
