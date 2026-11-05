package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Ném ra khi sandbox Docker không khả dụng ⇒ ánh xạ HTTP 503.
 *
 * <p>Nguyên tắc: tuyệt đối KHÔNG fallback sang chạy code học viên trong JVM của
 * API/judge worker. Khi Docker chết thì console trả lỗi 503 rõ ràng.
 */
public class SandboxUnavailableException extends RuntimeException {

    public SandboxUnavailableException(String message) {
        super(message);
    }

    public SandboxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
