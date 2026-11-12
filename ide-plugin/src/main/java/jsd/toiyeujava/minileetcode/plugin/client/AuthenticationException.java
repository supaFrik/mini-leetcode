package jsd.toiyeujava.minileetcode.plugin.client;

/**
 * Ngoại lệ khi xác thực thất bại (HTTP 401).
 * Chứa mã code (mặc định PAT_INVALID hoặc UNAUTHORIZED) để UI mở Settings yêu cầu người dùng cập nhật PAT.
 */
public class AuthenticationException extends MiniLeetCodeApiException {

    public AuthenticationException(String message, String code) {
        super(message, 401, code != null ? code : "UNAUTHORIZED");
    }

    public AuthenticationException(String message, String code, ProblemDetail problemDetail) {
        super(401, message, code != null ? code : "UNAUTHORIZED", problemDetail, null);
    }

    public AuthenticationException(String message, String code, Throwable cause) {
        super(message, 401, code != null ? code : "UNAUTHORIZED", cause);
    }
}
