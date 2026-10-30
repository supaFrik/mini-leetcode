package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.dto.AuthResponse;
import jsd.toiyeujava.mini_leetcode.problem.dto.CreateProblemRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.RefreshTokenRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.RegisterRequest;
import jsd.toiyeujava.mini_leetcode.problem.dto.UpdateProblemRequest;
import jsd.toiyeujava.mini_leetcode.entity.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.entity.RefreshToken;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.exception.InvalidTokenException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.RefreshTokenRepository;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.auth.service.AuthService;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemService;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.testcase.service.TestCaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdversarialReviewerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private TestCaseService testCaseService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private StorageService storageService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.when(storageService.uploadFile(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(java.io.InputStream.class), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> "minio://testcases/" + invocation.getArgument(0));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        testCaseRepository.deleteAll();
        problemRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Adversarial Test 1: Concurrent refresh with identical token - only 1 succeeds, other rejected with 401")
    void testConcurrentRefreshTokenRotation() throws Exception {
        // Register user
        RegisterRequest registerReq = new RegisterRequest("concurrent_user", "concurrent@example.com", "Password123!", Role.USER);
        AuthResponse authRes = authService.register(registerReq);
        String sharedRefreshToken = authRes.refreshToken();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Throwable> failureReason = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    authService.refresh(new RefreshTokenRequest(sharedRefreshToken));
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                    failureReason.set(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
        assertThat(failureReason.get()).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Adversarial Test 2: Blocked user cannot use existing valid access token on protected endpoints")
    void testBlockedUserCannotUseValidJwtAccessTokens() throws Exception {
        User instructor = userRepository.save(User.builder()
                .username("active_to_blocked")
                .email("blocked_jwt@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        String accessToken = jwtTokenProvider.generateAccessToken(instructor);

        // User is currently ACTIVE: should succeed
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Active Problem", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isCreated());

        // Now block the user in database
        User user = userRepository.findByUsername("active_to_blocked").orElseThrow();
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        // Same unexpired token must now be rejected with 401 Unauthorized
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Blocked Problem", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Adversarial Test 3: Token reuse detection revokes all tokens of compromised user (RFC 6819)")
    void testRevokedTokenReuseTriggersFamilyRevocation() {
        RegisterRequest registerReq = new RegisterRequest("reuse_victim", "reuse_victim@example.com", "Password123!", Role.USER);
        AuthResponse auth1 = authService.register(registerReq);
        String tokenR1 = auth1.refreshToken();

        // Normal rotation: R1 -> R2
        AuthResponse auth2 = authService.refresh(new RefreshTokenRequest(tokenR1));
        String tokenR2 = auth2.refreshToken();
        assertThat(tokenR2).isNotEqualTo(tokenR1);

        // Attack: Re-presenting already rotated token R1
        try {
            authService.refresh(new RefreshTokenRequest(tokenR1));
        } catch (InvalidTokenException expected) {
            assertThat(expected.getMessage()).contains("revoked");
        }

        // Family revocation: R2 must now ALSO be revoked automatically!
        try {
            authService.refresh(new RefreshTokenRequest(tokenR2));
        } catch (InvalidTokenException expected) {
            assertThat(expected.getMessage()).contains("revoked");
        }
    }

    @Test
    @DisplayName("Adversarial Test 4: deleteTestCase physically deletes input and output files from StorageService")
    void testDeleteTestCaseCleansUpStorageFiles() {
        User creator = userRepository.save(User.builder()
                .username("tc_owner")
                .email("tc_owner@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        Problem problem = problemRepository.save(Problem.builder()
                .title("Storage Test Problem")
                .slug("storage-test-problem")
                .description("Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(creator)
                .build());

        TestCase tc = testCaseRepository.save(TestCase.builder()
                .problem(problem)
                .inputFileUri("minio://testcases/problems/1/testcases/0_input_abc.txt")
                .expectedOutputUri("minio://testcases/problems/1/testcases/0_output_abc.txt")
                .fileSizeBytes(128L)
                .isHidden(false)
                .orderIndex(0)
                .build());

        testCaseService.deleteTestCase(problem.getId(), tc.getId());

        verify(storageService).deleteFileByUri(eq("minio://testcases/problems/1/testcases/0_input_abc.txt"));
        verify(storageService).deleteFileByUri(eq("minio://testcases/problems/1/testcases/0_output_abc.txt"));
        assertThat(testCaseRepository.existsById(tc.getId())).isFalse();
    }

    @Test
    @DisplayName("Adversarial Test 5: Vietnamese 'đ' and 'Đ' letters properly normalized to 'd' in slug")
    void testVietnameseSlugNormalization() {
        assertThat(ProblemService.toSlug("Đếm Số Lượng Đảo")).isEqualTo("dem-so-luong-dao");
        assertThat(ProblemService.toSlug("Đường Đi Ngắn Nhất")).isEqualTo("duong-di-ngan-nhat");
        assertThat(ProblemService.toSlug("Cây Khung Nhỏ Nhất")).isEqualTo("cay-khung-nho-nhat");
        assertThat(ProblemService.toSlug("Tìm Đường")).isEqualTo("tim-duong");
    }

    @Test
    @DisplayName("Adversarial Test 6: Problem slug that is numeric can still be resolved when ID does not match")
    void testNumericSlugFallbackInController() throws Exception {
        User creator = userRepository.save(User.builder()
                .username("numeric_slug_user")
                .email("num_slug@example.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        Problem problem = problemRepository.save(Problem.builder()
                .title("777")
                .slug("777")
                .description("Problem with numeric title")
                .difficulty(Difficulty.MEDIUM)
                // Bài phải PUBLISHED để người dùng thường đọc được (bài chưa
                // publish bị ẩn khỏi các endpoint công khai).
                .status(ProblemStatus.PUBLISHED)
                .createdBy(creator)
                .build());

        // Even though problem.getId() != 777, querying /api/v1/problems/777 should find it by slug
        mockMvc.perform(get("/api/v1/problems/777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("777"))
                .andExpect(jsonPath("$.title").value("777"));
    }

    @Test
    @DisplayName("Adversarial Test 7: Invalid enum query param returns RFC 7807 400 Bad Request, not 500")
    void testTypeMismatchReturnsRfc7807ProblemDetail400() throws Exception {
        mockMvc.perform(get("/api/v1/problems")
                        .param("difficulty", "EXTREME_SUPER_HARD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.type").value("https://api.mini-leetcode.com/errors/bad-request"));
    }

    @Test
    @DisplayName("Adversarial Test 8: Public registration with role=ADMIN is forced to USER and denied instructor privileges")
    void testPublicRegistrationPrivilegeEscalationPrevented() throws Exception {
        String payload = """
                {
                    "username": "wannabe_admin",
                    "email": "wannabe_admin@example.com",
                    "password": "Password123!",
                    "role": "ADMIN"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();

        // Attempting to create a problem with this token must return 403 Forbidden
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Hacked Problem", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("Adversarial Test 9: deleteProblem deletes all associated MinIO testcase files")
    void testProblemDeletionCleansUpAllTestcaseMinioFiles() {
        User creator = userRepository.save(User.builder()
                .username("prob_owner")
                .email("prob_owner@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        Problem problem = problemRepository.save(Problem.builder()
                .title("Problem With Testcases")
                .slug("problem-with-testcases")
                .description("Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(creator)
                .build());

        TestCase tc1 = testCaseRepository.save(TestCase.builder()
                .problem(problem)
                .inputFileUri("minio://testcases/problems/99/testcases/0_input_111.txt")
                .expectedOutputUri("minio://testcases/problems/99/testcases/0_output_111.txt")
                .fileSizeBytes(100L)
                .isHidden(false)
                .orderIndex(0)
                .build());

        TestCase tc2 = testCaseRepository.save(TestCase.builder()
                .problem(problem)
                .inputFileUri("minio://testcases/problems/99/testcases/1_input_222.txt")
                .expectedOutputUri("minio://testcases/problems/99/testcases/1_output_222.txt")
                .fileSizeBytes(200L)
                .isHidden(true)
                .orderIndex(1)
                .build());

        problemService.deleteProblem(problem.getId());

        verify(storageService).deleteFileByUri(eq("minio://testcases/problems/99/testcases/0_input_111.txt"));
        verify(storageService).deleteFileByUri(eq("minio://testcases/problems/99/testcases/0_output_111.txt"));
        verify(storageService).deleteFileByUri(eq("minio://testcases/problems/99/testcases/1_input_222.txt"));
        verify(storageService).deleteFileByUri(eq("minio://testcases/problems/99/testcases/1_output_222.txt"));

        assertThat(problemRepository.existsById(problem.getId())).isFalse();
        assertThat(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId())).isEmpty();
    }

    @Test
    @DisplayName("Adversarial Test 10: Invalid sort property returns RFC 7807 400 Bad Request, not 500")
    void testInvalidSortPropertyReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/problems")
                        .param("sort", "nonExistentField,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Adversarial Test 11: Malformed JSON body returns RFC 7807 400 Bad Request, not 500")
    void testMalformedJsonBodyReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ unclosed-json: "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"));
    }

    @Test
    @DisplayName("Adversarial Test 12: Updating problem with blank title returns RFC 7807 400 Bad Request")
    void testBlankTitleProblemUpdateReturns400ProblemDetail() throws Exception {
        User admin = userRepository.save(User.builder()
                .username("admin_updater")
                .email("admin_updater@example.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        String adminToken = jwtTokenProvider.generateAccessToken(admin);

        Problem problem = problemRepository.save(Problem.builder()
                .title("Valid Title")
                .slug("valid-title")
                .description("Valid Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(admin)
                .build());

        mockMvc.perform(put("/api/v1/problems/" + problem.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProblemRequest("   ", null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Problem title cannot be blank"));
    }

    @Test
    @DisplayName("Adversarial Test 13: Negative page returns 400 and excessive page size is clamped to max 100")
    void testPaginationPageSizeCappedAndNegativePageReturns400() throws Exception {
        // Negative page -> 400 Bad Request
        mockMvc.perform(get("/api/v1/problems").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Excessive page size -> clamped to 100
        mockMvc.perform(get("/api/v1/problems").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    @DisplayName("Adversarial Test 14: SQL Injection payloads in search query are safely parameterized")
    void testSqlInjectionInSearchSafe() throws Exception {
        mockMvc.perform(get("/api/v1/problems")
                        .param("search", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mockMvc.perform(get("/api/v1/problems")
                        .param("search", "'; DROP TABLE problems; --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("Adversarial Test 15: Token for deleted user does not crash servlet filter and proceeds unauthenticated")
    void testDeletedUserTokenDoesNotCrashFilter() throws Exception {
        User tempUser = userRepository.save(User.builder()
                .username("deleted_user")
                .email("deleted@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        String token = jwtTokenProvider.generateAccessToken(tempUser);

        // Delete user from repository
        userRepository.delete(tempUser);

        // 1. Accessing public endpoint with stale token must NOT crash with 500/ServletException
        mockMvc.perform(get("/api/v1/problems")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 2. Accessing protected endpoint with stale token must return 401 Unauthorized ProblemDetail
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Title", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("Adversarial Test 16: Uploading testcase with negative orderIndex returns HTTP 400 ProblemDetail")
    void testNegativeOrderIndexTestCaseUploadReturns400() throws Exception {
        User instructor = userRepository.save(User.builder()
                .username("tc_instructor")
                .email("tc_inst@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        String token = jwtTokenProvider.generateAccessToken(instructor);

        Problem problem = problemRepository.save(Problem.builder()
                .title("Negative TC Problem")
                .slug("negative-tc-problem")
                .description("Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(instructor)
                .build());

        org.springframework.mock.web.MockMultipartFile in = new org.springframework.mock.web.MockMultipartFile("inputFile", "in.txt", "text/plain", "1".getBytes());
        org.springframework.mock.web.MockMultipartFile out = new org.springframework.mock.web.MockMultipartFile("outputFile", "out.txt", "text/plain", "2".getBytes());

        mockMvc.perform(multipart("/api/v1/problems/" + problem.getId() + "/test-cases")
                        .file(in)
                        .file(out)
                        .param("orderIndex", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Order index must not be negative"));
    }

    @Test
    @DisplayName("Adversarial Test 17: Uploading testcases without orderIndex assigns clean sequential indexes")
    void testSequentialAutoOrderIndexForTestCases() throws Exception {
        User instructor = userRepository.save(User.builder()
                .username("seq_instructor")
                .email("seq_inst@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        String token = jwtTokenProvider.generateAccessToken(instructor);

        Problem problem = problemRepository.save(Problem.builder()
                .title("Sequential TC Problem")
                .slug("sequential-tc-problem")
                .description("Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(instructor)
                .build());

        org.springframework.mock.web.MockMultipartFile in1 = new org.springframework.mock.web.MockMultipartFile("inputFile", "in1.txt", "text/plain", "1".getBytes());
        org.springframework.mock.web.MockMultipartFile out1 = new org.springframework.mock.web.MockMultipartFile("outputFile", "out1.txt", "text/plain", "1".getBytes());

        org.springframework.mock.web.MockMultipartFile in2 = new org.springframework.mock.web.MockMultipartFile("inputFile", "in2.txt", "text/plain", "2".getBytes());
        org.springframework.mock.web.MockMultipartFile out2 = new org.springframework.mock.web.MockMultipartFile("outputFile", "out2.txt", "text/plain", "2".getBytes());

        mockMvc.perform(multipart("/api/v1/problems/" + problem.getId() + "/test-cases")
                        .file(in1)
                        .file(out1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderIndex").value(0));

        mockMvc.perform(multipart("/api/v1/problems/" + problem.getId() + "/test-cases")
                        .file(in2)
                        .file(out2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderIndex").value(1));
    }

    @Test
    @DisplayName("Adversarial Test 18: Missing multipart file part returns RFC 7807 400 Bad Request")
    void testMissingMultipartPartReturns400ProblemDetail() throws Exception {
        User instructor = userRepository.save(User.builder()
                .username("multipart_instructor")
                .email("multipart_inst@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        String token = jwtTokenProvider.generateAccessToken(instructor);

        Problem problem = problemRepository.save(Problem.builder()
                .title("Missing Part Problem")
                .slug("missing-part-problem")
                .description("Desc")
                .difficulty(Difficulty.EASY)
                .createdBy(instructor)
                .build());

        org.springframework.mock.web.MockMultipartFile in = new org.springframework.mock.web.MockMultipartFile("inputFile", "in.txt", "text/plain", "1".getBytes());

        // Only inputFile provided, outputFile is missing from multipart form data
        mockMvc.perform(multipart("/api/v1/problems/" + problem.getId() + "/test-cases")
                        .file(in)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Adversarial Test 19: Expired or tampered JWT access token on protected endpoint returns 401 ProblemDetail")
    void testExpiredOrTamperedAccessTokenReturns401() throws Exception {
        User instructor = userRepository.save(User.builder()
                .username("tamper_instructor")
                .email("tamper_inst@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        String validToken = jwtTokenProvider.generateAccessToken(instructor);
        String tamperedToken = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "forgedSignature";

        // Tampered token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + tamperedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Title", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        // Malformed token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer not.a.valid.jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Title", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Empty Bearer -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProblemRequest("Title", "Desc", Difficulty.EASY, 1000, 256, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
