package jsd.toiyeujava.minileetcode.plugin.client;

import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemType;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit and integration test suite for Mini LeetCode Slice 4 ApiClient endpoints:
 * <ul>
 *     <li>{@code GET /api/v1/problems/{idOrSlug}/starter-template} (binary ZIP download)</li>
 *     <li>{@code GET /api/v1/problems/{idOrSlug}} (problem metadata and description JSON retrieval)</li>
 * </ul>
 * <p>
 * Constraints verified:
 * <ul>
 *     <li>Zero GUI / IntelliJ Platform UI thread dependencies (runs headlessly under Gradle).</li>
 *     <li>Simulated ZIP bytes verified with PK\003\004 magic header and byte-for-byte equality.</li>
 *     <li>Full field deserialization of ProblemDto (id, title, slug, description, difficulty, type, tags).</li>
 *     <li>RFC 7807 error translation: 401 PAT_INVALID -&gt; AuthenticationException, 404 -&gt; MiniLeetCodeApiException.</li>
 *     <li>Network timeout, disconnect, and server 500 fault tolerance.</li>
 *     <li>Header correctness (Accept, User-Agent, Authorization Bearer &lt;pat&gt; present vs omitted).</li>
 *     <li>URL encoding for slugs containing spaces and special characters.</li>
 *     <li>Multi-threaded concurrency safety.</li>
 * </ul>
 */
class ApiClientSlice4Test {

    private MockWebServer server;
    private ApiClient client;
    private static final String TEST_PAT = "mlc_pat_slice4test1234567890abcdef12345678";

