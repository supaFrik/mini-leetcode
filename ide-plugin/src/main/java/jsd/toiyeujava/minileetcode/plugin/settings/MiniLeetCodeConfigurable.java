package jsd.toiyeujava.minileetcode.plugin.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Controller for Settings › Tools › Mini LeetCode configuration page.
 * Implements {@link SearchableConfigurable} for integration into IntelliJ IDEA Settings dialog.
 * Synchronizes user inputs with {@link MiniLeetCodeSettingsState} (server URL)
 * and {@link MiniLeetCodePasswordSafe} (secure PAT storage).
 */
public class MiniLeetCodeConfigurable implements SearchableConfigurable {

    public static final String ID = "jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeConfigurable";
    public static final String DISPLAY_NAME = "Mini LeetCode";

    private MiniLeetCodeSettingsComponent mySettingsComponent;
    private final Supplier<MiniLeetCodeSettingsState> settingsStateSupplier;
    private final Supplier<MiniLeetCodePasswordSafe> passwordSafeSupplier;

    /**
     * Default constructor invoked by IntelliJ Platform extension point.
     */
    public MiniLeetCodeConfigurable() {
        this(MiniLeetCodeSettingsState::getInstance, MiniLeetCodePasswordSafe::getInstance);
    }

    /**
     * Dependency injection constructor for headless unit testing.
     */
    public MiniLeetCodeConfigurable(@Nullable Supplier<MiniLeetCodeSettingsState> settingsStateSupplier,
                                    @Nullable Supplier<MiniLeetCodePasswordSafe> passwordSafeSupplier) {
        this.settingsStateSupplier = settingsStateSupplier != null
                ? settingsStateSupplier
                : MiniLeetCodeSettingsState::getInstance;
        this.passwordSafeSupplier = passwordSafeSupplier != null
                ? passwordSafeSupplier
                : MiniLeetCodePasswordSafe::getInstance;
    }

    @NotNull
    @Override
    @NonNls
    public String getId() {
        return ID;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (mySettingsComponent == null) {
            mySettingsComponent = new MiniLeetCodeSettingsComponent();
        }
        return mySettingsComponent.getPanel();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent != null ? mySettingsComponent.getPreferredFocusedComponent() : null;
    }

    @Override
    public boolean isModified() {
        if (mySettingsComponent == null) {
            return false;
        }

        MiniLeetCodeSettingsState settings = settingsStateSupplier.get();
        String savedUrl = (settings != null && settings.getServerUrl() != null)
                ? settings.getServerUrl()
                : MiniLeetCodeSettingsComponent.DEFAULT_SERVER_URL;
        String currentUrl = mySettingsComponent.getServerUrl();
        boolean urlModified = !Objects.equals(currentUrl, savedUrl);

        MiniLeetCodePasswordSafe passwordSafe = passwordSafeSupplier.get();
        String savedPassword = (passwordSafe != null) ? passwordSafe.getPassword() : null;
        if (savedPassword == null) {
            savedPassword = "";
        }
        String currentPassword = mySettingsComponent.getPassword();
        boolean passwordModified = !Objects.equals(currentPassword, savedPassword);

        return urlModified || passwordModified;
    }

    @Override
    public void apply() throws ConfigurationException {
        if (mySettingsComponent == null) {
            return;
        }

        String serverUrl = mySettingsComponent.getServerUrl();
        if (serverUrl.isBlank()) {
            throw new ConfigurationException("Server URL must not be blank.");
        }

        MiniLeetCodeSettingsState settings = settingsStateSupplier.get();
        if (settings != null) {
            settings.setServerUrl(serverUrl);
        }

        String password = mySettingsComponent.getPassword();
        MiniLeetCodePasswordSafe passwordSafe = passwordSafeSupplier.get();
        if (passwordSafe != null) {
            if (password.isBlank()) {
                passwordSafe.clearPassword();
            } else {
                passwordSafe.setPassword(password);
            }
        }
    }

    @Override
    public void reset() {
        if (mySettingsComponent == null) {
            return;
        }

        MiniLeetCodeSettingsState settings = settingsStateSupplier.get();
        String savedUrl = (settings != null && settings.getServerUrl() != null)
                ? settings.getServerUrl()
                : MiniLeetCodeSettingsComponent.DEFAULT_SERVER_URL;
        mySettingsComponent.setServerUrl(savedUrl);

        MiniLeetCodePasswordSafe passwordSafe = passwordSafeSupplier.get();
        String savedPassword = (passwordSafe != null) ? passwordSafe.getPassword() : null;
        mySettingsComponent.setPassword(savedPassword != null ? savedPassword : "");

        mySettingsComponent.clearStatus();
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    @Nullable
    public MiniLeetCodeSettingsComponent getSettingsComponent() {
        return mySettingsComponent;
    }
}
