package jsd.toiyeujava.mini_leetcode.exception;

import java.util.List;

/** {@code grading_config} không hợp lệ. Mang danh sách lỗi theo đường dẫn JSON để hiện cho giảng viên. Trả 400. */
public class InvalidGradingConfigException extends RuntimeException {
    private final List<String> errors;

    public InvalidGradingConfigException(List<String> errors) {
        super("Invalid grading configuration: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
