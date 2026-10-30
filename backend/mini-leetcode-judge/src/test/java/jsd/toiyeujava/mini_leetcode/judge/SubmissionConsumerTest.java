package jsd.toiyeujava.mini_leetcode.judge;

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
import org.springframework.data.redis.connection.stream.RecordId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubmissionConsumerTest {

    @Mock
    private SubmissionStreamClient submissionStreamClient;
    @Mock
    private JudgeService judgeService;

    private SubmissionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SubmissionConsumer(submissionStreamClient, judgeService,
                new JudgeProperties(), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private static SubmissionStreamClient.StreamMessage message(String payload) {
        return new SubmissionStreamClient.StreamMessage(RecordId.of("1-0"), payload);
    }

    @Test
    @DisplayName("Calls judgeService.judge with the right message for valid JSON")
    void handle_ValidJson_CallsJudgeService() {
        consumer.handle(message("{\"submissionId\":1,\"problemId\":10,\"userId\":5,\"language\":\"PYTHON\"}"));

        verify(judgeService).judge(new SubmissionTaskMessage(1L, 10L, 5L, SubmissionLanguage.PYTHON));
    }

    @Test
    @DisplayName("Acknowledges the stream record after judging")
    void handle_ValidJson_AcknowledgesRecord() {
        RecordId id = RecordId.of("5-3");

        consumer.handle(new SubmissionStreamClient.StreamMessage(id,
                "{\"submissionId\":1,\"problemId\":10,\"userId\":5,\"language\":\"PYTHON\"}"));

        verify(submissionStreamClient).acknowledge(id);
        verify(submissionStreamClient).delete(id);
    }

    @Test
    @DisplayName("Drops malformed JSON without calling judgeService and acks the record")
    void handle_MalformedJson_DropsWithoutJudging() {
        RecordId id = RecordId.of("2-0");

        consumer.handle(new SubmissionStreamClient.StreamMessage(id, "not json"));

        verifyNoInteractions(judgeService);
        verify(submissionStreamClient).acknowledge(id);
        verify(submissionStreamClient).delete(id);
    }

    @Test
    @DisplayName("Drops a message missing submissionId without calling judgeService")
    void handle_MissingSubmissionId_DropsWithoutJudging() {
        RecordId id = RecordId.of("3-0");

        consumer.handle(new SubmissionStreamClient.StreamMessage(id,
                "{\"problemId\":10,\"userId\":5,\"language\":\"PYTHON\"}"));

        verifyNoInteractions(judgeService);
        verify(submissionStreamClient).acknowledge(id);
    }

    @Test
    @DisplayName("Drops a message with an unknown language without calling judgeService")
    void handle_UnknownLanguage_DropsWithoutJudging() {
        RecordId id = RecordId.of("4-0");

        consumer.handle(new SubmissionStreamClient.StreamMessage(id,
                "{\"submissionId\":1,\"problemId\":10,\"userId\":5,\"language\":\"RUST\"}"));

        verifyNoInteractions(judgeService);
        verify(submissionStreamClient).acknowledge(id);
    }

    @Test
    @DisplayName("Drops a message without payload without calling judgeService")
    void handle_NoPayload_DropsWithoutJudging() {
        RecordId id = RecordId.of("6-0");

        consumer.handle(new SubmissionStreamClient.StreamMessage(id, null));

        verifyNoInteractions(judgeService);
        verify(submissionStreamClient).acknowledge(id);
        verify(submissionStreamClient).delete(id);
    }

    @Test
    @DisplayName("Leaves the message pending when judging throws so it can be reclaimed")
    void handle_JudgeThrows_LeavesMessagePending() {
        RecordId id = RecordId.of("7-0");
        org.mockito.Mockito.doThrow(new RuntimeException("docker unavailable"))
                .when(judgeService).judge(any(SubmissionTaskMessage.class));

        consumer.handle(new SubmissionStreamClient.StreamMessage(id,
                "{\"submissionId\":1,\"problemId\":10,\"userId\":5,\"language\":\"PYTHON\"}"));

        // Không ack => message vẫn pending, ZombieJobReclaimer sẽ XCLAIM lại.
        verify(submissionStreamClient, never()).acknowledge(any());
        verify(submissionStreamClient, never()).delete(any());
    }

    @Test
    @DisplayName("Ignores a null message without touching Redis")
    void handle_NullMessage_DoesNothing() {
        consumer.handle(null);

        verify(submissionStreamClient, never()).acknowledge(any());
    }
}
