package jsd.toiyeujava.minileetcode.plugin.workspace;

import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Test Suite for Study Plan Slice 4 Milestone 2.
 * Focus: Pipeline orchestration, Concurrency (20 threads), Re-entrancy,
 * Non-overwrite progression guarantees (AC-004), YAML fuzzing/stress, and Filesystem edge cases.
 */
@DisplayName("Slice 4 Milestone 2 Pipeline & Concurrency Adversarial Stress Tests")
class Slice4Milestone2PipelineStressTest {

    @TempDir
    Path tempDir;

    private ConcurrentStubApiClient apiClient;
    private ConcurrentRecordingProjectOpener projectOpener;
    private ArchiveExtractor archiveExtractor;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        apiClient = new ConcurrentStubApiClient();
        projectOpener = new ConcurrentRecordingProjectOpener();
        archiveExtractor = new ArchiveExtractor();
        workspaceService = new WorkspaceService(apiClient, archiveExtractor, projectOpener);
    }

    // =========================================================================
    // 1. Multi-Threaded Concurrency Stress (20 Threads)
    // =========================================================================
    @Nested
    @DisplayName("1. Multi-Threaded Concurrency Stress (20 Threads)")
    class ConcurrencyStressTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("20 concurrent threads calling initializeProject into 20 distinct directories succeed with zero deadlocks or corruption")
        void testConcurrentInitializationDistinctDirectories20Threads() throws Exception {
            final int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            byte[] defaultStarter = createZip(Map.of(
                    "src/main/java/Solution.java", "public class Solution { /* Thread starter */ }"
            ));
            for (int i = 0; i < threadCount; i++) {
                String slug = "problem-" + i;
                apiClient.setStarterTemplate(slug, defaultStarter);
                apiClient.setProblem(slug, new ProblemDto((long) (100 + i), "Title " + i, slug, "Desc " + i));
            }

            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<WorkspaceResult>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                final Path targetDir = tempDir.resolve("proj-distinct-" + idx);
                final WorkspaceInitRequest request = new WorkspaceInitRequest(
                        targetDir, "plan-concurrency", "item-" + idx, 1, "problem-" + idx, (long) (100 + idx)
                );

                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    assertTrue(startLatch.await(10, TimeUnit.SECONDS), "Start latch timed out");
                    return workspaceService.initializeProject(request);
                }));
            }

            // Await all threads ready, then fire simultaneously
            readyLatch.await(10, TimeUnit.SECONDS);
            startLatch.countDown();

            // Collect all results
            List<WorkspaceResult> results = new ArrayList<>();
            for (Future<WorkspaceResult> f : futures) {
                results.add(f.get(15, TimeUnit.SECONDS));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Assertions
            assertEquals(threadCount, results.size(), "All 20 threads must produce a WorkspaceResult");
            assertEquals(threadCount, projectOpener.openedDirectories.size(), "ProjectOpener must be called exactly 20 times");

            for (int i = 0; i < threadCount; i++) {
                Path dir = tempDir.resolve("proj-distinct-" + i);
                assertTrue(Files.isDirectory(dir), "Directory must exist: " + dir);

                Path manifestPath = dir.resolve(".mlc/project.yaml");
                assertTrue(Files.exists(manifestPath), "Manifest must exist in: " + dir);
                ProjectManifest manifest = ProjectManifest.readFrom(manifestPath);
                assertEquals("plan-concurrency", manifest.plan());
                assertEquals("item-" + i, manifest.item());
                assertEquals(1, manifest.currentStage());
                assertEquals("problem-" + i, manifest.problemSlug());
                assertEquals((long) (100 + i), manifest.problemId());

                Path pomPath = dir.resolve("pom.xml");
                assertTrue(Files.exists(pomPath), "pom.xml must exist");
                String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
                assertTrue(pom.contains("<artifactId>problem-" + i + "</artifactId>"));

                Path mdPath = dir.resolve("stages/stage-1.md");
                assertTrue(Files.exists(mdPath), "stage-1.md must exist");
                assertEquals("Desc " + i, Files.readString(mdPath, StandardCharsets.UTF_8));

                Path javaFile = dir.resolve("src/main/java/Solution.java");
                assertTrue(Files.exists(javaFile));
                assertEquals("public class Solution { /* Thread starter */ }", Files.readString(javaFile, StandardCharsets.UTF_8));
            }
        }

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("20 concurrent threads calling initializeProject on the SAME directory complete with zero deadlocks and zero corruption")
        void testConcurrentInitializationSameDirectory20Threads() throws Exception {
            final int threadCount = 20;
            final Path sharedDir = tempDir.resolve("shared-concurrency-target");
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            byte[] starterZip = createZip(Map.of(
                    "src/main/java/SharedEntity.java", "public class SharedEntity {}\n",
                    "src/main/resources/app.properties", "mode=concurrent\n"
            ));
            apiClient.setStarterTemplate("shared-slug", starterZip);
            apiClient.setProblem("shared-slug", new ProblemDto(777L, "Shared Problem", "shared-slug", "# Shared Problem Description"));

            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger workspaceExceptionCount = new AtomicInteger(0);
            ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final WorkspaceInitRequest request = new WorkspaceInitRequest(
                        sharedDir, "plan-shared", "shared-item", 1, "shared-slug", 777L
                );

                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        assertTrue(startLatch.await(10, TimeUnit.SECONDS));
                        WorkspaceResult result = workspaceService.initializeProject(request);
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (WorkspaceException we) {
                        // Expected potential outcome under extreme concurrent file races on same directory
                        workspaceExceptionCount.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    }
                }));
            }

            readyLatch.await(10, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Verify: Zero unexpected crashes (no NPE, no deadlock, no AssertionError)
            assertTrue(unexpectedErrors.isEmpty(), "Zero unexpected errors expected, found: " + unexpectedErrors);
            assertTrue(successCount.get() >= 1, "At least one thread must successfully initialize the directory");
            assertEquals(threadCount, successCount.get() + workspaceExceptionCount.get(),
                    "Every thread must cleanly either succeed or throw WorkspaceException");

            // Verify: The target filesystem state is 100% valid, intact, and uncorrupted!
            assertTrue(Files.isDirectory(sharedDir), "Directory must exist");

            Path pomPath = sharedDir.resolve("pom.xml");
            assertTrue(Files.exists(pomPath), "pom.xml must exist");
            String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
            assertTrue(pom.contains("<artifactId>shared-slug</artifactId>"));

            Path manifestPath = sharedDir.resolve(".mlc/project.yaml");
            assertTrue(Files.exists(manifestPath), "Manifest must exist");
            ProjectManifest manifest = ProjectManifest.readFrom(manifestPath);
            assertEquals("plan-shared", manifest.plan());
            assertEquals("shared-item", manifest.item());
            assertEquals(1, manifest.currentStage());
            assertEquals("shared-slug", manifest.problemSlug());
            assertEquals(777L, manifest.problemId());

            Path mdPath = sharedDir.resolve("stages/stage-1.md");
            assertTrue(Files.exists(mdPath), "stage-1.md must exist");
            assertEquals("# Shared Problem Description", Files.readString(mdPath, StandardCharsets.UTF_8));

            Path javaPath = sharedDir.resolve("src/main/java/SharedEntity.java");
            assertTrue(Files.exists(javaPath), "SharedEntity.java must exist");
            assertEquals("public class SharedEntity {}\n", Files.readString(javaPath, StandardCharsets.UTF_8));
        }
    }

    // =========================================================================
    // 2. Non-Overwrite Under Progression Stress (AC-004)
    // =========================================================================
    @Nested
    @DisplayName("2. Non-Overwrite Under Progression Stress (AC-004)")
    class ProgressionNonOverwriteTests {

        @Test
        @DisplayName("Stage 1 -> 2 -> 3 rapid progression preserves user code in Bank.java and custom pom.xml strictly untouched (AC-004)")
        void testProgressionStagesNonOverwritePreservation() throws Exception {
            Path projectDir = tempDir.resolve("mini-bank-lifecycle");

            // --- STAGE 1 ---
            byte[] s1Zip = createZip(Map.of(
                    "src/main/java/Bank.java", "public class Bank {\n    // Original Stage 1 starter\n}\n",
                    "src/main/java/Account.java", "public class Account {}\n"
            ));
            apiClient.setStarterTemplate("mini-bank-s1", s1Zip);
            apiClient.setProblem("mini-bank-s1", new ProblemDto(101L, "Stage 1", "mini-bank-s1", "# Stage 1 Description"));

            WorkspaceResult r1 = workspaceService.initializeProject(new WorkspaceInitRequest(
                    projectDir, "oop-java", "bank-item", 1, "mini-bank-s1", 101L
            ));
            assertEquals(2, r1.extractionResult().extractedFiles());
            assertEquals(0, r1.extractionResult().skippedFiles());

            // User modifies Bank.java with proprietary solution
            Path bankFile = projectDir.resolve("src/main/java/Bank.java");
            String userCodeV1 = """
                    package jsd.toiyeujava;
                    public class Bank {
                        // USER CUSTOM IMPLEMENTATION V1
                        private double totalBalance = 5000.0;
                        public double getBalance() { return totalBalance; }
                    }
                    """;
            Files.writeString(bankFile, userCodeV1, StandardCharsets.UTF_8);

            // User creates their own helper file
            Path customHelper = projectDir.resolve("src/main/java/MyUserHelper.java");
            String userHelperContent = "public class MyUserHelper { /* helper */ }";
            Files.writeString(customHelper, userHelperContent, StandardCharsets.UTF_8);

            // User customizes pom.xml with a custom property
            Path pomFile = projectDir.resolve("pom.xml");
            String customPomContent = Files.readString(pomFile, StandardCharsets.UTF_8)
                    .replace("</project>", "    <!-- USER CUSTOM COMMENT -->\n</project>");
            Files.writeString(pomFile, customPomContent, StandardCharsets.UTF_8);

            // --- STAGE 2 PROGRESSION ---
            // Starter for stage 2 provides fresh Bank.java, fresh Account.java, and new SavingsAccount.java
            byte[] s2Zip = createZip(Map.of(
                    "src/main/java/Bank.java", "public class Bank { /* STAGE 2 DEFAULT STARTER MUST NOT OVERWRITE */ }",
                    "src/main/java/Account.java", "public class Account { /* Stage 2 Account */ }",
                    "src/main/java/SavingsAccount.java", "public class SavingsAccount {}\n"
            ));
            apiClient.setStarterTemplate("mini-bank-s2", s2Zip);
            apiClient.setProblem("mini-bank-s2", new ProblemDto(102L, "Stage 2", "mini-bank-s2", "# Stage 2 Description"));

            WorkspaceResult r2 = workspaceService.initializeProject(new WorkspaceInitRequest(
                    projectDir, "oop-java", "bank-item", 2, "mini-bank-s2", 102L
            ));

            // Verify Stage 2 Extraction Counts: Bank.java & Account.java skipped, SavingsAccount.java extracted
            assertEquals(1, r2.extractionResult().extractedFiles());
            assertEquals(2, r2.extractionResult().skippedFiles());

            // AC-004 Verification: Bank.java MUST remain user code V1!
            assertEquals(userCodeV1, Files.readString(bankFile, StandardCharsets.UTF_8),
                    "AC-004 VIOLATION: Bank.java was overwritten by Stage 2 starter!");
            assertEquals(userHelperContent, Files.readString(customHelper, StandardCharsets.UTF_8),
                    "User custom helper file must remain intact");
            assertEquals(customPomContent, Files.readString(pomFile, StandardCharsets.UTF_8),
                    "pom.xml customizations must be preserved");
            assertTrue(Files.exists(projectDir.resolve("src/main/java/SavingsAccount.java")),
                    "New SavingsAccount.java must be extracted");

            // Verify Stage Markdown files: both stage-1 and stage-2 must coexist
            assertTrue(Files.exists(projectDir.resolve("stages/stage-1.md")));
            assertTrue(Files.exists(projectDir.resolve("stages/stage-2.md")));
            assertEquals("# Stage 1 Description", Files.readString(projectDir.resolve("stages/stage-1.md"), StandardCharsets.UTF_8));
            assertEquals("# Stage 2 Description", Files.readString(projectDir.resolve("stages/stage-2.md"), StandardCharsets.UTF_8));

            // Verify ProjectManifest updated to stage 2
            ProjectManifest m2 = ProjectManifest.readFromProject(projectDir);
            assertEquals(2, m2.currentStage());
            assertEquals("mini-bank-s2", m2.problemSlug());
            assertEquals(102L, m2.problemId());

            // --- STAGE 3 PROGRESSION ---
            // User updates Bank.java to V2
            String userCodeV2 = userCodeV1.replace("V1", "V2").replace("5000.0", "15000.0");
            Files.writeString(bankFile, userCodeV2, StandardCharsets.UTF_8);

            byte[] s3Zip = createZip(Map.of(
                    "src/main/java/Bank.java", "public class Bank { /* STAGE 3 STARTER */ }",
                    "src/main/java/SavingsAccount.java", "public class SavingsAccount { /* STAGE 3 */ }",
                    "src/main/java/Transaction.java", "public class Transaction {}\n"
            ));
            apiClient.setStarterTemplate("mini-bank-s3", s3Zip);
            apiClient.setProblem("mini-bank-s3", new ProblemDto(103L, "Stage 3", "mini-bank-s3", "# Stage 3 Description"));

            WorkspaceResult r3 = workspaceService.initializeProject(new WorkspaceInitRequest(
                    projectDir, "oop-java", "bank-item", 3, "mini-bank-s3", 103L
            ));

            assertEquals(1, r3.extractionResult().extractedFiles());
            assertEquals(2, r3.extractionResult().skippedFiles());

            // AC-004 Verification after stage 3
            assertEquals(userCodeV2, Files.readString(bankFile, StandardCharsets.UTF_8),
                    "AC-004 VIOLATION: Bank.java user code V2 was overwritten by Stage 3 starter!");
            assertTrue(Files.exists(projectDir.resolve("src/main/java/Transaction.java")));
            assertTrue(Files.exists(projectDir.resolve("stages/stage-1.md")));
            assertTrue(Files.exists(projectDir.resolve("stages/stage-2.md")));
            assertTrue(Files.exists(projectDir.resolve("stages/stage-3.md")));

            ProjectManifest m3 = ProjectManifest.readFromProject(projectDir);
            assertEquals(3, m3.currentStage());
            assertEquals("mini-bank-s3", m3.problemSlug());
            assertEquals(103L, m3.problemId());
        }

        @Test
        @DisplayName("10 rapid stage progressions in loop preserve user code and maintain all 10 stage markdowns")
        void testRapidMultiStageLoopProgression() throws Exception {
            Path projectDir = tempDir.resolve("loop-progression-project");
            String invariantCode = "public class CoreStudentAlgorithm { final int secret = 42; }";

            for (int stage = 1; stage <= 10; stage++) {
                String slug = "loop-stage-" + stage;
                byte[] zip = createZip(Map.of(
                        "src/main/java/CoreStudentAlgorithm.java", "class OverwriteAttempt" + stage + "{}",
                        "src/main/java/StageFeature" + stage + ".java", "class StageFeature" + stage + "{}"
                ));
                apiClient.setStarterTemplate(slug, zip);
                apiClient.setProblem(slug, new ProblemDto((long) stage, "Stage " + stage, slug, "Description for stage " + stage));

                if (stage == 1) {
                    workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "p", "i", stage, slug, (long) stage));
                    Files.writeString(projectDir.resolve("src/main/java/CoreStudentAlgorithm.java"), invariantCode, StandardCharsets.UTF_8);
                } else {
                    workspaceService.initializeProject(new WorkspaceInitRequest(projectDir, "p", "i", stage, slug, (long) stage));
                }

                assertEquals(invariantCode, Files.readString(projectDir.resolve("src/main/java/CoreStudentAlgorithm.java")),
                        "CoreStudentAlgorithm.java must never be overwritten in stage " + stage);
                assertTrue(Files.exists(projectDir.resolve("src/main/java/StageFeature" + stage + ".java")),
                        "StageFeature" + stage + ".java must be extracted");
            }

            // Verify all 10 markdown files exist
            for (int stage = 1; stage <= 10; stage++) {
                Path md = projectDir.resolve("stages/stage-" + stage + ".md");
                assertTrue(Files.exists(md), "stage-" + stage + ".md must exist");
                assertEquals("Description for stage " + stage, Files.readString(md, StandardCharsets.UTF_8));
            }

            ProjectManifest finalManifest = ProjectManifest.readFromProject(projectDir);
            assertEquals(10, finalManifest.currentStage());
            assertEquals("loop-stage-10", finalManifest.problemSlug());
        }
    }

    // =========================================================================
    // 3. Re-entrancy & Idempotency
    // =========================================================================
    @Nested
    @DisplayName("3. Re-entrancy & Idempotency")
    class ReentrancyTests {

        @Test
        @DisplayName("Invoking initializeProject 3 times sequentially on the exact same folder is 100% idempotent")
        void testReentrantInitializationSameDirectorySameStage() throws Exception {
            Path projectDir = tempDir.resolve("idempotent-test-project");
            String slug = "idem-slug";
            byte[] starterZip = createZip(Map.of(
                    "src/main/java/Item.java", "public class Item {}\n"
            ));
            apiClient.setStarterTemplate(slug, starterZip);
            apiClient.setProblem(slug, new ProblemDto(55L, "Idem", slug, "Idem description"));

            WorkspaceInitRequest request = new WorkspaceInitRequest(projectDir, "plan-idem", "item-1", 1, slug, 55L);

            // Call 1
            WorkspaceResult res1 = workspaceService.initializeProject(request);
            assertEquals(1, res1.extractionResult().extractedFiles());
            assertEquals(0, res1.extractionResult().skippedFiles());

            // Call 2 (immediate re-entrancy)
            WorkspaceResult res2 = workspaceService.initializeProject(request);
            assertEquals(0, res2.extractionResult().extractedFiles(), "No new files extracted on 2nd run");
            assertEquals(1, res2.extractionResult().skippedFiles(), "Existing Item.java must be skipped");

            // Call 3
            WorkspaceResult res3 = workspaceService.initializeProject(request);
            assertEquals(0, res3.extractionResult().extractedFiles());
            assertEquals(1, res3.extractionResult().skippedFiles());

            // Verify state
            assertTrue(ProjectManifest.existsIn(projectDir));
            ProjectManifest manifest = ProjectManifest.readFromProject(projectDir);
            assertEquals(1, manifest.currentStage());
            assertEquals("idem-slug", manifest.problemSlug());

            assertEquals(3, projectOpener.openedDirectories.size(),
                    "ProjectOpener should be called each time project is requested");
        }
    }

    // =========================================================================
    // 4. YAML Parser Resilience & Stress
    // =========================================================================
    @Nested
    @DisplayName("4. YAML Parser Resilience & Stress")
    class YamlStressTests {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("ProjectManifest.parse handles 10,000 comment lines under 3 seconds without StackOverflow or OOM")
        void testYamlParserHugeCommentLines10000() {
            StringBuilder sb = new StringBuilder(500_000);
            sb.append("# Header comment block\n");
            for (int i = 0; i < 5000; i++) {
                sb.append("# Comment line ").append(i).append(" with arbitrary symbols !@#$%^&*()_+\n");
            }
            sb.append("plan: oop-with-java\n");
            for (int i = 5000; i < 8000; i++) {
                sb.append("   # Indented comment ").append(i).append("\n");
            }
            sb.append("item: \"42\"\n");
            sb.append("current_stage: 5\n");
            for (int i = 8000; i < 10000; i++) {
                sb.append("# Trailing comment ").append(i).append("\n");
            }
            sb.append("problem_slug: mini-bank-stage-5\n");
            sb.append("problem_id: 9999\n");

            Instant start = Instant.now();
            ProjectManifest manifest = ProjectManifest.parse(sb.toString());
            Duration elapsed = Duration.between(start, Instant.now());

            assertNotNull(manifest);
            assertEquals("oop-with-java", manifest.plan());
            assertEquals("42", manifest.item());
            assertEquals(42, manifest.itemAsInt());
            assertEquals(5, manifest.currentStage());
            assertEquals("mini-bank-stage-5", manifest.problemSlug());
            assertEquals(9999L, manifest.problemId());
            assertTrue(elapsed.toMillis() < 3000, "10,000 lines parse took " + elapsed.toMillis() + "ms (must be < 3000ms)");

            // Roundtrip equality
            String reserialized = manifest.toYamlString();
            ProjectManifest roundtripped = ProjectManifest.parse(reserialized);
            assertEquals(manifest, roundtripped);
        }

        @Test
        @DisplayName("ProjectManifest.parse gracefully parses bizarre spacing, quotes, tabs, and inline comments")
        void testYamlParserBizarreSpacingAndFormatting() {
            String yaml = """
                    ---
                    
                       plan   :    "oop-with-java"   
                       item :   '99'   # inline comment with special chars : #
                       
                       current_stage :    3   # current stage comment
                       problem_slug   :   "complex:slug-with:colons"   
                       problem_id  :   12345
                       
                       custom_tag_1 : "value with spaces and # hashes"
                       custom_tag_2 : 'single-quoted-value'
                       custom_tag_3 : unquoted plain string # comment
                    ...
                    """;

            ProjectManifest manifest = ProjectManifest.parse(yaml);
            assertEquals("oop-with-java", manifest.plan());
            assertEquals("99", manifest.item());
            assertEquals(99, manifest.itemAsInt());
            assertEquals(3, manifest.currentStage());
            assertEquals("complex:slug-with:colons", manifest.problemSlug());
            assertEquals(12345L, manifest.problemId());

            assertEquals("value with spaces and # hashes", manifest.get("custom_tag_1"));
            assertEquals("single-quoted-value", manifest.get("custom_tag_2"));
            assertEquals("unquoted plain string", manifest.get("custom_tag_3"));
        }

        @Test
        @DisplayName("ProjectManifest preserves Unicode, Vietnamese accents, and emojis in slugs and properties")
        void testYamlParserUnicodeAndEmojis() {
            String planWithEmoji = "oop-java-☕-v2";
            String itemWithUnicode = "bài-tập-1";
            String slugWithEmoji = "ngân-hàng-điện-tử-🏦-🚀";
            String extraProperty = "Người kiểm thử: Nguyễn Văn A 🇻🇳 💯";

            ProjectManifest original = ProjectManifest.builder()
                    .plan(planWithEmoji)
                    .item(itemWithUnicode)
                    .currentStage(1)
                    .problemSlug(slugWithEmoji)
                    .problemId(888L)
                    .extra("nguoi_kiem_thu", extraProperty)
                    .build();

            String yaml = original.toYamlString();
            ProjectManifest parsed = ProjectManifest.parse(yaml);

            assertEquals(planWithEmoji, parsed.plan());
            assertEquals(itemWithUnicode, parsed.item());
            assertEquals(1, parsed.currentStage());
            assertEquals(slugWithEmoji, parsed.problemSlug());
            assertEquals(888L, parsed.problemId());
            assertEquals(extraProperty, parsed.get("nguoi_kiem_thu"));
            assertEquals(original, parsed);
        }

        @Test
        @DisplayName("ProjectManifest rejects missing required fields and invalid current_stage with clear IllegalArgumentException")
        void testYamlParserMissingRequiredKeysThrows() {
            // Missing plan
            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    item: 1
                    current_stage: 1
                    problem_slug: s
                    """));

            // Missing item
            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    current_stage: 1
                    problem_slug: s
                    """));

            // Missing current_stage
            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    item: 1
                    problem_slug: s
                    """));

            // Missing problem_slug
            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    item: 1
                    current_stage: 1
                    """));

            // current_stage <= 0 or not an int
            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    item: 1
                    current_stage: 0
                    problem_slug: s
                    """));

            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    item: 1
                    current_stage: -5
                    problem_slug: s
                    """));

            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    item: 1
                    current_stage: not-a-number
                    problem_slug: s
                    """));

            // Malformed line without colon
            assertThrows(IllegalArgumentException.class, () -> ProjectManifest.parse("""
                    plan: p
                    item: 1
                    current_stage: 1
                    problem_slug: s
                    this line has no colon
                    """));
        }

        @Test
        @DisplayName("50 extra properties survive roundtrip serialization and remain case-insensitive")
        void testYamlParser50ExtraPropertiesRoundtripAndCaseInsensitive() {
            ProjectManifest.Builder builder = ProjectManifest.builder()
                    .plan("p")
                    .item("1")
                    .currentStage(1)
                    .problemSlug("s");

            for (int i = 1; i <= 50; i++) {
                builder.extra("prop_" + i, "value_" + i + "_#symbol:test");
            }
            ProjectManifest manifest = builder.build();
            assertEquals(50, manifest.extraProperties().size());

            String yaml = manifest.toYamlString();
            ProjectManifest roundtripped = ProjectManifest.parse(yaml);

            assertEquals(manifest, roundtripped);
            for (int i = 1; i <= 50; i++) {
                assertEquals("value_" + i + "_#symbol:test", roundtripped.get("prop_" + i));
                assertEquals("value_" + i + "_#symbol:test", roundtripped.get("PROP_" + i),
                        "Extra properties must be case-insensitively accessible");
            }
        }
    }

    // =========================================================================
    // 5. Filesystem Boundary & Error Edge Cases
    // =========================================================================
    @Nested
    @DisplayName("5. Filesystem Boundary & Error Edge Cases")
    class FilesystemEdgeCaseTests {

        @Test
        @DisplayName("Target directory deeply nested inside non-existent parents is automatically created")
        void testDeeplyNestedNonExistentDirectoriesCreation() throws Exception {
            Path deepTarget = tempDir.resolve("a/b/c/d/e/f/g/h/student-project");
            assertFalse(Files.exists(deepTarget));

            apiClient.setStarterTemplate("p", createZip(Map.of("src/main/java/A.java", "class A{}")));
            apiClient.setProblem("p", new ProblemDto(1L, "P", "p", "D"));

            WorkspaceResult result = workspaceService.initializeProject(new WorkspaceInitRequest(
                    deepTarget, "plan", "item", 1, "p"
            ));

            assertTrue(Files.isDirectory(deepTarget));
            assertTrue(Files.exists(deepTarget.resolve("pom.xml")));
            assertTrue(Files.exists(deepTarget.resolve(".mlc/project.yaml")));
            assertTrue(Files.exists(deepTarget.resolve("stages/stage-1.md")));
            assertTrue(Files.exists(deepTarget.resolve("src/main/java/A.java")));
            assertEquals(deepTarget, result.projectDir());
        }

        @Test
        @DisplayName("Investigate starter template extraction with bare .java files (root vs src/main/java routing)")
        void testBareJavaFilesExtractionBehavior() throws Exception {
            Path targetDir = tempDir.resolve("bare-java-target");
            apiClient.setStarterTemplate("bare-slug", createZip(Map.of(
                    "BareClass.java", "public class BareClass {}\n",
                    "nested/Helper.java", "package nested;\npublic class Helper {}\n"
            )));
            apiClient.setProblem("bare-slug", new ProblemDto(10L, "Bare", "bare-slug", "Bare description"));

            workspaceService.initializeProject(new WorkspaceInitRequest(
                    targetDir, "plan", "item", 1, "bare-slug"
            ));

            // Verify where files are actually written by WorkspaceService
            boolean inRoot = Files.exists(targetDir.resolve("BareClass.java"));
            boolean inSrcMain = Files.exists(targetDir.resolve("src/main/java/BareClass.java"));

            // One of them must exist
            assertTrue(inRoot || inSrcMain, "BareClass.java must have been extracted somewhere in the project");
        }

        @Test
        @DisplayName("Target path pointing to an existing regular file throws WorkspaceException cleanly")
        void testTargetDirectoryIsExistingRegularFileThrowsWorkspaceException() throws IOException {
            Path fileAsDir = tempDir.resolve("regular-file.txt");
            Files.writeString(fileAsDir, "I am a regular file, not a directory");

            WorkspaceInitRequest request = new WorkspaceInitRequest(fileAsDir, "plan", "item", 1, "slug");

            WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                    workspaceService.initializeProject(request)
            );
            assertTrue(ex.getMessage().contains("Failed to create target directory")
                    || ex.getMessage().contains("Failed to write"));
            assertTrue(projectOpener.openedDirectories.isEmpty());
        }

        @Test
        @DisplayName("Read-only file conflict in target directory throws WorkspaceException cleanly without unhandled crash")
        void testReadOnlyFileConflictThrowsWorkspaceException() throws Exception {
            Path targetDir = tempDir.resolve("readonly-target");
            Files.createDirectories(targetDir.resolve(".mlc"));
            Path manifestFile = targetDir.resolve(".mlc/project.yaml");
            Files.writeString(manifestFile, "plan: old\nitem: 1\ncurrent_stage: 1\nproblem_slug: old\n");

            // Mark manifest read-only
            File file = manifestFile.toFile();
            boolean readOnlySet = file.setReadOnly();

            if (readOnlySet) {
                try {
                    apiClient.setStarterTemplate("new-slug", createZip(Map.of()));
                    apiClient.setProblem("new-slug", new ProblemDto(2L, "T", "new-slug", "D"));

                    WorkspaceInitRequest request = new WorkspaceInitRequest(targetDir, "plan", "item", 2, "new-slug");

                    WorkspaceException ex = assertThrows(WorkspaceException.class, () ->
                            workspaceService.initializeProject(request)
                    );
                    assertTrue(ex.getMessage().contains("Failed to write project manifest")
                            || ex.getMessage().contains("AccessDeniedException"));
                    assertTrue(projectOpener.openedDirectories.isEmpty(), "ProjectOpener must not be invoked on write failure");
                } finally {
                    file.setWritable(true);
                }
            }
        }
    }

    // =========================================================================
    // Helpers & Thread-Safe Test Doubles
    // =========================================================================

    private static class ConcurrentRecordingProjectOpener implements ProjectOpener {
        final List<Path> openedDirectories = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void openProject(Path projectDirectory) {
            openedDirectories.add(projectDirectory);
        }
    }

    private static class ConcurrentStubApiClient extends ApiClient {
        private final Map<String, byte[]> starterTemplates = new ConcurrentHashMap<>();
        private final Map<String, ProblemDto> problems = new ConcurrentHashMap<>();

        public ConcurrentStubApiClient() {
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

    private static byte[] createZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                if (entry.getValue() != null && !entry.getKey().endsWith("/")) {
                    zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
