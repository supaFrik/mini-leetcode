package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Người dùng đã đăng nhập nhưng không được phép làm việc này vì luật nghiệp vụ
 * (ví dụ đăng ký kỳ thi chỉ dành cho người được mời). Trả về 403 kèm lý do cụ thể,
 * khác với {@code AccessDeniedException} vốn trả thông điệp chung về quyền.
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
