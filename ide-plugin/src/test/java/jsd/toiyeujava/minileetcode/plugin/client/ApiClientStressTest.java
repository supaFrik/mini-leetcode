package jsd.toiyeujava.minileetcode.plugin.client;

import jsd.toiyeujava.minileetcode.plugin.model.ProblemDto;
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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Network and Concurrency Stress Test Suite for {@link ApiClient}.
 * <p>
 * Evaluates operational bounds and resilience against:
 * <ol>
 *     <li>High concurrency bursts (50+ simultaneous multi-threaded requests).</li>
 *     <li>Large payload transfers (10MB binary ZIP archives, 5MB JSON structures, 10MB error bodies).</li>
 *     <li>Truncated network streams & abrupt socket terminations mid-transfer.</li>
 *     <li>Slow Lorris attacks and delayed chunked transmissions (within and exceeding timeout limits).</li>
 *     <li>Thread interruption and lifecycle handling during active HTTP transmission.</li>
 * </ol>
 */
class ApiClientStressTest {

    private MockWebServer server;
    private ApiClient client;
    private static final String TEST_PAT = "mlc_pat_stress_test_token_9876543210fedcba";

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
    // 1. High Concurrency Burst (50 Simultaneous Requests)
    // =========================================================================
    @Nested
    @DisplayName("1. High Concurrency Burst Stress Tests")
    class ConcurrencyBurstTests {

