package jsd.toiyeujava.mini_leetcode.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.queue.JudgeProperties;
import jsd.toiyeujava.mini_leetcode.submission.queue.SubmissionStreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.RecordId;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZombieJobReclaimerTest {

    @Mock
    private SubmissionStreamClient submissionStreamClient;
    @Mock
    private JudgeService judgeService;

    private ZombieJobReclaimer reclaimer;
    private JudgeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JudgeProperties();
        properties.setMinIdleMs(1000);
        properties.setRecoveryBatchSize(50);
        reclaimer = new ZombieJobReclaimer(submissionStreamClient, judgeService, properties, new ObjectMapper());
    }

    private static PendingMessage pending(RecordId id, Duration idle) {
        return new PendingMessage(id, null, idle, 1);
    }

    @Test
    @DisplayName("Does nothing when there are no pending messages")
    void reclaim_NoPendingMessages_DoesNothing() {
        when(submissionStreamClient.pending(50)).thenReturn(List.of());

        assertThat(reclaimer.reclaim()).isZero();

        verifyNoInteractions(judgeService);
        verify(submissionStreamClient, never()).claim(any(), any(), anyList());
    }

    @Test
    @DisplayName("Does not claim messages that are not idle long enough")
    void reclaim_MessageNotIdleYet_DoesNotClaim() {
        RecordId id = RecordId.of("1-0");
        when(submissionStreamClient.pending(50)).thenReturn(List.of(pending(id, Duration.ofMillis(100))));

        assertThat(reclaimer.reclaim()).isZero();

        verify(submissionStreamClient, never()).claim(any(), any(), anyList());
        verifyNoInteractions(judgeService);
    }

    @Test
    @DisplayName("Claims and re-judges a message that has been idle beyond the threshold")
    void reclaim_IdleMessage_ClaimsAndRejudges() {
        RecordId id = RecordId.of("2-0");
        when(submissionStreamClient.pending(50)).thenReturn(List.of(pending(id, Duration.ofSeconds(30))));
        when(submissionStreamClient.claim(any(), any(Duration.class), anyList()))
                .thenReturn(List.of(new SubmissionStreamClient.StreamMessage(id,
                        "{\"submissionId\":501,\"problemId\":10,\"userId\":7,\"language\":\"PYTHON\"}")));

        int reclaimed = reclaimer.reclaim();

        assertThat(reclaimed).isEqualTo(1);
        verify(judgeService).judge(new SubmissionTaskMessage(501L, 10L, 7L,
                jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage.PYTHON));
        verify(submissionStreamClient).acknowledge(id);
        verify(submissionStreamClient).delete(id);
    }

    @Test
    @DisplayName("Leaves the message pending when re-judging throws so it can be retried")
    void reclaim_JudgeThrows_LeavesMessagePending() {
        RecordId id = RecordId.of("3-0");
        when(submissionStreamClient.pending(50)).thenReturn(List.of(pending(id, Duration.ofSeconds(30))));
        when(submissionStreamClient.claim(any(), any(Duration.class), anyList()))
                .thenReturn(List.of(new SubmissionStreamClient.StreamMessage(id,
                        "{\"submissionId\":501,\"problemId\":10,\"userId\":7,\"language\":\"PYTHON\"}")));
        org.mockito.Mockito.doThrow(new RuntimeException("docker down"))
                .when(judgeService).judge(any(SubmissionTaskMessage.class));

        assertThat(reclaimer.reclaim()).isZero();

        verify(submissionStreamClient, never()).acknowledge(any());
        verify(submissionStreamClient, never()).delete(any());
    }

    @Test
    @DisplayName("Drops a malformed claimed message instead of retrying it forever")
    void reclaim_MalformedMessage_DropsIt() {
        RecordId id = RecordId.of("4-0");
        when(submissionStreamClient.pending(50)).thenReturn(List.of(pending(id, Duration.ofSeconds(30))));
        when(submissionStreamClient.claim(any(), any(Duration.class), anyList()))
                .thenReturn(List.of(new SubmissionStreamClient.StreamMessage(id, "not-json")));

        assertThat(reclaimer.reclaim()).isZero();

        verifyNoInteractions(judgeService);
        verify(submissionStreamClient).acknowledge(id);
        verify(submissionStreamClient).delete(id);
    }
}
