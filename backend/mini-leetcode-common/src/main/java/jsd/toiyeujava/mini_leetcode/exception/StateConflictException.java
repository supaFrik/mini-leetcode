package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Xung đột trạng thái: yêu cầu hợp lệ về mặt hình thức nhưng vi phạm quy tắc
 * nghiệp vụ của trạng thái hiện tại (ví dụ: chuyển sang {@code PUBLISHED} khi
 * bài chưa có testcase công khai nào).
 *
 * Trả về HTTP 409 Conflict — khác với {@code IllegalArgumentException} (400)
 * vốn dành cho payload sai.
 */
public class StateConflictException extends RuntimeException {
    public StateConflictException(String message) {
        super(message);
    }
}
