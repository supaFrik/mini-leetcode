package jsd.toiyeujava.mini_leetcode.submission.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình cho hàng đợi bài nộp dựa trên Redis Streams.
 *
 * <p>Các giá trị mặc định phải giống nhau giữa module {@code mini-leetcode-api}
 * (producer) và {@code mini-leetcode-judge} (consumer), vì cả hai cùng trỏ tới
 * một stream key. Nếu triển khai Docker Compose, hãy truyền cùng giá trị qua
 * biến môi trường cho cả hai service.
 */
@Component
@ConfigurationProperties(prefix = "app.judge")
public class JudgeProperties {

    /** Key của Redis Stream chứa bài nộp chờ chấm. */
    private String streamKey = "stream:submissions";

    /**
     * Key của Redis List theo cơ chế cũ (RPUSH/BLPOP).
     * Dùng một lần duy nhất để chuyển dữ liệu tồn đọng sang stream khi deploy.
     */
    private String legacyQueueKey = "queue.submissions";

    /** Tên consumer group của judge worker. */
    private String consumerGroup = "judge-group";

    /**
     * Tên consumer. Rỗng => tự sinh theo hostname + random suffix để nhiều worker
     * không đụng tên nhau trong {@code XINFO CONSUMERS}.
     */
    private String consumerName = "";

    /** Thời gian chặn (ms) trong {@code XREADGROUP} khi không có bài mới. */
    private long blockTimeoutMs = 5_000;

    /** Chu kỳ (ms) quét {@code XPENDING} để thu hồi bài nộp "zombie". */
    private long recoveryIntervalMs = 30_000;

    /**
     * Thời gian (ms) một bài nộp phải nằm trong trạng thái pending mà không được
     * xử lý lại thì coi là zombie và được {@code XCLAIM} về.
     */
    private long minIdleMs = 120_000;

    /** Số bản ghi pending tối đa lấy ra trong một lần quét. */
    private int recoveryBatchSize = 50;

    /** Bật chạy song song các testcase (đổi ngữ nghĩa verdict so với fail-fast). */
    private boolean parallelTestcases = false;

    /** Số testcase chạy đồng thời tối đa khi bật parallel. */
    private int maxParallelTestcases = 4;

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getLegacyQueueKey() {
        return legacyQueueKey;
    }

    public void setLegacyQueueKey(String legacyQueueKey) {
        this.legacyQueueKey = legacyQueueKey;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public long getBlockTimeoutMs() {
        return blockTimeoutMs;
    }

    public void setBlockTimeoutMs(long blockTimeoutMs) {
        this.blockTimeoutMs = blockTimeoutMs;
    }

    public long getRecoveryIntervalMs() {
        return recoveryIntervalMs;
    }

    public void setRecoveryIntervalMs(long recoveryIntervalMs) {
        this.recoveryIntervalMs = recoveryIntervalMs;
    }

    public long getMinIdleMs() {
        return minIdleMs;
    }

    public void setMinIdleMs(long minIdleMs) {
        this.minIdleMs = minIdleMs;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public boolean isParallelTestcases() {
        return parallelTestcases;
    }

    public void setParallelTestcases(boolean parallelTestcases) {
        this.parallelTestcases = parallelTestcases;
    }

    public int getMaxParallelTestcases() {
        return maxParallelTestcases;
    }

    public void setMaxParallelTestcases(int maxParallelTestcases) {
        this.maxParallelTestcases = maxParallelTestcases;
    }
}
