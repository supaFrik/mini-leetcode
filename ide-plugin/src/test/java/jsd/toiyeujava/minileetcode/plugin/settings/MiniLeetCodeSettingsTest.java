package jsd.toiyeujava.minileetcode.plugin.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.JBColor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit test suite for Milestone 2:
 * - MiniLeetCodeSettingsState (PersistentStateComponent, default URL, serialization, security audit)
 * - MiniLeetCodePasswordSafe (secure PAT storage, trimming, null-safety, credential attributes, concurrency)
 * - MiniLeetCodeConfigurable (isModified detection, apply, reset, lifecycle)
 * - MiniLeetCodeSettingsComponent (Swing controls, asynchronous test connection with MockWebServer)
 *
 * Runs 100% headless (java.awt.headless=true) without HeadlessException or heavy IDE fixtures.
 */
class MiniLeetCodeSettingsTest {

    private MockWebServer server;
    private TestPasswordSafe inMemoryPlatformSafe;
    private MiniLeetCodePasswordSafe passwordSafe;
    private MiniLeetCodeSettingsState settingsState;
    private MiniLeetCodeConfigurable configurable;

    private static final String DEFAULT_URL = MiniLeetCodeSettingsState.DEFAULT_SERVER_URL;
    private static final String SAMPLE_PAT = "mlc_pat_test1234567890abcdef1234567890abcdef";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        inMemoryPlatformSafe = new TestPasswordSafe();
        passwordSafe = new MiniLeetCodePasswordSafe(inMemoryPlatformSafe);
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
    // 1. MiniLeetCodeSettingsState Unit Tests
    // =========================================================================

