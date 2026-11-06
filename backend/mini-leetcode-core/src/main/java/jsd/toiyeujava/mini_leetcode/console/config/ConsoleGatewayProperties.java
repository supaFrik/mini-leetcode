package jsd.toiyeujava.mini_leetcode.console.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình phía API cho console live: API relay WebSocket của trình duyệt tới judge worker
 * ({@code judgeWsUrl}, header {@code X-Internal-Token}). Judge KHÔNG publish port ra host.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.console")
public class ConsoleGatewayProperties {

    /** WebSocket nội bộ của judge worker. */
    private String judgeWsUrl = "ws://localhost:8081/internal/console/ws";

    /** Token nội bộ gửi qua header {@code X-Internal-Token} khi handshake với judge. */
    private String internalToken = "mini-leetcode-internal-token";

    /** Số lần Run tối đa của một user trong mỗi cửa sổ {@link #windowSeconds}. */
    private int runsPerWindow = 30;

    private long windowSeconds = 600;

    /** Số file mã nguồn tối đa cho một lần Run. */
    private int maxFiles = 100;

    /** Tổng dung lượng mã nguồn tối đa (byte). */
    private long maxSourceBytes = 1024L * 1024;

    /** Kích thước tối đa một WebSocket text frame (frame start chứa mã nguồn). */
    private int maxMessageBytes = 2 * 1024 * 1024;

    /** Kích thước tối đa một frame stdin (byte). */
    private int maxStdinBytes = 64 * 1024;
}
