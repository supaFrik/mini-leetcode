package jsd.toiyeujava.mini_leetcode.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleTranscriptEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưu transcript console trong Redis với TTL.
 *
 * <p>Key: {@code console:transcript:{userId}:{problemId}}. Giá trị là list các bản
 * ghi JSON. TTL mặc định 1 giờ ⇒ dữ liệu tự biến mất, không tích tụ vô hạn.
 *
 * <p>Dùng {@link StringRedisTemplate} (serialize thẳng chuỗi UTF-8) thay vì
 * {@code RedisTemplate<String, Object>} để tránh serializer ghi kèm type-info
 * {@code @class} làm payload khó đọc lại.
 */
@Slf4j
@Component
public class ConsoleTranscriptStore {

    public static final String KEY_PREFIX = "console:transcript:";

    /** Số bản ghi tối đa giữ lại cho mỗi phiên. */
    public static final int MAX_ENTRIES = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ConsoleTranscriptStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(1);
    }

    private String key(Long userId, Long problemId) {
        return KEY_PREFIX + userId + ":" + problemId;
    }

    /** Đọc transcript (cũ nhất trước). */
    public List<ConsoleTranscriptEntry> read(Long userId, Long problemId) {
        try {
            List<String> raw = redisTemplate.opsForList().range(key(userId, problemId), 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<ConsoleTranscriptEntry> entries = new ArrayList<>(raw.size());
            for (String item : raw) {
                ConsoleTranscriptEntry entry = deserialize(item);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (Exception e) {
            log.warn("Failed to read console transcript for user {} problem {}: {}",
                    userId, problemId, e.getMessage());
            return List.of();
        }
    }

    /** Ghi thêm một bản ghi vào transcript. */
    public void append(Long userId, Long problemId, ConsoleTranscriptEntry entry) {
        try {
            String key = key(userId, problemId);
            String json = serialize(entry);
            if (json == null) {
                return;
            }
            redisTemplate.opsForList().rightPush(key, json);
            // Giới hạn số bản ghi để transcript không phình vô hạn.
            redisTemplate.opsForList().trim(key, 0, MAX_ENTRIES - 1);
            redisTemplate.expire(key, ttl);
        } catch (Exception e) {
            log.warn("Failed to append console transcript for user {} problem {}: {}",
                    userId, problemId, e.getMessage());
        }
    }

    /** Xóa transcript của phiên. */
    public void clear(Long userId, Long problemId) {
        try {
            redisTemplate.delete(key(userId, problemId));
        } catch (Exception e) {
            log.warn("Failed to clear console transcript for user {} problem {}: {}",
                    userId, problemId, e.getMessage());
        }
    }

    /** Số bản ghi hiện có trong transcript. */
    public int count(Long userId, Long problemId) {
        try {
            Long size = redisTemplate.opsForList().size(key(userId, problemId));
            return size == null ? 0 : size.intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String serialize(ConsoleTranscriptEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (Exception e) {
            log.warn("Failed to serialize console transcript entry: {}", e.getMessage());
            return null;
        }
    }

    private ConsoleTranscriptEntry deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ConsoleTranscriptEntry.class);
        } catch (Exception e) {
            log.debug("Skipped unreadable console transcript entry");
            return null;
        }
    }

    /** Tạo bản ghi transcript với thời điểm hiện tại. */
    public static ConsoleTranscriptEntry entryOf(String input,
                                                 String stdout,
                                                 String stderr,
                                                 Integer exitCode,
                                                 Integer runtimeMs,
                                                 Integer peakMemoryKb,
                                                 String compileErrors) {
        return new ConsoleTranscriptEntry(input, stdout, stderr, exitCode, runtimeMs, peakMemoryKb,
                compileErrors, Instant.now());
    }
}
