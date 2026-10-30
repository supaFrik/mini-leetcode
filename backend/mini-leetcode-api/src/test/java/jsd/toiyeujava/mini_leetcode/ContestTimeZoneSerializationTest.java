package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.dto.ContestResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.ScoringPolicy;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy cho lỗi múi giờ khiến đồng hồ đếm ngược của phòng thi về 0 ngay lập tức.
 *
 * <p>Server lưu mọi mốc thời gian theo UTC. Nếu API trả về {@code LocalDateTime}
 * không kèm múi giờ (ví dụ {@code "2026-10-29T20:43:40"}), trình duyệt sẽ hiểu là
 * giờ địa phương của máy thí sinh. Với UTC+7, mốc kết thúc 20:43 UTC bị hiểu thành
 * 20:43 giờ local → đã quá hạn 7 tiếng → bài thi bị khoá ngay khi vừa vào phòng.
 *
 * <p>API phải xuất mốc thời gian kèm hậu tố {@code "Z"} để mọi client diễn giải
 * thống nhất và tuyệt đối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContestTimeZoneSerializationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User candidate;
    private String candidateToken;

    @BeforeEach
    void setUp() {
        candidate = userRepository.save(User.builder()
                .username("tz_candidate")
                .email("tz_candidate@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        candidateToken = "Bearer " + jwtTokenProvider.generateAccessToken(candidate);
    }

    @Test
    @DisplayName("LocalDateTime phải serialize kèm hậu tố 'Z' để client hiểu là UTC")
    void localDateTime_IsSerializedWithUtcSuffix() throws Exception {
        String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 10, 29, 20, 43, 40));

        assertThat(json).isEqualTo("\"2026-10-29T20:43:40Z\"");
    }

    @Test
    @DisplayName("ContestResponse: mốc thời gian phải được xuất kèm hậu tố Z (UTC) để client không lệch múi giờ")
    void contestResponse_SerializesTimestampsWithUtcSuffix() throws Exception {
        ContestResponse response = new ContestResponse(
                1L,
                "Midterm Exam",
                LocalDateTime.of(2026, 10, 29, 19, 13, 40),
                LocalDateTime.of(2026, 10, 29, 20, 43, 40),
                ContestStatus.RUNNING,
                ScoringPolicy.ACM_ICPC,
                3,
                List.of(),
                ParticipantStatus.REGISTERED,
                0);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"startTime\":\"2026-10-29T19:13:40Z\"")
                .contains("\"endTime\":\"2026-10-29T20:43:40Z\"");
    }

    @Test
    @DisplayName("Endpoint kỳ thi: JSON trả về cho client phải có hậu tố Z trên mọi mốc thời gian")
    void contestEndpoint_ReturnsUtcTimestamps() throws Exception {
        Contest contest = contestRepository.save(Contest.builder()
                .title("Timezone Contest")
                .slug("timezone-contest")
                .startTime(LocalDateTime.of(2026, 10, 29, 19, 13, 40))
                .endTime(LocalDateTime.of(2026, 10, 29, 20, 43, 40))
                .scoringPolicy(ScoringPolicy.ACM_ICPC)
                .maxViolationsAllowed(3)
                .status(ContestStatus.RUNNING)
                .createdBy(candidate)
                .build());

        mockMvc.perform(get("/api/v1/contests/" + contest.getId())
                        .header("Authorization", candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("2026-10-29T19:13:40Z"))
                .andExpect(jsonPath("$.endTime").value("2026-10-29T20:43:40Z"));
    }
}
