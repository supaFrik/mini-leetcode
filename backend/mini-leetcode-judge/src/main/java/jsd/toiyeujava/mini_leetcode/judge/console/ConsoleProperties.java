package jsd.toiyeujava.mini_leetcode.judge.console;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Giới hạn của console live (WebSocket nội bộ {@code /internal/console/ws}). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.console")
public class ConsoleProperties {

    /** Token header {@code X-Internal-Token}; rỗng ⇒ mọi handshake bị từ chối. */
    private String internalToken = "mini-leetcode-internal-token";

    /** Thời gian chạy tối đa của một lần Run (kể cả lúc chờ người dùng gõ). */
    private long maxWallMs = 120_000L;

    /** Kill nếu không có stdin lẫn output trong khoảng này. */
    private long idleMs = 30_000L;

    /** Tổng stdout + stderr tối đa (byte). */
    private int maxOutputBytes = 64 * 1024;

    /** Số container console chạy đồng thời tối đa trên một judge. */
    private int maxConcurrent = 20;

    private int memoryLimitMb = 256;

    /** Kích thước tối đa một WebSocket text frame (frame start chứa mã nguồn). */
    private int maxMessageBytes = 2 * 1024 * 1024;
}
