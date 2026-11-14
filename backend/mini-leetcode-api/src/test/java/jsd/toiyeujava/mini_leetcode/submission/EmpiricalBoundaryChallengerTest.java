package jsd.toiyeujava.mini_leetcode.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.exception.InvalidSubmissionPathException;
import jsd.toiyeujava.mini_leetcode.exception.PayloadTooLargeException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionArchiveResult;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionFileDto;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.producer.SubmissionProducer;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionArchiveService;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EmpiricalBoundaryChallengerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestParticipantRepository contestParticipantRepository;

    @Autowired
    private SubmissionArchiveService submissionArchiveService;

    @MockBean
    private SubmissionProducer submissionProducer;

    @MockBean
    private StorageService storageService;

    // We can also test with a mock SubmissionService for controller endpoint testing
    @MockBean
    private SubmissionService mockSubmissionService;

    private User student;
    private String studentToken;
    private Problem oopProblem;
    private Submission mockSubmission;

    @BeforeEach
    void setUp() {
        student = userRepository.save(User.builder()
                .username("challenger_student_" + System.currentTimeMillis())
                .email("challenger_" + System.currentTimeMillis() + "@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        studentToken = "Bearer " + jwtTokenProvider.generateAccessToken(student);

        oopProblem = problemRepository.save(Problem.builder()
                .title("OOP Boundary Problem")
                .slug("oop-boundary-" + System.currentTimeMillis())
                .description("Test problem for boundary challenges")
                .difficulty(Difficulty.HARD)
                .timeLimitMs(2000)
                .memoryLimitMb(256)
                .problemType(ProblemType.OOP_GRINDING)
                .status(ProblemStatus.PUBLISHED)
                .createdBy(student)
                .build());

        mockSubmission = Submission.builder()
                .id(8888L)
                .problem(oopProblem)
                .user(student)
                .language(SubmissionLanguage.JAVA)
                .codeStorageUri("submissions/" + oopProblem.getId() + "/8888.zip")
                .status(SubmissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(mockSubmissionService.createSubmission(anyLong(), anyLong(), any(SubmitCodeRequest.class)))
                .thenReturn(mockSubmission);

        when(storageService.generateSubmissionZipKey(anyLong(), anyLong()))
                .thenReturn("submissions/" + oopProblem.getId() + "/" + student.getId() + "/test.zip");

        when(storageService.getSubmissionsBucketName())
                .thenReturn("submissions");

        when(storageService.uploadFile(anyString(), anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("submissions/" + oopProblem.getId() + "/" + student.getId() + "/test.zip");
    }

    // =========================================================================
    // 1. JSON Multi-File Payload Boundary Testing
    // =========================================================================
    @Nested
    @DisplayName("1. JSON Multi-File Payload Boundary Testing")
    class JsonMultiFileBoundaryTests {

        @Test
        @DisplayName("1.1 Nested directory structures and deep 10-level folder paths")
        void testJson_NestedAndDeepFolders() throws Exception {
            String payload = """
            {
                "problemId": %d,
                "language": "JAVA",
                "files": [
                    {"path": "src/main/java/Solution.java", "content": "package src.main.java; public class Solution {}"},
                    {"path": "a/b/c/d/e/f/g/h/i/j/DeepClass.java", "content": "public class DeepClass {}"}
                ]
            }
            """.formatted(oopProblem.getId());

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Authorization", studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("1.2 Empty contents, whitespace contents, and full Unicode UTF-8 contents")
        void testJson_EmptyWhitespaceAndUnicodeUtf8() throws Exception {
            String unicodeContent = "// Tiếng Việt: Chào thế giới!\n"
                    + "// 日本語: こんにちは世界\n"
                    + "// 中文: 你好世界\n"
                    + "// Emojis: 🚀🔥🎉💻💯\n"
                    + "public class UnicodeTest {}";

            String payload = """
            {
                "problemId": %d,
                "language": "JAVA",
                "files": [
                    {"path": "Empty.java", "content": ""},
                    {"path": "Whitespace.java", "content": "   \\n\\t  \\r\\n  "},
                    {"path": "Unicode.java", "content": %s}
                ]
            }
            """.formatted(oopProblem.getId(), objectMapper.writeValueAsString(unicodeContent));

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Authorization", studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify packageFiles preserves Unicode UTF-8 exactly
            SubmissionArchiveResult result = submissionArchiveService.packageFiles(List.of(
                    new SubmissionFileDto("Empty.java", ""),
                    new SubmissionFileDto("Whitespace.java", "   \n\t  \r\n  "),
                    new SubmissionFileDto("Unicode.java", unicodeContent)
            ));
            assertThat(result.entryPaths()).containsExactlyInAnyOrder("Empty.java", "Whitespace.java", "Unicode.java");

            // Extract from zip and verify UTF-8 content
            String extracted = extractEntryFromZip(result.zipBytes(), "Unicode.java");
            assertThat(extracted).isEqualTo(unicodeContent);
        }

        @Test
        @DisplayName("1.3 Filenames with spaces, hyphens, and underscores")
        void testJson_FilenamesWithSpacesHyphensUnderscores() throws Exception {
            String payload = """
            {
                "problemId": %d,
                "language": "JAVA",
                "files": [
                    {"path": "My Solution.java", "content": "public class Solution {}"},
                    {"path": "package-one/my-custom-service.java", "content": "public class Service {}"},
                    {"path": "package_two/order_detail_dao.java", "content": "public class OrderDao {}"}
                ]
            }
            """.formatted(oopProblem.getId());

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Authorization", studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("1.4 Large file arrays: Exactly 100 entries succeeds")
        void testJson_Exactly100Entries_Succeeds() throws Exception {
            List<Map<String, String>> files = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                files.add(Map.of("path", "pkg/Class" + i + ".java", "content", "public class Class" + i + " {}"));
            }

            Map<String, Object> req = Map.of(
                    "problemId", oopProblem.getId(),
                    "language", "JAVA",
                    "files", files
            );

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Authorization", studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("1.5 Large file arrays: 101 entries fails with 400 Bad Request")
        void testJson_101Entries_FailsWith400() throws Exception {
            List<Map<String, String>> files = new ArrayList<>();
            for (int i = 1; i <= 101; i++) {
                files.add(Map.of("path", "pkg/Class" + i + ".java", "content", "public class Class" + i + " {}"));
            }

            Map<String, Object> req = Map.of(
                    "problemId", oopProblem.getId(),
                    "language", "JAVA",
                    "files", files
            );

            mockMvc.perform(post("/api/v1/submissions")
                            .header("Authorization", studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("1.6 Payload size approaching 20MB limit: 20MB + 1 byte rejected with 413 Payload Too Large")
        void testJson_PayloadOver20MB_RejectedWith413() {
            int overLimit = 20 * 1024 * 1024 + 1;
            byte[] bytes = new byte[overLimit];
            Arrays.fill(bytes, (byte) 'x');
            String hugeContent = new String(bytes, StandardCharsets.ISO_8859_1);

            List<SubmissionFileDto> files = List.of(new SubmissionFileDto("Huge.java", hugeContent));

            assertThatThrownBy(() -> submissionArchiveService.packageFiles(files))
                    .isInstanceOf(PayloadTooLargeException.class);
        }
    }

    // =========================================================================
    // 2. Multipart Upload Boundary Testing
    // =========================================================================
    @Nested
    @DisplayName("2. Multipart Upload Boundary Testing")
    class MultipartBoundaryTests {

        @Test
        @DisplayName("2.1 Valid zip file repackaging succeeds via multipart/form-data")
        void testMultipart_ValidZip_Succeeds() throws Exception {
            byte[] zipData = createZip(Map.of(
                    "Shape.java", "public abstract class Shape {}",
                    "Circle.java", "public class Circle extends Shape {}"
            ));

            MockMultipartFile zipFile = new MockMultipartFile(
                    "file",
                    "solution.zip",
                    "application/zip",
                    zipData
            );

            mockMvc.perform(multipart("/api/v1/submissions")
                            .file(zipFile)
                            .param("problemId", oopProblem.getId().toString())
                            .param("language", "JAVA")
                            .header("Authorization", studentToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("2.2 Single multipart file array (multiple .java files) succeeds")
        void testMultipart_MultipleJavaFiles_Succeeds() throws Exception {
            MockMultipartFile file1 = new MockMultipartFile(
                    "files",
                    "Shape.java",
                    "text/plain",
                    "public abstract class Shape {}".getBytes(StandardCharsets.UTF_8)
            );
            MockMultipartFile file2 = new MockMultipartFile(
                    "files",
                    "Circle.java",
                    "text/plain",
                    "public class Circle extends Shape {}".getBytes(StandardCharsets.UTF_8)
            );

            mockMvc.perform(multipart("/api/v1/submissions")
                            .file(file1)
                            .file(file2)
                            .param("problemId", oopProblem.getId().toString())
                            .param("language", "JAVA")
                            .header("Authorization", studentToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("2.3 Empty zip file (0 entries) returns 400 Bad Request")
        void testMultipart_EmptyZip_Returns400() throws Exception {
            byte[] emptyZip = createZip(Map.of());

            MockMultipartFile zipFile = new MockMultipartFile(
                    "file",
                    "empty.zip",
                    "application/zip",
                    emptyZip
            );

            mockMvc.perform(multipart("/api/v1/submissions")
                            .file(zipFile)
                            .param("problemId", oopProblem.getId().toString())
                            .param("language", "JAVA")
                            .header("Authorization", studentToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("2.4 Zero-byte raw file returns 400 Bad Request")
        void testMultipart_ZeroByteFile_Returns400() throws Exception {
            MockMultipartFile zeroByteFile = new MockMultipartFile(
                    "file",
                    "zero.zip",
                    "application/zip",
                    new byte[0]
            );

            mockMvc.perform(multipart("/api/v1/submissions")
                            .file(zeroByteFile)
                            .param("problemId", oopProblem.getId().toString())
                            .param("language", "JAVA")
                            .header("Authorization", studentToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("2.5 Multiple files with the same name in files array returns 400 Bad Request")
        void testMultipart_DuplicateFilenamesInFiles_Returns400() throws Exception {
            MockMultipartFile file1 = new MockMultipartFile(
                    "files",
                    "Solution.java",
                    "text/plain",
                    "public class Solution {}".getBytes(StandardCharsets.UTF_8)
            );
            MockMultipartFile file2 = new MockMultipartFile(
                    "files",
                    "Solution.java",
                    "text/plain",
                    "public class SolutionAgain {}".getBytes(StandardCharsets.UTF_8)
            );

            mockMvc.perform(multipart("/api/v1/submissions")
                            .file(file1)
                            .file(file2)
                            .param("problemId", oopProblem.getId().toString())
                            .param("language", "JAVA")
                            .header("Authorization", studentToken))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // 3. Delimiter Syntax Boundary Testing
    // =========================================================================
    @Nested
    @DisplayName("3. Delimiter Syntax Boundary Testing")
    class DelimiterSyntaxBoundaryTests {

        @Test
        @DisplayName("3.1 Varying newline combinations (\\r\\n vs \\n) in delimiter bundles")
        void testDelimiter_VaryingNewlines() {
            String crlfCode = "// === FILE: A.java ===\r\npublic class A {}\r\n"
                    + "// === FILE: B.java ===\r\npublic class B {}\r\n";

            SubmissionArchiveResult crlfResult = submissionArchiveService.packageDelimitedCode(crlfCode, SubmissionLanguage.JAVA);
            assertThat(crlfResult.entryPaths()).containsExactlyInAnyOrder("A.java", "B.java");

            String lfCode = "// === FILE: C.java ===\npublic class C {}\n"
                    + "// === FILE: D.java ===\npublic class D {}\n";

            SubmissionArchiveResult lfResult = submissionArchiveService.packageDelimitedCode(lfCode, SubmissionLanguage.JAVA);
            assertThat(lfResult.entryPaths()).containsExactlyInAnyOrder("C.java", "D.java");

            // Mixed CRLF and LF in the same bundle
            String mixedCode = "// === FILE: M1.java ===\r\npublic class M1 {}\n"
                    + "// === FILE: M2.java ===\npublic class M2 {}\r\n";

            SubmissionArchiveResult mixedResult = submissionArchiveService.packageDelimitedCode(mixedCode, SubmissionLanguage.JAVA);
            assertThat(mixedResult.entryPaths()).containsExactlyInAnyOrder("M1.java", "M2.java");
        }

        @Test
        @DisplayName("3.2 Leading comments before first delimiter are omitted, trailing comments are kept in last file")
        void testDelimiter_LeadingAndTrailingComments() throws IOException {
            String code = "// Header Comment: Author\n"
                    + "// Copyright 2026\n"
                    + "// === FILE: Main.java ===\n"
                    + "public class Main {}\n"
                    + "// Trailing comment after Main\n";

            SubmissionArchiveResult result = submissionArchiveService.packageDelimitedCode(code, SubmissionLanguage.JAVA);
            assertThat(result.entryPaths()).containsExactly("Main.java");

            String mainContent = extractEntryFromZip(result.zipBytes(), "Main.java");
            assertThat(mainContent).doesNotContain("Header Comment");
            assertThat(mainContent).contains("public class Main {}");
            assertThat(mainContent).contains("// Trailing comment after Main");
        }

        @Test
        @DisplayName("3.3 Duplicate delimiter file paths throws InvalidSubmissionPathException")
        void testDelimiter_DuplicateMarkers_ThrowsException() {
            String duplicateCode = "// === FILE: Solution.java ===\n"
                    + "public class Solution {}\n"
                    + "// === FILE: Solution.java ===\n"
                    + "public class Solution2 {}\n";

            assertThatThrownBy(() -> submissionArchiveService.packageDelimitedCode(duplicateCode, SubmissionLanguage.JAVA))
                    .isInstanceOf(InvalidSubmissionPathException.class)
                    .hasMessageContaining("Duplicate entry path");
        }
    }

    // =========================================================================
    // 4. Critical Architecture Verification (Real SubmissionServiceImpl Check)
    // =========================================================================
    @Nested
    @DisplayName("4. Empirical Investigation of Multipart ZIP vs SubmissionServiceImpl")
    class RealServiceInvestigationTests {

        @Test
        @DisplayName("Direct test: SubmissionServiceImpl.createSubmission with empty files and null code throws IllegalArgumentException")
        void testRealService_EmptyFilesAndNullCode_ThrowsException() {
            // Instantiate real SubmissionServiceImpl
            SubmissionServiceImpl realService = new SubmissionServiceImpl(
                    submissionRepository,
                    problemRepository,
                    userRepository,
                    storageService,
                    contestRepository,
                    contestParticipantRepository,
                    submissionArchiveService
            );

            // This is the exact SubmitCodeRequest constructed by SubmissionController line 219 when a ZIP file is uploaded:
            SubmitCodeRequest requestForZipUpload = new SubmitCodeRequest(
                    oopProblem.getId(),
                    SubmissionLanguage.JAVA,
                    null,       // code is null
                    List.of(),  // files is empty!
                    null,
                    false
            );

            // Observe what happens when real service receives this request:
            assertThatThrownBy(() -> realService.createSubmission(oopProblem.getId(), student.getId(), requestForZipUpload))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Code must not be blank");
        }
    }

    // --- Helpers ---

    private byte[] createZip(Map<String, String> entries) throws IOException {
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

    private String extractEntryFromZip(byte[] zipBytes, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry ze;
            byte[] buf = new byte[4096];
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(entryName)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }
                    return baos.toString(StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }
        return null;
    }
}
