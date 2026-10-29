package jsd.toiyeujava.mini_leetcode.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.config.RedisConfig;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionConsumer implements SmartLifecycle {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final long ERROR_BACKOFF_MS = 2_000;
    private static final long SHUTDOWN_WAIT_MS = 60_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final JudgeService judgeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean running;
    private Thread worker;

    @Override
    public void start() {
        running = true;
        worker = new Thread(this::pollLoop, "submission-consumer");
        worker.start();
        log.info("Submission consumer started, listening on {}", RedisConfig.SUBMISSIONS_QUEUE);
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

    private void pollLoop() {
        while (running) {
            try {
                String json = stringRedisTemplate.opsForList()
                        .leftPop(RedisConfig.SUBMISSIONS_QUEUE, POLL_TIMEOUT);
                if (json != null) {
                    handle(json);
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

    void handle(String json) {
        SubmissionTaskMessage message;
        try {
            message = objectMapper.readValue(json, SubmissionTaskMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Dropping malformed submission task: {}", json, e);
            return;
        }
        if (message.submissionId() == null) {
            log.error("Dropping submission task without submissionId: {}", json);
            return;
        }
        log.info("Judging submission {}", message.submissionId());
        judgeService.judge(message);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}