package jsd.toiyeujava.mini_leetcode.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.queue.SubmissionStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Worker lấy bài nộp từ Redis Stream bằng consumer group và chấm.
 *
 * <p>Khác với cơ chế Redis List cũ ({@code BLPOP} — xoá message ngay khi lấy),
 * ở đây message chỉ được gỡ khỏi pending list khi {@code XACK} sau khi chấm xong.
 * Nếu worker chết giữa chừng, {@link ZombieJobReclaimer} sẽ {@code XCLAIM} lại
 * message đó để không bài nộp nào bị mất vĩnh viễn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionConsumer implements SmartLifecycle {

    private static final long ERROR_BACKOFF_MS = 2_000;
    private static final long SHUTDOWN_WAIT_MS = 60_000;
    private static final int READ_BATCH_SIZE = 1;

    private final SubmissionStreamClient submissionStreamClient;
    private final JudgeService judgeService;
    private final JudgeProperties judgeProperties;
    private final ObjectMapper objectMapper;

    private volatile boolean running;
    private Thread worker;
    private Consumer consumer;

    @Override
    public void start() {
        // Chuyển dữ liệu tồn đọng từ Redis List cũ sang stream trước khi nhận việc mới.
        submissionStreamClient.drainLegacyQueue();
        submissionStreamClient.ensureConsumerGroup();

        this.consumer = Consumer.from(judgeProperties.getConsumerGroup(), resolveConsumerName());
        running = true;
        worker = new Thread(this::pollLoop, "submission-consumer");
        worker.start();
        log.info("Submission consumer {} started, listening on stream {} group {}",
                consumer.getName(), judgeProperties.getStreamKey(), judgeProperties.getConsumerGroup());
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            try {
                worker.join(SHUTDOWN_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Tên consumer. Mặc định sinh từ hostname + uuid ngắn để nhiều worker chạy
     * song song không trùng tên trong {@code XINFO CONSUMERS}.
     */
    private String resolveConsumerName() {
        String configured = judgeProperties.getConsumerName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return "worker-" + host + "-" + suffix;
    }

    private void pollLoop() {
        Duration blockTimeout = Duration.ofMillis(judgeProperties.getBlockTimeoutMs());
        while (running) {
            try {
                List<SubmissionStreamClient.StreamMessage> messages =
                        submissionStreamClient.readGroup(consumer, READ_BATCH_SIZE, blockTimeout);
                for (SubmissionStreamClient.StreamMessage message : messages) {
                    handle(message);
                }
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.error("Consumer error, retrying in {} ms", ERROR_BACKOFF_MS, e);
                sleepQuietly(ERROR_BACKOFF_MS);
            }
        }
    }

    /**
     * Xử lý một message đã đọc từ stream.
     *
     * <p>Message hỏng (JSON không parse được) bị ack + xoá ngay để không bị thử
     * lại vô hạn; message hợp lệ chỉ được ack sau khi đã chấm xong.
     */
    void handle(SubmissionStreamClient.StreamMessage message) {
        if (message == null || message.recordId() == null) {
            return;
        }
        String payload = message.payload();
        if (payload == null || payload.isBlank()) {
            log.error("Dropping submission task without payload: {}", message.recordId());
            ackAndDelete(message.recordId());
            return;
        }

        SubmissionTaskMessage task;
        try {
            task = objectMapper.readValue(payload, SubmissionTaskMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Dropping malformed submission task {}: {}", message.recordId(), payload, e);
            ackAndDelete(message.recordId());
            return;
        }
        if (task.submissionId() == null) {
            log.error("Dropping submission task without submissionId: {}", message.recordId());
            ackAndDelete(message.recordId());
            return;
        }

        log.info("Judging submission {}", task.submissionId());
        try {
            judgeService.judge(task);
        } catch (RuntimeException e) {
            // Không ack: message phải ở lại pending để ZombieJobReclaimer thu hồi
            // và thử lại. JudgeServiceImpl tự ghi verdict FAILED khi lỗi xảy ra
            // trong lúc chấm, nên chỉ lỗi hệ thống mới đến được đây.
            log.error("Judging submission {} failed, leaving message pending for recovery: {}",
                    task.submissionId(), e.getMessage(), e);
            return;
        }
        // Chỉ ack sau khi đã có verdict cuối cùng.
        ackAndDelete(message.recordId());
    }

    /** Ack + xoá message khỏi stream (xoá để stream không phình vô hạn). */
    private void ackAndDelete(RecordId recordId) {
        try {
            submissionStreamClient.acknowledge(recordId);
            submissionStreamClient.delete(recordId);
        } catch (Exception e) {
            log.warn("Failed to ack/delete stream record {}: {}", recordId, e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
