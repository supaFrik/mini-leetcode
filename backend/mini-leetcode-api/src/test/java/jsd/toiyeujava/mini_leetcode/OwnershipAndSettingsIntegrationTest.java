package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quyền sở hữu nội dung của giảng viên, cài đặt chỉ ADMIN, và trang /teaching. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnershipAndSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private ContestRepository contestRepository;
    @Autowired private SubmissionRepository submissionRepository;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private Problem problem(String slug, User owner) {
        return problemRepository.save(Problem.builder().title(slug).slug(slug).description("d")
                .difficulty(Difficulty.EASY).createdBy(owner).build());
    }

    @Test
    @DisplayName("Instructor gets 403 editing someone else's problem or contest; owner and admin pass the check")
    void ownership() throws Exception {
        User owner = user("ow_owner", Role.INSTRUCTOR);
        User other = user("ow_other", Role.INSTRUCTOR);
        User admin = user("ow_admin", Role.ADMIN);
        Problem p = problem("ow-problem", owner);
        Contest c = contestRepository.save(Contest.builder().title("Ow Contest").slug("ow-contest")
                .startTime(LocalDateTime.now().plusDays(1)).endTime(LocalDateTime.now().plusDays(2))
                .createdBy(owner).build());

        // Body rỗng là update hợp lệ (không đổi gì): chủ bài và ADMIN nhận 200, người khác 403.
        mockMvc.perform(put("/api/v1/problems/{id}", p.getId()).header("Authorization", token(other))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/problems/{id}/transitions", p.getId()).header("Authorization", token(other))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/problems/{id}/reference-solution", p.getId())
                .header("Authorization", token(other))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/contests/{id}", c.getId()).header("Authorization", token(other))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/problems/{id}", p.getId()).header("Authorization", token(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/problems/{id}", p.getId()).header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // Đọc vẫn được: không chặn GET.
        mockMvc.perform(get("/api/v1/admin/contests/{id}", c.getId()).header("Authorization", token(other)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Settings are ADMIN only; closing registration blocks sign-ups and shows in public settings")
    void settings() throws Exception {
        User instructor = user("st_teacher", Role.INSTRUCTOR);
        User admin = user("st_admin", Role.ADMIN);

        mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", token(instructor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteName").value("Mini LeetCode"));
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nope\":\"x\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"defaultTimeLimitMs\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/settings").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationOpen\":\"false\",\"maintenanceMode\":\"true\",\"bannerMessage\":\"Back soon\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(false))
                .andExpect(jsonPath("$.maintenanceMode").value(true))
                .andExpect(jsonPath("$.bannerMessage").value("Back soon"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"st_new\",\"email\":\"st_new@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Teaching dashboard: staff only, submissions scoped to the caller's problems")
    void teaching() throws Exception {
        User owner = user("tc_owner", Role.INSTRUCTOR);
        User other = user("tc_other", Role.INSTRUCTOR);
        User learner = user("tc_learner", Role.USER);
        Problem p = problem("tc-problem", owner);
        submissionRepository.save(Submission.builder().user(learner).problem(p).language(SubmissionLanguage.JAVA)
                .codeStorageUri("s3://x").status(SubmissionStatus.COMPLETED).verdict(SubmissionVerdict.WA)
                .quickRun(false).build());

        mockMvc.perform(get("/api/v1/teaching/dashboard").header("Authorization", token(learner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teaching/dashboard").header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentSubmissions[0].username").value("tc_learner"));
        mockMvc.perform(get("/api/v1/admin/submissions").param("problem", "tc-problem")
                        .header("Authorization", token(other)))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/teaching/progress").header("Authorization", token(owner)))
                .andExpect(status().isOk());
    }
}
