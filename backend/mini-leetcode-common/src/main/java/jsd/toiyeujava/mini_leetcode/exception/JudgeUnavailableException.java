package jsd.toiyeujava.mini_leetcode.exception;

/** Judge worker không phản hồi (hoặc đang bận) khi API cần gọi dịch vụ nội bộ của nó. Trả 503. */
public class JudgeUnavailableException extends RuntimeException {
    public JudgeUnavailableException(String message) {
        super(message);
    }

    public JudgeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