    /**
     * Simulated authentic ZIP archive bytes starting with local file header signature PK\003\004 (0x50, 0x4B, 0x03, 0x04).
     */
    private static final byte[] SIMULATED_ZIP_BYTES = new byte[]{
            0x50, 0x4B, 0x03, 0x04, // Local file header signature PK\003\004
            0x14, 0x00,             // Version needed to extract (2.0)
            0x00, 0x00,             // General purpose bit flag
            0x08, 0x00,             // Compression method (deflated)
            0x21, 0x54, 0x55, 0x46, // Mod time & date
            0x12, 0x34, 0x56, 0x78, // CRC-32
            0x1A, 0x00, 0x00, 0x00, // Compressed size
            0x2A, 0x00, 0x00, 0x00, // Uncompressed size
            0x0D, 0x00,             // Filename length (13 bytes)
            0x00, 0x00,             // Extra field length
            // Filename: "Solution.java"
            'S', 'o', 'l', 'u', 't', 'i', 'o', 'n', '.', 'j', 'a', 'v', 'a',
            // Simulated compressed data stream payload
            0x78, (byte) 0x9C, 0x2B, 0x4E, 0x4D, 0x2C, 0x49, 0x55, 0x00, 0x00,
            // Central directory header signature PK\001\002
            0x50, 0x4B, 0x01, 0x02,
            0x14, 0x00, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00,
            // End of central directory record PK\005\006
            0x50, 0x4B, 0x05, 0x06,
            0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x3F, 0x00, 0x00, 0x00, 0x2B, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        client = new ApiClient(baseUrl, TEST_PAT, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    @Nested
    @DisplayName("1. Binary Starter Template Download Tests (GET /api/v1/problems/{idOrSlug}/starter-template)")
    class StarterTemplateDownloadTests {

        @Test
        @DisplayName("1.1 testDownloadStarterTemplate_Success_BySlug_ReturnsSimulatedZipWithPkHeader")
        void testDownloadStarterTemplate_Success_BySlug_ReturnsSimulatedZipWithPkHeader() throws InterruptedException {
            Buffer buffer = new Buffer().write(SIMULATED_ZIP_BYTES);

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setHeader("Content-Disposition", "attachment; filename=\"mini-bank-stage-1-starter.zip\"")
                    .setBody(buffer));

            byte[] actualBytes = client.getStarterTemplate("mini-bank-stage-1");

            assertNotNull(actualBytes, "Downloaded starter template bytes must not be null");
            assertArrayEquals(SIMULATED_ZIP_BYTES, actualBytes, "Byte array must match simulated zip payload byte-for-byte");

            // Verify PK\003\004 magic header bytes
            assertTrue(actualBytes.length >= 4, "ZIP archive must contain at least 4 header bytes");
            assertEquals(0x50, actualBytes[0] & 0xFF, "Magic byte 0 must be 'P' (0x50)");
            assertEquals(0x4B, actualBytes[1] & 0xFF, "Magic byte 1 must be 'K' (0x4B)");
            assertEquals(0x03, actualBytes[2] & 0xFF, "Magic byte 2 must be 0x03");
            assertEquals(0x04, actualBytes[3] & 0xFF, "Magic byte 3 must be 0x04");

            // Verify request metadata
            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req, "RecordedRequest must not be null");
            assertEquals("GET", req.getMethod(), "HTTP method must be GET");
            assertEquals("/api/v1/problems/mini-bank-stage-1/starter-template", req.getPath());
            assertEquals("application/zip", req.getHeader("Accept"), "Must negotiate Accept: application/zip");
            assertEquals("Bearer " + TEST_PAT, req.getHeader("Authorization"), "Must provide Bearer PAT");
            assertEquals("MiniLeetCode-IntelliJ-Plugin/0.1.0", req.getHeader("User-Agent"));
        }

        @Test
        @DisplayName("1.2 testDownloadStarterTemplate_Success_ById_ReturnsSimulatedZip")
        void testDownloadStarterTemplate_Success_ById_ReturnsSimulatedZip() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(SIMULATED_ZIP_BYTES)));

            byte[] actualBytes = client.getStarterTemplate("101");

            assertNotNull(actualBytes);
            assertArrayEquals(SIMULATED_ZIP_BYTES, actualBytes);

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("GET", req.getMethod());
            assertEquals("/api/v1/problems/101/starter-template", req.getPath());
        }

        @Test
        @DisplayName("1.3 testDownloadStarterTemplate_UrlEncoding_EncodesSpecialCharsAndSpaces")
        void testDownloadStarterTemplate_UrlEncoding_EncodesSpecialCharsAndSpaces() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(SIMULATED_ZIP_BYTES)));

            byte[] actualBytes = client.getStarterTemplate("oop grinding/bank stage 1");

            assertNotNull(actualBytes);

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("/api/v1/problems/oop%20grinding%2Fbank%20stage%201/starter-template", req.getPath(),
                    "Slug containing spaces and slashes must be properly percent-encoded");
        }

        @Test
        @DisplayName("1.4 testDownloadStarterTemplate_WithoutPat_OmitsAuthorizationHeader")
        void testDownloadStarterTemplate_WithoutPat_OmitsAuthorizationHeader() throws InterruptedException {
            ApiClient noPatClient = new ApiClient(server.url("/").toString(), null);

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(SIMULATED_ZIP_BYTES)));

            byte[] actualBytes = noPatClient.getStarterTemplate("mini-bank-stage-1");

            assertNotNull(actualBytes);

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertNull(req.getHeader("Authorization"), "Authorization header must be omitted when PAT is null");
        }

        @Test
        @DisplayName("1.5 testDownloadStarterTemplate_Unauthorized_401_ThrowsAuthenticationException")
        void testDownloadStarterTemplate_Unauthorized_401_ThrowsAuthenticationException() {
            String errorJson = """
                    {
                      "type": "https://api.mini-leetcode.com/errors/unauthorized",
                      "title": "Authentication Failed",
                      "status": 401,
                      "detail": "Personal access token is invalid or expired",
                      "code": "PAT_INVALID",
                      "timestamp": "2026-11-11T13:00:00Z"
                    }
                    """;

            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody(errorJson));

            AuthenticationException ex = assertThrows(AuthenticationException.class,
                    () -> client.getStarterTemplate("mini-bank-stage-1"));

            assertEquals(401, ex.getStatusCode(), "Status code must be 401");
            assertEquals("PAT_INVALID", ex.getCode(), "Error code must match PAT_INVALID");
            assertTrue(ex.getMessage().contains("invalid or expired"), "Message should contain detail from RFC 7807 response");
            assertNotNull(ex.getProblemDetail(), "RFC 7807 problem detail must be preserved");
            assertEquals("https://api.mini-leetcode.com/errors/unauthorized", ex.getProblemDetail().type());
        }

        @Test
        @DisplayName("1.6 testDownloadStarterTemplate_NotFound_404_ThrowsMiniLeetCodeApiException")
        void testDownloadStarterTemplate_NotFound_404_ThrowsMiniLeetCodeApiException() {
            String errorJson = """
                    {
                      "type": "https://api.mini-leetcode.com/errors/not-found",
                      "title": "Not Found",
                      "status": 404,
                      "detail": "Problem not found with slug: nonexistent-problem",
                      "code": "PROBLEM_NOT_FOUND"
                    }
                    """;

            server.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody(errorJson));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> client.getStarterTemplate("nonexistent-problem"));

            assertEquals(404, ex.getStatusCode(), "Status code must be 404");
            assertEquals("PROBLEM_NOT_FOUND", ex.getCode());
            assertTrue(ex.getMessage().contains("nonexistent-problem"));
        }

        @Test
        @DisplayName("1.7 testDownloadStarterTemplate_ServerError_500_ThrowsMiniLeetCodeApiException")
        void testDownloadStarterTemplate_ServerError_500_ThrowsMiniLeetCodeApiException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Internal Server Error"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> client.getStarterTemplate("mini-bank-stage-1"));

            assertEquals(500, ex.getStatusCode(), "Status code must be 500");
            assertTrue(ex.getMessage().contains("500"), "Error message must indicate HTTP 500");
        }

        @Test
        @DisplayName("1.8 testDownloadStarterTemplate_NetworkTimeout_ThrowsMiniLeetCodeApiException")
        void testDownloadStarterTemplate_NetworkTimeout_ThrowsMiniLeetCodeApiException() {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            ApiClient fastTimeoutClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofMillis(200));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> fastTimeoutClient.getStarterTemplate("mini-bank-stage-1"));

            assertNotNull(ex);
            assertTrue(ex.getMessage().toLowerCase().contains("timeout")
                            || (ex.getCause() != null && ex.getCause().getClass().getSimpleName().contains("Timeout")),
                    "Exception must indicate network timeout");
        }

        @Test
        @DisplayName("1.9 testDownloadStarterTemplate_NetworkDisconnect_ThrowsMiniLeetCodeApiException")
        void testDownloadStarterTemplate_NetworkDisconnect_ThrowsMiniLeetCodeApiException() {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> client.getStarterTemplate("mini-bank-stage-1"));

            assertNotNull(ex);
            assertNotNull(ex.getCause(), "Root cause (IOException) must be preserved");
        }

        @Test
        @DisplayName("1.10 testDownloadStarterTemplate_InvalidInput_NullOrBlank_ThrowsIllegalArgumentException")
        void testDownloadStarterTemplate_InvalidInput_NullOrBlank_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate(null));
            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate(""));
            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate("   "));
        }
    }

    @Nested
    @DisplayName("2. Problem Details Retrieval Tests (GET /api/v1/problems/{idOrSlug})")
    class ProblemDetailsRetrievalTests {

        @Test
        @DisplayName("2.1 testGetProblem_Success_AlgorithmicProblem_DeserializesAllFields")
        void testGetProblem_Success_AlgorithmicProblem_DeserializesAllFields() throws InterruptedException {
            String json = """
                    {
                      "id": 101,
                      "title": "Two Sum",
                      "slug": "two-sum",
                      "description": "# Two Sum\\n\\nGiven an array of integers `nums` and an integer `target`, return indices of the two numbers.",
                      "difficulty": "EASY",
                      "type": "ALGORITHMIC",
                      "timeLimitMs": 1000,
                      "memoryLimitMb": 256,
                      "status": "PUBLISHED",
                      "tags": ["Array", "Hash Table"],
                      "starterTemplates": "[{\\"name\\":\\"Solution.java\\",\\"content\\":\\"public class Solution {}\\"}]",
                      "testCaseCount": 3,
                      "createdById": 1,
                      "createdByUsername": "instructor_alice",
                      "createdAt": "2026-11-01T10:00:00",
                      "updatedAt": "2026-11-02T12:00:00",
                      "progress": "IN_PROGRESS"
                    }
                    """;

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(json));

            ProblemDto problem = client.getProblem("two-sum");

            assertNotNull(problem, "ProblemDto must not be null");
            assertEquals(101L, problem.id());
            assertEquals("Two Sum", problem.title());
            assertEquals("two-sum", problem.slug());
            assertTrue(problem.description().contains("Two Sum"), "Description must contain markdown title");
            assertTrue(problem.description().contains("Given an array of integers"));
            assertEquals(Difficulty.EASY, problem.difficulty());
            assertEquals("Easy", problem.difficulty().getDisplayName());
            assertEquals(ProblemType.ALGORITHMIC, problem.type());
            assertFalse(problem.type().isOopGrinding());
            assertEquals(1000, problem.timeLimitMs());
            assertEquals(256, problem.memoryLimitMb());
            assertEquals(List.of("Array", "Hash Table"), problem.tags());
            assertTrue(problem.hasTags());
            assertTrue(problem.hasStarterTemplates());
            assertEquals("PUBLISHED", problem.status());
            assertTrue(problem.isPublished());
            assertEquals("IN_PROGRESS", problem.progress());
            assertTrue(problem.isInProgress());
            assertFalse(problem.isDone());
            assertEquals(3L, problem.testCaseCount());

            // Bean getter aliases compatibility
            assertEquals(101L, problem.getId());
            assertEquals("Two Sum", problem.getTitle());
            assertEquals("two-sum", problem.getSlug());
            assertEquals(Difficulty.EASY, problem.getDifficulty());
            assertEquals(ProblemType.ALGORITHMIC, problem.getProblemType());

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("GET", req.getMethod());
            assertEquals("/api/v1/problems/two-sum", req.getPath());
            assertEquals("application/json", req.getHeader("Accept"));
            assertEquals("Bearer " + TEST_PAT, req.getHeader("Authorization"));
            assertEquals("MiniLeetCode-IntelliJ-Plugin/0.1.0", req.getHeader("User-Agent"));
        }

        @Test
        @DisplayName("2.2 testGetProblem_Success_OopGrindingProblem_DeserializesDifficultyAndType")
        void testGetProblem_Success_OopGrindingProblem_DeserializesDifficultyAndType() throws InterruptedException {
            String json = """
                    {
                      "id": 202,
                      "title": "Tài khoản đơn",
                      "slug": "mini-bank-stage-1",
                      "description": "# Stage 1: Thiết kế Account\\n\\nYêu cầu cài đặt thuộc tính balance, hàm nạp và rút tiền.",
                      "difficulty": "MEDIUM",
                      "type": "OOP_GRINDING",
                      "status": "PUBLISHED",
                      "tags": ["OOP", "Java", "Encapsulation"],
                      "timeLimitMs": 2000,
                      "memoryLimitMb": 512
                    }
                    """;

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(json));

            ProblemDto problem = client.getProblem("mini-bank-stage-1");

            assertNotNull(problem);
            assertEquals(202L, problem.id());
            assertEquals("Tài khoản đơn", problem.title());
            assertEquals("mini-bank-stage-1", problem.slug());
            assertEquals(Difficulty.MEDIUM, problem.difficulty());
            assertEquals("Medium", problem.difficulty().getDisplayName());
            assertEquals(ProblemType.OOP_GRINDING, problem.type());
            assertTrue(problem.type().isOopGrinding());
            assertTrue(problem.isOopGrinding());
            assertEquals(List.of("OOP", "Java", "Encapsulation"), problem.tags());

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("/api/v1/problems/mini-bank-stage-1", req.getPath());
        }

        @Test
        @DisplayName("2.3 testGetProblem_Success_ById_RequestsNumericPath")
        void testGetProblem_Success_ById_RequestsNumericPath() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": 101,
                              "title": "Account Class",
                              "slug": "mini-bank-stage-1",
                              "description": "Design an Account class"
                            }
                            """));

            ProblemDto problem = client.getProblem("101");

            assertNotNull(problem);
            assertEquals(101L, problem.id());

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("/api/v1/problems/101", req.getPath());
        }

        @Test
        @DisplayName("2.4 testGetProblem_UrlEncoding_EncodesSpecialCharsAndSpaces")
        void testGetProblem_UrlEncoding_EncodesSpecialCharsAndSpaces() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": 303,
                              "title": "OOP Grinding Stage 1",
                              "slug": "oop grinding stage 1",
                              "description": "Problem with spaces"
                            }
                            """));

            ProblemDto problem = client.getProblem("oop grinding stage 1");

            assertNotNull(problem);
            assertEquals("oop grinding stage 1", problem.slug());

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("/api/v1/problems/oop%20grinding%20stage%201", req.getPath(),
                    "Problem slug with spaces must be encoded as %20");
        }

        @Test
        @DisplayName("2.5 testGetProblem_WithoutPat_OmitsAuthorizationHeader")
        void testGetProblem_WithoutPat_OmitsAuthorizationHeader() throws InterruptedException {
            ApiClient noPatClient = new ApiClient(server.url("/").toString(), null);

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": 101,
                              "title": "Two Sum",
                              "slug": "two-sum",
                              "description": "Desc"
                            }
                            """));

            ProblemDto problem = noPatClient.getProblem("two-sum");

            assertNotNull(problem);

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertNull(req.getHeader("Authorization"), "Authorization header must be omitted when PAT is null");
        }

        @Test
        @DisplayName("2.6 testGetProblem_Unauthorized_401_ThrowsAuthenticationException")
        void testGetProblem_Unauthorized_401_ThrowsAuthenticationException() {
            String errorJson = """
                    {
                      "type": "https://api.mini-leetcode.com/errors/unauthorized",
                      "title": "Authentication Failed",
                      "status": 401,
                      "detail": "PAT is required to access problem details",
                      "code": "PAT_REQUIRED"
                    }
                    """;

            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody(errorJson));

            AuthenticationException ex = assertThrows(AuthenticationException.class,
                    () -> client.getProblem("mini-bank-stage-1"));

            assertEquals(401, ex.getStatusCode());
            assertEquals("PAT_REQUIRED", ex.getCode());
            assertTrue(ex.getMessage().contains("PAT is required"));
        }

        @Test
        @DisplayName("2.7 testGetProblem_Unauthorized_401_FallbackPlainText_ThrowsAuthenticationException")
        void testGetProblem_Unauthorized_401_FallbackPlainText_ThrowsAuthenticationException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Unauthorized access"));

            AuthenticationException ex = assertThrows(AuthenticationException.class,
                    () -> client.getProblem("two-sum"));

            assertEquals(401, ex.getStatusCode());
            assertEquals("UNAUTHORIZED", ex.getCode(), "Fallback error code must be UNAUTHORIZED");
        }

        @Test
        @DisplayName("2.8 testGetProblem_NotFound_404_ThrowsMiniLeetCodeApiException")
        void testGetProblem_NotFound_404_ThrowsMiniLeetCodeApiException() {
            String errorJson = """
                    {
                      "type": "https://api.mini-leetcode.com/errors/not-found",
                      "title": "Not Found",
                      "status": 404,
                      "detail": "Problem not found with slug: missing-problem",
                      "code": "PROBLEM_NOT_FOUND"
                    }
                    """;

            server.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody(errorJson));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> client.getProblem("missing-problem"));

            assertEquals(404, ex.getStatusCode());
            assertEquals("PROBLEM_NOT_FOUND", ex.getCode());
            assertTrue(ex.getMessage().contains("missing-problem"));
        }

        @Test
        @DisplayName("2.9 testGetProblem_ServerError_500_ThrowsMiniLeetCodeApiException")
        void testGetProblem_ServerError_500_ThrowsMiniLeetCodeApiException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Internal Server Error"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> client.getProblem("two-sum"));

            assertEquals(500, ex.getStatusCode());
        }

        @Test
        @DisplayName("2.10 testGetProblem_NetworkTimeout_ThrowsMiniLeetCodeApiException")
        void testGetProblem_NetworkTimeout_ThrowsMiniLeetCodeApiException() {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            ApiClient fastTimeoutClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofMillis(200));

            assertThrows(MiniLeetCodeApiException.class, () -> fastTimeoutClient.getProblem("two-sum"));
        }

        @Test
        @DisplayName("2.11 testGetProblem_EmptyBodyOn200_ReturnsNull")
        void testGetProblem_EmptyBodyOn200_ReturnsNull() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(""));

            ProblemDto problem = client.getProblem("two-sum");
            assertNull(problem, "Empty JSON response on 200 OK should safely return null");
        }

        @Test
        @DisplayName("2.12 testGetProblem_NullTagsHandling_SafeEmptyList")
        void testGetProblem_NullTagsHandling_SafeEmptyList() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id": 404,
                              "title": "Untagged Problem",
                              "slug": "untagged-problem",
                              "description": "Problem without tags",
                              "difficulty": "EASY",
                              "type": "ALGORITHMIC",
                              "tags": null
                            }
                            """));

            ProblemDto problem = client.getProblem("untagged-problem");

            assertNotNull(problem);
            assertNotNull(problem.tags(), "tags() accessor must never return null even if backend returns tags: null");
            assertTrue(problem.tags().isEmpty(), "tags() should be an empty list");
            assertFalse(problem.hasTags());
        }

        @Test
        @DisplayName("2.13 testGetProblem_InvalidInput_NullOrBlank_ThrowsIllegalArgumentException")
        void testGetProblem_InvalidInput_NullOrBlank_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> client.getProblem(null));
            assertThrows(IllegalArgumentException.class, () -> client.getProblem(""));
            assertThrows(IllegalArgumentException.class, () -> client.getProblem("   "));
        }
    }

    @Nested
    @DisplayName("3. Multi-Threaded Concurrency Safety Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("3.1 testConcurrentRequests_BothEndpoints_ThreadSafety: 20 concurrent threads calling both endpoints simultaneously")
        void testConcurrentRequests_BothEndpoints_ThreadSafety() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            String problemJsonTemplate = """
                    {
                      "id": %d,
                      "title": "Problem %d",
                      "slug": "problem-%d",
                      "description": "Description %d",
                      "difficulty": "EASY",
                      "type": "OOP_GRINDING"
                    }
                    """;

            // Use Dispatcher to route requests concurrently and prevent non-deterministic arrival FIFO collisions
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.endsWith("/starter-template")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/zip")
                                .setBody(new Buffer().write(SIMULATED_ZIP_BYTES));
                    } else if (path != null && path.startsWith("/api/v1/problems/problem-")) {
                        String idxStr = path.substring("/api/v1/problems/problem-".length());
                        long id = Long.parseLong(idxStr);
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(problemJsonTemplate.formatted(id, id, id, id));
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });

            // Submit 20 concurrent worker tasks
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startLatch.await(); // Synchronize simultaneous release
                    if (idx % 2 == 0) {
                        byte[] zip = client.getStarterTemplate("problem-" + idx);
                        assertNotNull(zip);
                        assertArrayEquals(SIMULATED_ZIP_BYTES, zip);
                        assertEquals(0x50, zip[0] & 0xFF);
                    } else {
                        ProblemDto p = client.getProblem("problem-" + idx);
                        assertNotNull(p);
                        assertEquals((long) idx, p.id());
                        assertEquals("problem-" + idx, p.slug());
                        assertTrue(p.isOopGrinding());
                    }
                    return null;
                }));
            }

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for all tasks to complete without throwing exceptions
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor must terminate cleanly");
        }
    }
}
