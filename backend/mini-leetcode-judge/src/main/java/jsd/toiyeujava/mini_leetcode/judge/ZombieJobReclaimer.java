package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.queue.SubmissionStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thu hồi các bài nộp "zombie" — những message đã được giao cho worker nhưng
 * worker chết trước khi {@code XACK}.
 *
 * <p>Cơ chế {@code XPENDING} + {@code XCLAIM}: xem các message đang pending,
 * lọc ra message idle quá {@code app.judge.min-idle-ms}, rồi {@code XCLAIM} về
 * consumer hiện tại để chấm lại. Nhờ vậy bài nộp không bao giờ bị mất vĩnh viễn
 * khi judge worker gặp sự cố.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZombieJobReclaimer {

    private final SubmissionStreamClient submissionStreamClient;
    private final JudgeService judgeService;
    private final JudgeProperties judgeProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Quét định kỳ các message pending quá hạn và chấm lại.
     *
     * <p>Chạy trong thread của scheduler; mỗi lần quét đều bọc try/catch để
     * một lỗi Redis cục bể không làm chết scheduler.
     */
    @Scheduled(fixedDelayString = "${app.judge.recovery-interval-ms:30000}")
    public void reclaimZombieJobs() {
        try {
            reclaim();
        } catch (Exception e) {
            log.error("Zombie job reclamation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Thực hiện một vòng thu hồi.
     *
     * @return số bài nộp đã được thu hồi lại
     */
    public int reclaim() {
        String group = judgeProperties.getConsumerGroup();
        List<PendingMessage> pending = submissionStreamClient.pending(judgeProperties.getRecoveryBatchSize());
        if (pending.isEmpty()) {
            return 0;
        }

        Duration minIdle = Duration.ofMillis(judgeProperties.getMinIdleMs());
        List<RecordId> staleIds = new ArrayList<>();
        for (PendingMessage message : pending) {
            Duration idle = message.getElapsedTimeSinceLastDelivery();
            if (idle != null && idle.compareTo(minIdle) >= 0) {
                staleIds.add(message.getId());
            }
        }
        if (staleIds.isEmpty()) {
            return 0;
        }

        String consumerName = resolveConsumerName(group);
        Consumer consumer = Consumer.from(group, consumerName);
        List<SubmissionStreamClient.StreamMessage> claimed =
                submissionStreamClient.claim(consumer, minIdle, staleIds);

        int reclaimed = 0;
        for (SubmissionStreamClient.StreamMessage message : claimed) {
            SubmissionTaskMessage task = parse(message);
            if (task == null) {
                // Message hỏng không thể chấm lại -> ack + xoá để không bị loop.
                ackAndDelete(message.recordId());
                continue;
            }
            log.warn("Reclaiming zombie submission {} (idle longer than {} ms)",
                    task.submissionId(), judgeProperties.getMinIdleMs());
            try {
                judgeService.judge(task);
            } catch (RuntimeException e) {
                // Không ack: để message vẫn pending và được thử lại ở vòng sau.
                log.error("Failed to re-judge zombie submission {}: {}", task.submissionId(), e.getMessage(), e);
                continue;
            }
            ackAndDelete(message.recordId());
            reclaimed++;
        }
        if (reclaimed > 0) {
            log.info("Reclaimed {} zombie submission(s) in group {}", reclaimed, group);
        }
        return reclaimed;
    }

    private SubmissionTaskMessage parse(SubmissionStreamClient.StreamMessage message) {
        if (message.payload() == null || message.payload().isBlank()) {
            return null;
        }
        try {
            SubmissionTaskMessage task = objectMapper.readValue(message.payload(), SubmissionTaskMessage.class);
            return task.submissionId() == null ? null : task;
        } catch (Exception e) {
            log.error("Dropping malformed zombie task {}: {}", message.recordId(), e.getMessage());
            return null;
        }
    }

    private void ackAndDelete(RecordId recordId) {
        try {
            submissionStreamClient.acknowledge(recordId);
            submissionStreamClient.delete(recordId);
        } catch (Exception e) {
            log.warn("Failed to ack/delete reclaimed record {}: {}", recordId, e.getMessage());
        }
    }

    /**
     * Tên consumer dùng cho {@code XCLAIM}. Phải khớp với tên consumer mà
     * {@link SubmissionConsumer} đang dùng để message được claim về đúng worker.
     */
    private String resolveConsumerName(String group) {
        String configured = judgeProperties.getConsumerName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        // Không có tên cố định -> dùng tên riêng cho reclaimer. XCLAIM vẫn hoạt
        // động vì consumer group chấp nhận consumer name bất kỳ.
        return "reclaimer-" + group;
    }
}
