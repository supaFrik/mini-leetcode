package jsd.toiyeujava.mini_leetcode.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.queue.SubmissionStreamClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tích hợp với Redis thật: vòng tròn producer → stream → consumer,
 * và kịch bản "worker chết giữa lúc chấm" được {@code XCLAIM} thu hồi.
 *
 * <p>Bỏ qua (assumption) nếu không kết nối được Redis.
 */
class SubmissionStreamRedisIntegrationTest {

    private static final String STREAM_KEY = "test:stream:submissions";
    private static final String LEGACY_KEY = "test:queue.submissions";
    private static final String REDIS_PASSWORD = System.getenv().getOrDefault("REDIS_PASSWORD", "redis_password");

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private SubmissionStreamClient client;
    private JudgeProperties properties;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(isRedisAvailable(), "Redis is not available on localhost:6379");

        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.setPassword(REDIS_PASSWORD);
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();

        properties = new JudgeProperties();
        properties.setStreamKey(STREAM_KEY);
        properties.setLegacyQueueKey(LEGACY_KEY);
        properties.setConsumerGroup("test-judge-group");
        properties.setMinIdleMs(1);
        client = new SubmissionStreamClient(template, properties);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            try {
                template.delete(List.of(STREAM_KEY, LEGACY_KEY));
            } catch (Exception ignored) {
                // dọn dẹp là best-effort
            }
            connectionFactory.destroy();
        }
    }

    private boolean isRedisAvailable() {
        try {
            LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
            factory.setPassword(REDIS_PASSWORD);
            factory.afterPropertiesSet();
            factory.getConnection().ping();
            factory.destroy();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Producer -> stream -> consumer round trip preserves the payload byte-exactly")
    void roundTrip_ProducerToConsumer_PreservesPayload() throws Exception {
        client.ensureConsumerGroup();
        SubmissionTaskMessage task =
                new SubmissionTaskMessage(501L, 10L, 7L, SubmissionLanguage.PYTHON);
        String json = new ObjectMapper().writeValueAsString(task);

        client.addPayload(json);

        var consumer = org.springframework.data.redis.connection.stream.Consumer.from("test-judge-group", "worker-a");
        List<SubmissionStreamClient.StreamMessage> messages =
                client.readGroup(consumer, 1, Duration.ofSeconds(2));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).payload()).isEqualTo(json);

        SubmissionTaskMessage parsed =
                new ObjectMapper().readValue(messages.get(0).payload(), SubmissionTaskMessage.class);
        assertThat(parsed).isEqualTo(task);
    }

    @Test
    @DisplayName("Acknowledging removes the message from the pending list")
    void acknowledge_ClearsPendingEntry() {
        client.ensureConsumerGroup();
        client.addPayload("{\"submissionId\":1}");

        var consumer = org.springframework.data.redis.connection.stream.Consumer.from("test-judge-group", "worker-a");
        List<SubmissionStreamClient.StreamMessage> messages =
                client.readGroup(consumer, 1, Duration.ofSeconds(2));
        assertThat(messages).hasSize(1);

        client.acknowledge(messages.get(0).recordId());

        assertThat(client.pending(10)).isEmpty();
    }

    @Test
    @DisplayName("A message left pending by a dead worker is reclaimed via XCLAIM")
    void reclaim_AbandonedMessage_IsClaimedByAnotherWorker() {
        client.ensureConsumerGroup();
        client.addPayload("{\"submissionId\":1,\"problemId\":10,\"userId\":7,\"language\":\"PYTHON\"}");

        // Worker A nhận message rồi "chết" (không ack).
        var workerA = org.springframework.data.redis.connection.stream.Consumer.from("test-judge-group", "worker-dead");
        List<SubmissionStreamClient.StreamMessage> first =
                client.readGroup(workerA, 1, Duration.ofSeconds(2));
        assertThat(first).hasSize(1);

        // Message vẫn nằm trong pending list.
        List<org.springframework.data.redis.connection.stream.PendingMessage> pending = client.pending(10);
        assertThat(pending).hasSize(1);
        var id = pending.get(0).getId();

        // Worker B thu hồi sau khi idle quá minIdle.
        var workerB = org.springframework.data.redis.connection.stream.Consumer.from("test-judge-group", "worker-alive");
        List<SubmissionStreamClient.StreamMessage> claimed =
                client.claim(workerB, Duration.ofMillis(1), List.of(id));

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).recordId()).isEqualTo(id);
        assertThat(claimed.get(0).payload()).contains("\"submissionId\":1");
    }

    @Test
    @DisplayName("Drains residue from the legacy Redis List into the stream")
    void drainLegacyQueue_MovesItemsIntoTheStream() {
        client.ensureConsumerGroup();
        template.opsForList().rightPush(LEGACY_KEY, "{\"submissionId\":1}");
        template.opsForList().rightPush(LEGACY_KEY, "{\"submissionId\":2}");

        int moved = client.drainLegacyQueue();

        assertThat(moved).isEqualTo(2);
        assertThat(template.opsForList().size(LEGACY_KEY)).isZero();

        var consumer = org.springframework.data.redis.connection.stream.Consumer.from("test-judge-group", "worker-a");
        List<SubmissionStreamClient.StreamMessage> messages =
                client.readGroup(consumer, 10, Duration.ofSeconds(2));
        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("Creating the consumer group twice is a no-op instead of failing")
    void ensureConsumerGroup_AlreadyExists_DoesNotThrow() {
        client.ensureConsumerGroup();
        // Lần thứ hai phải bị bỏ qua: BUSYGROUP được Lettuce bọc trong
        // RedisSystemException nên phải duyệt cả chuỗi cause.
        client.ensureConsumerGroup();

        assertThat(template.opsForStream().size(STREAM_KEY)).isZero();
    }
}
