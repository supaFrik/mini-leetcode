package jsd.toiyeujava.mini_leetcode.exception;

/** Nộp stage N khi stage N-1 của cùng mục lộ trình chưa hoàn thành (HTTP 409, code STAGE_LOCKED). */
public class StageLockedException extends StateConflictException {
    public static final String CODE = "STAGE_LOCKED";

    public StageLockedException(String message) {
        super(message);
    }
}
