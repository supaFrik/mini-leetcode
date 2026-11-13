package jsd.toiyeujava.minileetcode.plugin.client;

import com.google.gson.JsonSyntaxException;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial and empirical stress tests for ApiClient and Model layer.
 * Challenges include malformed JSON, HTTP error codes, socket abruptly dropping,
 * path traversal & adversarial slugs, edge-case PATs, schema evolution, and concurrency.
 */
class ApiClientAdversarialTest {

    private MockWebServer server;
    private ApiClient client;
    private static final String TEST_PAT = "mlc_pat_adversarial_test_token_12345";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        client = new ApiClient(baseUrl, TEST_PAT, Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    @Nested
    @DisplayName("1. Malformed and Truncated JSON Responses")
    class MalformedJsonTests {

        @Test
        @DisplayName("1.1 Truncated JSON on 200 OK getStudyPlans -> throws JsonSyntaxException")
        void testGetStudyPlans_truncatedJson_throwsJsonSyntaxException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[{\"id\": 1, \"slug\": \"incomplete"));

            assertThrows(JsonSyntaxException.class, () -> client.getStudyPlans(),
                    "Truncated JSON must throw JsonSyntaxException from Gson parser");
        }

        @Test
        @DisplayName("1.2 Object instead of Array on 200 OK getStudyPlans -> throws JsonSyntaxException")
        void testGetStudyPlans_objectInsteadOfArray_throwsJsonSyntaxException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\": \"unexpected object structure\"}"));

            assertThrows(JsonSyntaxException.class, () -> client.getStudyPlans());
        }

        @Test
        @DisplayName("1.3 Plain text HTML on 200 OK getStudyPlans -> throws JsonSyntaxException")
        void testGetStudyPlans_htmlOn200_throwsJsonSyntaxException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<!DOCTYPE html><html><body>Error</body></html>"));

            assertThrows(JsonSyntaxException.class, () -> client.getStudyPlans());
        }

        @Test
        @DisplayName("1.4 Truncated JSON on 200 OK getStudyPlan detail -> throws JsonSyntaxException")
        void testGetStudyPlan_truncatedJson_throwsJsonSyntaxException() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"oop\", \"items\": [{\"id\": 10"));

            assertThrows(JsonSyntaxException.class, () -> client.getStudyPlan("oop"));
        }

        @Test
        @DisplayName("1.5 Empty body on 200 OK getStudyPlan detail -> returns null")
        void testGetStudyPlan_emptyBodyOn200_returnsNull() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(""));

            PlanDetail detail = client.getStudyPlan("oop");
            assertNull(detail, "Gson returns null when parsing empty string body");
        }

        @Test
        @DisplayName("1.6 Error response (500) with malformed JSON body -> safely falls back to standard message")
        void testErrorResponse_withMalformedProblemDetail_fallsBackSafely() {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("{this is not valid json at all!!!"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(500, ex.getStatusCode());
            assertNull(ex.getProblemDetail(), "ProblemDetail must be null if JSON parsing fails");
            assertTrue(ex.getMessage().contains("500"), "Fallback message must mention status code 500");
        }

        @Test
        @DisplayName("1.7 Large JSON payload on 200 OK -> handled without OOM or crash")
        void testLargeJsonPayload() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 500; i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"id\": ").append(i).append(", \"slug\": \"slug-").append(i)
                        .append("\", \"title\": \"Plan ").append(i)
                        .append("\", \"description\": \"").append("x".repeat(100)).append("\"}");
            }
            sb.append("]");

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(sb.toString()));

            List<PlanSummary> plans = client.getStudyPlans();
            assertEquals(500, plans.size());
        }
    }

    @Nested
    @DisplayName("2. HTTP Status Codes Stress Testing")
    class HttpStatusTests {

        @Test
        @DisplayName("2.1 HTTP 502 Bad Gateway -> throws MiniLeetCodeApiException with status 502")
        void testHttp502_BadGateway() {
            server.enqueue(new MockResponse()
                    .setResponseCode(502)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>502 Bad Gateway</body></html>"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(502, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("502"));
            assertNull(ex.getProblemDetail());
        }

        @Test
        @DisplayName("2.2 HTTP 503 Service Unavailable -> throws MiniLeetCodeApiException with status 503")
        void testHttp503_ServiceUnavailable() {
            server.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""
                        {
                          "title": "Service Unavailable",
                          "status": 503,
                          "detail": "Database maintenance in progress",
                          "code": "MAINTENANCE_MODE"
                        }
                        """));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(503, ex.getStatusCode());
            assertEquals("MAINTENANCE_MODE", ex.getCode());
            assertTrue(ex.getMessage().contains("Database maintenance in progress"));
        }

        @Test
        @DisplayName("2.3 HTTP 429 Too Many Requests with RFC 7807 problem detail")
        void testHttp429_RateLimited_withProblemDetail() {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""
                        {
                          "title": "Too Many Requests",
                          "status": 429,
                          "detail": "Rate limit quota exceeded. Try again in 60s.",
                          "code": "RATE_LIMITED"
                        }
                        """));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(429, ex.getStatusCode());
            assertEquals("RATE_LIMITED", ex.getCode());
            assertTrue(ex.getMessage().contains("Rate limit quota exceeded"));
        }

        @Test
        @DisplayName("2.4 HTTP 429 Too Many Requests without problem detail")
        void testHttp429_RateLimited_plainText() {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Rate limit exceeded"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(429, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("429"));
        }

        @Test
        @DisplayName("2.5 HTTP 504 Gateway Timeout -> throws MiniLeetCodeApiException with status 504")
        void testHttp504_GatewayTimeout() {
            server.enqueue(new MockResponse()
                    .setResponseCode(504)
                    .setBody("Gateway Timeout"));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(504, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("504"));
        }

        @Test
        @DisplayName("2.6 HTTP 204 No Content -> treated as unexpected status, throws MiniLeetCodeApiException")
        void testHttp204_NoContent() {
            server.enqueue(new MockResponse()
                    .setResponseCode(204));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(204, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("204"));
        }

        @Test
        @DisplayName("2.7 HTTP 400 Bad Request with RFC 7807 validation error")
        void testHttp400_BadRequest() {
            server.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Invalid parameter format",
                          "code": "VALIDATION_FAILED"
                        }
                        """));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertEquals(400, ex.getStatusCode());
            assertEquals("VALIDATION_FAILED", ex.getCode());
            assertTrue(ex.getMessage().contains("Invalid parameter format"));
        }
    }

    @Nested
    @DisplayName("3. Socket & Network Failure Simulation")
    class NetworkFailureTests {

        @Test
        @DisplayName("3.1 Socket disconnect during response body -> throws MiniLeetCodeApiException wrapping IOException")
        void testDisconnectDuringResponseBody() {
            server.enqueue(new MockResponse()
                    .setBody("{\"some_large_body\": \"starts here...")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof IOException, "Root cause must be an IOException");
        }

        @Test
        @DisplayName("3.2 Socket disconnect after request -> throws MiniLeetCodeApiException")
        void testDisconnectAfterRequest() {
            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof IOException);
        }

        @Test
        @DisplayName("3.3 Abrupt TCP shutdown (SHUTDOWN_INPUT_AT_END) -> handled gracefully")
        void testShutdownInputAtEnd() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("[]")
                    .setSocketPolicy(SocketPolicy.SHUTDOWN_INPUT_AT_END));

            List<PlanSummary> plans = client.getStudyPlans();
            assertNotNull(plans);
            assertTrue(plans.isEmpty());
        }

        @Test
        @DisplayName("3.4 Interrupted thread during send -> preserves interrupt flag")
        void testInterruptedThread_preservesInterruptFlag() throws Exception {
            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE));

            Thread workerThread = new Thread(() -> {
                try {
                    client.getStudyPlans();
                } catch (MiniLeetCodeApiException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Flag properly preserved
                        System.setProperty("test.interrupted.flag", "preserved");
                    }
                }
            });

            workerThread.start();
            Thread.sleep(100);
            workerThread.interrupt();
            workerThread.join(2000);

            assertEquals("preserved", System.getProperty("test.interrupted.flag"));
            System.clearProperty("test.interrupted.flag");
        }
    }

    @Nested
    @DisplayName("4. Adversarial Slugs and URI Encoding")
    class AdversarialSlugTests {

        @Test
        @DisplayName("4.1 Path traversal tokens (../) in slug are percent-encoded and do not escape base path")
        void testSlug_withPathTraversal_isEncodedSafely() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"encoded\"}"));

            client.getStudyPlan("../../admin/secret");

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            // Slashes must be percent encoded as %2F
            assertEquals("/api/v1/study-plans/..%2F..%2Fadmin%2Fsecret", request.getPath(),
                    "Path traversal slashes must be percent-encoded to prevent path traversal");
        }

        @Test
        @DisplayName("4.1b Slug with double dot (..) behavior check")
        void testSlug_withDoubleDot() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"dots\"}"));

            client.getStudyPlan("..");

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            // Verify what path the server receives
            assertEquals("/api/v1/study-plans/..", request.getPath());
        }

        @Test
        @DisplayName("4.2 Unicode and Vietnamese diacritics in slug are UTF-8 encoded")
        void testSlug_withUnicodeAndDiacritics() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"unicode\"}"));

            client.getStudyPlan("lộ-trình-thuật-toán-2026");

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            String expectedPath = "/api/v1/study-plans/" + java.net.URLEncoder.encode("lộ-trình-thuật-toán-2026", java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(expectedPath, request.getPath(),
                    "Unicode characters must be UTF-8 percent-encoded");
        }

        @Test
        @DisplayName("4.3 Query parameter injection in slug is encoded (? and & -> %3F, %26)")
        void testSlug_withQueryParameterInjection() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"query-injection\"}"));

            client.getStudyPlan("plan-1?admin=true&format=raw");

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/api/v1/study-plans/plan-1%3Fadmin%3Dtrue%26format%3Draw", request.getPath(),
                    "? and & must be encoded so they cannot inject query parameters");
        }

        @Test
        @DisplayName("4.4 Hash fragment injection in slug is encoded (# -> %23)")
        void testSlug_withHashFragmentInjection() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"hash-injection\"}"));

            client.getStudyPlan("plan-1#secret-fragment");

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/api/v1/study-plans/plan-1%23secret-fragment", request.getPath(),
                    "# must be encoded so it cannot alter URI fragment");
        }

        @Test
        @DisplayName("4.5 Slug with spaces and leading/trailing whitespace is trimmed and space-encoded as %20")
        void testSlug_withWhitespace() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"slug\": \"spaces\"}"));

            client.getStudyPlan("   dsa basics 2026   ");

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/api/v1/study-plans/dsa%20basics%202026", request.getPath());
        }

        @Test
        @DisplayName("4.6 Null, empty, and whitespace-only slug throws IllegalArgumentException")
        void testSlug_invalidThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan(null));
            assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan(""));
            assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan("     "));
            assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan("\t\n"));
        }
    }

    @Nested
    @DisplayName("5. Edge-Case PATs and Authorization Headers")
    class EdgeCasePatTests {

        @Test
        @DisplayName("5.1 Empty string PAT -> Authorization header omitted")
        void testEmptyPat_omitsAuthorizationHeader() throws InterruptedException {
            ApiClient emptyPatClient = new ApiClient(server.url("/").toString(), "");
            server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

            emptyPatClient.getStudyPlans();

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertNull(request.getHeader("Authorization"));
        }

        @Test
        @DisplayName("5.2 Whitespace-only PAT -> Authorization header omitted")
        void testWhitespaceOnlyPat_omitsAuthorizationHeader() throws InterruptedException {
            ApiClient wsPatClient = new ApiClient(server.url("/").toString(), "   \t\n  ");
            server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

            wsPatClient.getStudyPlans();

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertNull(request.getHeader("Authorization"));
        }

        @Test
        @DisplayName("5.3 Untrimmed PAT is automatically trimmed")
        void testUntrimmedPat_isTrimmed() throws InterruptedException {
            ApiClient untrimmedClient = new ApiClient(server.url("/").toString(), "   mlc_pat_clean_me   ");
            server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

            untrimmedClient.getStudyPlans();

            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("Bearer mlc_pat_clean_me", request.getHeader("Authorization"));
        }

        @Test
        @DisplayName("5.4 CRLF injection in PAT -> throws IllegalArgumentException from HttpClient")
        void testPat_crlfInjection_throwsIllegalArgumentException() {
            ApiClient crlfClient = new ApiClient(server.url("/").toString(), "token\r\nX-Injected: evil");
            assertThrows(IllegalArgumentException.class, crlfClient::getStudyPlans);
        }

        @Test
        @DisplayName("5.5 Malformed baseUrl -> constructor or send throws IllegalArgumentException")
        void testMalformedBaseUrl() {
            assertThrows(IllegalArgumentException.class, () -> new ApiClient(""));
            assertThrows(IllegalArgumentException.class, () -> new ApiClient("   "));
        }
    }

    @Nested
    @DisplayName("6. Model Layer Edge Cases and Defensive Defaults")
    class ModelEdgeCaseTests {

        @Test
        @DisplayName("6.1 Zero stages -> getProgressPercentage returns 0 without divide-by-zero exception")
        void testZeroStages_noDivisionByZero() {
            PlanSummary summary = new PlanSummary(1L, "slug", "title", "desc", StudyPlanStatus.PUBLISHED, 0, 0, 0);
            assertEquals(0, summary.getProgressPercentage());
            assertFalse(summary.isCompleted());

            PlanDetail detail = new PlanDetail(1L, "slug", "title", "desc", StudyPlanStatus.PUBLISHED, 0, 0, List.of());
            assertEquals(0, detail.getProgressPercentage());
            assertFalse(detail.isCompleted());
        }

        @Test
        @DisplayName("6.2 Done stages exceed stageCount -> getProgressPercentage clamps to 100")
        void testOverflowStages_clampsTo100() {
            PlanSummary summary = new PlanSummary(1L, "slug", "title", "desc", StudyPlanStatus.PUBLISHED, 5, 10, 15);
            assertEquals(100, summary.getProgressPercentage());
            assertTrue(summary.isCompleted());

            PlanDetail detail = new PlanDetail(1L, "slug", "title", "desc", StudyPlanStatus.PUBLISHED, 10, 15, List.of());
            assertEquals(100, detail.getProgressPercentage());
            assertTrue(detail.isCompleted());
        }

        @Test
        @DisplayName("6.3 Negative doneStages -> getProgressPercentage clamps to 0")
        void testNegativeDoneStages_clampsTo0() {
            PlanSummary summary = new PlanSummary(1L, "slug", "title", "desc", StudyPlanStatus.PUBLISHED, 5, 10, -3);
            assertEquals(0, summary.getProgressPercentage());
            assertFalse(summary.isCompleted());
        }

        @Test
        @DisplayName("6.4 Unknown enum values in JSON -> safely default without crashing")
        void testUnknownEnumValues_handledSafely() {
            String json = """
                {
                  "id": 1,
                  "slug": "future-plan",
                  "title": "Future Plan",
                  "status": "FUTURE_UNKNOWN_STATUS",
                  "stageCount": 1,
                  "doneStages": 0,
                  "items": [
                    {
                      "id": 10,
                      "position": 1,
                      "kind": "FUTURE_UNKNOWN_KIND",
                      "title": "Future Item",
                      "done": false,
                      "stages": [
                        {
                          "stageNo": 1,
                          "problemId": 100,
                          "slug": "s1",
                          "title": "Future Stage",
                          "difficulty": "NIGHTMARE",
                          "problemType": "QUANTUM",
                          "published": true,
                          "status": "SUPER_DONE"
                        }
                      ]
                    }
                  ]
                }
                """;

            com.google.gson.Gson gson = new com.google.gson.Gson();
            PlanDetail detail = gson.fromJson(json, PlanDetail.class);

            assertNotNull(detail);
            // Default mappings ensure null enums are treated defensively
            assertNull(detail.status()); // StudyPlanStatus unmapped is null
            ItemDto item = detail.items().get(0);
            assertNotNull(item);
            // ItemDto compact constructor defaults null kind to PROBLEM
            assertEquals(StudyPlanItemKind.PROBLEM, item.kind());

            StageDto stage = item.stages().get(0);
            assertNotNull(stage);
            // StageDto compact constructor defaults null difficulty to EASY, problemType to OOP_GRINDING, status to LOCKED
            assertEquals(Difficulty.EASY, stage.difficulty());
            assertEquals(ProblemType.OOP_GRINDING, stage.problemType());
            assertEquals(StageStatus.LOCKED, stage.status());
        }
    }

    @Nested
    @DisplayName("7. Concurrency and Thread Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("7.1 20 concurrent threads calling same ApiClient -> all succeed without race condition")
        void testConcurrentRequests_allSucceed() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<List<PlanSummary>>> futures = new ArrayList<>();

            // Enqueue 20 identical mock responses
            for (int i = 0; i < threadCount; i++) {
                server.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("[{\"id\": " + i + ", \"slug\": \"slug-" + i + "\", \"title\": \"Plan " + i + "\"}]"));
            }

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    return client.getStudyPlans();
                }));
            }

            // Unleash all threads simultaneously
            latch.countDown();

            for (Future<List<PlanSummary>> future : futures) {
                List<PlanSummary> result = future.get(10, TimeUnit.SECONDS);
                assertNotNull(result);
                assertEquals(1, result.size());
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
