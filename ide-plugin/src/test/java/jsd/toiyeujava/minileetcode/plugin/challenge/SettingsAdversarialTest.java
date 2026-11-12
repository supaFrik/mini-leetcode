package jsd.toiyeujava.minileetcode.plugin.challenge;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.JBColor;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeConfigurable;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodePasswordSafe;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsComponent;
import jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 2:
 * Settings & PasswordSafe Storage under hostile, edge-case, and boundary conditions.
 *
 * Vectors tested:
 * 1. Extreme and malformed server URLs (trailing slashes, whitespace, tabs, query params, port limits, localhost vs IP).
 * 2. Adversarial PAT values (empty string, 10,000-character tokens, emojis, non-ASCII Unicode, zero-width characters, null handling).
 * 3. Configurable lifecycle edge cases: rapid isModified toggling, apply without change, reset without change, multiple consecutive apply calls.
 * 4. MockWebServer edge cases during test connection: slow responses, dropped connections, HTTP 500, HTTP 403, malformed JSON error bodies.
 * 5. High-concurrency multi-threaded access and defensive supplier handling.
 */
class SettingsAdversarialTest {

    private MockWebServer server;
    private AdversarialPasswordSafe platformSafe;
    private MiniLeetCodePasswordSafe passwordSafe;
    private MiniLeetCodeSettingsState settingsState;
    private MiniLeetCodeConfigurable configurable;

    private static final String DEFAULT_URL = MiniLeetCodeSettingsState.DEFAULT_SERVER_URL;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        platformSafe = new AdversarialPasswordSafe();
        passwordSafe = new MiniLeetCodePasswordSafe(platformSafe);
        MiniLeetCodePasswordSafe.setTestInstance(passwordSafe);

