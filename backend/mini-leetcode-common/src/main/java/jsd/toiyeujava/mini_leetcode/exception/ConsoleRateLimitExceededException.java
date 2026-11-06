package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Ném ra khi phiên console đã vượt quá số lệnh cho phép ⇒ ánh xạ HTTP 429.
 */
public class ConsoleRateLimitExceededException extends RuntimeException {

    public ConsoleRateLimitExceededException(String message) {
        super(message);
    }
}
