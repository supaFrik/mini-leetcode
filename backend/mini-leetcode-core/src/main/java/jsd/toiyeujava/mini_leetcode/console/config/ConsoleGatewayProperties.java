package jsd.toiyeujava.mini_leetcode.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình phía API cho console OOP.
 *
 * <p>API gọi judge worker qua HTTP nội bộ (docker network), kèm header
 * {@code X-Internal-Token}. Judge worker KHÔNG publish port ra host.
 */
@Component
@ConfigurationProperties(prefix = "app.console")
public class ConsoleGatewayProperties {

    /** Base URL của judge worker (nội bộ). */
    private String judgeBaseUrl = "http://localhost:8081";

    /**
     * Token nội bộ gửi qua header {@code X-Internal-Token}.
     * Rỗng ⇒ console bị tắt (mọi lệnh trả 503).
     */
    private String internalToken = "mini-leetcode-internal-token";

    /** Số lệnh tối đa cho mỗi phiên console (theo user + problem). */
    private int maxCommandsPerSession = 50;

    /** TTL transcript trong Redis (giây). */
    private long transcriptTtlSeconds = 3_600L;

    /** Tổng dung lượng mã nguồn tối đa cho một lệnh (byte). */
    private long maxSourceBytes = 20L * 1024 * 1024;

    /** Số file mã nguồn tối đa cho một lệnh. */
    private int maxFiles = 100;

    /** Timeout gọi judge worker (ms). */
    private long requestTimeoutMs = 20_000L;

    public String getJudgeBaseUrl() {
        return judgeBaseUrl;
    }

    public void setJudgeBaseUrl(String judgeBaseUrl) {
        this.judgeBaseUrl = judgeBaseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public int getMaxCommandsPerSession() {
        return maxCommandsPerSession;
    }

    public void setMaxCommandsPerSession(int maxCommandsPerSession) {
        this.maxCommandsPerSession = maxCommandsPerSession;
    }

    public long getTranscriptTtlSeconds() {
        return transcriptTtlSeconds;
    }

    public void setTranscriptTtlSeconds(long transcriptTtlSeconds) {
        this.transcriptTtlSeconds = transcriptTtlSeconds;
    }

    public long getMaxSourceBytes() {
        return maxSourceBytes;
    }

    public void setMaxSourceBytes(long maxSourceBytes) {
        this.maxSourceBytes = maxSourceBytes;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