        settingsState = new MiniLeetCodeSettingsState();
        configurable = new MiniLeetCodeConfigurable(() -> settingsState, () -> passwordSafe);
        configurable.createComponent();
        configurable.reset();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (configurable != null) {
            configurable.disposeUIResources();
        }
        MiniLeetCodePasswordSafe.setTestInstance(null);
        if (server != null) {
            server.shutdown();
        }
    }

    // =========================================================================
    // 1. EXTREME AND MALFORMED SERVER URLS
    // =========================================================================

    @Test
    @DisplayName("URL Vector 1.1: Multiple consecutive trailing slashes are completely stripped")
    void testUrl_MultipleConsecutiveTrailingSlashes() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        state.setServerUrl("http://localhost:8080/////");
        assertEquals("http://localhost:8080/////", state.getServerUrl(), "Raw URL preserves entered slashes");
        assertEquals("http://localhost:8080", state.getNormalizedServerUrl(), "All trailing slashes must be stripped");

        state.setServerUrl("https://leetcode.com:9443/custom/path///");
        assertEquals("https://leetcode.com:9443/custom/path", state.getNormalizedServerUrl());

        // Single slash
        state.setServerUrl("https://example.com/");
        assertEquals("https://example.com", state.getNormalizedServerUrl());
    }

    @Test
    @DisplayName("URL Vector 1.2: Excessive whitespace, tab, and newline padding around server URL")
    void testUrl_WhitespaceTabAndNewlinePadding() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        state.setServerUrl("\t\r\n   http://127.0.0.1:8080/api/  \n\t  ");
        assertEquals("http://127.0.0.1:8080/api/", state.getServerUrl());
        assertEquals("http://127.0.0.1:8080/api", state.getNormalizedServerUrl());

        // Setting blank or whitespace-only reverts safely to DEFAULT_SERVER_URL
        state.setServerUrl("   \t\n\r   ");
        assertEquals(DEFAULT_URL, state.getServerUrl());
        assertEquals(DEFAULT_URL, state.getNormalizedServerUrl());
    }

    @Test
    @DisplayName("URL Vector 1.3: URL with query parameters and fragments")
    void testUrl_QueryParamsAndFragments() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        state.setServerUrl("http://localhost:8080?env=staging&debug=true");
        assertEquals("http://localhost:8080?env=staging&debug=true", state.getServerUrl());
        assertEquals("http://localhost:8080?env=staging&debug=true", state.getNormalizedServerUrl());

        // With trailing slash after query param
        state.setServerUrl("http://localhost:8080/api/?ver=1/");
        assertEquals("http://localhost:8080/api/?ver=1", state.getNormalizedServerUrl());
    }

    @Test
    @DisplayName("URL Vector 1.4: Extreme port boundaries (port 65535, port 0, port out of range)")
    void testUrl_PortBoundaries() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        // Max valid TCP port: 65535
        state.setServerUrl("http://127.0.0.1:65535");
        assertEquals("http://127.0.0.1:65535", state.getNormalizedServerUrl());

        // Port 0
        state.setServerUrl("http://localhost:0");
        assertEquals("http://localhost:0", state.getNormalizedServerUrl());

        // Port out of range: 99999999
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);
        ui.setServerUrl("http://localhost:99999999");
        // Test connection should catch URI / networking error cleanly without uncaught exception
        ui.performTestConnectionSync();
        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getStatusLabel().getText().toLowerCase().contains("failed")
                || ui.getStatusLabel().getText().toLowerCase().contains("error"));
        assertTrue(ui.getTestConnectionButton().isEnabled(), "Button must remain enabled after failure");
    }

    @Test
    @DisplayName("URL Vector 1.5: Localhost, IPv4, IPv6, and FQDN host variations")
    void testUrl_HostVariations() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        String[] hosts = {
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://[::1]:8080",
                "https://sub.domain.corp.internal:443",
                "http://0.0.0.0:3000"
        };

        for (String host : hosts) {
            state.setServerUrl(host);
            assertEquals(host, state.getServerUrl());
            assertEquals(host, state.getNormalizedServerUrl());
        }
    }

    @Test
    @DisplayName("URL Vector 1.6: Non-HTTP schemes and totally malformed URLs fail cleanly in UI")
    void testUrl_MalformedSchemesAndInvalidSyntax() {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        String[] badUrls = {
                "ftp://files.example.com",
                "javascript:alert(1)",
                "ht tp://broken-spaces:8080",
                "://missing-scheme",
                "http://[invalid-ipv6"
        };

        for (String bad : badUrls) {
            ui.setServerUrl(bad);
            ui.performTestConnectionSync();

            assertTrue(ui.getStatusLabel().isVisible(), "Status label must display for bad URL: " + bad);
            assertEquals(JBColor.RED, ui.getStatusLabel().getForeground(), "Error color expected for: " + bad);
            assertTrue(ui.getStatusLabel().getText().contains("failed")
                    || ui.getStatusLabel().getText().contains("error")
                    || ui.getStatusLabel().getText().contains("Exception"),
                    "Error description expected for: " + bad);
            assertTrue(ui.getTestConnectionButton().isEnabled());
        }
    }

    // =========================================================================
    // 2. ADVERSARIAL PERSONAL ACCESS TOKEN (PAT) VALUES
    // =========================================================================

    @Test
    @DisplayName("PAT Vector 2.1: Empty and blank string tokens automatically clear PasswordSafe")
    void testPat_EmptyAndBlankTokenClearsCredentialStore() {
        passwordSafe.setPassword("valid_initial_pat");
        assertTrue(passwordSafe.hasPassword());

        // Empty string
        passwordSafe.setPassword("");
        assertNull(passwordSafe.getPassword(), "Empty string must clear stored token");
        assertFalse(passwordSafe.hasPassword());

        // Whitespace and tab string
        passwordSafe.setPassword("valid_pat_2");
        passwordSafe.setPassword("   \t\n   ");
        assertNull(passwordSafe.getPassword(), "Blank string must clear stored token");
        assertFalse(passwordSafe.hasPassword());

        // Null value
        passwordSafe.setPassword("valid_pat_3");
        passwordSafe.setPassword(null);
        assertNull(passwordSafe.getPassword(), "Null value must clear stored token");
        assertFalse(passwordSafe.hasPassword());
    }

    @Test
    @DisplayName("PAT Vector 2.2: Extremely large PAT (10,000 characters) preserves integrity and transmits")
    void testPat_ExtremelyLargeToken_10kChars() throws InterruptedException {
        String largePat = "mlc_pat_" + "x".repeat(10_000);
        assertEquals(10_008, largePat.length());

        // 1. PasswordSafe storage
        passwordSafe.setPassword(largePat);
        assertEquals(largePat, passwordSafe.getPassword(), "PasswordSafe must store and retrieve full 10k token");
        assertTrue(passwordSafe.hasPassword());

        // 2. UI integration & test connection
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword(largePat);
        assertEquals(largePat, ui.getPassword(), "UI password field must retain full 10k chars");

        ui.performTestConnectionSync();

        assertEquals("Connection successful!", ui.getStatusLabel().getText());
        assertEquals(JBColor.GREEN, ui.getStatusLabel().getForeground());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("Bearer " + largePat, request.getHeader("Authorization"),
                "MockWebServer must receive exact 10,000-character Bearer token");
    }

    @Test
    @DisplayName("PAT Vector 2.3: Emojis and surrogate pairs handled safely across PasswordSafe and UI")
    void testPat_EmojisAndSurrogatePairs() {
        String emojiPat = "mlc_pat_🚀🔥_secret_🎉🔑_999";

        // Store and retrieve in PasswordSafe
        passwordSafe.setPassword(emojiPat);
        assertEquals(emojiPat, passwordSafe.getPassword(), "Emojis must be accurately stored and retrieved");
        assertTrue(passwordSafe.hasPassword());

        // UI roundtrip
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword(emojiPat);
        assertEquals(emojiPat, ui.getPassword());

        // Test connection: JDK HttpRequest might reject non-ASCII header value with IllegalArgumentException
        // performTestConnectionSync must catch it gracefully without crashing or freezing Swing
        assertDoesNotThrow(() -> ui.performTestConnectionSync(),
                "Testing connection with emoji PAT must never throw uncaught exception to UI thread");
        assertTrue(ui.getStatusLabel().isVisible());
        assertTrue(ui.getTestConnectionButton().isEnabled(), "Button must remain interactive");
    }

    @Test
    @DisplayName("PAT Vector 2.4: Non-ASCII Unicode (Vietnamese, Cyrillic, Chinese) in PAT")
    void testPat_NonAsciiUnicodeCharacters() {
        String unicodePat = "mlc_pat_khóa_bảo_mật_tiếng_việt_русский_中文_123";

        passwordSafe.setPassword(unicodePat);
        assertEquals(unicodePat, passwordSafe.getPassword());

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);
        ui.setPassword(unicodePat);
        assertEquals(unicodePat, ui.getPassword());

        assertDoesNotThrow(() -> ui.performTestConnectionSync());
        assertTrue(ui.getStatusLabel().isVisible());
    }

    @Test
    @DisplayName("PAT Vector 2.5: Zero-width characters (ZWSP, ZWNJ, ZWJ, BOM) preserved without crashing")
    void testPat_ZeroWidthCharacters() {
        // \u200B: zero-width space, \u200C: ZWNJ, \u200D: ZWJ, \uFEFF: zero-width no-break space
        String zeroWidthPat = "mlc_pat_\u200Bpart1\u200Cpart2\u200Dpart3\uFEFF";

        passwordSafe.setPassword(zeroWidthPat);
        assertEquals(zeroWidthPat, passwordSafe.getPassword());
        assertTrue(passwordSafe.hasPassword());

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);
        ui.setPassword(zeroWidthPat);
        assertEquals(zeroWidthPat, ui.getPassword());
    }

    // =========================================================================
    // 3. CONFIGURABLE LIFECYCLE EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Lifecycle Vector 3.1: Rapid isModified toggling across 1000 iterations")
    void testLifecycle_RapidIsModifiedToggling_1000Cycles() {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        assertFalse(configurable.isModified());

        for (int i = 0; i < 1000; i++) {
            ui.setServerUrl("http://modified-host-" + i + ":8080");
            assertTrue(configurable.isModified(), "isModified must be true at iteration " + i);

            ui.setServerUrl(DEFAULT_URL);
            assertFalse(configurable.isModified(), "isModified must be false when reverted at iteration " + i);
        }
    }

    @Test
    @DisplayName("Lifecycle Vector 3.2: apply() without change is completely idempotent")
    void testLifecycle_ApplyWithoutChange_IsIdempotent() throws ConfigurationException {
        assertFalse(configurable.isModified());

        // Multiple calls to apply() on unmodified state
        for (int i = 0; i < 5; i++) {
            configurable.apply();
            assertFalse(configurable.isModified());
            assertEquals(DEFAULT_URL, settingsState.getServerUrl());
            assertNull(passwordSafe.getPassword());
        }
    }

    @Test
    @DisplayName("Lifecycle Vector 3.3: reset() without change is completely idempotent")
    void testLifecycle_ResetWithoutChange_IsIdempotent() {
        assertFalse(configurable.isModified());

        for (int i = 0; i < 5; i++) {
            configurable.reset();
            assertFalse(configurable.isModified());
            assertEquals(DEFAULT_URL, configurable.getSettingsComponent().getServerUrl());
            assertEquals("", configurable.getSettingsComponent().getPassword());
        }
    }

    @Test
    @DisplayName("Lifecycle Vector 3.4: Multiple consecutive apply() calls persist correctly and remain clean")
    void testLifecycle_MultipleConsecutiveApplies() throws ConfigurationException {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl("https://prod.minileetcode.com");
        ui.setPassword("mlc_pat_prod_secret");
        assertTrue(configurable.isModified());

        // First apply
        configurable.apply();
        assertFalse(configurable.isModified());
        assertEquals("https://prod.minileetcode.com", settingsState.getServerUrl());
        assertEquals("mlc_pat_prod_secret", passwordSafe.getPassword());

        // Second apply immediately after
        configurable.apply();
        assertFalse(configurable.isModified());

        // Third apply immediately after
        configurable.apply();
        assertFalse(configurable.isModified());
    }

    @Test
    @DisplayName("Lifecycle Vector 3.5: Comprehensive state-machine transition sequence")
    void testLifecycle_ComplexTransitionSequence() throws ConfigurationException {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        // 1. Initial state
        assertFalse(configurable.isModified());

        // 2. Edit URL only -> isModified=true
        ui.setServerUrl("http://custom:1111");
        assertTrue(configurable.isModified());

        // 3. Reset -> reverts URL
        configurable.reset();
        assertFalse(configurable.isModified());
        assertEquals(DEFAULT_URL, ui.getServerUrl());

        // 4. Edit PAT only -> isModified=true
        ui.setPassword("pat_stage4");
        assertTrue(configurable.isModified());

        // 5. Apply -> PAT persisted
        configurable.apply();
        assertFalse(configurable.isModified());
        assertEquals("pat_stage4", passwordSafe.getPassword());

        // 6. Edit URL & PAT -> isModified=true
        ui.setServerUrl("http://custom:2222");
        ui.setPassword("pat_stage6");
        assertTrue(configurable.isModified());

        // 7. Apply both
        configurable.apply();
        assertFalse(configurable.isModified());
        assertEquals("http://custom:2222", settingsState.getServerUrl());
        assertEquals("pat_stage6", passwordSafe.getPassword());

        // 8. Blank URL apply throws ConfigurationException and leaves saved URL intact
        ui.setServerUrl("   ");
        assertTrue(configurable.isModified());
        assertThrows(ConfigurationException.class, () -> configurable.apply());
        assertEquals("http://custom:2222", settingsState.getServerUrl(),
                "Invalid apply must NOT overwrite previously saved valid URL");

        // 9. Revert UI and clear PAT
        configurable.reset();
        assertEquals("http://custom:2222", ui.getServerUrl());
        ui.setPassword("");
        assertTrue(configurable.isModified());
        configurable.apply();
        assertFalse(configurable.isModified());
        assertNull(passwordSafe.getPassword(), "Clearing PAT must remove it from PasswordSafe");
    }

    @Test
    @DisplayName("Lifecycle Vector 3.6: Defensive handling when suppliers return null")
    void testLifecycle_DefensiveSupplierNullHandling() throws ConfigurationException {
        // Instantiate configurable with suppliers returning null
        MiniLeetCodeConfigurable nullSafeConfigurable = new MiniLeetCodeConfigurable(() -> null, () -> null);
        nullSafeConfigurable.createComponent();

        // None of these should throw NullPointerException
        assertDoesNotThrow(() -> nullSafeConfigurable.isModified());
        assertDoesNotThrow(() -> nullSafeConfigurable.reset());
        assertDoesNotThrow(() -> nullSafeConfigurable.apply());

        nullSafeConfigurable.disposeUIResources();
        assertNull(nullSafeConfigurable.getSettingsComponent());

        // Post-disposal calls should be safe
        assertFalse(nullSafeConfigurable.isModified());
        assertDoesNotThrow(() -> nullSafeConfigurable.reset());
        assertDoesNotThrow(() -> nullSafeConfigurable.apply());
    }

    // =========================================================================
    // 4. MOCKWEBSERVER EDGE CASES DURING TEST CONNECTION
    // =========================================================================

    @Test
    @DisplayName("Network Vector 4.1: Slow responses trigger timeout without freezing EDT")
    void testConnection_SlowResponse_Timeout() {
        // Delay headers by 3 seconds
        server.enqueue(new MockResponse()
                .setHeadersDelay(3, TimeUnit.SECONDS)
                .setBody("[]"));

        // Use custom factory with 200ms timeout
        MiniLeetCodeSettingsComponent shortTimeoutUi = new MiniLeetCodeSettingsComponent(
                (url, pat) -> new ApiClient(url, pat, Duration.ofMillis(200))
        );

        shortTimeoutUi.setServerUrl(server.url("/").toString());
        shortTimeoutUi.performTestConnectionSync();

        assertTrue(shortTimeoutUi.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, shortTimeoutUi.getStatusLabel().getForeground());
        String statusText = shortTimeoutUi.getStatusLabel().getText().toLowerCase();
        assertTrue(statusText.contains("timed out") || statusText.contains("connection failed"),
                "Status must report timeout or connection failure, got: " + statusText);
        assertTrue(shortTimeoutUi.getTestConnectionButton().isEnabled(), "Button must be re-enabled");
    }

    @Test
    @DisplayName("Network Vector 4.2: Dropped connection at start (SocketPolicy.DISCONNECT_AT_START)")
    void testConnection_DroppedConnection_AtStart() {
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getStatusLabel().getText().toLowerCase().contains("failed")
                || ui.getStatusLabel().getText().toLowerCase().contains("network"));
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.3: Dropped connection mid-request (SocketPolicy.DISCONNECT_AFTER_REQUEST)")
    void testConnection_DroppedConnection_AfterRequest() {
        server.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.4: HTTP 500 Internal Server Error with plain text body")
    void testConnection_Http500_PlainText() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/plain")
                .setBody("Internal Server Error: Database deadlock detected"));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getStatusLabel().getText().contains("500")
                || ui.getStatusLabel().getText().contains("Connection failed"));
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.5: HTTP 500 with RFC 7807 ProblemDetail detail message")
    void testConnection_Http500_Rfc7807ProblemDetail() {
        String problemJson = """
            {
              "type": "https://api.mini-leetcode.com/errors/internal",
              "title": "Service Unavailable",
              "status": 500,
              "code": "DB_DOWN",
              "detail": "PostgreSQL connection pool exhausted on master node"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemJson));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getStatusLabel().getText().contains("PostgreSQL connection pool exhausted"),
                "Status must extract and display RFC 7807 detail");
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.6: HTTP 403 Forbidden with PAT_FORBIDDEN detail")
    void testConnection_Http403_ForbiddenWithDetail() {
        String problemJson = """
            {
              "status": 403,
              "code": "PAT_FORBIDDEN",
              "detail": "PAT is missing required scope: study-plan:read"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemJson));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getStatusLabel().getText().contains("PAT is missing required scope"),
                "Status must display forbidden reason");
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.7: Malformed error body - Truncated JSON on HTTP 500")
    void testConnection_MalformedErrorBody_TruncatedJson() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\": 500, \"detail\": \"Incomplete json message..."));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        // Must not throw unhandled JsonSyntaxException
        assertDoesNotThrow(() -> ui.performTestConnectionSync());

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.8: Malformed error body - HTML 502 Bad Gateway page")
    void testConnection_MalformedErrorBody_HtmlGatewayError() {
        String htmlError = """
            <!DOCTYPE html>
            <html>
              <head><title>502 Bad Gateway</title></head>
              <body><center><h1>502 Bad Gateway</h1><hr>nginx/1.24.0</center></body>
            </html>
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(502)
                .setHeader("Content-Type", "text/html")
                .setBody(htmlError));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        assertDoesNotThrow(() -> ui.performTestConnectionSync());

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getStatusLabel().getText().contains("502")
                || ui.getStatusLabel().getText().contains("failed"));
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("Network Vector 4.9: HTTP 200 with invalid JSON payload (HTML or non-array JSON)")
    void testConnection_Http200_InvalidJsonContent() {
        // Server returns HTTP 200 but body is HTML login portal
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body>Captive Portal Login Required</body></html>"));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        // Gson fails to parse HTML as List<PlanSummary> -> performTestConnectionSync handles error safely
        assertDoesNotThrow(() -> ui.performTestConnectionSync());

        assertTrue(ui.getStatusLabel().isVisible());
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    // =========================================================================
    // 5. HIGH-CONCURRENCY MULTI-THREADED ACCESS
    // =========================================================================

    @Test
    @DisplayName("Concurrency Vector 5.1: Concurrent reads and writes on MiniLeetCodeSettingsState")
    void testConcurrency_SettingsStateConcurrentAccess() throws InterruptedException {
        int threadCount = 16;
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        String url = "https://host-" + threadId + "-" + j + ".example.com:8443///";
                        settingsState.setServerUrl(url);
                        String normalized = settingsState.getNormalizedServerUrl();
                        assertFalse(normalized.endsWith("/"));
                        assertNotNull(settingsState.getServerUrl());
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(0, errors.get(), "Zero concurrency errors expected in SettingsState");
    }

    @Test
    @DisplayName("Concurrency Vector 5.2: Concurrent isModified() calls during apply() and reset()")
    void testConcurrency_ConfigurableConcurrentLifecycle() throws InterruptedException {
        int threadCount = 8;
        int iterations = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        if (threadId % 2 == 0) {
                            configurable.isModified();
                        } else {
                            configurable.reset();
                        }
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(0, errors.get(), "Zero concurrency errors expected in Configurable lifecycle");
    }

    // =========================================================================
    // 6. ADVERSARIAL TEST DOUBLE FOR PASSWORD SAFE
    // =========================================================================

    /**
     * Isolated thread-safe in-memory test double for IntelliJ {@link PasswordSafe}.
     */
    static class AdversarialPasswordSafe extends PasswordSafe {

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