    @Test
    @DisplayName("testDefaultSettingsState: Server URL defaults to 'http://localhost:8080'")
    void testDefaultSettingsState() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        assertEquals("http://localhost:8080", state.getServerUrl(),
                "getServerUrl() must default to http://localhost:8080");
        assertNotNull(state.getState(), "getState() must not be null");
        assertEquals("http://localhost:8080", state.getState().serverUrl,
                "State.serverUrl must default to http://localhost:8080");
        assertEquals("http://localhost:8080", state.getNormalizedServerUrl(),
                "Normalized URL should strip nothing from default URL");
    }

    @Test
    @DisplayName("testSettingsStateSetServerUrl: Setting URL handles trimming, trailing slashes, and defaults")
    void testSettingsStateSetServerUrl() {
        MiniLeetCodeSettingsState state = new MiniLeetCodeSettingsState();

        state.setServerUrl("https://leetcode.custom-domain.com:8443");
        assertEquals("https://leetcode.custom-domain.com:8443", state.getServerUrl());
        assertEquals("https://leetcode.custom-domain.com:8443", state.getNormalizedServerUrl());

        // Whitespace trimming
        state.setServerUrl("  https://api.domain.org/  ");
        assertEquals("https://api.domain.org/", state.getServerUrl());
        assertEquals("https://api.domain.org", state.getNormalizedServerUrl(),
                "getNormalizedServerUrl must strip trailing slashes");

        // Multiple trailing slashes
        state.setServerUrl("http://localhost:8080///");
        assertEquals("http://localhost:8080", state.getNormalizedServerUrl());

        // Setting null or blank falls back to default
        state.setServerUrl(null);
        assertEquals(DEFAULT_URL, state.getServerUrl());
        state.setServerUrl("   ");
        assertEquals(DEFAULT_URL, state.getServerUrl());
    }

    @Test
    @DisplayName("testSettingsStateSerialization: Verifies State getters/setters, loadState, and bean copying")
    void testSettingsStateSerialization() {
        MiniLeetCodeSettingsState original = new MiniLeetCodeSettingsState();
        original.setServerUrl("https://prod.minileetcode.com");

        MiniLeetCodeSettingsState.State exportedState = original.getState();
        assertNotNull(exportedState);
        assertEquals("https://prod.minileetcode.com", exportedState.serverUrl);

        MiniLeetCodeSettingsState target = new MiniLeetCodeSettingsState();
        assertEquals(DEFAULT_URL, target.getServerUrl());

        target.loadState(exportedState);
        assertEquals("https://prod.minileetcode.com", target.getServerUrl(),
                "Target state must reflect loaded configuration");

        // State equality & hashCode check
        MiniLeetCodeSettingsState.State s1 = new MiniLeetCodeSettingsState.State("http://host:8080");
        MiniLeetCodeSettingsState.State s2 = new MiniLeetCodeSettingsState.State("http://host:8080");
        MiniLeetCodeSettingsState.State s3 = new MiniLeetCodeSettingsState.State("http://different:8080");

        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    @DisplayName("testSettingsStateSecurityStrictCompliance: Enforce FR-PL-003 - No PAT fields in XML State")
    void testSettingsStateSecurityStrictCompliance() {
        Field[] stateFields = MiniLeetCodeSettingsState.State.class.getDeclaredFields();
        for (Field field : stateFields) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("pat") || name.contains("token") || name.contains("password")
                            || name.contains("secret") || name.contains("auth"),
                    "FR-PL-003 Violation: MiniLeetCodeSettingsState.State must NEVER declare sensitive credential fields! Found: " + field.getName());
        }

        Field[] enclosingFields = MiniLeetCodeSettingsState.class.getDeclaredFields();
        for (Field field : enclosingFields) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("pat") || name.contains("token") || name.contains("password")
                            || name.contains("secret"),
                    "FR-PL-003 Violation: MiniLeetCodeSettingsState must NEVER store credentials! Found: " + field.getName());
        }

        Method[] methods = MiniLeetCodeSettingsState.class.getDeclaredMethods();
        for (Method method : methods) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("password") || name.contains("pat") || name.contains("token"),
                    "FR-PL-003 Violation: MiniLeetCodeSettingsState must NOT expose credential methods! Found: " + method.getName());
        }
    }

    // =========================================================================
    // 2. MiniLeetCodePasswordSafe Unit Tests
    // =========================================================================

    @Test
    @DisplayName("testPasswordSafe_StoreRetrieveClear: Verifies secure storage lifecycle in in-memory test double")
    void testPasswordSafe_StoreRetrieveClear() {
        assertNull(passwordSafe.getPassword(), "Initial PAT should be null");
        assertFalse(passwordSafe.hasPassword(), "hasPassword should be false initially");

        passwordSafe.setPassword(SAMPLE_PAT);
        assertEquals(SAMPLE_PAT, passwordSafe.getPassword(), "Stored PAT must match");
        assertTrue(passwordSafe.hasPassword(), "hasPassword should be true when PAT is set");

        passwordSafe.clearPassword();
        assertNull(passwordSafe.getPassword(), "PAT must be null after clearPassword()");
        assertFalse(passwordSafe.hasPassword(), "hasPassword should be false after clearPassword()");
    }

    @Test
    @DisplayName("testPasswordSafe_WhitespaceTrimmingAndNullHandling: Handles leading/trailing whitespace and blanks")
    void testPasswordSafe_WhitespaceTrimmingAndNullHandling() {
        passwordSafe.setPassword("  " + SAMPLE_PAT + "  ");
        assertEquals(SAMPLE_PAT, passwordSafe.getPassword(), "PasswordSafe must trim whitespace on set");

        // Blank string acts as clearing password
        passwordSafe.setPassword("   ");
        assertNull(passwordSafe.getPassword(), "Blank string must clear PAT");
        assertFalse(passwordSafe.hasPassword());

        passwordSafe.setPassword(null);
        assertNull(passwordSafe.getPassword(), "Null value must clear PAT");
    }

    @Test
    @DisplayName("testPasswordSafe_CredentialAttributesSpec: Verifies serviceName and accountName construction")
    void testPasswordSafe_CredentialAttributesSpec() {
        CredentialAttributes attrs = passwordSafe.getCredentialAttributes();
        assertNotNull(attrs);
        assertEquals("Personal Access Token", attrs.getUserName());
        assertTrue(attrs.getServiceName().contains("Mini LeetCode"),
                "Service name must contain 'Mini LeetCode'");
        assertTrue(attrs.getServiceName().contains("Personal Access Token"),
                "Service name must contain 'Personal Access Token'");
    }

    @Test
    @DisplayName("testPasswordSafe_Concurrency: Concurrent access from multiple threads")
    void testPasswordSafe_Concurrency() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String token = "mlc_pat_thread_" + threadId + "_" + j;
                        passwordSafe.setPassword(token);
                        String read = passwordSafe.getPassword();
                        assertNotNull(read);
                        assertTrue(passwordSafe.hasPassword());
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, successCount.get());

        passwordSafe.clearPassword();
        assertNull(passwordSafe.getPassword());
        assertFalse(passwordSafe.hasPassword());
    }

    // =========================================================================
    // 3. MiniLeetCodeConfigurable UI & Lifecycle Tests
    // =========================================================================

    @Test
    @DisplayName("testConfigurableLifecycle: Verifies displayName, ID, component creation, and disposal")
    void testConfigurableLifecycle() {
        assertEquals("Mini LeetCode", configurable.getDisplayName());
        assertEquals("jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeConfigurable", configurable.getId());

        assertNotNull(configurable.createComponent(), "createComponent must return root JPanel");
        assertNotNull(configurable.getPreferredFocusedComponent(), "Preferred focused component should be server URL field");
        assertNotNull(configurable.getSettingsComponent(), "getSettingsComponent must return active component");

        configurable.disposeUIResources();
        assertNull(configurable.getSettingsComponent(), "disposeUIResources must nullify component reference");
    }

    @Test
    @DisplayName("testConfigurableIsModified: Returns false when UI matches state/PAT, true when modified")
    void testConfigurableIsModified() {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        // Sub-case 1: Initially unmodified
        assertFalse(configurable.isModified(), "Initially UI should match state and PAT");

        // Sub-case 2: Server URL changed
        ui.setServerUrl("http://custom-host:9000");
        assertTrue(configurable.isModified(), "isModified must be true when Server URL is changed");

        // Sub-case 3: Server URL reverted
        ui.setServerUrl(DEFAULT_URL);
        assertFalse(configurable.isModified(), "isModified must be false when Server URL is reverted");

        // Sub-case 4: PAT changed
        ui.setPassword("mlc_pat_new_secret_123");
        assertTrue(configurable.isModified(), "isModified must be true when PAT is changed");

        // Sub-case 5: PAT reverted
        ui.setPassword("");
        assertFalse(configurable.isModified(), "isModified must be false when PAT is reverted");

        // Sub-case 6: Both modified
        ui.setServerUrl("http://custom-host:9000");
        ui.setPassword("mlc_pat_new_secret_123");
        assertTrue(configurable.isModified(), "isModified must be true when both fields are modified");
    }

    @Test
    @DisplayName("testConfigurableApplyAndReset: apply() persists URL and PAT; reset() restores stored values")
    void testConfigurableApplyAndReset() throws ConfigurationException {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        // 1. Modify UI values
        ui.setServerUrl("https://cloud.minileetcode.com");
        ui.setPassword(SAMPLE_PAT);
        assertTrue(configurable.isModified());

        // 2. Call apply()
        configurable.apply();
        assertFalse(configurable.isModified(), "isModified must be false after apply()");
        assertEquals("https://cloud.minileetcode.com", settingsState.getServerUrl(),
                "SettingsState server URL must be updated");
        assertEquals(SAMPLE_PAT, passwordSafe.getPassword(),
                "PasswordSafe PAT must be updated");

        // 3. Modify UI values again without applying
        ui.setServerUrl("https://temporary-unapplied.com");
        ui.setPassword("temporary-pat");
        assertTrue(configurable.isModified());

        // 4. Call reset()
        configurable.reset();
        assertFalse(configurable.isModified(), "isModified must be false after reset()");
        assertEquals("https://cloud.minileetcode.com", ui.getServerUrl(),
                "UI server URL must revert to stored setting");
        assertEquals(SAMPLE_PAT, ui.getPassword(),
                "UI PAT must revert to stored setting");

        // 5. Clear PAT in UI and apply
        ui.setPassword("");
        assertTrue(configurable.isModified());
        configurable.apply();
        assertNull(passwordSafe.getPassword(), "Clearing PAT in UI must clear PasswordSafe");
        assertFalse(passwordSafe.hasPassword());
    }

    @Test
    @DisplayName("testConfigurableApply_BlankServerUrlThrowsConfigurationException")
    void testConfigurableApply_BlankServerUrlThrowsConfigurationException() {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl("   ");
        assertThrows(ConfigurationException.class, () -> configurable.apply(),
                "Blank Server URL must throw ConfigurationException on apply()");
    }

    // =========================================================================
    // 4. "Test connection" Verification Tests (MockWebServer)
    // =========================================================================

    @Test
    @DisplayName("testTestConnectionSuccess: HTTP 200 list -> displays green success feedback")
    void testTestConnectionSuccess() throws InterruptedException {
        String jsonPlans = """
            [
              {
                "id": 1,
                "slug": "oop-with-java",
                "title": "OOP với Java",
                "status": "PUBLISHED",
                "itemCount": 3,
                "stageCount": 7,
                "doneStages": 0
              }
            ]
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonPlans));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword(SAMPLE_PAT);

        ui.performTestConnectionSync();

        // Verify UI feedback
        assertTrue(ui.getStatusLabel().isVisible(), "Status label must be visible after test");
        assertEquals("Connection successful!", ui.getStatusLabel().getText(),
                "Success message should be 'Connection successful!'");
        assertEquals(JBColor.GREEN, ui.getStatusLabel().getForeground(),
                "Status label foreground must be JBColor.GREEN on success");
        assertTrue(ui.getTestConnectionButton().isEnabled(),
                "Test connection button must be re-enabled after completion");

        // Verify request dispatched to server
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "RecordedRequest must not be null");
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/study-plans", request.getPath());
        assertEquals("Bearer " + SAMPLE_PAT, request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
    }

    @Test
    @DisplayName("testTestConnectionAuthenticationFailure: HTTP 401 with PAT_INVALID -> displays red failure feedback")
    void testTestConnectionAuthenticationFailure() throws InterruptedException {
        String problemDetail = """
            {
              "type": "https://api.mini-leetcode.com/errors/unauthorized",
              "title": "Authentication Failed",
              "status": 401,
              "code": "PAT_INVALID",
              "detail": "Personal access token has expired"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemDetail));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword("mlc_pat_expired_token");

        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertTrue(ui.getStatusLabel().getText().contains("Authentication failed"),
                "Status message must state authentication failed");
        assertTrue(ui.getStatusLabel().getText().contains("Personal access token has expired"),
                "Status message must include error detail from problem JSON");
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground(),
                "Status label foreground must be JBColor.RED on error");
        assertTrue(ui.getTestConnectionButton().isEnabled());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/v1/study-plans", request.getPath());
    }

    @Test
    @DisplayName("testTestConnectionForbidden_403: HTTP 403 PAT_FORBIDDEN -> displays red error feedback")
    void testTestConnectionForbidden_403() throws InterruptedException {
        String problemDetail = """
            {
              "status": 403,
              "code": "PAT_FORBIDDEN",
              "detail": "PAT access denied for this resource"
            }
            """;

        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemDetail));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword(SAMPLE_PAT);

        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertTrue(ui.getStatusLabel().getText().contains("Connection failed"));
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("testTestConnectionNetworkError: Server shutdown before request -> displays network error feedback")
    void testTestConnectionNetworkError() throws IOException {
        server.shutdown();

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword(SAMPLE_PAT);

        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertTrue(ui.getStatusLabel().getText().toLowerCase().contains("connection failed")
                        || ui.getStatusLabel().getText().toLowerCase().contains("network error"),
                "Error message should mention connection failure or network error");
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertTrue(ui.getTestConnectionButton().isEnabled());
    }

    @Test
    @DisplayName("testTestConnectionValidation_EmptyServerUrl: Displays error without making network request")
    void testTestConnectionValidation_EmptyServerUrl() {
        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl("   ");
        ui.performTestConnectionSync();

        assertTrue(ui.getStatusLabel().isVisible());
        assertTrue(ui.getStatusLabel().getText().contains("Server URL must not be blank"));
        assertEquals(JBColor.RED, ui.getStatusLabel().getForeground());
        assertEquals(0, server.getRequestCount(), "No network requests should be dispatched for blank URL");
    }

    @Test
    @DisplayName("testTestConnectionWithoutPat_OmitsAuthorizationHeader: 200 OK -> connects without auth header")
    void testTestConnectionWithoutPat_OmitsAuthorizationHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        MiniLeetCodeSettingsComponent ui = configurable.getSettingsComponent();
        assertNotNull(ui);

        ui.setServerUrl(server.url("/").toString());
        ui.setPassword("");

        ui.performTestConnectionSync();

        assertEquals("Connection successful!", ui.getStatusLabel().getText());
        assertEquals(JBColor.GREEN, ui.getStatusLabel().getForeground());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertNull(request.getHeader("Authorization"),
                "Authorization header must be omitted when PAT is empty");
    }

    // =========================================================================
    // 5. In-Memory Test Double for PasswordSafe
    // =========================================================================

    /**
     * In-memory test double for {@link PasswordSafe}.
     * Implements abstract methods of PasswordSafe & CredentialStore with ConcurrentHashMap backing.
     * Guarantees 0 dependencies on Mockito or IntelliJ Platform service container.
     */
    static class TestPasswordSafe extends PasswordSafe {

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
