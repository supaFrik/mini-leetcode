package jsd.toiyeujava.mini_leetcode.judge.console;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình console OOP phía judge worker ({@code app.console.*}).
 *
 * <p>Token nội bộ xác thực API với judge worker; rỗng/sai ⇒ 401. KHÔNG có giá trị
 * mặc định: quên cấu hình ⇒ console tắt (fail closed), không phải mở với token công khai.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.console")
public class ConsoleProperties {
    private String internalToken = "";
    /** Số lượt chạy console đồng thời tối đa trên worker (mỗi lượt = 1 container). */
    private int maxConcurrentRuns = 4;
    /** Timeout cho mỗi lần chạy (ms), cộng thêm thời gian khởi động JVM. */
    private long timeoutMs = 3_000L;
    private int memoryLimitMb = 256;
    private long maxSourceBytes = 20L * 1024 * 1024;
    private int maxFiles = 100;
}
