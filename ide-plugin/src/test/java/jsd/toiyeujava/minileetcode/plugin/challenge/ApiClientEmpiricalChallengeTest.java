package jsd.toiyeujava.minileetcode.plugin.challenge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import jsd.toiyeujava.minileetcode.plugin.model.*;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Stress Test Suite for Milestone 1:
 * - Multi-threaded concurrent ApiClient execution sharing single instance
 * - Large payload deserialization (50 items, 200 stages, 50KB strings)
 * - Extreme boundary values (negatives, overflows, null fields, extra JSON keys, unknown enums)
 * - Record immutability and defensive copying
 * - Comprehensive HTTP status error mapping (400, 401, 403, 404, 429, 500, 502, 503, 504)
 */
class ApiClientEmpiricalChallengeTest {

    private MockWebServer server;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    // =========================================================================
    // 1. CONCURRENT EXECUTION STRESS TESTING
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: 30 concurrent threads sharing single ApiClient making mixed requests")
    void testConcurrentExecution_30Threads_SharingSingleClient() throws Exception {
        int threadCount = 30;
        int requestsPerThread = 5;
        int totalRequests = threadCount * requestsPerThread;

        String listJson = """
            [
              {"id": 1, "slug": "concurrent-plan-1", "title": "Plan 1", "status": "PUBLISHED", "itemCount": 2, "stageCount": 5, "doneStages": 2},
              {"id": 2, "slug": "concurrent-plan-2", "title": "Plan 2", "status": "PUBLISHED", "itemCount": 3, "stageCount": 10, "doneStages": 5}
            ]
            """;

        String detailJson = """
            {
              "id": 1,
              "slug": "concurrent-plan-1",
              "title": "Plan 1",
              "status": "PUBLISHED",
              "stageCount": 2,
              "doneStages": 1,
              "items": [
                {
                  "id": 10,
                  "position": 1,
                  "kind": "PROJECT",
                  "title": "Item 1",
                  "stages": [
                    {"stageNo": 1, "problemId": 101, "slug": "s-1", "title": "Stage 1", "difficulty": "EASY", "problemType": "OOP_GRINDING", "published": true, "status": "DONE"}
                  ]
                }
              ]
            }
            """;

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.equals("/api/v1/study-plans")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(listJson);
                } else if (path != null && path.startsWith("/api/v1/study-plans/")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(detailJson);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        ApiClient sharedClient = new ApiClient(server.url("/").toString(), "test-token", Duration.ofSeconds(10));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Thundering herd start
                    for (int j = 0; j < requestsPerThread; j++) {
                        if ((threadId + j) % 2 == 0) {
                            List<PlanSummary> plans = sharedClient.getStudyPlans();
                            assertEquals(2, plans.size());
                        } else {
                            PlanDetail detail = sharedClient.getStudyPlan("concurrent-plan-1");
                            assertEquals("concurrent-plan-1", detail.slug());
                            assertEquals(1, detail.items().size());
                        }
                        successCount.incrementAndGet();
                        doneLatch.countDown();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent requests should finish within 30 seconds");
        assertTrue(errors.isEmpty(), "Zero errors expected during concurrent calls, but got: " + errors);
        assertEquals(totalRequests, successCount.get(), "All 150 requests must succeed");
    }

    @Test
    @DisplayName("Challenge 1.2: Concurrent testConnection() calls under load")
    void testConcurrentTestConnection_20Threads() throws Exception {
        int threadCount = 20;

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("[]");
            }
        });
        server.start();

        ApiClient sharedClient = new ApiClient(server.url("/").toString(), "valid-pat");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean ok = sharedClient.testConnection();
                    if (ok) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, successCount.get(), "All 20 concurrent testConnection() calls must return true");
    }

    @Test
    @DisplayName("Challenge 1.3: Concurrent error handling preserves thread isolation and exception types")
    void testConcurrentErrorHandling_PreservesThreadIsolation() throws Exception {
        int threadCount = 16;

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.contains("auth-fail")) {
                    return new MockResponse()
                            .setResponseCode(401)
                            .setHeader("Content-Type", "application/problem+json")
                            .setBody("{\"status\":401,\"code\":\"PAT_INVALID\",\"detail\":\"Invalid token\"}");
                } else if (path != null && path.contains("forbidden")) {
                    return new MockResponse()
                            .setResponseCode(403)
                            .setHeader("Content-Type", "application/problem+json")
                            .setBody("{\"status\":403,\"code\":\"PAT_FORBIDDEN\",\"detail\":\"Forbidden\"}");
                } else if (path != null && path.contains("not-found")) {
                    return new MockResponse()
                            .setResponseCode(404)
                            .setHeader("Content-Type", "application/problem+json")
                            .setBody("{\"status\":404,\"code\":\"NOT_FOUND\",\"detail\":\"Not found\"}");
                }
                return new MockResponse().setResponseCode(500).setBody("Server Error");
            }
        });
        server.start();

        ApiClient client = new ApiClient(server.url("/").toString(), "any-pat");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger authFailures = new AtomicInteger(0);
        AtomicInteger apiExceptions = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int mode = i % 4;
            executor.submit(() -> {
                try {
                    switch (mode) {
                        case 0 -> {
                            assertThrows(AuthenticationException.class, () -> client.getStudyPlan("auth-fail"));
                            authFailures.incrementAndGet();
                        }
                        case 1 -> {
                            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlan("forbidden"));
                            assertEquals(403, ex.getStatusCode());
                            apiExceptions.incrementAndGet();
                        }
                        case 2 -> {
                            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlan("not-found"));
                            assertEquals(404, ex.getStatusCode());
                            apiExceptions.incrementAndGet();
                        }
                        case 3 -> {
                            MiniLeetCodeApiException ex = assertThrows(MiniLeetCodeApiException.class, () -> client.getStudyPlan("server-err"));
                            assertEquals(500, ex.getStatusCode());
                            apiExceptions.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(4, authFailures.get());
        assertEquals(12, apiExceptions.get());
    }

    // =========================================================================
    // 2. LARGE PAYLOAD DESERIALIZATION STRESS TESTING
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: Deserialization of massive study plan (50 items, 200 stages)")
    void testLargePayloadDeserialization_50Items_200Stages() throws Exception {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n")
                .append("  \"id\": 999,\n")
                .append("  \"slug\": \"mega-study-plan\",\n")
                .append("  \"title\": \"Mega Master Study Plan\",\n")
                .append("  \"description\": \"Comprehensive full curriculum\",\n")
                .append("  \"status\": \"PUBLISHED\",\n")
                .append("  \"stageCount\": 200,\n")
                .append("  \"doneStages\": 150,\n")
                .append("  \"items\": [\n");

        int globalStageCount = 0;
        int doneStageCount = 0;
        for (int i = 1; i <= 50; i++) {
            if (i > 1) jsonBuilder.append(",\n");
            jsonBuilder.append("    {\n")
                    .append("      \"id\": ").append(i).append(",\n")
                    .append("      \"position\": ").append(i).append(",\n")
                    .append("      \"kind\": \"").append(i % 2 == 0 ? "PROJECT" : "PROBLEM").append("\",\n")
                    .append("      \"title\": \"Curriculum Item #").append(i).append("\",\n")
                    .append("      \"description\": \"Description for item ").append(i).append("\",\n")
                    .append("      \"done\": ").append(i <= 35).append(",\n")
                    .append("      \"stages\": [\n");

            for (int s = 1; s <= 4; s++) {
                globalStageCount++;
                boolean isDone = globalStageCount <= 150;
                if (isDone) doneStageCount++;
                String status = isDone ? "DONE" : (globalStageCount == 151 ? "IN_PROGRESS" : "LOCKED");
                if (s > 1) jsonBuilder.append(",\n");
                jsonBuilder.append("        {\n")
                        .append("          \"stageNo\": ").append(s).append(",\n")
                        .append("          \"problemId\": ").append(1000 + globalStageCount).append(",\n")
                        .append("          \"slug\": \"stage-slug-").append(globalStageCount).append("\",\n")
                        .append("          \"title\": \"Stage Exercise ").append(globalStageCount).append("\",\n")
                        .append("          \"difficulty\": \"").append(s == 1 ? "EASY" : (s <= 3 ? "MEDIUM" : "HARD")).append("\",\n")
                        .append("          \"problemType\": \"OOP_GRINDING\",\n")
                        .append("          \"published\": true,\n")
                        .append("          \"status\": \"").append(status).append("\"\n")
                        .append("        }");
            }
            jsonBuilder.append("\n      ]\n    }");
        }
        jsonBuilder.append("\n  ]\n}");

        String massiveJson = jsonBuilder.toString();
        assertEquals(200, globalStageCount);
        assertEquals(150, doneStageCount);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(massiveJson));
        server.start();

        ApiClient client = new ApiClient(server.url("/").toString(), "token");

        long start = System.nanoTime();
        PlanDetail detail = client.getStudyPlan("mega-study-plan");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(detail);
        assertEquals(999L, detail.id());
        assertEquals("mega-study-plan", detail.slug());
        assertEquals(50, detail.items().size(), "Must parse exactly 50 items");
        assertEquals(50, detail.getItemCount());
        assertEquals(75, detail.getProgressPercentage(), "150/200 = 75%");
        assertFalse(detail.isCompleted());

        int computedTotalStages = 0;
        int computedDoneStages = 0;
        for (ItemDto item : detail.items()) {
            computedTotalStages += item.getStageCount();
            computedDoneStages += item.getDoneStageCount();
        }
        assertEquals(200, computedTotalStages, "Sum of stages across all items must be 200");
        assertEquals(150, computedDoneStages, "Sum of done stages across all items must be 150");

        // Verify deserialization latency is reasonable (< 1500 ms)
        assertTrue(elapsedMs < 1500, "Large payload deserialization took " + elapsedMs + "ms, expected < 1500ms");
    }

    @Test
    @DisplayName("Challenge 2.2: Deserialization with very large strings (50KB description)")
    void testLargePayloadWithHugeDescription() throws Exception {
        String hugeDescription = "A".repeat(50_000);
        String json = """
            {
              "id": 1,
              "slug": "huge-desc-plan",
              "title": "Huge Desc Plan",
              "description": "%s",
              "status": "PUBLISHED",
              "stageCount": 0,
              "doneStages": 0,
              "items": []
            }
            """.formatted(hugeDescription);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));
        server.start();

        ApiClient client = new ApiClient(server.url("/").toString(), "token");
        PlanDetail detail = client.getStudyPlan("huge-desc-plan");

        assertNotNull(detail);
        assertEquals(50_000, detail.description().length());
        assertEquals(hugeDescription, detail.description());
    }

    // =========================================================================
    // 3. EXTREME BOUNDARY CONDITIONS & DEFENSIVE RESILIENCE
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: Null fields in JSON default safely without NullPointerException")
    void testBoundary_NullFieldsInJson() {
        String minimalPlanDetailJson = """
            {
              "id": 42
            }
            """;

        PlanDetail detail = gson.fromJson(minimalPlanDetailJson, PlanDetail.class);

        assertNotNull(detail);
        assertEquals(42L, detail.id());
        assertEquals("", detail.slug(), "Null slug should default to empty string");
        assertEquals("", detail.title(), "Null title should default to empty string");
        assertEquals("", detail.description(), "Null description should default to empty string");
        assertNull(detail.status());
        assertEquals(0, detail.stageCount());
        assertEquals(0, detail.doneStages());
        assertNotNull(detail.items(), "Null items should default to empty list");
        assertTrue(detail.items().isEmpty());
        assertEquals(0, detail.getItemCount());
        assertEquals(0, detail.getProgressPercentage());
        assertFalse(detail.isCompleted());
    }

    @Test
    @DisplayName("Challenge 3.2: Negative numbers, zero, and overflow in progress calculations")
    void testBoundary_NegativeAndOverflowProgressCalculations() {
        // Zero stages
        PlanSummary zeroStages = new PlanSummary(1L, "s", "t", "d", StudyPlanStatus.PUBLISHED, 0, 0, 0);
        assertEquals(0, zeroStages.getProgressPercentage());
        assertFalse(zeroStages.isCompleted());

        // Negative stage count
        PlanSummary negativeStages = new PlanSummary(1L, "s", "t", "d", StudyPlanStatus.PUBLISHED, 0, -5, 2);
        assertEquals(0, negativeStages.getProgressPercentage());
        assertFalse(negativeStages.isCompleted());

        // Negative done stages
        PlanSummary negativeDone = new PlanSummary(1L, "s", "t", "d", StudyPlanStatus.PUBLISHED, 1, 10, -3);
        assertEquals(0, negativeDone.getProgressPercentage());
        assertFalse(negativeDone.isCompleted());

        // Done stages exceeding stage count (overflow)
        PlanSummary overflow = new PlanSummary(1L, "s", "t", "d", StudyPlanStatus.PUBLISHED, 1, 10, 15);
        assertEquals(100, overflow.getProgressPercentage(), "Progress percentage must cap at 100%");
        assertTrue(overflow.isCompleted());

        // Exactly completed
        PlanSummary exact = new PlanSummary(1L, "s", "t", "d", StudyPlanStatus.PUBLISHED, 1, 10, 10);
        assertEquals(100, exact.getProgressPercentage());
        assertTrue(exact.isCompleted());

        // Large integer values
        PlanSummary large = new PlanSummary(1L, "s", "t", "d", StudyPlanStatus.PUBLISHED, 1, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(100, large.getProgressPercentage());
        assertTrue(large.isCompleted());
    }

    @Test
    @DisplayName("Challenge 3.3: Unexpected extra JSON keys from future API versions do not break parsing")
    void testBoundary_UnexpectedExtraJsonKeys() {
        String jsonWithExtraFields = """
            {
              "id": 1,
              "slug": "plan-extra",
              "title": "Extra Fields Plan",
              "description": "Forward compatibility test",
              "status": "PUBLISHED",
              "stageCount": 1,
              "doneStages": 0,
              "extra_unsupported_field": "some_value",
              "nested_unknown_object": {
                "author": "admin",
                "version": 2
              },
              "unknown_list": [1, 2, 3],
              "items": [
                {
                  "id": 100,
                  "position": 1,
                  "kind": "PROBLEM",
                  "title": "Item with extra fields",
                  "future_flag": true,
                  "stages": []
                }
              ]
            }
            """;

        PlanDetail detail = gson.fromJson(jsonWithExtraFields, PlanDetail.class);

        assertNotNull(detail);
        assertEquals(1L, detail.id());
        assertEquals("plan-extra", detail.slug());
        assertEquals(1, detail.items().size());
        assertEquals("Item with extra fields", detail.items().get(0).title());
    }

    @Test
    @DisplayName("Challenge 3.4: Unknown enum values deserialize gracefully without crashing")
    void testBoundary_UnknownEnumValues() {
        String json = """
            {
              "stageNo": 1,
              "problemId": 100,
              "slug": "stage-unknown-enum",
              "title": "Unknown Enums",
              "difficulty": "ULTRA_NIGHTMARE",
              "problemType": "SYSTEM_DESIGN",
              "published": true,
              "status": "ARCHIVED_V2"
            }
            """;

        // Gson deserializes unknown enum values to null
        StageDto stage = gson.fromJson(json, StageDto.class);

        assertNotNull(stage);
        assertEquals(1, stage.stageNo());
        // Since Gson invokes the canonical constructor with null for unknown enums,
        // the StageDto compact constructor defaults them defensively!
        assertEquals(Difficulty.EASY, stage.difficulty(), "Unknown difficulty should default to EASY");
        assertEquals(ProblemType.OOP_GRINDING, stage.problemType(), "Unknown problemType should default to OOP_GRINDING");
        assertEquals(StageStatus.LOCKED, stage.status(), "Unknown status should default to LOCKED");
    }

    @Test
    @DisplayName("Challenge 3.5: HTTP Status Codes Matrix (400, 429, 502, 503, 504, malformed error body)")
    void testBoundary_HttpStatusCodesMatrix() {
        int[] statuses = {400, 429, 502, 503, 504};
        String[] errorBodies = {
                "{\"status\":400,\"code\":\"BAD_REQUEST\",\"detail\":\"Malformed query\"}",
                "{\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\",\"detail\":\"Too many requests\"}",
                "<html><body>502 Bad Gateway</body></html>", // Non-JSON HTML error
                "{\"broken_json\":", // Truncated/corrupted JSON
                "" // Empty response body
        };

        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(statuses[i])
                    .setBody(errorBodies[i]));
        }

        ApiClient client = new ApiClient(server.url("/").toString(), "token");

        // 400 with RFC 7807
        MiniLeetCodeApiException ex400 = assertThrows(MiniLeetCodeApiException.class, client::getStudyPlans);
        assertEquals(400, ex400.getStatusCode());
        assertEquals("BAD_REQUEST", ex400.getCode());
        assertEquals("Malformed query", ex400.getMessage());

        // 429 Rate Limit
        MiniLeetCodeApiException ex429 = assertThrows(MiniLeetCodeApiException.class, client::getStudyPlans);
        assertEquals(429, ex429.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", ex429.getCode());

        // 502 Bad Gateway with HTML body
        MiniLeetCodeApiException ex502 = assertThrows(MiniLeetCodeApiException.class, client::getStudyPlans);
        assertEquals(502, ex502.getStatusCode());
        assertTrue(ex502.getMessage().contains("502"));

        // 503 with truncated JSON
        MiniLeetCodeApiException ex503 = assertThrows(MiniLeetCodeApiException.class, client::getStudyPlans);
        assertEquals(503, ex503.getStatusCode());
        assertTrue(ex503.getMessage().contains("503"));

        // 504 with empty body
        MiniLeetCodeApiException ex504 = assertThrows(MiniLeetCodeApiException.class, client::getStudyPlans);
        assertEquals(504, ex504.getStatusCode());
        assertTrue(ex504.getMessage().contains("504"));
    }

    // =========================================================================
    // 4. RECORD IMMUTABILITY & DEFENSIVE COPYING
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: PlanDetail and ItemDto enforce defensive copying against list mutation")
    void testRecordImmutability_DefensiveCopying() {
        StageDto stage = new StageDto(1, 101L, "s-1", "Stage 1", Difficulty.EASY, ProblemType.OOP_GRINDING, true, StageStatus.DONE);

        List<StageDto> mutableStageList = new ArrayList<>();
        mutableStageList.add(stage);

        ItemDto item = new ItemDto(10L, 1, StudyPlanItemKind.PROJECT, "Project 1", "Desc", false, mutableStageList);

        // Mutate original list after construction
        mutableStageList.clear();

        // Verify item.stages() still contains the stage
        assertEquals(1, item.stages().size(), "ItemDto must retain defensive copy when original list is mutated");
        assertEquals("s-1", item.stages().get(0).slug());

        // Verify returned list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> item.stages().add(stage),
                "ItemDto.stages() must be unmodifiable");

        List<ItemDto> mutableItemList = new ArrayList<>();
        mutableItemList.add(item);

        PlanDetail plan = new PlanDetail(1L, "plan-1", "Title", "Desc", StudyPlanStatus.PUBLISHED, 1, 1, mutableItemList);

        // Mutate original list after construction
        mutableItemList.clear();

        // Verify plan.items() still contains the item
        assertEquals(1, plan.items().size(), "PlanDetail must retain defensive copy when original list is mutated");

        // Verify returned list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> plan.items().add(item),
                "PlanDetail.items() must be unmodifiable");
    }

    @Test
    @DisplayName("Challenge 4.2: Records are genuine Java records with correct equality and hashing")
    void testRecordContracts_IsRecordAndEquality() {
        assertTrue(PlanSummary.class.isRecord());
        assertTrue(PlanDetail.class.isRecord());
        assertTrue(ItemDto.class.isRecord());
        assertTrue(StageDto.class.isRecord());

        PlanSummary p1 = new PlanSummary(1L, "slug", "Title", "Desc", StudyPlanStatus.PUBLISHED, 2, 5, 2);
        PlanSummary p2 = new PlanSummary(1L, "slug", "Title", "Desc", StudyPlanStatus.PUBLISHED, 2, 5, 2);
        PlanSummary p3 = new PlanSummary(2L, "slug", "Title", "Desc", StudyPlanStatus.PUBLISHED, 2, 5, 2);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
    }
}
