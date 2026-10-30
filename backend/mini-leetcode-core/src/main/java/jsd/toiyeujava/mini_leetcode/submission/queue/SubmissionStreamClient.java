package jsd.toiyeujava.mini_leetcode.submission.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lớp bọc mỏng quanh các lệnh Redis Streams (XADD / XREADGROUP / XACK / XDEL /
 * XPENDING / XCLAIM) dùng chung cho producer và consumer.
 *
 * <p>Dùng {@link StringRedisTemplate} nên payload được ghi dưới dạng chuỗi
 * UTF-8 nguyên vẹn — consumer giải mã lại bằng {@code ObjectMapper} nên không
 * có sai lệch bytes nào.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionStreamClient {

    /** Tên field chứa payload JSON trong mỗi entry của stream. */
    public static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate stringRedisTemplate;
    private final JudgeProperties judgeProperties;

    private StreamOperations<String, String, String> streams() {
        return stringRedisTemplate.opsForStream();
    }

    /**
     * Ghi một payload JSON vào stream.
     *
     * @return id của record vừa tạo
     */
    public String addPayload(String json) {
        String streamKey = judgeProperties.getStreamKey();
        MapRecord<String, String, String> record = MapRecord.create(streamKey, Map.of(PAYLOAD_FIELD, json));
        RecordId id = streams().add(record);
        log.info("Added submission task to Redis stream {} with id {}", streamKey, id);
        return id.toString();
    }

    /**
     * Đảm bảo consumer group tồn tại. Tạo với {@code MKSTREAM} nên không lỗi
     * khi stream chưa có entry nào; nếu group đã tồn tại thì bỏ qua lỗi
     * {@code BUSYGROUP}.
     */
    public void ensureConsumerGroup() {
        String streamKey = judgeProperties.getStreamKey();
        String group = judgeProperties.getConsumerGroup();
        try {
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                connection.streamCommands().xGroupCreate(
                        streamKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        group,
                        ReadOffset.from("0-0"),
                        true);
                return null;
            });
            log.info("Created consumer group {} on stream {}", group, streamKey);
        } catch (RuntimeException e) {
            if (isBusyGroup(e)) {
                log.debug("Consumer group {} already exists on stream {}", group, streamKey);
                return;
            }
            throw e;
        }
    }

    /**
     * Kiểm tra lỗi {@code BUSYGROUP}.
     *
     * <p>Lettuce bọc lỗi Redis thành {@code RedisBusyException}, sau đó Spring Data
     * bọc tiếp thành {@code RedisSystemException}, nên phải duyệt cả chuỗi
     * {@code cause} thay vì chỉ xem message của exception ngoài cùng.
     */
    private static boolean isBusyGroup(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Đọc tối đa {@code batchSize} message mới từ consumer group, chặn tối đa
     * {@code blockTimeoutMs} nếu chưa có message nào.
     *
     * @return danh sách message (rỗng nếu hết thời gian chờ)
     */
    public List<StreamMessage> readGroup(Consumer consumer, int batchSize, Duration blockTimeout) {
        StreamReadOptions options = StreamReadOptions.empty()
                .count(batchSize)
                .block(blockTimeout);
        StreamOffset<String> offset = StreamOffset.create(
                judgeProperties.getStreamKey(), ReadOffset.lastConsumed());
        List<MapRecord<String, String, String>> records =
                streams().read(consumer, options, offset);
        return toMessages(records);
    }

    /** Xác nhận (ack) một message đã xử lý xong. */
    public void acknowledge(RecordId id) {
        streams().acknowledge(judgeProperties.getStreamKey(), judgeProperties.getConsumerGroup(), id);
    }

    /** Xoá message khỏi stream sau khi đã ack, để stream không phình vô hạn. */
    public void delete(RecordId id) {
        streams().delete(judgeProperties.getStreamKey(), id);
    }

    /**
     * Lấy danh sách message đang "pending" (đã giao cho consumer nhưng chưa ack).
     *
     * @param count số lượng tối đa lấy ra
     */
    public List<PendingMessage> pending(int count) {
        PendingMessages messages = streams().pending(
                judgeProperties.getStreamKey(),
                judgeProperties.getConsumerGroup(),
                Range.unbounded(),
                count);
        if (messages == null) {
            return List.of();
        }
        List<PendingMessage> result = new ArrayList<>(messages.size());
        messages.forEach(result::add);
        return result;
    }

    /**
     * Thu hồi các message pending đã "idle" quá {@code minIdle} về consumer này.
     * Đây là cơ chế {@code XCLAIM} để bài nộp không bị mất khi worker chết.
     */
    public List<StreamMessage> claim(Consumer consumer, Duration minIdle, List<RecordId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<MapRecord<String, String, String>> records = streams().claim(
                judgeProperties.getStreamKey(),
                judgeProperties.getConsumerGroup(),
                consumer.getName(),
                minIdle,
                ids.toArray(new RecordId[0]));
        return toMessages(records);
    }

    /**
     * Chuyển dữ liệu tồn đọng từ Redis List cũ ({@code RPUSH}/{@code BLPOP})
     * sang stream, để việc deploy không làm mất bài nộp đang chờ.
     *
     * @return số message đã chuyển
     */
    public int drainLegacyQueue() {
        String legacyKey = judgeProperties.getLegacyQueueKey();
        int moved = 0;
        try {
            while (true) {
                String payload = stringRedisTemplate.opsForList().leftPop(legacyKey);
                if (payload == null) {
                    break;
                }
                try {
                    addPayload(payload);
                    moved++;
                } catch (RuntimeException e) {
                    // Trả lại message vào list để không mất dữ liệu nếu ghi stream thất bại.
                    log.error("Failed to migrate legacy queue item to stream, pushing back: {}", e.getMessage());
                    stringRedisTemplate.opsForList().rightPush(legacyKey, payload);
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not drain legacy queue {}: {}", legacyKey, e.getMessage());
        }
        if (moved > 0) {
            log.info("Migrated {} pending submissions from legacy list {} to stream {}",
                    moved, legacyKey, judgeProperties.getStreamKey());
        }
        return moved;
    }

    private List<StreamMessage> toMessages(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<StreamMessage> messages = new ArrayList<>(records.size());
        for (MapRecord<String, String, String> record : records) {
            String payload = record.getValue() == null ? null : record.getValue().get(PAYLOAD_FIELD);
            messages.add(new StreamMessage(record.getId(), payload));
        }
        return messages;
    }

    /**
     * Một message trong stream: id để ack/delete và payload JSON thô.
     *
     * @param recordId id của entry trong stream
     * @param payload  nội dung JSON, {@code null} nếu record thiếu field {@code payload}
     */
    public record StreamMessage(RecordId recordId, String payload) {
    }
}