        @Test
        @DisplayName("1.1 testHighConcurrencyBurst_50Threads_MixedEndpoints_Success")
        void testHighConcurrencyBurst_50Threads_MixedEndpoints_Success() throws Exception {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            byte[] simulatedZip = createSimulatedZip(4096); // 4KB zip payload
            String problemJsonTemplate = """
                    {
                      "id": %d,
                      "title": "Concurrent Problem %d",
                      "slug": "concurrent-prob-%d",
                      "description": "Concurrent stress description %d",
                      "difficulty": "MEDIUM",
                      "type": "OOP_GRINDING",
                      "tags": ["concurrency", "stress"]
                    }
                    """;

            AtomicInteger zipRequestCounter = new AtomicInteger();
            AtomicInteger jsonRequestCounter = new AtomicInteger();

            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.endsWith("/starter-template")) {
                        zipRequestCounter.incrementAndGet();
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/zip")
                                .setBody(new Buffer().write(simulatedZip));
                    } else if (path != null && path.startsWith("/api/v1/problems/concurrent-prob-")) {
                        jsonRequestCounter.incrementAndGet();
                        String idStr = path.substring("/api/v1/problems/concurrent-prob-".length());
                        long id = Long.parseLong(idStr);
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(problemJsonTemplate.formatted(id, id, id, id));
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startGate.await(); // Hold all threads at barrier
                    if (idx % 2 == 0) {
                        byte[] zip = client.getStarterTemplate("concurrent-prob-" + idx);
                        assertNotNull(zip, "Thread " + idx + " received null zip");
                        assertEquals(0x50, zip[0] & 0xFF, "Magic byte 0");
                        assertEquals(0x4B, zip[1] & 0xFF, "Magic byte 1");
                        assertEquals(0x03, zip[2] & 0xFF, "Magic byte 2");
                        assertEquals(0x04, zip[3] & 0xFF, "Magic byte 3");
                        assertEquals(simulatedZip.length, zip.length);
                    } else {
                        ProblemDto prob = client.getProblem("concurrent-prob-" + idx);
                        assertNotNull(prob, "Thread " + idx + " received null problem");
                        assertEquals((long) idx, prob.id());
                        assertEquals("concurrent-prob-" + idx, prob.slug());
                        assertTrue(prob.isOopGrinding());
                        assertEquals(List.of("concurrency", "stress"), prob.tags());
                    }
                    return null;
                }));
            }

            // Release all 50 threads simultaneously
            startGate.countDown();

            // Wait for all 50 threads to complete within 15 seconds
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(25, zipRequestCounter.get(), "Must have completed 25 binary ZIP downloads");
            assertEquals(25, jsonRequestCounter.get(), "Must have completed 25 problem JSON requests");
        }

        @Test
        @DisplayName("1.2 testHighConcurrencyBurst_50Threads_ConcurrentAuth401Failures")
        void testHighConcurrencyBurst_50Threads_ConcurrentAuth401Failures() throws Exception {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            String rfc7807AuthError = """
                    {
                      "type": "https://api.minileetcode.com/errors/unauthorized",
                      "title": "Unauthorized",
                      "status": 401,
                      "detail": "PAT has expired or is revoked",
                      "instance": "/api/v1/problems/stress-401/starter-template",
                      "code": "PAT_INVALID"
                    }
                    """;

            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse()
                            .setResponseCode(401)
                            .setHeader("Content-Type", "application/problem+json")
                            .setBody(rfc7807AuthError);
                }
            });

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    AuthenticationException ex = assertThrows(
                            AuthenticationException.class,
                            () -> client.getStarterTemplate("stress-401-" + idx),
                            "Thread " + idx + " should throw AuthenticationException"
                    );
                    assertEquals("PAT_INVALID", ex.getCode());
                    assertEquals("PAT has expired or is revoked", ex.getMessage());
                    assertNotNull(ex.getProblemDetail());
                    assertEquals(401, ex.getProblemDetail().status());
                    return null;
                }));
            }

            startGate.countDown();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("1.3 testHighConcurrencyBurst_50Threads_Concurrent404And500Errors")
        void testHighConcurrencyBurst_50Threads_Concurrent404And500Errors() throws Exception {
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.contains("-404-")) {
                        return new MockResponse()
                                .setResponseCode(404)
                                .setHeader("Content-Type", "application/problem+json")
                                .setBody("""
                                        {
                                          "title": "Not Found",
                                          "status": 404,
                                          "detail": "Problem not found",
                                          "code": "PROBLEM_NOT_FOUND"
                                        }
                                        """);
                    } else {
                        return new MockResponse()
                                .setResponseCode(500)
                                .setHeader("Content-Type", "application/problem+json")
                                .setBody("""
                                        {
                                          "title": "Internal Server Error",
                                          "status": 500,
                                          "detail": "Database connection pool exhausted",
                                          "code": "DATABASE_ERROR"
                                        }
                                        """);
                    }
                }
            });

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    if (idx % 2 == 0) {
                        MiniLeetCodeApiException ex = assertThrows(
                                MiniLeetCodeApiException.class,
                                () -> client.getProblem("prob-404-" + idx)
                        );
                        assertEquals(404, ex.getStatusCode());
                        assertEquals("PROBLEM_NOT_FOUND", ex.getErrorCode());
                    } else {
                        MiniLeetCodeApiException ex = assertThrows(
                                MiniLeetCodeApiException.class,
                                () -> client.getStarterTemplate("prob-500-" + idx)
                        );
                        assertEquals(500, ex.getStatusCode());
                        assertEquals("DATABASE_ERROR", ex.getErrorCode());
                        assertTrue(ex.getMessage().contains("Database connection pool exhausted"));
                    }
                    return null;
                }));
            }

            startGate.countDown();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("1.4 testHighConcurrencyBurst_MultiClientPerThread_NoResourceExhaustion")
        void testHighConcurrencyBurst_MultiClientPerThread_NoResourceExhaustion() throws Exception {
            int threadCount = 30;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(threadCount);

            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/zip")
                            .setBody(new Buffer().write(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x01, 0x02}));
                }
            });

            String baseUrl = server.url("/").toString();

            // Each thread instantiates and uses its own ApiClient
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    ApiClient localClient = new ApiClient(baseUrl, "token-" + idx, Duration.ofSeconds(5));
                    byte[] bytes = localClient.getStarterTemplate("prob-" + idx);
                    assertNotNull(bytes);
                    assertEquals(6, bytes.length);
                    return null;
                }));
            }

            startGate.countDown();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    // =========================================================================
    // 2. Large Payload Transfers (10MB binary, 5MB JSON, 10MB error)
    // =========================================================================
    @Nested
    @DisplayName("2. Large Payload Stress Tests")
    class LargePayloadTests {

        @Test
        @DisplayName("2.1 testLargeBinaryPayload_10MbZipArchive_ByteIntegrityAndNoOom")
        void testLargeBinaryPayload_10MbZipArchive_ByteIntegrityAndNoOom() throws InterruptedException {
            int payloadSize = 10 * 1024 * 1024; // Exactly 10MB (10,485,760 bytes)
            byte[] largeZipBytes = new byte[payloadSize];

            // Set ZIP header magic bytes
            largeZipBytes[0] = 0x50; // 'P'
            largeZipBytes[1] = 0x4B; // 'K'
            largeZipBytes[2] = 0x03;
            largeZipBytes[3] = 0x04;

            // Fill deterministic pseudo-random sequence for data integrity assertion
            for (int i = 4; i < payloadSize; i++) {
                largeZipBytes[i] = (byte) ((i * 31 + 17) & 0xFF);
            }

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setHeader("Content-Disposition", "attachment; filename=\"large-archive-10mb.zip\"")
                    .setBody(new Buffer().write(largeZipBytes)));

            long startTime = System.currentTimeMillis();
            byte[] actualBytes = client.getStarterTemplate("large-problem-10mb");
            long durationMs = System.currentTimeMillis() - startTime;

            assertNotNull(actualBytes, "Downloaded 10MB bytes must not be null");
            assertEquals(payloadSize, actualBytes.length, "Downloaded byte array must match 10MB length exactly");

            // Verify header
            assertEquals(0x50, actualBytes[0] & 0xFF);
            assertEquals(0x4B, actualBytes[1] & 0xFF);
            assertEquals(0x03, actualBytes[2] & 0xFF);
            assertEquals(0x04, actualBytes[3] & 0xFF);

            // Verify sampled offsets across the 10MB buffer to verify byte-for-byte fidelity
            int[] sampleOffsets = new int[]{
                    4, 1024, 65536, 1048576, 5242880, 8388608, payloadSize - 1
            };
            for (int offset : sampleOffsets) {
                assertEquals(largeZipBytes[offset], actualBytes[offset],
                        "Byte mismatch at offset " + offset);
            }

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("GET", req.getMethod());
            assertEquals("/api/v1/problems/large-problem-10mb/starter-template", req.getPath());
            assertEquals("application/zip", req.getHeader("Accept"));
        }

        @Test
        @DisplayName("2.2 testLargeJsonPayload_5MbProblemDescription_ParsesSuccessfully")
        void testLargeJsonPayload_5MbProblemDescription_ParsesSuccessfully() throws InterruptedException {
            int descriptionLength = 5 * 1024 * 1024; // 5MB description text
            char[] descChars = new char[descriptionLength];
            Arrays.fill(descChars, 'A');
            String hugeDescription = new String(descChars);

            String largeJson = "{"
                    + "\"id\":99999,"
                    + "\"title\":\"Huge Problem\","
                    + "\"slug\":\"huge-problem\","
                    + "\"description\":\"" + hugeDescription + "\","
                    + "\"difficulty\":\"HARD\","
                    + "\"type\":\"OOP_GRINDING\""
                    + "}";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(largeJson));

            ProblemDto dto = client.getProblem("huge-problem");
            assertNotNull(dto);
            assertEquals(99999L, dto.id());
            assertEquals("Huge Problem", dto.title());
            assertEquals("huge-problem", dto.slug());
            assertEquals(descriptionLength, dto.description().length());
            assertEquals('A', dto.description().charAt(0));
            assertEquals('A', dto.description().charAt(descriptionLength - 1));

            RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(req);
            assertEquals("/api/v1/problems/huge-problem", req.getPath());
        }

        @Test
        @DisplayName("2.3 testLargePayload_10MbServerErrorBody_HandledGracefullyWithoutOom")
        void testLargePayload_10MbServerErrorBody_HandledGracefullyWithoutOom() {
            int errorBodySize = 10 * 1024 * 1024; // 10MB server 500 error body
            byte[] errorBytes = new byte[errorBodySize];
            Arrays.fill(errorBytes, (byte) 'E');

            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody(new Buffer().write(errorBytes)));

            MiniLeetCodeApiException ex = assertThrows(
                    MiniLeetCodeApiException.class,
                    () -> client.getStarterTemplate("problem-server-dump")
            );

            assertEquals(500, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("500"));
        }
    }

    // =========================================================================
    // 3. Truncated Streams & Abrupt Connection Drops (No Hanging)
    // =========================================================================
    @Nested
    @DisplayName("3. Truncated Stream & Network Disconnect Tests")
    class TruncatedStreamTests {

        @Test
        @DisplayName("3.1 testTruncatedBinaryStream_DisconnectDuringResponseBody_ThrowsApiExceptionPromptly")
        void testTruncatedBinaryStream_DisconnectDuringResponseBody_ThrowsApiExceptionPromptly() {
            // Buffer with 1MB data, server forcibly cuts socket mid-stream
            byte[] partialData = new byte[1024 * 1024];
            Arrays.fill(partialData, (byte) 0x50);

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBody(new Buffer().write(partialData))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

            // Must throw MiniLeetCodeApiException without hanging (preemptive timeout of 5 seconds)
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> client.getStarterTemplate("truncated-problem")
                );
                assertNotNull(ex.getMessage());
                assertTrue(ex.getCause() instanceof IOException,
                        "Root cause must be an IOException, was: " + (ex.getCause() != null ? ex.getCause().getClass() : "null"));
            });
        }

        @Test
        @DisplayName("3.2 testTruncatedStream_DisconnectAtStart_ThrowsApiExceptionPromptly")
        void testTruncatedStream_DisconnectAtStart_ThrowsApiExceptionPromptly() {
            // Use 1s client timeout so connection retries and drops resolve promptly
            ApiClient fastFailClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(1));

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

            assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> fastFailClient.getStarterTemplate("disconnect-start")
                );
                assertNotNull(ex);
            });
        }

        @Test
        @DisplayName("3.3 testTruncatedStream_DisconnectAfterRequest_ThrowsApiExceptionPromptly")
        void testTruncatedStream_DisconnectAfterRequest_ThrowsApiExceptionPromptly() {
            ApiClient fastFailClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(1));

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

            assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> fastFailClient.getProblem("disconnect-after-req")
                );
                assertNotNull(ex);
            });
        }

        @Test
        @DisplayName("3.4 testTruncatedJsonStream_DisconnectDuringResponseBody_ThrowsApiExceptionPromptly")
        void testTruncatedJsonStream_DisconnectDuringResponseBody_ThrowsApiExceptionPromptly() {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\": 1, \"title\": \"Incomplete json that never ends...")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> client.getProblem("truncated-json")
                );
                assertTrue(ex.getCause() instanceof IOException);
            });
        }
    }

    // =========================================================================
    // 4. Slow Lorris & Delayed Packets (Within and Exceeding Timeout)
    // =========================================================================
    @Nested
    @DisplayName("4. Slow Lorris & Timeout Boundary Tests")
    class SlowLorrisAndTimeoutTests {

        @Test
        @DisplayName("4.1 testDelayedPacketResponse_WithinTimeout_Succeeds")
        void testDelayedPacketResponse_WithinTimeout_Succeeds() {
            // Client configured with 4 seconds timeout
            ApiClient tolerantClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(4));

            byte[] simulatedZip = createSimulatedZip(1024);

            // Server delays body response by 1 second (well within 4s timeout)
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBodyDelay(1, TimeUnit.SECONDS)
                    .setBody(new Buffer().write(simulatedZip)));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                byte[] actual = tolerantClient.getStarterTemplate("delayed-ok");
                assertNotNull(actual);
                assertArrayEquals(simulatedZip, actual);
            });
        }

        @Test
        @DisplayName("4.2 testDelayedPacketResponse_ExceedingTimeout_ThrowsTimeoutException")
        void testDelayedPacketResponse_ExceedingTimeout_ThrowsTimeoutException() {
            // Client configured with 1 second timeout
            ApiClient tightClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(1));

            // Server delays headers by 3 seconds (exceeds 1s timeout)
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setHeadersDelay(3, TimeUnit.SECONDS)
                    .setBody(new Buffer().write(new byte[]{0x50, 0x4B, 0x03, 0x04})));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> tightClient.getStarterTemplate("delayed-exceed")
                );
                assertTrue(ex.getMessage().contains("timed out") || ex.getMessage().contains("timeout"),
                        "Message should indicate timeout: " + ex.getMessage());
                assertTrue(ex.getCause() instanceof HttpTimeoutException,
                        "Root cause must be HttpTimeoutException, was: " + ex.getCause());
            });
        }

        @Test
        @DisplayName("4.3 testSlowLorris_NoResponse_TimeoutsCleanlyWithoutHanging")
        void testSlowLorris_NoResponse_TimeoutsCleanlyWithoutHanging() {
            // Client configured with 1 second timeout
            ApiClient tightClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(1));

            server.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> tightClient.getProblem("slow-lorris-no-response")
                );
                assertTrue(ex.getMessage().contains("timed out") || ex.getMessage().contains("timeout"));
                assertTrue(ex.getCause() instanceof HttpTimeoutException);
            });
        }

        @Test
        @DisplayName("4.4 testSlowLorris_ThrottledChunkTrickle_ExceedingTimeout_EmpiricalObservation")
        void testSlowLorris_ThrottledChunkTrickle_ExceedingTimeout_EmpiricalObservation() {
            // Client configured with 1 second timeout
            ApiClient tightClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(1));

            // Throttle body: 1 byte every 300ms for 10 bytes (takes 3 seconds total)
            byte[] tenBytes = new byte[10];
            Arrays.fill(tenBytes, (byte) 'X');

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .throttleBody(1, 300, TimeUnit.MILLISECONDS)
                    .setBody(new Buffer().write(tenBytes)));

            long start = System.currentTimeMillis();
            // Empirical observation: JDK java.net.http.HttpRequest.timeout only bounds TTFB (time to headers).
            // Once HTTP 200 headers are received, BodyHandlers.ofByteArray() continues reading trickling bytes.
            byte[] bytes = tightClient.getStarterTemplate("slow-lorris-trickle");
            long elapsed = System.currentTimeMillis() - start;

            assertNotNull(bytes);
            assertEquals(10, bytes.length);
            assertTrue(elapsed >= 2500, "Body trickle took " + elapsed + "ms, showing JDK HttpClient does not abort active chunked trickle once headers are accepted");
        }

        @Test
        @DisplayName("4.5 testDelayedHeaders_ExceedingTimeout_ThrowsTimeoutExceptionPromptly")
        void testDelayedHeaders_ExceedingTimeout_ThrowsTimeoutExceptionPromptly() {
            // Client configured with 1 second timeout
            ApiClient tightClient = new ApiClient(server.url("/").toString(), TEST_PAT, Duration.ofSeconds(1));

            // Server stalls sending headers by 3 seconds
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setHeadersDelay(3, TimeUnit.SECONDS)
                    .setBody(new Buffer().write(new byte[]{0x50, 0x4B, 0x03, 0x04})));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                MiniLeetCodeApiException ex = assertThrows(
                        MiniLeetCodeApiException.class,
                        () -> tightClient.getStarterTemplate("delayed-headers-timeout")
                );
                assertTrue(ex.getMessage().contains("timed out") || ex.getMessage().contains("timeout"));
                assertTrue(ex.getCause() instanceof HttpTimeoutException);
            });
        }
    }

    // =========================================================================
    // 5. Thread Interruption Resilience
    // =========================================================================
    @Nested
    @DisplayName("5. Thread Interruption Handling")
    class InterruptionTests {

        @Test
        @DisplayName("5.1 testThreadInterruption_DuringBinaryDownload_RestoresInterruptFlagAndThrows")
        void testThreadInterruption_DuringBinaryDownload_RestoresInterruptFlagAndThrows() throws Exception {
            // Slow response to give time to interrupt
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/zip")
                    .setBodyDelay(5, TimeUnit.SECONDS)
                    .setBody(new Buffer().write(new byte[]{0x50, 0x4B, 0x03, 0x04})));

            CountDownLatch startedLatch = new CountDownLatch(1);
            AtomicInteger exceptionCaught = new AtomicInteger();
            AtomicInteger interruptedFlagSet = new AtomicInteger();

            Thread workerThread = new Thread(() -> {
                try {
                    startedLatch.countDown();
                    client.getStarterTemplate("interrupt-test");
                } catch (MiniLeetCodeApiException e) {
                    exceptionCaught.incrementAndGet();
                    if (Thread.currentThread().isInterrupted()) {
                        interruptedFlagSet.incrementAndGet();
                    }
                    if (e.getMessage().contains("interrupted")) {
                        exceptionCaught.incrementAndGet();
                    }
                }
            });

            workerThread.start();
            assertTrue(startedLatch.await(2, TimeUnit.SECONDS));

            // Wait a brief moment to ensure request is inflight
            Thread.sleep(100);

            // Interrupt worker thread
            workerThread.interrupt();
            workerThread.join(4000);

            assertFalse(workerThread.isAlive(), "Worker thread must terminate cleanly");
            assertTrue(exceptionCaught.get() >= 1, "Must catch MiniLeetCodeApiException on interruption");
            assertEquals(1, interruptedFlagSet.get(), "Thread interrupt flag must be preserved");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================
    private static byte[] createSimulatedZip(int totalSize) {
        if (totalSize < 4) totalSize = 4;
        byte[] bytes = new byte[totalSize];
        bytes[0] = 0x50; // 'P'
        bytes[1] = 0x4B; // 'K'
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        for (int i = 4; i < totalSize; i++) {
            bytes[i] = (byte) (i & 0xFF);
        }
        return bytes;
    }
}
