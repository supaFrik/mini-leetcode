package jsd.toiyeujava.minileetcode.plugin.client;

import jsd.toiyeujava.minileetcode.plugin.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ApiClientTest {

    private MockWebServer server;
    private ApiClient client;
    private static final String TEST_PAT = "mlc_pat_test1234567890abcdef1234567890abcdef";

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

    @Test
    @DisplayName("testGetAllStudyPlans_Success: 200 with PlanSummary list -> verify list deserialization and headers")
    void testGetAllStudyPlans_Success() throws InterruptedException {
        String jsonResponse = """
            [
              {
                "id": 1,
                "slug": "oop-with-java",
                "title": "OOP với Java",
                "description": "Lộ trình thực chiến OOP",
                "status": "PUBLISHED",
                "itemCount": 3,
                "stageCount": 7,
                "doneStages": 2
              },
              {
                "id": 2,
                "slug": "dsa-essentials",
                "title": "DSA Cơ Bản",
                "description": "Nền tảng thuật toán",
                "status": "PUBLISHED",
                "itemCount": 5,
                "stageCount": 15,
                "doneStages": 0
              }
            ]
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse));

        List<PlanSummary> plans = client.getStudyPlans();

        assertNotNull(plans, "Returned study plans list must not be null");
        assertEquals(2, plans.size(), "Should parse exactly 2 study plans");

        PlanSummary p1 = plans.get(0);
        assertEquals(1L, p1.id());
        assertEquals("oop-with-java", p1.slug());
        assertEquals("OOP với Java", p1.title());
        assertEquals("Lộ trình thực chiến OOP", p1.description());
        assertEquals(StudyPlanStatus.PUBLISHED, p1.status());
        assertEquals(3, p1.itemCount());
        assertEquals(7, p1.stageCount());
        assertEquals(2, p1.doneStages());
        assertEquals(29, p1.getProgressPercentage());
        assertFalse(p1.isCompleted());

        PlanSummary p2 = plans.get(1);
        assertEquals(2L, p2.id());
        assertEquals("dsa-essentials", p2.slug());
        assertEquals(0, p2.doneStages());
        assertEquals(0, p2.getProgressPercentage());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "RecordedRequest must not be null");
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/study-plans", request.getPath());
        assertEquals("Bearer " + TEST_PAT, request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertEquals("MiniLeetCode-IntelliJ-Plugin/0.1.0", request.getHeader("User-Agent"));
    }

    @Test
    @DisplayName("testGetStudyPlans_withoutPat_omitsAuthorizationHeader: no PAT -> omit Authorization header")
    void testGetStudyPlans_withoutPat_omitsAuthorizationHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        ApiClient noAuthClient = new ApiClient(server.url("/").toString(), null);
        List<PlanSummary> plans = noAuthClient.getStudyPlans();

        assertNotNull(plans);
        assertTrue(plans.isEmpty());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertNull(request.getHeader("Authorization"), "Authorization header should be omitted when PAT is null");
    }

    @Test
    @DisplayName("testGetStudyPlanBySlug_Success: 200 with PlanDetail JSON -> verify nested items, stages and enum mappings")
    void testGetStudyPlanBySlug_Success() throws InterruptedException {
        String jsonResponse = """
            {
              "id": 1,
              "slug": "oop-with-java",
              "title": "OOP với Java",
              "description": "Lộ trình thực chiến OOP",
              "status": "PUBLISHED",
              "stageCount": 2,
              "doneStages": 1,
              "items": [
                {
                  "id": 10,
                  "position": 1,
                  "kind": "PROJECT",
                  "title": "Mini Bank Console",
                  "description": "Ngân hàng dòng lệnh",
                  "done": false,
                  "stages": [
                    {
                      "stageNo": 1,
                      "problemId": 101,
                      "slug": "mini-bank-stage-1",
                      "title": "Tài khoản đơn",
                      "difficulty": "EASY",
                      "problemType": "OOP_GRINDING",
                      "published": true,
                      "status": "DONE"
                    },
                    {
                      "stageNo": 2,
                      "problemId": 102,
                      "slug": "mini-bank-stage-2",
                      "title": "Kế thừa Account",
                      "difficulty": "MEDIUM",
                      "problemType": "OOP_GRINDING",
                      "published": true,
                      "status": "IN_PROGRESS"
                    }
                  ]
                }
              ]
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse));

        PlanDetail detail = client.getStudyPlan("oop-with-java");

        assertNotNull(detail);
        assertEquals(1L, detail.id());
        assertEquals("oop-with-java", detail.slug());
        assertEquals("OOP với Java", detail.title());
        assertEquals("Lộ trình thực chiến OOP", detail.description());
        assertEquals(StudyPlanStatus.PUBLISHED, detail.status());
        assertEquals(2, detail.stageCount());
        assertEquals(1, detail.doneStages());
        assertEquals(50, detail.getProgressPercentage());
        assertFalse(detail.isCompleted());

        assertNotNull(detail.items());
        assertEquals(1, detail.items().size());
        ItemDto item = detail.items().get(0);
        assertEquals(10L, item.id());
        assertEquals(1, item.position());
        assertEquals(StudyPlanItemKind.PROJECT, item.kind());
        assertTrue(item.isProject());
        assertFalse(item.isProblem());
        assertEquals("Mini Bank Console", item.title());
        assertEquals("Ngân hàng dòng lệnh", item.description());
        assertFalse(item.done());
        assertEquals(2, item.getStageCount());
        assertEquals(1, item.getDoneStageCount());

        assertNotNull(item.stages());
        assertEquals(2, item.stages().size());

        StageDto s1 = item.stages().get(0);
        assertEquals(1, s1.stageNo());
        assertEquals(101L, s1.problemId());
        assertEquals("mini-bank-stage-1", s1.slug());
        assertEquals("Tài khoản đơn", s1.title());
        assertEquals(Difficulty.EASY, s1.difficulty());
        assertEquals("Easy", s1.difficulty().getDisplayName());
        assertEquals(ProblemType.OOP_GRINDING, s1.problemType());
        assertTrue(s1.problemType().isOopGrinding());
        assertTrue(s1.published());
        assertEquals(StageStatus.DONE, s1.status());
        assertTrue(s1.isDone());
        assertFalse(s1.isLocked());
        assertTrue(s1.canAttempt());

        StageDto s2 = item.stages().get(1);
        assertEquals(2, s2.stageNo());
        assertEquals(102L, s2.problemId());
        assertEquals("mini-bank-stage-2", s2.slug());
        assertEquals("Kế thừa Account", s2.title());
        assertEquals(Difficulty.MEDIUM, s2.difficulty());
        assertEquals("Medium", s2.difficulty().getDisplayName());
        assertEquals(ProblemType.OOP_GRINDING, s2.problemType());
        assertTrue(s2.published());
        assertEquals(StageStatus.IN_PROGRESS, s2.status());
        assertTrue(s2.isInProgress());
        assertFalse(s2.isLocked());
        assertTrue(s2.canAttempt());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/study-plans/oop-with-java", request.getPath());
        assertEquals("Bearer " + TEST_PAT, request.getHeader("Authorization"));
    }

    @Test
    @DisplayName("testGetStudyPlan_withSpecialCharsInSlug_urlEncodesSlug: slug with space -> encoded as %20")
    void testGetStudyPlan_withSpecialCharsInSlug_urlEncodesSlug() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "id": 1,
                      "slug": "java basics",
                      "title": "Java Basics",
                      "status": "PUBLISHED",
                      "stageCount": 0,
                      "doneStages": 0,
                      "items": []
                    }
                    """));

        PlanDetail detail = client.getStudyPlan("java basics");
        assertNotNull(detail);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/v1/study-plans/java%20basics", request.getPath());
    }

    @Test
    @DisplayName("testAuthenticationFailure_401: 401 with RFC 7807 PAT_INVALID -> throws AuthenticationException")
    void testAuthenticationFailure_401() throws InterruptedException {
        String problemDetailJson = """
            {
              "type": "https://api.mini-leetcode.com/errors/unauthorized",
              "title": "Authentication Failed",
              "status": 401,
              "detail": "Invalid PAT",
              "code": "PAT_INVALID",
              "timestamp": "2026-11-09T03:43:53Z"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemDetailJson));

        AuthenticationException ex = assertThrows(AuthenticationException.class, () -> client.getStudyPlans());

        assertEquals("PAT_INVALID", ex.getCode(), "Exception code must be PAT_INVALID");
        assertEquals("PAT_INVALID", ex.getErrorCode());
        assertEquals(401, ex.getStatusCode(), "Status code must be 401");
        assertTrue(ex.getMessage().contains("Invalid PAT"), "Message must contain detail from server");
        assertNotNull(ex.getProblemDetail());
        assertEquals("https://api.mini-leetcode.com/errors/unauthorized", ex.getProblemDetail().type());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/v1/study-plans", request.getPath());
    }

    @Test
    @DisplayName("testAuthenticationFailure_401_withoutProblemDetail_fallback: plain text 401 -> fallback AuthenticationException")
    void testAuthenticationFailure_401_withoutProblemDetail_fallback() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "text/plain")
                .setBody("Unauthorized"));

        AuthenticationException ex = assertThrows(AuthenticationException.class, () -> client.getStudyPlans());

        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("HTTP 401"));
    }

    @Test
    @DisplayName("testForbidden_403: 403 with RFC 7807 PAT_FORBIDDEN -> throws MiniLeetCodeApiException")
    void testForbidden_403() {
        String problemDetailJson = """
            {
              "type": "https://api.mini-leetcode.com/errors/forbidden",
              "title": "Forbidden",
              "status": 403,
              "detail": "PAT cannot be used for contests",
              "code": "PAT_FORBIDDEN"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemDetailJson));

        MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());

        assertEquals(403, ex.getStatusCode());
        assertEquals("PAT_FORBIDDEN", ex.getCode());
        assertEquals("PAT_FORBIDDEN", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("PAT cannot be used for contests"));
    }

    @Test
    @DisplayName("testNotFound_404: 404 with RFC 7807 problem details -> throws MiniLeetCodeApiException")
    void testNotFound_404() throws InterruptedException {
        String problemDetailJson = """
            {
              "type": "https://api.mini-leetcode.com/errors/not-found",
              "title": "Resource Not Found",
              "status": 404,
              "detail": "Study plan not found: non-existent-slug"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemDetailJson));

        MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlan("non-existent-slug"));

        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("not found") || ex.getMessage().contains("non-existent-slug"),
                "Exception message should mention problem details");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/v1/study-plans/non-existent-slug", request.getPath());
    }

    @Test
    @DisplayName("testServerError_500: 500 plain text -> throws MiniLeetCodeApiException with status 500")
    void testServerError_500() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/plain")
                .setBody("Internal Server Error"));

        MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());

        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @DisplayName("testNetworkTimeoutOrConnectionError_Disconnect: Server disconnects at start -> throws MiniLeetCodeApiException")
    void testNetworkTimeoutOrConnectionError_Disconnect() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());

        assertNotNull(ex);
        assertNotNull(ex.getMessage(), "Exception should have meaningful error message");
        assertNotNull(ex.getCause(), "Root cause should be preserved (IOException)");
    }

    @Test
    @DisplayName("testNetworkTimeoutOrConnectionError_Timeout: Read timeout triggers MiniLeetCodeApiException")
    void testNetworkTimeoutOrConnectionError_Timeout() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        ApiClient fastTimeoutClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofMillis(200));

        MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, fastTimeoutClient::getStudyPlans);

        assertNotNull(ex);
        assertTrue(ex.getMessage().toLowerCase().contains("timeout")
                        || (ex.getCause() != null && ex.getCause().getClass().getSimpleName().contains("Timeout")),
                "Exception must indicate timeout condition");
    }

    @Test
    @DisplayName("testNetworkTimeoutOrConnectionError_ServerDown: Server down before request -> throws MiniLeetCodeApiException")
    void testNetworkTimeoutOrConnectionError_ServerDown() throws IOException {
        server.shutdown();

        MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlans());

        assertNotNull(ex);
    }

    @Test
    @DisplayName("testConnection_returnsTrueOnSuccess: 200 OK -> returns true")
    void testConnection_returnsTrueOnSuccess() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        assertTrue(client.testConnection());
    }

    @Test
    @DisplayName("testConstructor_normalizesBaseUrl: strips trailing slashes and validates input")
    void testConstructor_normalizesBaseUrl() {
        ApiClient c1 = new ApiClient("http://localhost:8080///", TEST_PAT);
        assertEquals("http://localhost:8080", c1.getBaseUrl());

        assertThrows(IllegalArgumentException.class, () -> new ApiClient(null, TEST_PAT));
        assertThrows(IllegalArgumentException.class, () -> new ApiClient("   ", TEST_PAT));
    }

    @Test
    @DisplayName("testGetStudyPlan_nullOrBlankSlug_throwsIllegalArgumentException")
    void testGetStudyPlan_nullOrBlankSlug_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan(null));
        assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan(""));
        assertThrows(IllegalArgumentException.class, () -> client.getStudyPlan("   "));
    }
}
