package jsd.toiyeujava.minileetcode.plugin.challenge;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.client.ProblemDetail;
import jsd.toiyeujava.minileetcode.plugin.model.Difficulty;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
import jsd.toiyeujava.minileetcode.plugin.model.ProblemType;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial and Security Test Suite for Slice 4 Milestone 1:
 * ApiClient (getProblem, getStarterTemplate) &amp; ProblemDto.
 * <p>
 * Challenge vectors:
 * 1. Path traversal slugs (relative, encoded, double-encoded, special characters, unicode, emojis).
 * 2. Extreme JSON payloads (unknown keys, deeply nested objects, negative resource limits, boundary numbers, null fields, malformed syntax).
 * 3. Malformed RFC 7807 error responses (non-JSON HTML on 401/500/502, empty bodies, corrupted JSON error bodies).
 * 4. Header edge cases (null/empty server headers, client header validation with null/blank/whitespace PATs).
 * 5. Binary ZIP template edge cases (empty body, corrupted bytes, large binary stream).
 * 6. High-concurrency stress test with adversarial inputs across multiple threads.
 */
class Slice4Milestone1AdversarialTest {

    private MockWebServer server;
    private ApiClient client;
    private static final String TEST_PAT = "mlc_pat_sec_adversarial_9876543210abcdef";
    private final Gson gson = new Gson();

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

    // =========================================================================
    // 1. SLUGS WITH PATH TRAVERSAL ATTEMPTS, SPECIAL ENCODING, UNICODE, EMOJIS
    // =========================================================================

    @Nested
    @DisplayName("1. Slug Security & Path Traversal Challenge Tests")
    class SlugSecurityTests {

