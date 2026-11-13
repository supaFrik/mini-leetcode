package jsd.toiyeujava.mini_leetcode.exception;

/** Luyện tập khi chủ đề đang bị khoá (sai 3 lần) hoặc chưa đọc lại lý thuyết (HTTP 409, code TOPIC_LOCKED). */
public class TopicLockedException extends StateConflictException {
    public static final String CODE = "TOPIC_LOCKED";

    public TopicLockedException(String message) {
        super(message);
    }
}
