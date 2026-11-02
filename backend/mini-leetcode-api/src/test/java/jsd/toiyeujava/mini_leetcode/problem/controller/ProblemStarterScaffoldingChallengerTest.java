package jsd.toiyeujava.mini_leetcode.problem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Challenger Test Suite for Starter Scaffolding API:
 * GET /api/v1/problems/{id}/starter-template
 *
 * Verifies:
 * 1. Populated JSON starterTemplates placement and accurate extraction.
 * 2. Graceful fallback on null, empty, whitespace, and malformed JSON starterTemplates.
 * 3. Header correctness: Content-Disposition attachment filename and application/zip MediaType.
 * 4. ZIP archive integrity: relative path preservation, UTF-8 text integrity, non-empty content.
 * 5. Non-existent problem ID / slug error handling (HTTP 404).
 * 6. Access control and problem lifecycle permissions (DRAFT vs PUBLISHED for public, student, and staff).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemStarterScaffoldingChallengerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorageService storageService;

    private User student;
    private User instructor;
    private User admin;

    private String studentToken;
    private String instructorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        student = userRepository.save(User.builder()
                .username("student_challenger_" + ts)
                .email("student_" + ts + "@test.org")
                .passwordHash("hashed")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        instructor = userRepository.save(User.builder()
                .username("instructor_challenger_" + ts)
                .email("instructor_" + ts + "@test.org")
                .passwordHash("hashed")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        admin = userRepository.save(User.builder()
                .username("admin_challenger_" + ts)
                .email("admin_" + ts + "@test.org")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        studentToken = "Bearer " + jwtTokenProvider.generateAccessToken(student);
        instructorToken = "Bearer " + jwtTokenProvider.generateAccessToken(instructor);
        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin);
    }

    @Nested
    @DisplayName("1. Scaffolding Generation with Populated starterTemplates")
    class PopulatedTemplatesTests {

        @Test
        @DisplayName("1.1: Multi-file populated templates are all packaged with correct paths and UTF-8 content")
        void testPopulatedTemplates_AllFilesExtractedAccurately() throws Exception {
            List<SubmissionFileDto> templates = List.of(
                    new SubmissionFileDto("src/model/Shape.java",
                            "package model;\n\n/** Lớp hình học cơ bản */\npublic abstract class Shape {\n    public abstract double area();\n}\n"),
                    new SubmissionFileDto("src/model/Circle.java",
                            "package model;\n\npublic class Circle extends Shape {\n    private final double radius;\n    public Circle(double r) { this.radius = r; }\n    @Override public double area() { return Math.PI * radius * radius; }\n}\n"),
                    new SubmissionFileDto("src/app/Main.java",
                            "package app;\nimport model.Circle;\n\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println(new Circle(5).area());\n    }\n}\n")
            );

            String templatesJson = objectMapper.writeValueAsString(templates);
            String slug = "oop-shape-hierarchy-" + System.currentTimeMillis();

            Problem problem = problemRepository.save(Problem.builder()
                    .title("OOP Shape Hierarchy — Hình học Đối tượng")
                    .slug(slug)
                    .description("Thiết kế phân cấp lớp hình học Shape và Circle với tính kế thừa và đa hình.")
                    .difficulty(Difficulty.MEDIUM)
                    .timeLimitMs(2000)
                    .memoryLimitMb(256)
                    .problemType(ProblemType.OOP_GRINDING)
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates(templatesJson)
                    .createdBy(instructor)
                    .build());

            MvcResult result = mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template")
                            .header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + slug + "-starter.zip\""))
                    .andReturn();

            byte[] zipBytes = result.getResponse().getContentAsByteArray();
            assertThat(zipBytes).isNotEmpty();

            Map<String, String> extractedEntries = extractZipEntries(zipBytes);

            // Verify entry names
            assertThat(extractedEntries.keySet()).containsExactlyInAnyOrder(
                    "README.md",
                    "src/model/Shape.java",
                    "src/model/Circle.java",
                    "src/app/Main.java"
            );

            // Verify exact contents and UTF-8 preservation
            assertThat(extractedEntries.get("src/model/Shape.java"))
                    .contains("Lớp hình học cơ bản")
                    .contains("public abstract class Shape");
            assertThat(extractedEntries.get("src/model/Circle.java"))
                    .contains("public class Circle extends Shape");
            assertThat(extractedEntries.get("src/app/Main.java"))
                    .contains("public class Main");

            // Verify README.md synthesis
            String readme = extractedEntries.get("README.md");
            assertThat(readme)
                    .contains("OOP Shape Hierarchy — Hình học Đối tượng")
                    .contains(slug)
                    .contains("Thiết kế phân cấp lớp hình học Shape")
                    .contains("How to Run Locally")
                    .contains("How to Submit");
        }

        @Test
        @DisplayName("1.2: Endpoint works seamlessly when requested by slug instead of numeric ID")
        void testPopulatedTemplates_AccessibleBySlug() throws Exception {
            String slug = "slug-lookup-scaffold-" + System.currentTimeMillis();
            List<SubmissionFileDto> templates = List.of(
                    new SubmissionFileDto("Calculator.java", "public class Calculator {}")
            );

            Problem problem = problemRepository.save(Problem.builder()
                    .title("Calculator Problem")
                    .slug(slug)
                    .description("Simple calculator starter")
                    .difficulty(Difficulty.EASY)
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates(objectMapper.writeValueAsString(templates))
                    .createdBy(instructor)
                    .build());

            MvcResult result = mockMvc.perform(get("/api/v1/problems/" + slug + "/starter-template"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + slug + "-starter.zip\""))
                    .andReturn();

            Map<String, String> entries = extractZipEntries(result.getResponse().getContentAsByteArray());
            assertThat(entries).containsKey("Calculator.java");
            assertThat(entries).containsKey("README.md");
        }
    }

    @Nested
    @DisplayName("2. Graceful Fallback Scaffolding (Null, Empty, Malformed starterTemplates)")
    class FallbackScaffoldingTests {

        @Test
        @DisplayName("2.1: Problem with null starterTemplates generates valid zip with README.md and Solution.java")
        void testFallback_NullStarterTemplates() throws Exception {
            String slug = "fallback-null-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Two Sum Dynamic")
                    .slug(slug)
                    .description("Given an array of integers, return indices of two numbers that add up to target.")
                    .difficulty(Difficulty.EASY)
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates(null)
                    .createdBy(instructor)
                    .build());

            MvcResult result = mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + slug + "-starter.zip\""))
                    .andReturn();

            byte[] zipBytes = result.getResponse().getContentAsByteArray();
            Map<String, String> entries = extractZipEntries(zipBytes);

            assertThat(entries.keySet()).containsExactlyInAnyOrder("README.md", "Solution.java");
            assertThat(entries.get("Solution.java"))
                    .contains("public class Solution")
                    .contains("// TODO: Implement your solution here");
            assertThat(entries.get("README.md"))
                    .contains("Two Sum Dynamic")
                    .contains("Given an array of integers");
        }

        @Test
        @DisplayName("2.2: Problem with non-list JSON object '{}' in starterTemplates gracefully falls back to Solution.java")
        void testFallback_NonListJsonObjectStarterTemplates() throws Exception {
            String slugEmpty = "fallback-non-list-" + System.currentTimeMillis();
            Problem problemNonList = problemRepository.save(Problem.builder()
                    .title("Non List JSON Problem")
                    .slug(slugEmpty)
                    .description("Valid JSON object but not a list")
                    .difficulty(Difficulty.EASY)
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates("{\"key\": \"not a list of templates\"}")
                    .createdBy(instructor)
                    .build());

            MvcResult result = mockMvc.perform(get("/api/v1/problems/" + problemNonList.getId() + "/starter-template"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, String> entries = extractZipEntries(result.getResponse().getContentAsByteArray());
            assertThat(entries.keySet()).containsExactlyInAnyOrder("README.md", "Solution.java");
            assertThat(entries.get("Solution.java")).contains("public class Solution");
        }

        @Test
        @DisplayName("2.3: Problem with empty JSON array '[]' starterTemplates falls back to Solution.java")
        void testFallback_EmptyJsonArrayStarterTemplates() throws Exception {
            String slug = "fallback-empty-array-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Empty Array Problem")
                    .slug(slug)
                    .description("Empty array JSON")
                    .difficulty(Difficulty.MEDIUM)
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates("[]")
                    .createdBy(instructor)
                    .build());

            MvcResult result = mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template"))
                    .andExpect(status().isOk())
                    .andReturn();

            Map<String, String> entries = extractZipEntries(result.getResponse().getContentAsByteArray());
            assertThat(entries.keySet()).containsExactlyInAnyOrder("README.md", "Solution.java");
        }

        @Test
        @DisplayName("2.4: Direct Controller test verifies whitespace and syntax-error JSON strings gracefully fall back")
        void testFallback_DirectControllerHandlesWhitespaceAndMalformedJson() throws Exception {
            jsd.toiyeujava.mini_leetcode.submission.service.SubmissionArchiveService archiveService =
                    new jsd.toiyeujava.mini_leetcode.submission.service.SubmissionArchiveServiceImpl();

            Problem mockProblem = Problem.builder()
                    .id(777L)
                    .slug("raw-malformed-test")
                    .title("Raw Malformed Test")
                    .description("Description")
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates("  broken { not json } ")
                    .build();

            ProblemRepository mockRepo = org.mockito.Mockito.mock(ProblemRepository.class);
            org.mockito.Mockito.when(mockRepo.findById(777L)).thenReturn(java.util.Optional.of(mockProblem));

            ProblemController controller = new ProblemController(
                    null, null, mockRepo, archiveService, objectMapper
            );

            org.springframework.http.ResponseEntity<byte[]> response =
                    controller.getStarterTemplate("777", null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/zip");
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .isEqualTo("attachment; filename=\"raw-malformed-test-starter.zip\"");

            Map<String, String> entries = extractZipEntries(response.getBody());
            assertThat(entries.keySet()).containsExactlyInAnyOrder("README.md", "Solution.java");
        }
    }

    @Nested
    @DisplayName("3. Non-Existent Problem Handling (HTTP 404)")
    class NonExistentProblemTests {

        @Test
        @DisplayName("3.1: Requesting non-existent numeric problem ID returns HTTP 404")
        void testNonExistentProblem_NumericId_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/problems/99999999/starter-template")
                            .header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("3.2: Requesting non-existent slug returns HTTP 404")
        void testNonExistentProblem_StringSlug_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/problems/non-existent-problem-slug-99999/starter-template")
                            .header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("3.3: Requesting malformed/negative ID returns HTTP 404")
        void testNonExistentProblem_NegativeId_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/problems/-1/starter-template"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("4. Security and Lifecycle Access Control")
    class LifecycleAndAccessControlTests {

        @Test
        @DisplayName("4.1: Public / Anonymous user can download starter template for PUBLISHED problem")
        void testPublicAccess_PublishedProblem_Returns200() throws Exception {
            String slug = "published-public-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Public Published Problem")
                    .slug(slug)
                    .description("Publicly accessible")
                    .difficulty(Difficulty.EASY)
                    .status(ProblemStatus.PUBLISHED)
                    .createdBy(instructor)
                    .build());

            mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
        }

        @Test
        @DisplayName("4.2: Anonymous user requesting DRAFT problem receives HTTP 404 (no leak of unpublished problem)")
        void testDraftProblem_AnonymousUser_Returns404() throws Exception {
            String slug = "draft-problem-anon-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Secret Draft Problem")
                    .slug(slug)
                    .description("Under construction")
                    .difficulty(Difficulty.MEDIUM)
                    .status(ProblemStatus.DRAFT)
                    .createdBy(instructor)
                    .build());

            mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("4.3: Regular student requesting DRAFT problem receives HTTP 404")
        void testDraftProblem_StudentUser_Returns404() throws Exception {
            String slug = "draft-problem-student-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Secret Draft Problem for Student")
                    .slug(slug)
                    .description("Under construction")
                    .difficulty(Difficulty.MEDIUM)
                    .status(ProblemStatus.DRAFT)
                    .createdBy(instructor)
                    .build());

            mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template")
                            .header(HttpHeaders.AUTHORIZATION, studentToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("4.4: Instructor requesting DRAFT problem receives HTTP 200 with starter ZIP")
        void testDraftProblem_InstructorUser_Returns200() throws Exception {
            String slug = "draft-problem-instructor-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Instructor Draft Problem")
                    .slug(slug)
                    .description("Draft being prepared by instructor")
                    .difficulty(Difficulty.MEDIUM)
                    .status(ProblemStatus.DRAFT)
                    .createdBy(instructor)
                    .build());

            mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template")
                            .header(HttpHeaders.AUTHORIZATION, instructorToken))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
        }

        @Test
        @DisplayName("4.5: Admin requesting DRAFT problem receives HTTP 200 with starter ZIP")
        void testDraftProblem_AdminUser_Returns200() throws Exception {
            String slug = "draft-problem-admin-" + System.currentTimeMillis();
            Problem problem = problemRepository.save(Problem.builder()
                    .title("Admin Draft Problem")
                    .slug(slug)
                    .description("Draft inspected by admin")
                    .difficulty(Difficulty.HARD)
                    .status(ProblemStatus.DRAFT)
                    .createdBy(instructor)
                    .build());

            mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template")
                            .header(HttpHeaders.AUTHORIZATION, adminToken))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
        }
    }

    @Nested
    @DisplayName("5. ZIP Archive Integrity and Binary Robustness")
    class ZipIntegrityTests {

        @Test
        @DisplayName("5.1: Zip entries can be read and unpacked cleanly without corruption or truncation")
        void testZipIntegrity_CleanExtraction() throws Exception {
            List<SubmissionFileDto> templates = List.of(
                    new SubmissionFileDto("com/example/algo/Graph.java", "package com.example.algo;\npublic class Graph {}"),
                    new SubmissionFileDto("com/example/algo/Node.java", "package com.example.algo;\npublic class Node {}")
            );

            Problem problem = problemRepository.save(Problem.builder()
                    .title("Graph Problem")
                    .slug("graph-algo-" + System.currentTimeMillis())
                    .description("Graph traversal algorithms")
                    .difficulty(Difficulty.HARD)
                    .status(ProblemStatus.PUBLISHED)
                    .starterTemplates(objectMapper.writeValueAsString(templates))
                    .createdBy(instructor)
                    .build());

            MvcResult result = mockMvc.perform(get("/api/v1/problems/" + problem.getId() + "/starter-template"))
                    .andExpect(status().isOk())
                    .andReturn();

            byte[] zipBytes = result.getResponse().getContentAsByteArray();

            // Verify ZIP header bytes (PK\3\4)
            assertThat(zipBytes.length).isGreaterThan(4);
            assertThat(zipBytes[0]).isEqualTo((byte) 0x50); // 'P'
            assertThat(zipBytes[1]).isEqualTo((byte) 0x4B); // 'K'
            assertThat(zipBytes[2]).isEqualTo((byte) 0x03);
            assertThat(zipBytes[3]).isEqualTo((byte) 0x04);

            int entryCount = 0;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    entryCount++;
                    assertThat(ze.getName()).doesNotContain("..");
                    assertThat(ze.getName()).doesNotStartWith("/");
                    byte[] entryBytes = zis.readAllBytes();
                    assertThat(entryBytes).isNotEmpty();
                    // Verify UTF-8 decode
                    String str = new String(entryBytes, StandardCharsets.UTF_8);
                    assertThat(str).isNotNull();
                    zis.closeEntry();
                }
            }
            assertThat(entryCount).isEqualTo(3); // README.md + Graph.java + Node.java
        }
    }

    // --- Helper Methods ---

    private Map<String, String> extractZipEntries(byte[] zipBytes) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry ze;
            byte[] buf = new byte[4096];
            while ((ze = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                map.put(ze.getName(), baos.toString(StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return map;
    }
}
