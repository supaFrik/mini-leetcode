package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.dto.ProctoringEventRequest;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProctoringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestParticipantRepository participantRepository;

    private User candidate;
    private User examiner;
    private String candidateToken;
    private Contest contest;
    private ContestParticipant participant;

    @BeforeEach
    void setUp() {
        examiner = userRepository.save(User.builder()
                .username("examiner_lead")
                .email("examiner@example.com")
                .passwordHash("hash")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        candidate = userRepository.save(User.builder()
                .username("candidate_alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        candidateToken = "Bearer " + jwtTokenProvider.generateAccessToken(candidate);

        contest = contestRepository.save(Contest.builder()
                .title("Midterm Algorithm Contest")
                .slug("midterm-algorithm-contest")
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .maxViolationsAllowed(3)
                .status(ContestStatus.RUNNING)
                .createdBy(examiner)
                .build());

        participant = participantRepository.save(ContestParticipant.builder()
                .contest(contest)
                .user(candidate)
                .status(ParticipantStatus.IN_PROGRESS)
                .violationCount(0)
                .build());
    }

    @Test
    @DisplayName("WINDOW_BLUR: Ghi nhận thời điểm rời màn hình thành công")
    void recordBlurEvent_Success() throws Exception {
        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_BLUR, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.violationCount").value(0))
                .andExpect(jsonPath("$.data.isDisqualified").value(false));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getLastAwayStartTime()).isNotNull();
        assertThat(updated.getViolationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("WINDOW_FOCUS: Quay lại trong thời gian ân hạn (<1.5s) không bị tính lỗi vi phạm")
    void recordFocusEvent_WithinGracePeriod_NoPenalty() throws Exception {
        // Giả lập rời màn hình cách đây 500ms
        participant.setLastAwayStartTime(LocalDateTime.now().minusNanos(500_000_000));
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_FOCUS, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.violationCount").value(0))
                .andExpect(jsonPath("$.data.isDisqualified").value(false))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("grace period")));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getLastAwayStartTime()).isNull();
        assertThat(updated.getViolationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("WINDOW_FOCUS: Rời màn hình quá 1.5s bị tăng bộ đếm vi phạm trên máy chủ")
    void recordFocusEvent_ExceedingGracePeriod_IncrementsViolation() throws Exception {
        // Giả lập rời màn hình cách đây 4 giây
        participant.setLastAwayStartTime(LocalDateTime.now().minusSeconds(4));
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_FOCUS, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.violationCount").value(1))
                .andExpect(jsonPath("$.data.isDisqualified").value(false));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getLastAwayStartTime()).isNull();
        assertThat(updated.getViolationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("WINDOW_FOCUS: Đạt ngưỡng vi phạm tối đa (3 lần) thì thí sinh bị truất quyền thi (DISQUALIFIED)")
    void recordFocusEvent_ExceedingMaxLimit_DisqualifiesUser() throws Exception {
        // Đã có sẵn 2 lần vi phạm
        participant.setViolationCount(2);
        participant.setLastAwayStartTime(LocalDateTime.now().minusSeconds(5));
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_FOCUS, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.violationCount").value(3))
                .andExpect(jsonPath("$.data.isDisqualified").value(true))
                .andExpect(jsonPath("$.data.status").value("DISQUALIFIED"));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ParticipantStatus.DISQUALIFIED);
        assertThat(updated.getViolationCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("CLIPBOARD_PASTE: Phát hiện copy/paste lập tức tăng số lần vi phạm không cần grace period")
    void recordClipboardPaste_IncrementsViolationCountImmediately() throws Exception {
        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.CLIPBOARD_PASTE, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.violationCount").value(1))
                .andExpect(jsonPath("$.data.isDisqualified").value(false))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("unauthorized clipboard paste detected")));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getViolationCount()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("CLIPBOARD_PASTE: Đạt ngưỡng vi phạm tối đa (3 lần) thì bị truất quyền thi ngay lập tức")
    void recordClipboardPaste_ReachingLimit_DisqualifiesUser() throws Exception {
        participant.setViolationCount(2);
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.CLIPBOARD_PASTE, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.violationCount").value(3))
                .andExpect(jsonPath("$.data.isDisqualified").value(true))
                .andExpect(jsonPath("$.data.status").value("DISQUALIFIED"))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("Disqualified: unauthorized clipboard paste detected")));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getViolationCount()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(ParticipantStatus.DISQUALIFIED);
    }

    @Test
    @DisplayName("Khách chưa đăng nhập không được gửi sự kiện proctoring (401 Unauthorized)")
    void unauthenticatedRequest_Returns401() throws Exception {
        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_BLUR, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WINDOW_FOCUS: Xử lý an toàn khi đồng hồ hệ thống bị trôi lùi (clock drift / backwards timestamp)")
    void recordFocusEvent_ClockDriftBackwards_DoesNotYieldNegativeDuration() throws Exception {
        // Giả lập lastAwayStartTime ở tương lai so với hiện tại do clock drift / NTP sync
        participant.setLastAwayStartTime(LocalDateTime.now().plusSeconds(5));
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_FOCUS, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.awayDurationMs").value(0))
                .andExpect(jsonPath("$.data.violationCount").value(0))
                .andExpect(jsonPath("$.data.isDisqualified").value(false));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getLastAwayStartTime()).isNull();
        assertThat(updated.getViolationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Validation: Lỗi 400 Bad Request và thông báo lỗi đúng khi eventType bị null")
    void recordEvent_NullEventType_Returns400WithCorrectValidationMessage() throws Exception {
        String invalidJson = "{\"eventType\": null, \"clientTimestamp\": 123456789}";

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.eventType").value("Event type is required (WINDOW_BLUR, WINDOW_FOCUS, or CLIPBOARD_PASTE)"));
    }

    @Test
    @DisplayName("WINDOW_FOCUS: Khi chưa có sự kiện BLUR trước đó thì bỏ qua và trả về awayDurationMs = 0")
    void recordFocusEvent_WithoutPriorBlur_IgnoredWithZeroDuration() throws Exception {
        participant.setLastAwayStartTime(null);
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_FOCUS, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.awayDurationMs").value(0))
                .andExpect(jsonPath("$.data.violationCount").value(0))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("no corresponding blur event active")));
    }

    @Test
    @DisplayName("Proctoring: Khi thí sinh đã bị DISQUALIFIED từ trước thì phản hồi đã bị truất quyền")
    void recordEvent_AlreadyDisqualified_ReturnsDisqualifiedMessage() throws Exception {
        participant.setStatus(ParticipantStatus.DISQUALIFIED);
        participant.setViolationCount(3);
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_BLUR, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISQUALIFIED"))
                .andExpect(jsonPath("$.data.isDisqualified").value(true))
                .andExpect(jsonPath("$.data.message").value("Participant has already been disqualified"));
    }

    @Test
    @DisplayName("WINDOW_BLUR: Gửi nhiều sự kiện BLUR liên tiếp không làm reset bộ đếm thời gian vắng mặt ban đầu")
    void recordBlurEvent_ConsecutiveBlurs_DoesNotResetInitialAwayTimer() throws Exception {
        // Giả lập thí sinh đã blur từ 3 giây trước
        LocalDateTime initialAwayTime = LocalDateTime.now().minusSeconds(3);
        participant.setLastAwayStartTime(initialAwayTime);
        participantRepository.save(participant);

        // Gửi tiếp sự kiện BLUR thứ 2 trong lúc vẫn đang away
        ProctoringEventRequest blurRequest = new ProctoringEventRequest(ProctoringEventType.WINDOW_BLUR, System.currentTimeMillis());
        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blurRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Kiểm tra timestamp ban đầu không bị ghi đè thành now
        ContestParticipant afterSecondBlur = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(afterSecondBlur.getLastAwayStartTime()).isEqualToIgnoringNanos(initialAwayTime);

        // Gửi sự kiện FOCUS: phải bị phạt vi phạm vì tổng thời gian vắng mặt >= 3s (> 1.5s grace period)
        ProctoringEventRequest focusRequest = new ProctoringEventRequest(ProctoringEventType.WINDOW_FOCUS, System.currentTimeMillis());
        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(focusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violationCount").value(1))
                .andExpect(jsonPath("$.data.isDisqualified").value(false));
    }

    @Test
    @DisplayName("Proctoring: Thí sinh đã nộp bài (SUBMITTED) không bị ghi nhận vi phạm hay truất quyền khi nhận sự kiện")
    void recordEvent_AlreadySubmitted_ReturnsSubmittedNotificationWithoutPenalty() throws Exception {
        participant.setStatus(ParticipantStatus.SUBMITTED);
        participant.setViolationCount(1);
        participantRepository.save(participant);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.CLIPBOARD_PASTE, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.isDisqualified").value(false))
                .andExpect(jsonPath("$.data.violationCount").value(1))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("finalized their contest submission")));

        ContestParticipant updated = participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ParticipantStatus.SUBMITTED);
        assertThat(updated.getViolationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Proctoring: Từ chối sự kiện (403 Forbidden) khi contest đã kết thúc (COMPLETED)")
    void recordEvent_ContestCompleted_Forbidden() throws Exception {
        contest.setStatus(ContestStatus.COMPLETED);
        contestRepository.save(contest);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.CLIPBOARD_PASTE, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Proctoring: Từ chối sự kiện (403 Forbidden) khi contest chưa bắt đầu (SCHEDULED)")
    void recordEvent_ContestScheduled_Forbidden() throws Exception {
        contest.setStatus(ContestStatus.SCHEDULED);
        contestRepository.save(contest);

        ProctoringEventRequest request = new ProctoringEventRequest(ProctoringEventType.WINDOW_BLUR, System.currentTimeMillis());

        mockMvc.perform(post("/api/v1/contests/" + contest.getId() + "/proctoring/events")
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}

