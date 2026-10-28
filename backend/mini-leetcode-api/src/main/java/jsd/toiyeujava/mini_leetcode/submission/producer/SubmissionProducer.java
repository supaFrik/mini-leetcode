package jsd.toiyeujava.mini_leetcode.submission.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.config.RedisConfig;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionProducer {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueueSubmission(SubmissionTaskMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForList().rightPush(RedisConfig.SUBMISSIONS_QUEUE, json);
            log.info("Pushed submission task to Redis queue {}: {}", RedisConfig.SUBMISSIONS_QUEUE, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize submission task: {}", message, e);
            throw new RuntimeException("Failed to enqueue submission", e);
        }
    }
}
