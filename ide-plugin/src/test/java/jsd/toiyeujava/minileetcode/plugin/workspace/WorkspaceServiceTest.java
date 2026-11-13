package jsd.toiyeujava.minileetcode.plugin.workspace;

import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit test suite for {@link WorkspaceService}.
 * <p>
 * Constraints verified:
 * <ul>
 *   <li>Pure headless JUnit 5 execution without IntelliJ Application or EDT dependencies.</li>
 *   <li>Full verification of all 7 pipeline stages.</li>
 *   <li>Verification of POM generation, parameter replacement, and non-overwrite idempotency.</li>
 *   <li>Verification of .mlc/project.yaml manifest format and content.</li>
 *   <li>Verification of starter extraction with non-overwrite of existing student files (AC-004, FR-PL-002).</li>
 *   <li>Verification of UTF-8 stage Markdown generation (PL-4).</li>
 *   <li>Verification of ProjectOpener invocation with exact target directory path (PL-3).</li>
 *   <li>Comprehensive failure mode verification (Zip Slip defense, HTTP 401, HTTP 404, corrupt data).</li>
 * </ul>
 */
class WorkspaceServiceTest {

    @TempDir
    Path tempDir;

    private RecordingProjectOpener projectOpener;
    private StubApiClient apiClient;
    private ArchiveExtractor archiveExtractor;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        projectOpener = new RecordingProjectOpener();
        apiClient = new StubApiClient();
        archiveExtractor = new ArchiveExtractor();
        workspaceService = new WorkspaceService(apiClient, archiveExtractor, projectOpener);
    }

    @Nested
    @DisplayName("1. Happy Path Pipeline Tests (PL-3, PL-4)")
    class HappyPathTests {

        @Test
        @DisplayName("initializeProject executes all 7 stages and produces all expected artifacts")
        void testInitializeProjectCompletePipeline() throws Exception {
            // Arrange
            Path projectDir = tempDir.resolve("mini-bank-project");
            String planSlug = "oop-with-java";
            String itemSlug = "1";
            int stageNo = 1;
            String problemSlug = "mini-bank-stage-1";
            Long problemId = 101L;

            byte[] starterZip = createZipBytes(Map.of(
                    "src/main/java/Account.java", "public class Account {\n    // Initial starter code\n}\n"
            ));
            apiClient.setStarterTemplate(problemSlug, starterZip);
            apiClient.setProblem(problemSlug, new ProblemDto(
                    problemId, "Mini Bank Stage 1", problemSlug,
                    "# Stage 1: Account\n\nCreate a basic account class with balance and deposit.",
                    Difficulty.EASY, ProblemType.OOP_GRINDING
            ));

            WorkspaceInitRequest request = new WorkspaceInitRequest(
                    projectDir, planSlug, itemSlug, stageNo, problemSlug, problemId
            );

            // Act
            WorkspaceResult result = workspaceService.initializeProject(request);

            // Assert: Return values
            assertNotNull(result, "WorkspaceResult must not be null");
            assertEquals(projectDir, result.projectDir());
            assertEquals(projectDir.resolve(".mlc/project.yaml"), result.manifestPath());
            assertEquals(projectDir.resolve("stages/stage-1.md"), result.stageMarkdownPath());
            assertNotNull(result.extractionResult());
            assertEquals(1, result.extractionResult().extractedFiles());

            // Assert: Target directory created
            assertTrue(Files.isDirectory(projectDir), "Project directory must be created");

            // Assert: pom.xml generated
            Path pomPath = projectDir.resolve("pom.xml");
            assertTrue(Files.exists(pomPath), "pom.xml must exist");
            String pomContent = Files.readString(pomPath, StandardCharsets.UTF_8);
            assertTrue(pomContent.contains("<artifactId>mini-bank-stage-1</artifactId>"), "POM must contain resolved artifactId");
            assertTrue(pomContent.contains("<maven.compiler.source>17</maven.compiler.source>") || pomContent.contains("17"),
                    "POM must target Java 17");
            assertTrue(pomContent.contains("junit-jupiter"), "POM must contain JUnit 5 dependency");

            // Assert: .mlc/project.yaml generated and matches
            Path manifestPath = projectDir.resolve(".mlc/project.yaml");
            assertTrue(Files.exists(manifestPath), ".mlc/project.yaml must exist");
            ProjectManifest manifest = ProjectManifest.readFrom(manifestPath);
            assertEquals("oop-with-java", manifest.plan());
            assertEquals("1", manifest.item());
            assertEquals(1, manifest.currentStage());
            assertEquals("mini-bank-stage-1", manifest.problemSlug());
            assertEquals(101L, manifest.problemId());

            // Assert: Starter archive extracted
            Path javaFile = projectDir.resolve("src/main/java/Account.java");
            assertTrue(Files.exists(javaFile), "Extracted Java starter file must exist");
            String javaContent = Files.readString(javaFile, StandardCharsets.UTF_8);
            assertTrue(javaContent.contains("Initial starter code"));

            // Assert: stages/stage-1.md generated with UTF-8 problem description
            Path markdownPath = projectDir.resolve("stages/stage-1.md");
            assertTrue(Files.exists(markdownPath), "stages/stage-1.md must exist");
            String mdContent = Files.readString(markdownPath, StandardCharsets.UTF_8);
            assertTrue(mdContent.contains("# Stage 1: Account"), "Markdown must contain problem description");
            assertTrue(mdContent.contains("Create a basic account class"));

            // Assert: ProjectOpener invoked
            assertEquals(1, projectOpener.openedDirectories.size(), "ProjectOpener must be called exactly once");
            assertEquals(projectDir, projectOpener.openedDirectories.getFirst(), "ProjectOpener must open projectDir");
        }
    }

    @Nested
    @DisplayName("2. Non-Overwrite & Idempotency Tests (FR-PL-002, AC-004)")
    class NonOverwriteTests {

        @Test
        @DisplayName("Existing pom.xml is preserved and not overwritten")
        void testExistingPomXmlPreserved() throws Exception {
            Path projectDir = tempDir.resolve("existing-pom-project");
            Files.createDirectories(projectDir);
            Path pomPath = projectDir.resolve("pom.xml");
            String customPom = "<!-- Custom User Configured POM -->";
            Files.writeString(pomPath, customPom, StandardCharsets.UTF_8);

            apiClient.setStarterTemplate("p1", createZipBytes(Map.of()));
            apiClient.setProblem("p1", new ProblemDto(1L, "T1", "p1", "Desc"));

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan1", "1", 1, "p1");
            workspaceService.initializeProject(request);

            assertEquals(customPom, Files.readString(pomPath, StandardCharsets.UTF_8),
                    "Existing pom.xml must not be overwritten");
        }

        @Test
        @DisplayName("Student modified files are preserved when extracting starter archive (AC-004)")
        void testStudentCodePreservedOnStarterExtraction() throws Exception {
            Path projectDir = tempDir.resolve("ac004-project");
            Files.createDirectories(projectDir.resolve("src/main/java"));
            Path existingFile = projectDir.resolve("src/main/java/Bank.java");
            String studentCode = "public class Bank {\n    // Modified by student\n}\n";
            Files.writeString(existingFile, studentCode, StandardCharsets.UTF_8);

            // Starter archive contains both Bank.java and new SavingsAccount.java
            byte[] starterZip = createZipBytes(Map.of(
                    "src/main/java/Bank.java", "public class Bank {\n    // Overwritten starter?\n}\n",
                    "src/main/java/SavingsAccount.java", "public class SavingsAccount {}\n"
            ));
            apiClient.setStarterTemplate("p2", starterZip);
            apiClient.setProblem("p2", new ProblemDto(2L, "Stage 2", "p2", "Desc"));

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan1", "item1", 2, "p2");
            WorkspaceResult result = workspaceService.initializeProject(request);

            // Assert: Bank.java kept student code, SavingsAccount.java created
            assertEquals(studentCode, Files.readString(existingFile, StandardCharsets.UTF_8),
                    "Existing student file Bank.java MUST NOT be overwritten");
            assertTrue(Files.exists(projectDir.resolve("src/main/java/SavingsAccount.java")),
                    "New file SavingsAccount.java must be extracted");
            assertEquals(1, result.extractionResult().skippedFiles(), "Skipped files count must be 1");
            assertEquals(1, result.extractionResult().extractedFiles(), "Extracted files count must be 1");
        }

        @Test
        @DisplayName("Multi-stage progression updates project.yaml and creates stage-2.md without deleting stage-1.md")
        void testMultiStageProgression() throws Exception {
            Path projectDir = tempDir.resolve("multistage-project");

            // Setup stage 1
            apiClient.setStarterTemplate("stage-1", createZipBytes(Map.of("A.java", "class A {}")));
            apiClient.setProblem("stage-1", new ProblemDto(1L, "S1", "stage-1", "Stage 1 content"));
            workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "plan", "item", 1, "stage-1", 1L));

            // Setup stage 2
            apiClient.setStarterTemplate("stage-2", createZipBytes(Map.of("B.java", "class B {}")));
            apiClient.setProblem("stage-2", new ProblemDto(2L, "S2", "stage-2", "Stage 2 content"));
            workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "plan", "item", 2, "stage-2", 2L));

            // Assert
            ProjectManifest manifest = ProjectManifest.readFrom(projectDir.resolve(".mlc/project.yaml"));
            assertEquals(2, manifest.currentStage());
            assertEquals("stage-2", manifest.problemSlug());
            assertEquals(2L, manifest.problemId());

            assertTrue(Files.exists(projectDir.resolve("stages/stage-1.md")), "stage-1.md must still exist");
            assertTrue(Files.exists(projectDir.resolve("stages/stage-2.md")), "stage-2.md must exist");
            assertEquals("Stage 1 content", Files.readString(projectDir.resolve("stages/stage-1.md"), StandardCharsets.UTF_8));
            assertEquals("Stage 2 content", Files.readString(projectDir.resolve("stages/stage-2.md"), StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("3. Error Handling & Security Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Zip Slip attack in starter archive throws WorkspaceException and blocks ProjectOpener")
        void testZipSlipAttackThrowsAndBlocksOpener() throws Exception {
            Path projectDir = tempDir.resolve("zip-slip-project");
            byte[] maliciousZip = createZipBytes(Map.of(
                    "../../evil.sh", "rm -rf /"
            ));
            apiClient.setStarterTemplate("evil-problem", maliciousZip);
            apiClient.setProblem("evil-problem", new ProblemDto(666L, "Evil", "evil-problem", "Desc"));

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan", "1", 1, "evil-problem");

            WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                    workspaceService.initializeProject(request)
            );
            assertTrue(ex.getMessage().contains("Zip Slip") || ex.getCause() instanceof SecurityException,
                    "Exception message or cause must indicate Zip Slip violation");
            assertTrue(projectOpener.openedDirectories.isEmpty(), "ProjectOpener must NOT be invoked on security failure");
        }

        @Test
        @DisplayName("API Authentication failure (HTTP 401) wraps in WorkspaceException and blocks ProjectOpener")
        void testApiAuthenticationFailureThrows() {
            Path projectDir = tempDir.resolve("auth-fail-project");
            apiClient.setSimulateAuthError(true);

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan", "1", 1, "auth-problem");

            WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                    workspaceService.initializeProject(request)
            );
            assertTrue(ex.getMessage().contains("Authentication failed") || ex.getCause() instanceof AuthenticationException);
            assertTrue(projectOpener.openedDirectories.isEmpty(), "ProjectOpener must NOT be invoked on auth failure");
        }

        @Test
        @DisplayName("API 404 Problem Not Found wraps in WorkspaceException and blocks ProjectOpener")
        void testApiProblemNotFoundThrows() {
            Path projectDir = tempDir.resolve("404-project");
            apiClient.setSimulate404Error(true);

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan", "1", 1, "missing-problem");

            WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                    workspaceService.initializeProject(request)
            );
            assertTrue(ex.getMessage().contains("API error") || ex.getCause() instanceof MiniLeetCodeApiException);
            assertTrue(projectOpener.openedDirectories.isEmpty(), "ProjectOpener must NOT be invoked on API 404");
        }

        @Test
        @DisplayName("Corrupt starter archive bytes throw WorkspaceException and blocks ProjectOpener")
        void testCorruptArchiveThrows() {
            Path projectDir = tempDir.resolve("corrupt-project");
            byte[] corruptBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04};
            apiClient.setStarterTemplate("corrupt-problem", corruptBytes);
            apiClient.setProblem("corrupt-problem", new ProblemDto(1L, "C", "corrupt-problem", "D"));

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan", "1", 1, "corrupt-problem");

            WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                    workspaceService.initializeProject(request)
            );
            assertTrue(ex.getMessage().contains("Failed to extract starter template archive"));
            assertTrue(projectOpener.openedDirectories.isEmpty(), "ProjectOpener must NOT be invoked on corrupt archive");
        }

        @Test
        @DisplayName("ProjectOpener failure is wrapped in WorkspaceException")
        void testProjectOpenerFailureWrapped() throws Exception {
            Path projectDir = tempDir.resolve("opener-fail-project");
            apiClient.setStarterTemplate("p", createZipBytes(Map.of()));
            apiClient.setProblem("p", new ProblemDto(1L, "P", "p", "D"));

            ProjectOpener failingOpener = dir -> {
                throw new IOException("IDE project framework unavailable");
            };
            WorkspaceService service = new WorkspaceService(apiClient, archiveExtractor, failingOpener);

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan", "1", 1, "p");

            WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                    service.initializeProject(request)
            );
            assertTrue(ex.getMessage().contains("Failed to open project in IDE"));
            assertTrue(ex.getMessage().contains("IDE project framework unavailable"));
        }

        @Test
        @DisplayName("Review fix: backend starter ZIP has bare Main.java at the root -> it lands in src/main/java, README stays at root")
        void testRootJavaFilesRoutedToMavenSources() throws Exception {
            Path projectDir = tempDir.resolve("routing-project");
            apiClient.setStarterTemplate("mini-bank-stage-1", createZipBytes(Map.of(
                    "README.md", "# Starter", "Main.java", "public class Main {}")));
            apiClient.setProblem("mini-bank-stage-1", new ProblemDto(1L, "Stage 1", "mini-bank-stage-1", "# Stage 1"));

            workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "plan", "1", 1, "mini-bank-stage-1"));

            assertTrue(Files.isRegularFile(projectDir.resolve("src/main/java/Main.java")));
            assertFalse(Files.exists(projectDir.resolve("Main.java")));
            assertTrue(Files.isRegularFile(projectDir.resolve("README.md")));
        }

        @Test
        @DisplayName("Review fix: a failed starter download leaves the existing manifest on the previous stage")
        void testFailedFetchDoesNotMoveManifest() throws Exception {
            Path projectDir = tempDir.resolve("progress-project");
            apiClient.setStarterTemplate("s1", createZipBytes(Map.of("Main.java", "public class Main {}")));
            apiClient.setProblem("s1", new ProblemDto(1L, "S1", "s1", "# S1"));
            workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "plan", "1", 1, "s1"));

            apiClient.setSimulateAuthError(true);
            assertThrows(WorkspaceException.class, () ->
                    workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "plan", "1", 2, "s2")));

            ProjectManifest manifest = ProjectManifest.readFrom(projectDir.resolve(ProjectManifest.RELATIVE_PATH));
            assertEquals(1, manifest.currentStage());
            assertEquals("s1", manifest.problemSlug());
            assertFalse(Files.exists(projectDir.resolve("stages/stage-2.md")));
        }

        @Test
        @DisplayName("Validation rejects null or invalid request fields")
        void testRequestValidation() {
            assertThrows(NullPointerException.class, () ->
                    new WorkspaceInitRequest(null, "plan", "1", 1, "slug"));
            assertThrows(IllegalArgumentException.class, () ->
                    new WorkspaceInitRequest(tempDir, "", "1", 1, "slug"));
            assertThrows(IllegalArgumentException.class, () ->
                    new WorkspaceInitRequest(tempDir, "plan", "", 1, "slug"));
            assertThrows(IllegalArgumentException.class, () ->
                    new WorkspaceInitRequest(tempDir, "plan", "1", 0, "slug"));
            assertThrows(IllegalArgumentException.class, () ->
                    new WorkspaceInitRequest(tempDir, "plan", "1", 1, ""));
            assertThrows(NullPointerException.class, () ->
                    workspaceService.initializeProject(null));
        }
    }

    // =========================================================================
    // Test Doubles & Helpers
    // =========================================================================

    private static class RecordingProjectOpener implements ProjectOpener {
        final List<Path> openedDirectories = new ArrayList<>();

        @Override
        public void openProject(Path projectDirectory) {
            openedDirectories.add(projectDirectory);
        }
    }

    private static class StubApiClient extends ApiClient {
        private final Map<String, byte[]> starterTemplates = new java.util.HashMap<>();
        private final Map<String, ProblemDto> problems = new java.util.HashMap<>();
        private boolean simulateAuthError = false;
        private boolean simulate404Error = false;

        public StubApiClient() {
            super("http://localhost:8080");
        }

        public void setStarterTemplate(String slug, byte[] bytes) {
            starterTemplates.put(slug, bytes);
        }

        public void setProblem(String slug, ProblemDto dto) {
            problems.put(slug, dto);
        }

        public void setSimulateAuthError(boolean simulate) {
            this.simulateAuthError = simulate;
        }

        public void setSimulate404Error(boolean simulate) {
            this.simulate404Error = simulate;
        }

        @Override
        public byte[] getStarterTemplate(String idOrSlug) {
            if (simulateAuthError) {
                throw new AuthenticationException("Personal access token is invalid (HTTP 401)", "UNAUTHORIZED");
            }
            if (simulate404Error) {
                throw new MiniLeetCodeApiException(404, "Problem not found");
            }
            byte[] bytes = starterTemplates.get(idOrSlug);
            return bytes != null ? bytes : new byte[0];
        }

        @Override
        public ProblemDto getProblem(String idOrSlug) {
            if (simulateAuthError) {
                throw new AuthenticationException("Personal access token is invalid (HTTP 401)", "UNAUTHORIZED");
            }
            if (simulate404Error) {
                throw new MiniLeetCodeApiException(404, "Problem not found");
            }
            return problems.get(idOrSlug);
        }
    }

    private static byte[] createZipBytes(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
