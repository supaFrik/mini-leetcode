package jsd.toiyeujava.minileetcode.plugin.client;

/**
 * Đại diện cho cấu trúc lỗi chuẩn RFC 7807 ProblemDetail do backend trả về.
 */
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String timestamp
) {}
