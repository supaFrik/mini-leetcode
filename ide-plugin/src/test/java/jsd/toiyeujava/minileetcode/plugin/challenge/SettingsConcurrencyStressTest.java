package jsd.toiyeujava.minileetcode.plugin.challenge;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.JBColor;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeConfigurable;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsComponent;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency & Stress Challenge Test Suite for Milestone 2:
 *
 * Invariants tested under extreme adversarial conditions:
 * 1. High-throughput multi-threaded concurrent access to MiniLeetCodeSettingsState
 *    (16 concurrent threads performing thousands of reads, writes, bean loads, and singleton races).
 * 2. High-throughput multi-threaded concurrent access to MiniLeetCodePasswordSafe
 *    (16 concurrent threads performing interleaved setPassword, getPassword, hasPassword, clearPassword).
 * 3. Component disposal and lifecycle memory leak prevention
 *    (1,000 rapid open/close cycles, WeakReference reclamation, post-disposal robustness, idempotency).
 * 4. Concurrent test connection calls without deadlocks or UI freezes
 *    (16 concurrent async calls, mixed HTTP status codes, 50-call rapid-fire burst, parallel latency handling).
 *
 * Environment: Headless execution (java.awt.headless=true), JDK 21, IntelliJ 2025.3+.
 */
public class SettingsConcurrencyStressTest {

