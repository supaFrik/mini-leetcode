package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bảng quyền theo role: chỉ ADMIN sửa, chỉ quyền của INSTRUCTOR đổi được, và đổi xong có hiệu lực ngay. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolePermissionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;

    private static final String[] DEFAULTS = {"PROBLEM_EDIT", "PROBLEM_PUBLISH", "CONTEST_EDIT", "CONTEST_PARTICIPANTS",
                        "CONTEST_REPORTS", "COURSE_EDIT", "COURSE_DELETE", "ANNOUNCEMENT_SEND"};

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private void putPerms(User u, String body, ResultMatcher expected) throws Exception {
        mockMvc.perform(put("/api/v1/admin/permissions").header("Authorization", token(u))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(expected);
    }

    @Test
    @DisplayName("Defaults match the old role table; only ADMIN reads or writes the matrix")
    void defaultsAndAccess() throws Exception {
        User admin = user("rp_admin", Role.ADMIN);
        User inst = user("rp_inst", Role.INSTRUCTOR);
        mockMvc.perform(get("/api/v1/admin/permissions").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.USER", hasSize(0)))
                .andExpect(jsonPath("$.INSTRUCTOR", containsInAnyOrder(DEFAULTS)))
                .andExpect(jsonPath("$.ADMIN", hasSize(14)));
        mockMvc.perform(get("/api/v1/admin/permissions").header("Authorization", token(inst)))
                .andExpect(status().isForbidden());
        putPerms(inst, "{\"INSTRUCTOR\":[\"PROBLEM_EDIT\"]}", status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/me/permissions").header("Authorization", token(inst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder(DEFAULTS)));
        mockMvc.perform(get("/api/v1/auth/me/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT rejects USER, ADMIN and SYSTEM_MANAGE changes with 400")
    void rejectsLockedCells() throws Exception {
        User admin = user("rp_admin2", Role.ADMIN);
        putPerms(admin, "{\"USER\":[\"PROBLEM_EDIT\"]}", status().isBadRequest());
        putPerms(admin, "{\"ADMIN\":[]}", status().isBadRequest());
        putPerms(admin, "{\"INSTRUCTOR\":[\"SYSTEM_MANAGE\"]}", status().isBadRequest());
    }

    @Test
    @DisplayName("Toggling instructor permissions takes effect on the next request; ownership still applies")
    void togglesTakeEffect() throws Exception {
        User admin = user("rp_admin3", Role.ADMIN);
        User inst = user("rp_inst3", Role.INSTRUCTOR);
        User other = user("rp_other3", Role.INSTRUCTOR);
        Problem own = problemRepository.save(Problem.builder().title("rp-own").slug("rp-own").description("d")
                .difficulty(Difficulty.EASY).createdBy(inst).build());
        Problem notOwn = problemRepository.save(Problem.builder().title("rp-other").slug("rp-other").description("d")
                .difficulty(Difficulty.EASY).createdBy(other).build());

        // Mặc định: không xoá được, không quản lý tag.
        mockMvc.perform(delete("/api/v1/problems/{id}", own.getId()).header("Authorization", token(inst)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(inst)))
                .andExpect(status().isForbidden());

        putPerms(admin, "{\"INSTRUCTOR\":[\"PROBLEM_DELETE\",\"TAG_MANAGE\"]}", status().isOk());

        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(inst)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/problems/{id}", notOwn.getId()).header("Authorization", token(inst)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/problems/{id}", own.getId()).header("Authorization", token(inst)))
                .andExpect(status().isNoContent());
        // Bỏ PROBLEM_PUBLISH: không chuyển trạng thái bài được nữa, kể cả bài của mình.
        Problem own2 = problemRepository.save(Problem.builder().title("rp-own2").slug("rp-own2").description("d")
                .difficulty(Difficulty.EASY).createdBy(inst).build());
        mockMvc.perform(post("/api/v1/problems/{id}/transitions", own2.getId()).header("Authorization", token(inst))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isForbidden());
        // Bỏ PROBLEM_EDIT: không sửa bài được nữa.
        mockMvc.perform(put("/api/v1/problems/{id}", notOwn.getId()).header("Authorization", token(other))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        // Admin vẫn đủ quyền, không phụ thuộc bảng.
        mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", token(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/settings").header("Authorization", token(inst)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Without PROBLEM_VIEW_ALL an instructor sees only own and published problems; with it, others' drafts read only")
    void viewAllProblems() throws Exception {
        User admin = user("rp_admin4", Role.ADMIN);
        User inst = user("rp_inst4", Role.INSTRUCTOR);
        User other = user("rp_other4", Role.INSTRUCTOR);
        problemRepository.save(Problem.builder().title("rpv-own").slug("rpv-own").description("d")
                .difficulty(Difficulty.EASY).createdBy(inst).build());
        Problem draft = problemRepository.save(Problem.builder().title("rpv-draft").slug("rpv-draft").description("d")
                .difficulty(Difficulty.EASY).createdBy(other).build());
        problemRepository.save(Problem.builder().title("rpv-pub").slug("rpv-pub").description("d")
                .difficulty(Difficulty.EASY).createdBy(other).status(ProblemStatus.PUBLISHED).build());

        mockMvc.perform(get("/api/v1/problems").param("scope", "ALL").param("search", "rpv-")
                        .header("Authorization", token(inst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", containsInAnyOrder("rpv-own", "rpv-pub")));
        mockMvc.perform(get("/api/v1/problems/{id}", draft.getId()).header("Authorization", token(inst)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/problems/rpv-draft").header("Authorization", token(inst)))
                .andExpect(status().isForbidden());

        putPerms(admin, "{\"INSTRUCTOR\":[\"PROBLEM_EDIT\",\"PROBLEM_VIEW_ALL\"]}", status().isOk());

        mockMvc.perform(get("/api/v1/problems").param("scope", "ALL").param("search", "rpv-")
                        .header("Authorization", token(inst)))
                .andExpect(jsonPath("$.content[*].slug", containsInAnyOrder("rpv-own", "rpv-draft", "rpv-pub")));
        mockMvc.perform(get("/api/v1/problems/{id}", draft.getId()).header("Authorization", token(inst)))
                .andExpect(status().isOk());
        // Vẫn chỉ xem: sửa bài của người khác bị chặn.
        mockMvc.perform(put("/api/v1/problems/{id}", draft.getId()).header("Authorization", token(inst))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
}
