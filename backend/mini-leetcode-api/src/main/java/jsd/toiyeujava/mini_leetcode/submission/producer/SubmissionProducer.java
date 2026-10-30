package jsd.toiyeujava.mini_leetcode.submission.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.SubmissionStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionProducer {

    private final SubmissionStreamClient submissionStreamClient;
    private final ObjectMapper objectMapper;

    /**
     * Đẩy bài nộp vào hàng đợi để judge worker xử lý.
     *
     * <p>Message được giữ trong Redis Stream cho tới khi worker ack, nên nếu worker
     * chết giữa chừng thì bài nộp vẫn được thu hồi lại bằng {@code XCLAIM}
     * thay vì bị mất vĩnh viễn như cơ chế Redis List cũ.
     *
     * @return id của message trong stream
     * @throws com.fasterxml.jackson.core.JsonProcessingException nếu không serialize được
     * @throws RuntimeException nếu ghi vào Redis thất bại (giữ nguyên exception gốc
     *                      để controller báo lý do chính xác)
     */
    public String enqueueSubmission(SubmissionTaskMessage message) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize submission task: {}", message, e);
            throw new IllegalStateException("Failed to enqueue submission", e);
        }
        String recordId = submissionStreamClient.addPayload(json);
        log.info("Pushed submission task to Redis stream with id {}: {}", recordId, message);
        return recordId;
    }
}
