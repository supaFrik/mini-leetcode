package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Ném ra khi console bị khoá trong chế độ thi ⇒ ánh xạ HTTP 403.
 */
public class ConsoleDisabledException extends RuntimeException {

    public ConsoleDisabledException(String message) {
        super(message);
    }
}
