package jsd.toiyeujava.minileetcode.plugin.settings;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * PersistentStateComponent quản lý cấu hình không nhạy cảm của Mini LeetCode plugin.
 * Lưu trữ serverUrl trong tệp XML MiniLeetCodeSettings.xml tại thư mục options của IDE.
 *
 * TUYỆT ĐỐI TUÂN THỦ ĐẶC TẢ FR-PL-003:
 * Personal Access Token (PAT) KHÔNG ĐƯỢC LƯU TRỮ TẠI ĐÂY hay trong bất kỳ tệp XML nào.
 * PAT được quản lý hoàn toàn độc lập qua MiniLeetCodePasswordSafe (IntelliJ PasswordSafe).
 */
@State(
    name = "jsd.toiyeujava.minileetcode.plugin.settings.MiniLeetCodeSettingsState",
    storages = @Storage("MiniLeetCodeSettings.xml")
)
public class MiniLeetCodeSettingsState implements PersistentStateComponent<MiniLeetCodeSettingsState.State> {

    public static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    /**
     * DTO Schema đại diện cho dữ liệu XML được tuần tự hoá vào MiniLeetCodeSettings.xml.
     */
    public static class State {
        @NotNull
        public String serverUrl = DEFAULT_SERVER_URL;

        public State() {
        }

        public State(@NotNull String serverUrl) {
            this.serverUrl = serverUrl;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return Objects.equals(serverUrl, state.serverUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverUrl);
        }

        @Override
        public String toString() {
            return "State{serverUrl='" + serverUrl + "'}";
        }
    }

    private State state = new State();
    private static volatile MiniLeetCodeSettingsState instance;

    public MiniLeetCodeSettingsState() {
    }

    /**
     * Singleton accessor lấy instance từ IntelliJ Application Services (với fallback cho headless/testing).
     */
    public static MiniLeetCodeSettingsState getInstance() {
        Application app = ApplicationManager.getApplication();
        if (app != null) {
            MiniLeetCodeSettingsState service = app.getService(MiniLeetCodeSettingsState.class);
            if (service != null) {
                return service;
            }
        }
        if (instance == null) {
            synchronized (MiniLeetCodeSettingsState.class) {
                if (instance == null) {
                    instance = new MiniLeetCodeSettingsState();
                }
            }
        }
        return instance;
    }

    @Override
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    @NotNull
    public String getServerUrl() {
        return (state.serverUrl != null && !state.serverUrl.isBlank())
                ? state.serverUrl
                : DEFAULT_SERVER_URL;
    }

    public void setServerUrl(@Nullable String serverUrl) {
        state.serverUrl = (serverUrl != null && !serverUrl.isBlank())
                ? serverUrl.trim()
                : DEFAULT_SERVER_URL;
    }

    /**
     * Chuẩn hoá serverUrl: loại bỏ khoảng trắng và dấu gạch chéo cuối.
     * Đảm bảo ApiClient không gặp lỗi double-slash khi nối endpoint.
     */
    @NotNull
    public String getNormalizedServerUrl() {
        String url = getServerUrl();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
