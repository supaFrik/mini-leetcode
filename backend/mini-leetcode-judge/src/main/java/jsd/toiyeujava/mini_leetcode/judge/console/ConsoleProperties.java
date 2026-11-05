package jsd.toiyeujava.mini_leetcode.judge.console;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình cho console OOP và endpoint nội bộ của judge worker.
 *
 * <p>Token nội bộ dùng để API xác thực với judge worker. Judge worker KHÔNG
 * được publish port ra host — nó chỉ nghe trong docker network nội bộ.
 */
@Component
@ConfigurationProperties(prefix = "app.console")
public class ConsoleProperties {

    /**
     * Token nội bộ dùng cho header {@code X-Internal-Token}.
     * Rỗng ⇒ console bị tắt (mọi request trả 503).
     */
    private String internalToken = "mini-leetcode-internal-token";

    /** Số lệnh tối đa cho mỗi phiên console (theo user + problem). */
    private int maxCommandsPerSession = 50;

    /** Timeout cho mỗi lệnh console (ms). */
    private long timeoutMs = 3_000L;

    /** Thời gian khởi động JVM được cộng thêm (ms). */
    private long startupAllowanceMs = 2_000L;

    /** Giới hạn bộ nhớ của container console (MB). */
    private int memoryLimitMb = 256;

    /** Giới hạn output cho mỗi luồng (stdout/stderr) — byte. */
    private int maxOutputBytes = 64 * 1024;

    /** TTL của transcript trong Redis (giây). */
    private long transcriptTtlSeconds = 3_600L;

    /** Tổng dung lượng mã nguồn tối đa cho một lệnh (byte). */
    private long maxSourceBytes = 20L * 1024 * 1024;

    /** Số file mã nguồn tối đa cho một lệnh. */
    private int maxFiles = 100;

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

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getStartupAllowanceMs() {
        return startupAllowanceMs;
    }

    public void setStartupAllowanceMs(long startupAllowanceMs) {
        this.startupAllowanceMs = startupAllowanceMs;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public void setMemoryLimitMb(int memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
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
}
