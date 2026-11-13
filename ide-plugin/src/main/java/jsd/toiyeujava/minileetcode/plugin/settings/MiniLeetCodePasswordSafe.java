package jsd.toiyeujava.minileetcode.plugin.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * Lớp bọc bảo mật (Secure Storage Wrapper) tương tác với IntelliJ Platform PasswordSafe
 * để lưu trữ, truy xuất và xoá Personal Access Token (PAT).
 *
 * TUYỆT ĐỐI TUÂN THỦ FR-PL-003:
 * PAT được lưu trong System Keychain / OS Credential Store (qua PasswordSafe),
 * không bao giờ được ghi ra file XML cấu hình hoặc thư mục dự án.
 */
public class MiniLeetCodePasswordSafe {

    public static final String SERVICE_NAME = "Mini LeetCode";
    public static final String ACCOUNT_NAME = "Personal Access Token";

    private static volatile MiniLeetCodePasswordSafe instance;

    private final PasswordSafe passwordSafe;
    private final CredentialAttributes credentialAttributes;

    public MiniLeetCodePasswordSafe() {
        this(PasswordSafe.getInstance());
    }

    public MiniLeetCodePasswordSafe(@NotNull PasswordSafe passwordSafe) {
        this(passwordSafe, createCredentialAttributes());
    }

    public MiniLeetCodePasswordSafe(@NotNull PasswordSafe passwordSafe, @NotNull CredentialAttributes credentialAttributes) {
        this.passwordSafe = Objects.requireNonNull(passwordSafe, "passwordSafe must not be null");
        this.credentialAttributes = Objects.requireNonNull(credentialAttributes, "credentialAttributes must not be null");
    }

    /**
     * Khởi tạo CredentialAttributes tương thích với IntelliJ Platform 2025.3+.
     * ServiceName = CredentialAttributesKt.generateServiceName("Mini LeetCode", "Personal Access Token")
     * UserName = "Personal Access Token".
     */
    @NotNull
    public static CredentialAttributes createCredentialAttributes() {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE_NAME, ACCOUNT_NAME),
                ACCOUNT_NAME
        );
    }

    /**
     * Lấy instance Singleton (hỗ trợ Application container và thread-safe fallback).
     */
    @NotNull
    public static MiniLeetCodePasswordSafe getInstance() {
        if (instance != null) {
            return instance;
        }
        Application app = ApplicationManager.getApplication();
        if (app != null) {
            MiniLeetCodePasswordSafe service = app.getService(MiniLeetCodePasswordSafe.class);
            if (service != null) {
                return service;
            }
        }
        if (instance == null) {
            synchronized (MiniLeetCodePasswordSafe.class) {
                if (instance == null) {
                    instance = new MiniLeetCodePasswordSafe();
                }
            }
        }
        return instance;
    }

    /**
     * Đọc Personal Access Token từ PasswordSafe.
     *
     * @return Token dưới dạng chuỗi đã trim, hoặc null nếu chưa được lưu hoặc rỗng.
     */
    @Nullable
    public String getPassword() {
        String token = passwordSafe.getPassword(credentialAttributes);
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim();
    }

    /**
     * Lưu trữ Personal Access Token vào PasswordSafe.
     * Nếu token là null hoặc rỗng, sẽ tự động xoá token khỏi kho lưu trữ.
     *
     * @param password Token cần lưu trữ.
     */
    public void setPassword(@Nullable String password) {
        if (password == null || password.isBlank()) {
            clearPassword();
            return;
        }
        passwordSafe.setPassword(credentialAttributes, password.trim());
    }

    /**
     * Xoá Personal Access Token khỏi PasswordSafe.
     */
    public void clearPassword() {
        passwordSafe.setPassword(credentialAttributes, null);
    }

    /**
     * Kiểm tra nhanh sự tồn tại của PAT mà không làm lộ token.
     */
    public boolean hasPassword() {
        String token = getPassword();
        return token != null && !token.isBlank();
    }

    @NotNull
    public CredentialAttributes getCredentialAttributes() {
        return credentialAttributes;
    }

    @TestOnly
    public static void setTestInstance(@Nullable MiniLeetCodePasswordSafe testInstance) {
        synchronized (MiniLeetCodePasswordSafe.class) {
            instance = testInstance;
        }
    }

    @Override
    public String toString() {
        return "MiniLeetCodePasswordSafe{serviceName='" + credentialAttributes.getServiceName() + "'}";
    }
}