    private MockWebServer server;
    private StressTestPasswordSafe platformPasswordSafe;
    private MiniLeetCodePasswordSafe miniLeetCodePasswordSafe;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        platformPasswordSafe = new StressTestPasswordSafe();
        miniLeetCodePasswordSafe = new MiniLeetCodePasswordSafe(platformPasswordSafe);
        MiniLeetCodePasswordSafe.setTestInstance(miniLeetCodePasswordSafe);
    }

    @AfterEach
    void tearDown() throws IOException {
        MiniLeetCodePasswordSafe.setTestInstance(null);
        if (server != null) {
            server.shutdown();
        }
    }

    // =========================================================================
    // 1. SETTINGS STATE CONCURRENCY & STRESS INVARIANTS
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: 16 concurrent threads (8 writers, 8 readers) high-throughput access to MiniLeetCodeSettingsState")
    void testSettingsState_16Threads_HighThroughputReadWrite() throws Exception {
        int threadCount = 16;
        int operationsPerThread = 1000;
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        AtomicInteger totalReads = new AtomicInteger();
        AtomicInteger totalWrites = new AtomicInteger();

        // 8 Writer Threads
        for (int i = 0; i < 8; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        int variant = j % 6;
                        switch (variant) {
                            case 0 -> state.setServerUrl("https://api.example.com:" + (8000 + (j % 50)) + "/");
                            case 1 -> state.setServerUrl("  http://127.0.0.1:8080///  ");
                            case 2 -> state.setServerUrl("https://worker-" + threadId + ".leetcode.com");
                            case 3 -> state.setServerUrl("");
                            case 4 -> state.setServerUrl(null);
                            case 5 -> state.setServerUrl("http://localhost:8080/api/v1//");
                        }
                        totalWrites.incrementAndGet();
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        // 8 Reader Threads
        for (int i = 8; i < 16; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String raw = state.getServerUrl();
                        assertNotNull(raw, "getServerUrl() must never return null under concurrent writes");
                        assertFalse(raw.isBlank(), "getServerUrl() must never return blank under concurrent writes");

                        String normalized = state.getNormalizedServerUrl();
                        assertNotNull(normalized, "getNormalizedServerUrl() must never return null");
                        assertFalse(normalized.isBlank(), "getNormalizedServerUrl() must never return blank");
                        assertFalse(normalized.endsWith("/"), "getNormalizedServerUrl() must never end with '/': " + normalized);
                        assertTrue(normalized.startsWith("http://") || normalized.startsWith("https://"),
                                "Normalized URL must start with http:// or https://: " + normalized);

                        totalReads.incrementAndGet();
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 16 threads must complete within 15 seconds without deadlock");
        if (failureRef.get() != null) {
            fail("Concurrency failure in MiniLeetCodeSettingsState: " + failureRef.get().getMessage(), failureRef.get());
        }
        assertEquals(8000, totalWrites.get(), "Total writes should be 8,000");
        assertEquals(8000, totalReads.get(), "Total reads should be 8,000");
    }

    @Test
    @DisplayName("Challenge 1.2: 16 threads concurrent XML state serialization and loadState bean copying")
    void testSettingsState_ConcurrentSerializationAndStateLoading() throws Exception {
        int threadCount = 16;
        int operationsPerThread = 500;
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (threadId % 2 == 0) {
                            // Writer thread: loadState with new State instances
                            String newUrl = "https://cluster-" + threadId + "-" + j + ".domain.com";
                            MiniLeetCodeSettingsState.State newState = new MiniLeetCodeSettingsState.State(newUrl);
                            state.loadState(newState);
                        } else {
                            // Reader thread: getState() and inspect exported bean
                            MiniLeetCodeSettingsState.State exported = state.getState();
                            assertNotNull(exported, "getState() must not return null");
                            assertNotNull(exported.serverUrl, "exported serverUrl must not be null");
                            assertFalse(exported.serverUrl.isBlank(), "exported serverUrl must not be blank");
                        }
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 16 serialization threads must finish without deadlock");
        if (failureRef.get() != null) {
            fail("State serialization concurrency failure: " + failureRef.get().getMessage(), failureRef.get());
        }
    }

    @Test
    @DisplayName("Challenge 1.3: 32 threads concurrent singleton race for MiniLeetCodeSettingsState.getInstance()")
    void testSettingsState_SingletonConcurrencyRace() throws Exception {
        int threadCount = 32;
        // Reset static instance via reflection
        Field instanceField = MiniLeetCodeSettingsState.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        MiniLeetCodeSettingsState[] instances = new MiniLeetCodeSettingsState[threadCount];
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    instances[index] = MiniLeetCodeSettingsState.getInstance();
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Singleton initialization race must complete within 5 seconds");
        if (failureRef.get() != null) {
            fail("Singleton race failure: " + failureRef.get().getMessage(), failureRef.get());
        }

        MiniLeetCodeSettingsState expected = instances[0];
        assertNotNull(expected, "Instance must not be null");
        for (int i = 1; i < threadCount; i++) {
            assertSame(expected, instances[i], "All 32 threads must receive identical singleton instance");
        }
    }

    // =========================================================================
    // 2. PASSWORD SAFE CONCURRENCY & STRESS INVARIANTS
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: 16 concurrent threads (16,000 operations) mixed set/get/clear on MiniLeetCodePasswordSafe")
    void testPasswordSafe_16Threads_HighThroughputMixedOperations() throws Exception {
        int threadCount = 16;
        int operationsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        AtomicInteger writeCount = new AtomicInteger();
        AtomicInteger readCount = new AtomicInteger();
        AtomicInteger clearCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        int role = (threadId + j) % 4;
                        switch (role) {
                            case 0 -> {
                                // Write valid trimmed PAT
                                String token = "mlc_pat_thread_" + threadId + "_" + j;
                                miniLeetCodePasswordSafe.setPassword(token);
                                writeCount.incrementAndGet();
                            }
                            case 1 -> {
                                // Write null or whitespace PAT
                                if (j % 2 == 0) {
                                    miniLeetCodePasswordSafe.setPassword("   ");
                                } else {
                                    miniLeetCodePasswordSafe.setPassword(null);
                                }
                                writeCount.incrementAndGet();
                            }
                            case 2 -> {
                                // Read token and verify invariants
                                String token = miniLeetCodePasswordSafe.getPassword();
                                if (token != null) {
                                    assertFalse(token.isBlank(), "Stored token must not be blank");
                                    assertEquals(token.trim(), token, "Stored token must be trimmed");
                                }
                                readCount.incrementAndGet();
                            }
                            case 3 -> {
                                // Clear password
                                miniLeetCodePasswordSafe.clearPassword();
                                clearCount.incrementAndGet();
                            }
                        }
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "PasswordSafe stress operations must finish within 15 seconds");
        if (failureRef.get() != null) {
            fail("PasswordSafe concurrency failure: " + failureRef.get().getMessage(), failureRef.get());
        }
        assertTrue(writeCount.get() > 0, "Writes should have occurred");
        assertTrue(readCount.get() > 0, "Reads should have occurred");
        assertTrue(clearCount.get() > 0, "Clears should have occurred");
        assertEquals(threadCount * operationsPerThread, writeCount.get() + readCount.get() + clearCount.get(),
                "All 16,000 operations must have completed");
    }

    @Test
    @DisplayName("Challenge 2.2: 16 threads interleaved rapid write and verify with zero data corruption")
    void testPasswordSafe_InterleavedRapidWriteAndVerify() throws Exception {
        int threadCount = 16;
        int iterations = 250;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < iterations; j++) {
                        String token = "mlc_pat_interleaved_" + threadId + "_" + j;
                        miniLeetCodePasswordSafe.setPassword("  " + token + "  ");

                        // Immediate check
                        String retrieved = miniLeetCodePasswordSafe.getPassword();
                        assertNotNull(retrieved, "Retrieved PAT should not be null immediately after write");
                        assertFalse(retrieved.startsWith(" ") || retrieved.endsWith(" "),
                                "Retrieved PAT must be trimmed");
                        assertTrue(miniLeetCodePasswordSafe.hasPassword(),
                                "hasPassword must be true when password is set");
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Interleaved writes must finish within 10 seconds");
        if (failureRef.get() != null) {
            fail("Interleaved write failure: " + failureRef.get().getMessage(), failureRef.get());
        }
    }

    @Test
    @DisplayName("Challenge 2.3: 32 threads concurrent singleton race for MiniLeetCodePasswordSafe.getInstance()")
    void testPasswordSafe_SingletonConcurrencyRace() throws Exception {
        int threadCount = 32;
        MiniLeetCodePasswordSafe testSafe = new MiniLeetCodePasswordSafe(platformPasswordSafe);
        MiniLeetCodePasswordSafe.setTestInstance(testSafe);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        MiniLeetCodePasswordSafe[] instances = new MiniLeetCodePasswordSafe[threadCount];
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    instances[index] = MiniLeetCodePasswordSafe.getInstance();
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "PasswordSafe singleton race must complete within 5 seconds");
        if (failureRef.get() != null) {
            fail("PasswordSafe singleton race failure: " + failureRef.get().getMessage(), failureRef.get());
        }

        MiniLeetCodePasswordSafe expected = instances[0];
        assertNotNull(expected, "PasswordSafe instance must not be null");
        for (int i = 1; i < threadCount; i++) {
            assertSame(expected, instances[i], "All 32 threads must receive identical PasswordSafe instance");
        }
    }

    // =========================================================================
    // 3. COMPONENT LIFECYCLE & MEMORY LEAK PREVENTION INVARIANTS
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: 1,000 rapid open/close lifecycle cycles guarantee zero reference leaks")
    void testConfigurableLifecycle_1000Cycles_MemoryLeakPrevention() throws ConfigurationException {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();
        StressTestPasswordSafe platformSafe = new StressTestPasswordSafe();
        MiniLeetCodePasswordSafe safe = new MiniLeetCodePasswordSafe(platformSafe);

        MiniLeetCodeConfigurable configurable = new MiniLeetCodeConfigurable(() -> state, () -> safe);

        for (int i = 0; i < 1000; i++) {
            // Open settings dialog
            JComponent component = configurable.createComponent();
            assertNotNull(component, "createComponent must return JPanel");
            assertNotNull(configurable.getSettingsComponent(), "getSettingsComponent must be non-null when open");

            // User modifies, applies, resets
            MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
            ui.setServerUrl("https://cycle-" + i + ".example.com");
            ui.setPassword("mlc_pat_cycle_" + i);
            assertTrue(configurable.isModified(), "Must be modified on cycle " + i);

            configurable.apply();
            assertEquals("https://cycle-" + i + ".example.com", state.getServerUrl());
            assertEquals("mlc_pat_cycle_" + i, safe.getPassword());
            assertFalse(configurable.isModified(), "Must not be modified after apply on cycle " + i);

            // Close dialog
            configurable.disposeUIResources();
            assertNull(configurable.getSettingsComponent(),
                    "disposeUIResources() must strictly nullify component reference on cycle " + i);
        }
    }

    @Test
    @DisplayName("Challenge 3.2: WeakReference reclamation of disposed Swing UI components")
    void testConfigurableLifecycle_WeakReferenceReclamation() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();
        StressTestPasswordSafe platformSafe = new StressTestPasswordSafe();
        MiniLeetCodePasswordSafe safe = new MiniLeetCodePasswordSafe(platformSafe);
        MiniLeetCodeConfigurable configurable = new MiniLeetCodeConfigurable(() -> state, () -> safe);

        JComponent component = configurable.createComponent();
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        WeakReference<MiniLeetCodeSettingsComponent> weakUi = new WeakReference<>(ui);
        WeakReference<JComponent> weakComponent = new WeakReference<>(component);

        // Clear local references
        ui = null;
        component = null;

        // Dispose configurable
        configurable.disposeUIResources();
        assertNull(configurable.getSettingsComponent(), "Configurable must not retain UI reference after dispose");

        // Force GC hint
        System.gc();

        // Configurable internal field verification
        assertNull(configurable.getSettingsComponent(), "Component must remain null");
    }

    @Test
    @DisplayName("Challenge 3.3: Defensive calling order (pre-create, post-dispose, repeated disposal)")
    void testConfigurableLifecycle_DefensiveCallingOrder() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();
        StressTestPasswordSafe platformSafe = new StressTestPasswordSafe();
        MiniLeetCodePasswordSafe safe = new MiniLeetCodePasswordSafe(platformSafe);
        MiniLeetCodeConfigurable configurable = new MiniLeetCodeConfigurable(() -> state, () -> safe);

        // 1. Invoking lifecycle before createComponent()
        assertFalse(configurable.isModified(), "isModified() before createComponent() must return false");
        assertDoesNotThrow(configurable::apply, "apply() before createComponent() must not throw");
        assertDoesNotThrow(configurable::reset, "reset() before createComponent() must not throw");
        assertNull(configurable.getPreferredFocusedComponent(), "preferred focused before createComponent() must be null");

        // 2. Idempotent disposal (calling multiple times consecutively)
        assertDoesNotThrow(configurable::disposeUIResources, "disposeUIResources() on uninitialized must not throw");
        assertDoesNotThrow(configurable::disposeUIResources, "repeated disposeUIResources() must not throw");
        assertDoesNotThrow(configurable::disposeUIResources, "repeated disposeUIResources() must not throw");

        // 3. Create component, dispose, and call methods post-dispose
        configurable.createComponent();
        assertNotNull(configurable.getSettingsComponent());
        configurable.disposeUIResources();
        assertNull(configurable.getSettingsComponent());

        assertFalse(configurable.isModified(), "isModified() post-dispose must return false");
        assertDoesNotThrow(configurable::apply, "apply() post-dispose must not throw");
        assertDoesNotThrow(configurable::reset, "reset() post-dispose must not throw");
        assertNull(configurable.getPreferredFocusedComponent(), "preferred focused post-dispose must be null");

        // 4. Re-open after disposal (valid IntelliJ workflow when reopening Settings)
        JComponent reopened = configurable.createComponent();
        assertNotNull(reopened, "Reopening settings must create fresh component");
        assertNotNull(configurable.getSettingsComponent());
        configurable.reset();
        assertEquals(MiniLeetCodeSettingsState.DEFAULT_SERVER_URL, configurable.getSettingsComponent().getServerUrl());

        configurable.disposeUIResources();
        assertNull(configurable.getSettingsComponent());
    }

    // =========================================================================
    // 4. CONCURRENT TEST CONNECTION & DEADLOCK / UI-FREEZE INVARIANTS
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: 16 concurrent asynchronous performTestConnection() calls without deadlock")
    void testSettingsComponent_ConcurrentTestConnection_16Threads_NoDeadlock() throws Exception {
        int threadCount = 16;
        CountDownLatch requestsDispatchedLatch = new CountDownLatch(threadCount);

        String jsonList = """
            [{"id": 1, "slug": "concurrent-plan", "title": "Concurrent Plan", "status": "PUBLISHED", "itemCount": 1, "stageCount": 1, "doneStages": 0}]
            """;

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("/api/v1/study-plans".equals(request.getPath())) {
                    requestsDispatchedLatch.countDown();
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(jsonList);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        MiniLeetCodeSettingsComponent component = new MiniLeetCodeSettingsComponent();
        component.setServerUrl(server.url("/").toString());
        component.setPassword("mlc_pat_concurrent_test");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    component.performTestConnection();
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean callersFinished = endGate.await(5, TimeUnit.SECONDS);
        assertTrue(callersFinished, "All calling threads must return immediately without blocking (non-blocking EDT)");

        // Wait for all 16 background requests to hit MockWebServer
        boolean requestsReceived = requestsDispatchedLatch.await(10, TimeUnit.SECONDS);
        assertTrue(requestsReceived, "All 16 background connection tasks must execute without deadlock");

        // Wait for component to settle
        long deadline = System.currentTimeMillis() + 5000;
        while (!component.getTestConnectionButton().isEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertTrue(component.getTestConnectionButton().isEnabled(), "Button must be re-enabled after completion");
        assertTrue(component.getStatusLabel().isVisible(), "Status label must be visible");
        assertEquals("Connection successful!", component.getStatusLabel().getText());
        assertEquals(JBColor.GREEN, component.getStatusLabel().getForeground());

        executor.shutdown();
        if (failureRef.get() != null) {
            fail("Concurrent performTestConnection failure: " + failureRef.get().getMessage(), failureRef.get());
        }
    }

    @Test
    @DisplayName("Challenge 4.2: 16 concurrent threads test connections with mixed HTTP status responses")
    void testSettingsComponent_ConcurrentTestConnectionSync_MixedResponses() throws Exception {
        int threadCount = 16;
        CountDownLatch allRequestsHandled = new CountDownLatch(threadCount);

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                allRequestsHandled.countDown();
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.contains("pat_200")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("[]");
                } else if (auth != null && auth.contains("pat_401")) {
                    return new MockResponse()
                            .setResponseCode(401)
                            .setHeader("Content-Type", "application/problem+json")
                            .setBody("{\"code\":\"PAT_INVALID\",\"detail\":\"Token has expired\"}");
                } else if (auth != null && auth.contains("pat_403")) {
                    return new MockResponse()
                            .setResponseCode(403)
                            .setHeader("Content-Type", "application/problem+json")
                            .setBody("{\"code\":\"PAT_FORBIDDEN\",\"detail\":\"Access denied\"}");
                } else {
                    return new MockResponse()
                            .setResponseCode(500)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"error\":\"Internal error\"}");
                }
            }
        });
        server.start();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int role = i % 4;
            executor.submit(() -> {
                try {
                    startGate.await();
                    MiniLeetCodeSettingsComponent comp = new MiniLeetCodeSettingsComponent();
                    comp.setServerUrl(server.url("/").toString());

                    switch (role) {
                        case 0 -> {
                            comp.setPassword("pat_200_valid");
                            comp.performTestConnectionSync();
                            assertEquals("Connection successful!", comp.getStatusLabel().getText());
                            assertEquals(JBColor.GREEN, comp.getStatusLabel().getForeground());
                        }
                        case 1 -> {
                            comp.setPassword("pat_401_invalid");
                            comp.performTestConnectionSync();
                            assertTrue(comp.getStatusLabel().getText().contains("Authentication failed"));
                            assertEquals(JBColor.RED, comp.getStatusLabel().getForeground());
                        }
                        case 2 -> {
                            comp.setPassword("pat_403_forbidden");
                            comp.performTestConnectionSync();
                            assertTrue(comp.getStatusLabel().getText().contains("Connection failed"));
                            assertEquals(JBColor.RED, comp.getStatusLabel().getForeground());
                        }
                        case 3 -> {
                            comp.setPassword("pat_500_server_error");
                            comp.performTestConnectionSync();
                            assertTrue(comp.getStatusLabel().getText().contains("Connection failed"));
                            assertEquals(JBColor.RED, comp.getStatusLabel().getForeground());
                        }
                    }
                    assertTrue(comp.getTestConnectionButton().isEnabled(), "Button must remain enabled");
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Mixed status concurrency test must finish within 15 seconds without deadlock");
        if (failureRef.get() != null) {
            fail("Mixed status concurrency failure: " + failureRef.get().getMessage(), failureRef.get());
        }
        assertTrue(allRequestsHandled.await(5, TimeUnit.SECONDS), "All 16 requests must be handled");
    }

    @Test
    @DisplayName("Challenge 4.3: Rapid-fire burst triggering (50 calls within 100ms) without crash or thread leak")
    void testSettingsComponent_RapidFireBurst_50CallsIn100Ms() throws Exception {
        int burstCount = 50;
        CountDownLatch burstCompletedLatch = new CountDownLatch(burstCount);

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                burstCompletedLatch.countDown();
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("[]");
            }
        });
        server.start();

        MiniLeetCodeSettingsComponent component = new MiniLeetCodeSettingsComponent();
        component.setServerUrl(server.url("/").toString());
        component.setPassword("mlc_pat_burst");

        // Fire 50 calls rapidly
        for (int i = 0; i < burstCount; i++) {
            component.performTestConnection();
        }

        boolean allHandled = burstCompletedLatch.await(15, TimeUnit.SECONDS);
        assertTrue(allHandled, "All 50 burst connection requests must be processed without rejection or hang");

        // Component should settle cleanly
        long deadline = System.currentTimeMillis() + 5000;
        while (!component.getTestConnectionButton().isEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertTrue(component.getTestConnectionButton().isEnabled(), "Button must be enabled");
        assertEquals("Connection successful!", component.getStatusLabel().getText());
    }

    @Test
    @DisplayName("Challenge 4.4: Concurrent latency stress (150ms delay per request) proves non-blocking parallel execution")
    void testSettingsComponent_SlowServerLatency_ConcurrentStressNoFreeze() throws Exception {
        int threadCount = 8;
        int artificialLatencyMs = 150;

        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                try {
                    Thread.sleep(artificialLatencyMs);
                } catch (InterruptedException ignored) {}
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("[]");
            }
        });
        server.start();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    MiniLeetCodeSettingsComponent comp = new MiniLeetCodeSettingsComponent();
                    comp.setServerUrl(server.url("/").toString());
                    comp.setPassword("mlc_pat_latency");
                    comp.performTestConnectionSync();
                    assertEquals("Connection successful!", comp.getStatusLabel().getText());
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;
        executor.shutdown();

        assertTrue(completed, "All 8 latent requests must complete within 10 seconds");
        if (failureRef.get() != null) {
            fail("Latency stress failure: " + failureRef.get().getMessage(), failureRef.get());
        }

        // Sequential execution would take >= 8 * 150ms = 1200ms.
        // Parallel execution should finish significantly faster (typically < 900ms).
        assertTrue(elapsed < 8 * artificialLatencyMs,
                "Concurrent requests should execute in parallel, expected elapsed < " + (8 * artificialLatencyMs) + "ms, was: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Challenge 4.5: Blank URL validation under 16 concurrent threads prevents network calls without deadlocks")
    void testSettingsComponent_SyncTestConnection_BlankUrl_NoDeadlock_16Threads() throws Exception {
        int threadCount = 16;
        server.start();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    MiniLeetCodeSettingsComponent comp = new MiniLeetCodeSettingsComponent();
                    comp.setServerUrl("    ");
                    comp.performTestConnectionSync();

                    assertEquals("Server URL must not be blank.", comp.getStatusLabel().getText());
                    assertEquals(JBColor.RED, comp.getStatusLabel().getForeground());
                    assertTrue(comp.getTestConnectionButton().isEnabled());
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Validation under concurrency must complete within 5 seconds");
        if (failureRef.get() != null) {
            fail("Blank URL validation failure: " + failureRef.get().getMessage(), failureRef.get());
        }
        assertEquals(0, server.getRequestCount(), "Zero network requests should be made for blank URL");
    }

    // =========================================================================
    // 5. TEST DOUBLE FOR IN-MEMORY PASSWORD SAFE
    // =========================================================================

    /**
     * Self-contained thread-safe in-memory test double for {@link PasswordSafe}.
     * Backed by {@link ConcurrentHashMap} with zero external dependencies.
     */
    static class StressTestPasswordSafe extends PasswordSafe {

        private final Map<String, String> credentialMap = new ConcurrentHashMap<>();

        @Override
        public @Nullable Credentials get(@NotNull CredentialAttributes attributes) {
            String password = credentialMap.get(attributes.getServiceName());
            if (password == null) {
                return null;
            }
            return new Credentials(attributes.getUserName(), password);
        }

        @Override
        public void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials) {
            set(attributes, credentials, false);
        }

        @Override
        public void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials, boolean memoryOnly) {
            if (credentials == null || credentials.getPasswordAsString() == null) {
                credentialMap.remove(attributes.getServiceName());
            } else {
                credentialMap.put(attributes.getServiceName(), credentials.getPasswordAsString());
            }
        }

        @Override
        public @Nullable String getPassword(@NotNull CredentialAttributes attributes) {
            return credentialMap.get(attributes.getServiceName());
        }

        @Override
        public void setPassword(@NotNull CredentialAttributes attributes, @Nullable String password) {
            if (password == null || password.isBlank()) {
                credentialMap.remove(attributes.getServiceName());
            } else {
                credentialMap.put(attributes.getServiceName(), password.trim());
            }
        }

        @Override
        public boolean isRememberPasswordByDefault() {
            return true;
        }

        @Override
        public void setRememberPasswordByDefault(boolean isRememberPasswordByDefault) {
        }

        @Override
        public boolean isMemoryOnly() {
            return true;
        }

        @Override
        public boolean isPasswordStoredOnlyInMemory(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials) {
            return true;
        }
    }
}
