package jsd.toiyeujava.minileetcode.plugin.client;

/**
 * Root exception cho các lỗi kết nối hoặc phản hồi HTTP không thành công từ Mini LeetCode API.
 */
public class MiniLeetCodeApiException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final ProblemDetail problemDetail;

    public MiniLeetCodeApiException(String message) {
        this(0, message, null, null, null);
    }

    public MiniLeetCodeApiException(String message, Throwable cause) {
        this(0, message, null, null, cause);
    }

    public MiniLeetCodeApiException(int statusCode, String message) {
        this(statusCode, message, null, null, null);
    }

    public MiniLeetCodeApiException(int statusCode, String message, ProblemDetail problemDetail) {
        this(statusCode, message, problemDetail != null ? problemDetail.code() : null, problemDetail, null);
    }

    public MiniLeetCodeApiException(String message, int statusCode, String code) {
        this(statusCode, message, code, null, null);
    }

    public MiniLeetCodeApiException(String message, int statusCode, String code, Throwable cause) {
        this(statusCode, message, code, null, cause);
    }

    public MiniLeetCodeApiException(int statusCode, String message, String code, ProblemDetail problemDetail, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
        this.problemDetail = problemDetail;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code != null ? code : (problemDetail != null ? problemDetail.code() : null);
    }

    public String getErrorCode() {
        return getCode();
    }

    public ProblemDetail getProblemDetail() {
        return problemDetail;
    }
}
