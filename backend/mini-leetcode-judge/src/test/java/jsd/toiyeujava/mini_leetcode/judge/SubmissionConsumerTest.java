package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubmissionConsumerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private JudgeService judgeService;

    private SubmissionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SubmissionConsumer(stringRedisTemplate, judgeService);
    }

    @Test
    @DisplayName("Calls judgeService.judge with the right message for valid JSON")
    void handle_ValidJson_CallsJudgeService() {
        consumer.handle("{\"submissionId\":1,\"problemId\":10,\"userId\":5,\"language\":\"PYTHON\"}");

        verify(judgeService).judge(new SubmissionTaskMessage(1L, 10L, 5L, SubmissionLanguage.PYTHON));
    }

    @Test
    @DisplayName("Drops malformed JSON without calling judgeService")
    void handle_MalformedJson_DropsWithoutJudging() {
        consumer.handle("not json");

        verifyNoInteractions(judgeService);
    }

    @Test
    @DisplayName("Drops a message missing submissionId without calling judgeService")
    void handle_MissingSubmissionId_DropsWithoutJudging() {
        consumer.handle("{\"problemId\":10,\"userId\":5,\"language\":\"PYTHON\"}");

        verifyNoInteractions(judgeService);
    }

    @Test
    @DisplayName("Drops a message with an unknown language without calling judgeService")
    void handle_UnknownLanguage_DropsWithoutJudging() {
        consumer.handle("{\"submissionId\":1,\"problemId\":10,\"userId\":5,\"language\":\"RUST\"}");

        verifyNoInteractions(judgeService);
    }
}