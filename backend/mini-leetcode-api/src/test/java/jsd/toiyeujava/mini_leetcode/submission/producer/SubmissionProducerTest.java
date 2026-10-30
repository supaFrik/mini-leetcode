package jsd.toiyeujava.mini_leetcode.submission.producer;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.queue.SubmissionStreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionProducerTest {

    @Mock
    private SubmissionStreamClient submissionStreamClient;

    private SubmissionProducer producer;

    @BeforeEach
    void setUp() {
        producer = new SubmissionProducer(submissionStreamClient, new ObjectMapper());
    }

    @Test
    @DisplayName("Serializes the task as JSON and writes it to the stream")
    void enqueueSubmission_WritesJsonPayloadToStream() {
        when(submissionStreamClient.addPayload(anyString())).thenReturn("1700000000000-0");
        SubmissionTaskMessage task = new SubmissionTaskMessage(501L, 10L, 7L, SubmissionLanguage.PYTHON);

        String recordId = producer.enqueueSubmission(task);

        assertThat(recordId).isEqualTo("1700000000000-0");
        org.mockito.ArgumentCaptor<String> captor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(submissionStreamClient).addPayload(captor.capture());

        String payload = captor.getValue();
        assertThat(payload).contains("\"submissionId\":501");
        assertThat(payload).contains("\"problemId\":10");
        assertThat(payload).contains("\"userId\":7");
        assertThat(payload).contains("\"language\":\"PYTHON\"");
    }

    @Test
    @DisplayName("Propagates the Redis failure so the controller can report the real reason")
    void enqueueSubmission_StreamWriteFails_PropagatesRedisException() {
        when(submissionStreamClient.addPayload(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis connection timed out"));

        SubmissionTaskMessage task = new SubmissionTaskMessage(501L, 10L, 7L, SubmissionLanguage.PYTHON);

        // Exception gốc phải được giữ nguyên để controller ghi đúng lý do vào DB.
        assertThatThrownBy(() -> producer.enqueueSubmission(task))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("Redis connection timed out");
    }
}
