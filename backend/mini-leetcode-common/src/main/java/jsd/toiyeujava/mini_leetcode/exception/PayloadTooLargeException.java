package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Thrown when submission uncompressed size exceeds 20MB
 * or an archive entry count exceeds the allowable limit.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }

    public PayloadTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }
}
