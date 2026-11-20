package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trang admin: bảng mọi bài nộp và thông báo chung. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminSubmissionsAnnouncementsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private void submit(User u, Problem p, SubmissionVerdict verdict, boolean quickRun) {
        submissionRepository.save(Submission.builder().user(u).problem(p).language(SubmissionLanguage.JAVA)
                .codeStorageUri("s3://x").status(verdict == null ? SubmissionStatus.PENDING : SubmissionStatus.COMPLETED)
                .verdict(verdict).quickRun(quickRun).build());
    }

    @Test
    @DisplayName("Submissions table: staff only, filters by user, problem and verdict, excludes Run")
    void submissions() throws Exception {
        User instructor = user("as_teacher", Role.INSTRUCTOR);
        User alice = user("as_alice", Role.USER);
        User bob = user("as_bob", Role.USER);
        Problem p = problemRepository.save(Problem.builder().title("As Two Sum").slug("as-two-sum")
                .description("d").difficulty(Difficulty.EASY).createdBy(instructor).build());
        submit(alice, p, SubmissionVerdict.AC, false);
        submit(alice, p, SubmissionVerdict.WA, false);
        submit(alice, p, SubmissionVerdict.AC, true);
        submit(bob, p, null, false);

        mockMvc.perform(get("/api/v1/admin/submissions").header("Authorization", token(alice)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/submissions").param("problem", "as-two").header("Authorization", token(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].username").value("as_bob"))
                .andExpect(jsonPath("$.items[0].problemTitle").value("As Two Sum"));
        mockMvc.perform(get("/api/v1/admin/submissions").param("user", "AS_ALICE").param("verdict", "ac")
                        .header("Authorization", token(instructor)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].verdict").value("AC"));
        mockMvc.perform(get("/api/v1/admin/submissions").param("problem", "as two").param("verdict", "PENDING")
                        .header("Authorization", token(instructor)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].username").value("as_bob"));
        mockMvc.perform(get("/api/v1/admin/submissions").param("verdict", "NOPE").header("Authorization", token(instructor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Announcements: staff create, instructors delete only their own, admin deletes any, everyone reads")
    void announcements() throws Exception {
        User instructor = user("an_teacher", Role.INSTRUCTOR);
        User other = user("an_other", Role.INSTRUCTOR);
        User admin = user("an_admin", Role.ADMIN);
        User learner = user("an_learner", Role.USER);
        String body = "{\"title\":\"Contest on Friday\",\"body\":\"Starts at 9am.\"}";

        mockMvc.perform(post("/api/v1/admin/announcements").header("Authorization", token(learner))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/announcements").header("Authorization", token(instructor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \",\"body\":\"x\"}"))
                .andExpect(status().isBadRequest());
        String created = mockMvc.perform(post("/api/v1/admin/announcements").header("Authorization", token(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.createdByUsername").value("an_teacher"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/announcements").header("Authorization", token(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Contest on Friday"));
        mockMvc.perform(get("/api/v1/announcements")).andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/admin/announcements/{id}", id).header("Authorization", token(learner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/announcements/{id}", id).header("Authorization", token(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/announcements/{id}", id).header("Authorization", token(instructor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/announcements/{id}", id).header("Authorization", token(instructor)))
                .andExpect(status().isNotFound());

        String second = mockMvc.perform(post("/api/v1/admin/announcements").header("Authorization", token(other))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        long secondId = Long.parseLong(second.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(delete("/api/v1/admin/announcements/{id}", secondId).header("Authorization", token(admin)))
                .andExpect(status().isNoContent());
    }
}
