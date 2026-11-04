package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Thrown when a verdict filter is not a verdict name or PENDING.
 * Extends IllegalArgumentException so the global handler still answers 400 Bad Request.
 */
public class InvalidVerdictException extends IllegalArgumentException {

    public InvalidVerdictException(String verdict) {
        super("Unknown verdict: " + verdict);
    }
}