        @Test
        @DisplayName("1.1 Relative path traversal attempt '../../etc/passwd' is percent-encoded and contained")
        void testPathTraversalRelativeDirectory() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                        {"id": 1, "slug": "..%2F..%2Fetc%2Fpasswd", "title": "Path Traversal Test"}
                    """));

            ProblemDto dto = client.getProblem("../../etc/passwd");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            // The '/' must be percent-encoded to %2F so the request path does NOT traverse directories
            assertEquals("/api/v1/problems/..%2F..%2Fetc%2Fpasswd", recorded.getPath());
            assertNotNull(dto);
        }

        @Test
        @DisplayName("1.2 Percent-encoded traversal attempt '%2e%2e%2f' is double-encoded preventing traversal")
        void testPathTraversalPercentEncoded() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                        {"id": 2, "slug": "%2e%2e%2f", "title": "Double Encoded Test"}
                    """));

            ProblemDto dto = client.getProblem("%2e%2e%2f");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            // The '%' must be encoded to %25 -> %252e%252e%252f
            assertEquals("/api/v1/problems/%252e%252e%252f", recorded.getPath());
            assertNotNull(dto);
        }

        @Test
        @DisplayName("1.3 Path traversal on getStarterTemplate is safely contained")
        void testPathTraversalOnStarterTemplate() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(new byte[]{0x50, 0x4B, 0x03, 0x04})));

            byte[] bytes = client.getStarterTemplate("../../../windows/system32/cmd.exe");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            assertEquals("/api/v1/problems/..%2F..%2F..%2Fwindows%2Fsystem32%2Fcmd.exe/starter-template", recorded.getPath());
            assertEquals(4, bytes.length);
        }

        @Test
        @DisplayName("1.4 Non-ASCII Vietnamese characters with accents (tài-khoản-ngân-hàng)")
        void testNonAsciiVietnameseSlug() throws Exception {
            String vietnameseSlug = "tài-khoản-ngân-hàng-giai-đoạn-1";
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                        {"id": 10, "slug": "tài-khoản-ngân-hàng-giai-đoạn-1", "title": "Tài khoản ngân hàng"}
                    """));

            ProblemDto dto = client.getProblem(vietnameseSlug);
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            // Verify UTF-8 percent-encoding is used
            assertTrue(recorded.getPath().startsWith("/api/v1/problems/t%C3%A0i-kho%E1%BA%A3n-ng%C3%A2n-h%C3%A0ng"));
            assertNotNull(dto);
            assertEquals("Tài khoản ngân hàng", dto.title());
        }

        @Test
        @DisplayName("1.5 Unicode emojis in slug (problem-🚀-🔥-star)")
        void testUnicodeEmojiSlug() throws Exception {
            String emojiSlug = "problem-🚀-🔥-star";
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                        {"id": 11, "slug": "problem-🚀-🔥-star", "title": "Rocket Star"}
                    """));

            ProblemDto dto = client.getProblem(emojiSlug);
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            assertFalse(recorded.getPath().contains("🚀")); // Must be percent encoded, not raw unicode bytes in URI
            assertNotNull(dto);
        }

        @Test
        @DisplayName("1.6 Query string and URI fragment injection in slug")
        void testQueryStringAndFragmentInjection() throws Exception {
            String maliciousSlug = "problem-1?admin=true&action=delete#fragment";
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                        {"id": 12, "slug": "problem-1"}
                    """));

            client.getProblem(maliciousSlug);
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            // '?' -> %3F, '&' -> %26, '=' -> %3D, '#' -> %23
            assertEquals("/api/v1/problems/problem-1%3Fadmin%3Dtrue%26action%3Ddelete%23fragment", recorded.getPath());
        }

        @Test
        @DisplayName("1.7 Slugs with surrounding whitespace are trimmed")
        void testWhitespaceTrimmingInSlug() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                        {"id": 13, "slug": "two-sum"}
                    """));

            client.getProblem("   two-sum   ");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            assertEquals("/api/v1/problems/two-sum", recorded.getPath());
        }

        @Test
        @DisplayName("1.8 Null, empty, and whitespace-only slugs throw IllegalArgumentException")
        void testNullAndBlankSlugValidation() {
            assertThrows(IllegalArgumentException.class, () -> client.getProblem(null));
            assertThrows(IllegalArgumentException.class, () -> client.getProblem(""));
            assertThrows(IllegalArgumentException.class, () -> client.getProblem("   "));
            assertThrows(IllegalArgumentException.class, () -> client.getProblem("\t\n\r"));

            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate(null));
            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate(""));
            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate("   "));
            assertThrows(IllegalArgumentException.class, () -> client.getStarterTemplate("\t\n\r"));
        }
    }

    // =========================================================================
    // 2. EXTREME JSON PAYLOADS & PROBLEM DTO SCHEMA STRESS
    // =========================================================================

    @Nested
    @DisplayName("2. Extreme JSON Payloads & ProblemDto Schema Stress")
    class ExtremeJsonPayloadTests {

        @Test
        @DisplayName("2.1 Unknown and extra JSON fields do not crash ProblemDto deserialization")
        void testUnknownFieldsTolerance() {
            String json = """
                {
                  "id": 101,
                  "title": "Two Sum",
                  "slug": "two-sum",
                  "unknownString": "extra-data",
                  "unknownNumber": 12345,
                  "unknownBoolean": true,
                  "unknownArray": [1, 2, 3, "four"],
                  "unknownObject": {
                    "nestedKey": "nestedVal",
                    "deepArray": [{"x": 1}]
                  }
                }
                """;

            ProblemDto dto = gson.fromJson(json, ProblemDto.class);
            assertNotNull(dto);
            assertEquals(101L, dto.id());
            assertEquals("Two Sum", dto.title());
            assertEquals("two-sum", dto.slug());
        }

        @Test
        @DisplayName("2.2 Deeply nested unexpected JSON structures inside unknown fields")
        void testDeeplyNestedUnexpectedStructure() {
            StringBuilder sb = new StringBuilder("{\"id\": 102, \"title\": \"Deep Test\", \"deep\": ");
            int depth = 50;
            for (int i = 0; i < depth; i++) {
                sb.append("{\"layer\": ");
            }
            sb.append("\"innermost\"");
            for (int i = 0; i < depth; i++) {
                sb.append("}");
            }
            sb.append("}");

            ProblemDto dto = gson.fromJson(sb.toString(), ProblemDto.class);
            assertNotNull(dto);
            assertEquals(102L, dto.id());
            assertEquals("Deep Test", dto.title());
        }

        @Test
        @DisplayName("2.3 Negative resource limits (timeLimitMs: -1, memoryLimitMb: -512, testCaseCount: -5)")
        void testNegativeResourceLimits() {
            String json = """
                {
                  "id": 103,
                  "timeLimitMs": -1,
                  "memoryLimitMb": -512,
                  "testCaseCount": -5
                }
                """;

            ProblemDto dto = gson.fromJson(json, ProblemDto.class);
            assertNotNull(dto);
            assertEquals(-1, dto.timeLimitMs());
            assertEquals(-1, dto.effectiveTimeLimitMs());
            assertEquals(-512, dto.memoryLimitMb());
            assertEquals(-512, dto.effectiveMemoryLimitMb());
            assertEquals(-5L, dto.testCaseCount());
        }

        @Test
        @DisplayName("2.4 Boundary and overflow integer values in ProblemDto")
        void testBoundaryAndOverflowValues() {
            String json = """
                {
                  "id": 9223372036854775807,
                  "timeLimitMs": 2147483647,
                  "memoryLimitMb": 2147483647,
                  "testCaseCount": 9223372036854775807
                }
                """;

            ProblemDto dto = gson.fromJson(json, ProblemDto.class);
            assertNotNull(dto);
            assertEquals(Long.MAX_VALUE, dto.id());
            assertEquals(Integer.MAX_VALUE, dto.timeLimitMs());
            assertEquals(Integer.MAX_VALUE, dto.effectiveTimeLimitMs());
            assertEquals(Integer.MAX_VALUE, dto.memoryLimitMb());
            assertEquals(Integer.MAX_VALUE, dto.effectiveMemoryLimitMb());
            assertEquals(Long.MAX_VALUE, dto.testCaseCount());
        }

        @Test
        @DisplayName("2.5 Completely empty JSON '{}' defaults safely without NullPointerExceptions")
        void testCompletelyEmptyJson() {
            ProblemDto dto = gson.fromJson("{}", ProblemDto.class);
            assertNotNull(dto);
            assertNull(dto.id());
            assertEquals("", dto.title());
            assertEquals("", dto.slug());
            assertEquals("", dto.description());
            assertEquals(Difficulty.EASY, dto.difficulty());
            assertEquals(ProblemType.ALGORITHMIC, dto.type());
            assertNotNull(dto.tags());
            assertTrue(dto.tags().isEmpty());
            assertEquals("", dto.starterTemplates());
            assertEquals("", dto.status());
            assertEquals("", dto.createdByUsername());
            assertEquals(0L, dto.testCaseCount());
            assertEquals(2000, dto.effectiveTimeLimitMs());
            assertEquals(256, dto.effectiveMemoryLimitMb());
            assertFalse(dto.isPublished());
            assertFalse(dto.isDone());
            assertFalse(dto.isInProgress());
            assertFalse(dto.hasStarterTemplates());
            assertFalse(dto.hasTags());
            assertFalse(dto.isOopGrinding());
            assertTrue(dto.isAlgorithmic());
        }

        @Test
        @DisplayName("2.6 Unknown enum strings fallback to default enums safely")
        void testUnknownEnumStringsFallback() {
            String json = """
                {
                  "id": 104,
                  "difficulty": "UNRECOGNIZED_DIFFICULTY_TIER",
                  "type": "NEW_UNSEEN_TYPE"
                }
                """;

            ProblemDto dto = gson.fromJson(json, ProblemDto.class);
            assertNotNull(dto);
            // In Gson, unknown enum constants deserialize to null, then compact constructor enforces defaults
            assertEquals(Difficulty.EASY, dto.difficulty());
            assertEquals(ProblemType.ALGORITHMIC, dto.type());
            assertEquals(ProblemType.ALGORITHMIC, dto.getProblemType());
        }

        @Test
        @DisplayName("2.7 Tags array with mixed nulls, empty strings, and null tags field")
        void testTagsArrayHandling() {
            String jsonWithNulls = """
                {
                  "id": 105,
                  "tags": ["Java", null, "OOP", null, "Algorithm"]
                }
                """;

            ProblemDto dto = gson.fromJson(jsonWithNulls, ProblemDto.class);
            assertNotNull(dto);
            assertEquals(List.of("Java", "OOP", "Algorithm"), dto.tags());

            String jsonNullTags = "{\"id\": 106, \"tags\": null}";
            ProblemDto dtoNullTags = gson.fromJson(jsonNullTags, ProblemDto.class);
            assertNotNull(dtoNullTags);
            assertNotNull(dtoNullTags.tags());
            assertTrue(dtoNullTags.tags().isEmpty());
        }

        @Test
        @DisplayName("2.8 Malformed truncated JSON on 200 OK throws JsonSyntaxException")
        void testTruncatedJsonOn200ThrowsJsonSyntaxException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 107, \"title\": \"Incomplete"));

            assertThrows(JsonSyntaxException.class, () -> client.getProblem("test-problem"));
        }

        @Test
        @DisplayName("2.9 HTML response body on 200 OK throws JsonSyntaxException")
        void testHtmlOn200ThrowsJsonSyntaxException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body><h1>Welcome</h1></body></html>"));

            assertThrows(JsonSyntaxException.class, () -> client.getProblem("test-problem"));
        }

        @Test
        @DisplayName("2.10 Blank or empty body on 200 OK returns null safely")
        void testBlankBodyOn200ReturnsNull() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("   \n\t  "));

            ProblemDto dto = client.getProblem("empty-body-problem");
            assertNull(dto);
        }
    }

    // =========================================================================
    // 3. MALFORMED RFC 7807 ERROR RESPONSES
    // =========================================================================

    @Nested
    @DisplayName("3. Malformed RFC 7807 Error Responses Challenge Tests")
    class MalformedRfc7807Tests {

        @Test
        @DisplayName("3.1 Non-JSON HTML error page on 401 throws AuthenticationException with default code UNAUTHORIZED")
        void testNonJsonHtmlOn401() {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "text/html; charset=UTF-8")
                    .setBody("""
                        <html>
                          <head><title>401 Authorization Required</title></head>
                          <body><center><h1>401 Authorization Required</h1></center><hr><center>nginx/1.24.0</center></body>
                        </html>
                    """));

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> client.getProblem("protected-problem"));
            assertEquals("UNAUTHORIZED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("HTTP 401"));
            assertNull(ex.getProblemDetail());
        }

        @Test
        @DisplayName("3.2 Empty body on 401 throws AuthenticationException with default code UNAUTHORIZED")
        void testEmptyBodyOn401() {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody(""));

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> client.getProblem("protected-problem"));
            assertEquals("UNAUTHORIZED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("HTTP 401"));
            assertNull(ex.getProblemDetail());
        }

        @Test
        @DisplayName("3.3 Non-JSON HTML error page on 500 throws MiniLeetCodeApiException with status 500 and fallback message")
        void testNonJsonHtmlOn500() {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>500 Internal Server Error</body></html>"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getProblem("crash-problem"));
            assertEquals(500, ex.getStatusCode());
            assertEquals("API request failed with HTTP 500", ex.getMessage());
            assertNull(ex.getErrorCode());
            assertNull(ex.getProblemDetail());
        }

        @Test
        @DisplayName("3.4 Non-JSON HTML error page on 502/503/504 gateway errors")
        void testGatewayErrorsWithHtml() {
            server.enqueue(new MockResponse()
                    .setResponseCode(502)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>502 Bad Gateway</body></html>"));

            MiniLeetCodeApiException ex502 = assertThrows(MiniLeetCodeApiException.class, () -> client.getProblem("gw-problem"));
            assertEquals(502, ex502.getStatusCode());
            assertEquals("API request failed with HTTP 502", ex502.getMessage());

            server.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>503 Service Unavailable</body></html>"));

            MiniLeetCodeApiException ex503 = assertThrows(MiniLeetCodeApiException.class, () -> client.getProblem("gw-problem"));
            assertEquals(503, ex503.getStatusCode());
            assertEquals("API request failed with HTTP 503", ex503.getMessage());
        }

        @Test
        @DisplayName("3.5 Malformed/truncated JSON in error body (status 404) gracefully falls back")
        void testMalformedJsonInErrorBody() {
            server.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("{\"type\": \"https://example.com/not-found\", \"title\": \"Not Fo"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getProblem("non-existent"));
            assertEquals(404, ex.getStatusCode());
            assertEquals("API request failed with HTTP 404", ex.getMessage());
            assertNull(ex.getProblemDetail());
        }

        @Test
        @DisplayName("3.6 Starter template handles 401 HTML and empty error body safely")
        void testStarterTemplateHandles401MalformedError() {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>Unauthorized</body></html>"));

            AuthenticationException exHtml = assertThrows(AuthenticationException.class,
                    () -> client.getStarterTemplate("problem-1"));
            assertEquals("UNAUTHORIZED", exHtml.getErrorCode());

            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(""));

            AuthenticationException exEmpty = assertThrows(AuthenticationException.class,
                    () -> client.getStarterTemplate("problem-1"));
            assertEquals("UNAUTHORIZED", exEmpty.getErrorCode());
        }

        @Test
        @DisplayName("3.7 Starter template handles 500 HTML error body safely")
        void testStarterTemplateHandles500HtmlError() {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>Internal Error</body></html>"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class,
                    () -> client.getStarterTemplate("problem-1"));
            assertEquals(500, ex.getStatusCode());
            assertEquals("API request failed with HTTP 500", ex.getMessage());
        }
    }

    // =========================================================================
    // 4. INCOMPLETE, NULL, AND ADVERSARIAL HEADERS
    // =========================================================================

    @Nested
    @DisplayName("4. Header Robustness & Validation Tests")
    class HeaderRobustnessTests {

        @Test
        @DisplayName("4.1 Server response with completely empty headers is processed successfully")
        void testServerResponseWithNoHeaders() throws Exception {
            // MockResponse without any headers
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"id\": 200, \"title\": \"No Headers\"}"));

            ProblemDto dto = client.getProblem("no-headers");
            assertNotNull(dto);
            assertEquals(200L, dto.id());
            assertEquals("No Headers", dto.title());
        }

        @Test
        @DisplayName("4.2 Non-standard or absent Content-Type on 200 OK")
        void testNonStandardContentType() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody("{\"id\": 201, \"title\": \"Octet Stream JSON\"}"));

            ProblemDto dto = client.getProblem("octet-stream");
            assertNotNull(dto);
            assertEquals(201L, dto.id());
        }

        @Test
        @DisplayName("4.3 Authorization header behavior: null PAT omits header")
        void testNullPatOmitsAuthorizationHeader() throws Exception {
            ApiClient unauthenticatedClient = new ApiClient(server.url("/").toString(), null, Duration.ofSeconds(3));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\": 202}"));

            unauthenticatedClient.getProblem("open-problem");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            assertNull(recorded.getHeader("Authorization"));
        }

        @Test
        @DisplayName("4.4 Authorization header behavior: blank and whitespace PAT omits header")
        void testBlankPatOmitsAuthorizationHeader() throws Exception {
            ApiClient blankPatClient = new ApiClient(server.url("/").toString(), "    ", Duration.ofSeconds(3));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\": 203}"));

            blankPatClient.getProblem("open-problem");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            assertNull(recorded.getHeader("Authorization"));
            assertNull(blankPatClient.getPat());
        }

        @Test
        @DisplayName("4.5 Authorization header behavior: PAT with surrounding whitespace is trimmed")
        void testWhitespacePatIsTrimmed() throws Exception {
            ApiClient trimmedPatClient = new ApiClient(server.url("/").toString(), "   valid_pat_123   ", Duration.ofSeconds(3));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\": 204}"));

            trimmedPatClient.getProblem("open-problem");
            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(recorded);
            assertEquals("Bearer valid_pat_123", recorded.getHeader("Authorization"));
            assertEquals("valid_pat_123", trimmedPatClient.getPat());
        }

        @Test
        @DisplayName("4.6 Correct Accept and User-Agent headers sent for both endpoints")
        void testAcceptAndUserAgentHeaders() throws Exception {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\": 205}"));
            client.getProblem("prob");
            RecordedRequest reqProblem = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(reqProblem);
            assertEquals("application/json", reqProblem.getHeader("Accept"));
            assertEquals("MiniLeetCode-IntelliJ-Plugin/0.1.0", reqProblem.getHeader("User-Agent"));

            server.enqueue(new MockResponse().setResponseCode(200).setBody(new Buffer().write(new byte[]{1, 2, 3})));
            client.getStarterTemplate("prob");
            RecordedRequest reqTemplate = server.takeRequest(3, TimeUnit.SECONDS);

            assertNotNull(reqTemplate);
            assertEquals("application/zip", reqTemplate.getHeader("Accept"));
            assertEquals("MiniLeetCode-IntelliJ-Plugin/0.1.0", reqTemplate.getHeader("User-Agent"));
        }
    }

    // =========================================================================
    // 5. BINARY ZIP STARTER TEMPLATE EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("5. Binary Starter Template Edge Cases")
    class StarterTemplateEdgeCaseTests {

        @Test
        @DisplayName("5.1 Empty body on 200 OK returns empty byte array byte[0]")
        void testEmptyBodyOn200ReturnsEmptyByteArray() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer()));

            byte[] bytes = client.getStarterTemplate("empty-template-problem");
            assertNotNull(bytes);
            assertEquals(0, bytes.length);
        }

        @Test
        @DisplayName("5.2 Non-ZIP bytes (plain text or garbage) returned without crashing ApiClient")
        void testNonZipBytesReturnedSafely() {
            byte[] garbage = "This is not a zip archive".getBytes(StandardCharsets.UTF_8);
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(garbage)));

            byte[] result = client.getStarterTemplate("garbage-template-problem");
            assertNotNull(result);
            assertArrayEquals(garbage, result);
        }

        @Test
        @DisplayName("5.3 Large binary archive (256 KB) transferred intact byte-for-byte")
        void testLargeBinaryArchiveIntegrity() {
            byte[] largeData = new byte[256 * 1024];
            Arrays.fill(largeData, (byte) 0xAA);
            largeData[0] = 0x50; // PK\003\004
            largeData[1] = 0x4B;
            largeData[2] = 0x03;
            largeData[3] = 0x04;

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(largeData)));

            byte[] result = client.getStarterTemplate("large-template-problem");
            assertNotNull(result);
            assertEquals(largeData.length, result.length);
            assertArrayEquals(largeData, result);
        }
    }

    // =========================================================================
    // 6. HIGH-CONCURRENCY ADVERSARIAL STRESS TEST
    // =========================================================================

    @Nested
    @DisplayName("6. High-Concurrency Stress Test")
    class ConcurrencyStressTests {

        @Test
        @DisplayName("6.1 10 concurrent threads mixing getProblem and getStarterTemplate under adversarial conditions")
        void testConcurrentAdversarialCalls() throws Exception {
            int threadCount = 10;
            int iterationsPerThread = 5;
            int totalCalls = threadCount * iterationsPerThread;

            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.endsWith("/starter-template")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/zip")
                                .setBody(new Buffer().write(new byte[]{0x50, 0x4B, 0x03, 0x04}));
                    } else if (path != null && path.contains("error")) {
                        return new MockResponse()
                                .setResponseCode(500)
                                .setHeader("Content-Type", "text/html")
                                .setBody("<html><body>Server Error</body></html>");
                    } else {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""
                                    {"id": 300, "title": "Concurrent Problem", "slug": "concurrent-p"}
                                """);
                    }
                }
            });

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalCalls);
            AtomicInteger successProblemCount = new AtomicInteger(0);
            AtomicInteger successTemplateCount = new AtomicInteger(0);
            AtomicInteger expectedErrorCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            int callType = (threadId + i) % 3;
                            if (callType == 0) {
                                ProblemDto dto = client.getProblem("prob-" + threadId + "-" + i);
                                if (dto != null && "Concurrent Problem".equals(dto.title())) {
                                    successProblemCount.incrementAndGet();
                                }
                            } else if (callType == 1) {
                                byte[] bytes = client.getStarterTemplate("prob-" + threadId + "-" + i);
                                if (bytes.length == 4) {
                                    successTemplateCount.incrementAndGet();
                                }
                            } else {
                                try {
                                    client.getProblem("error-slug-" + i);
                                    fail("Should have thrown 500");
                                } catch (MiniLeetCodeApiException e) {
                                    if (e.getStatusCode() == 500) {
                                        expectedErrorCount.incrementAndGet();
                                    }
                                }
                            }
                            doneLatch.countDown();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
            assertTrue(finished, "Concurrent execution timed out");

            for (Future<?> f : futures) {
                f.get(1, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(totalCalls, successProblemCount.get() + successTemplateCount.get() + expectedErrorCount.get());
        }
    }
}
