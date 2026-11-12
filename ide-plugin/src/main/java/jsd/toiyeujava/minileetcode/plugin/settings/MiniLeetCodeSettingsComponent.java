package jsd.toiyeujava.minileetcode.plugin.settings;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import jsd.toiyeujava.minileetcode.plugin.client.ApiClient;
import jsd.toiyeujava.minileetcode.plugin.client.AuthenticationException;
import jsd.toiyeujava.minileetcode.plugin.client.MiniLeetCodeApiException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;

/**
 * UI Component for Mini LeetCode settings panel in Settings › Tools › Mini LeetCode.
 * Encapsulates Swing controls (JBTextField, JBPasswordField, JButton, JBLabel)
 * and handles asynchronous connection verification without blocking the EDT.
 */
public class MiniLeetCodeSettingsComponent {

    public static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private final JPanel mainPanel;
    private final JBTextField serverUrlField = new JBTextField();
    private final JBPasswordField passwordField = new JBPasswordField();
    private final JButton testConnectionButton = new JButton("Test connection");
    private final JBLabel statusLabel = new JBLabel();

    private final ApiClientFactory apiClientFactory;

    /**
     * Pluggable factory for creating {@link ApiClient} instances.
     * Enables isolated headless unit testing with custom timeout or MockWebServer instances.
     */
    @FunctionalInterface
    public interface ApiClientFactory {
        ApiClient create(String serverUrl, String pat);
    }

    public MiniLeetCodeSettingsComponent() {
        this((url, pat) -> new ApiClient(url, pat, Duration.ofSeconds(10)));
    }

    public MiniLeetCodeSettingsComponent(@Nullable ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory != null
                ? apiClientFactory
                : (url, pat) -> new ApiClient(url, pat, Duration.ofSeconds(10));

        serverUrlField.getEmptyText().setText(DEFAULT_SERVER_URL);
        serverUrlField.setText(DEFAULT_SERVER_URL);

        statusLabel.setVisible(false);

        // Action panel with "Test connection" button and visual feedback label
        JPanel testActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        testActionPanel.add(testConnectionButton);
        testActionPanel.add(statusLabel);

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Server URL: "), serverUrlField, 1, false)
                .addTooltip("Base URL of the Mini LeetCode server (e.g. http://localhost:8080)")
                .addLabeledComponent(new JBLabel("Personal Access Token: "), passwordField, 1, false)
                .addTooltip("Personal Access Token (PAT) created on web workspace. Stored securely via PasswordSafe.")
                .addComponent(testActionPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        testConnectionButton.addActionListener(e -> performTestConnection());
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return serverUrlField;
    }

    @NotNull
    public String getServerUrl() {
        String text = serverUrlField.getText();
        return text != null ? text.trim() : "";
    }

    public void setServerUrl(@Nullable String serverUrl) {
        serverUrlField.setText(serverUrl != null ? serverUrl.trim() : DEFAULT_SERVER_URL);
    }

    @NotNull
    public String getPassword() {
        char[] password = passwordField.getPassword();
        return password != null ? new String(password).trim() : "";
    }

    public void setPassword(@Nullable String password) {
        passwordField.setText(password != null ? password : "");
    }

    public JButton getTestConnectionButton() {
        return testConnectionButton;
    }

    public JBLabel getStatusLabel() {
        return statusLabel;
    }

    public void clearStatus() {
        runOnEdt(() -> {
            statusLabel.setText("");
            statusLabel.setVisible(false);
        });
    }

    public void showSuccessStatus(@NotNull String message) {
        runOnEdt(() -> {
            statusLabel.setForeground(JBColor.GREEN);
            statusLabel.setText(message);
            statusLabel.setVisible(true);
        });
    }

    public void showErrorStatus(@NotNull String message) {
        runOnEdt(() -> {
            statusLabel.setForeground(JBColor.RED);
            statusLabel.setText(message);
            statusLabel.setVisible(true);
        });
    }

    public void showInfoStatus(@NotNull String message) {
        runOnEdt(() -> {
            statusLabel.setForeground(JBColor.GRAY);
            statusLabel.setText(message);
            statusLabel.setVisible(true);
        });
    }

    public void setTestingInProgress(boolean inProgress, @Nullable String progressMessage) {
        runOnEdt(() -> {
            testConnectionButton.setEnabled(!inProgress);
            if (inProgress) {
                showInfoStatus(progressMessage != null ? progressMessage : "Testing connection...");
            }
        });
    }

    /**
     * Executes test connection asynchronously on a background thread.
     * Uses IntelliJ {@link ProgressManager} and {@link Task.Backgroundable} when running inside the IDE,
     * or a daemon background thread when running in headless unit test mode.
     */
    public void performTestConnection() {
        String url = getServerUrl();
        if (url.isBlank()) {
            showErrorStatus("Server URL must not be blank.");
            return;
        }

        Application app = ApplicationManager.getApplication();
        if (app != null) {
            ProgressManager.getInstance().run(new Task.Backgroundable(null, "Testing Mini LeetCode Connection", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("Connecting to " + url + "...");
                    performTestConnectionSync();
                }
            });
        } else {
            Thread bgThread = new Thread(this::performTestConnectionSync, "MiniLeetCode-TestConnection");
            bgThread.setDaemon(true);
            bgThread.start();
        }
    }

    /**
     * Synchronous implementation of the connection test.
     * Dispatches network calls using {@link ApiClient#testConnection()} and schedules UI updates on EDT.
     * Can be invoked directly in headless unit tests for deterministic assertions.
     */
    public void performTestConnectionSync() {
        String url = getServerUrl();
        String pat = getPassword();

        if (url.isBlank()) {
            showErrorStatus("Server URL must not be blank.");
            return;
        }

        setTestingInProgress(true, "Connecting to " + url + "...");

        try {
            ApiClient client = apiClientFactory.create(url, pat);
            client.testConnection();

            runOnEdt(() -> {
                setTestingInProgress(false, null);
                showSuccessStatus("Connection successful!");
            });
        } catch (AuthenticationException e) {
            String errorMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "Personal access token is invalid or expired (HTTP 401)";
            runOnEdt(() -> {
                setTestingInProgress(false, null);
                showErrorStatus("Authentication failed: " + errorMsg);
            });
        } catch (MiniLeetCodeApiException e) {
            String errorMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "API error (HTTP " + e.getStatusCode() + ")";
            runOnEdt(() -> {
                setTestingInProgress(false, null);
                showErrorStatus("Connection failed: " + errorMsg);
            });
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            runOnEdt(() -> {
                setTestingInProgress(false, null);
                showErrorStatus("Connection failed: " + errorMsg);
            });
        }
    }

    /**
     * Guarantees runnable execution on the Swing Event Dispatch Thread (EDT).
     * If IntelliJ ApplicationManager is available, delegates to {@link Application#invokeLater(Runnable)}.
     * If running in standalone headless test mode without ApplicationManager, executes immediately.
     */
    private void runOnEdt(Runnable runnable) {
        Application app = ApplicationManager.getApplication();
        if (app != null) {
            app.invokeLater(runnable);
        } else if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            runnable.run();
        }
    }
}
