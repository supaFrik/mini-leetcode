package jsd.toiyeujava.mini_leetcode.console.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình phía API cho console OOP ({@code app.console.*}).
 *
 * <p>API gọi judge worker qua HTTP nội bộ (docker network), kèm header
 * {@code X-Internal-Token}. Judge worker KHÔNG publish port ra host.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.console")
public class ConsoleGatewayProperties {
    private String judgeBaseUrl = "http://localhost:8081";
    /** Rỗng ⇒ console bị tắt (503). */
    private String internalToken = "";
    /** Số lần chạy tối đa mỗi giờ cho mỗi user, cộng dồn mọi bài (mỗi lần = 1 container Docker). */
    private int maxRunsPerHour = 50;
    private long maxSourceBytes = 20L * 1024 * 1024;
    private int maxFiles = 100;
    /** Timeout gọi judge worker (ms). */
    private long requestTimeoutMs = 20_000L;
}
