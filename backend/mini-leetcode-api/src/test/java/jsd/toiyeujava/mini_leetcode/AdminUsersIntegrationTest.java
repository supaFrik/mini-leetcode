package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1a: tổng quan admin và quản lý người dùng. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUsersIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private org.springframework.test.web.servlet.ResultActions patchUser(User actor, User target, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/users/{id}", target.getId()).header("Authorization", token(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("Stats and user list are ADMIN only")
    void adminOnly() throws Exception {
        User instructor = user("au_instructor", Role.INSTRUCTOR);
        User admin = user("au_admin", Role.ADMIN);
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", token(instructor))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", token(instructor))).andExpect(status().isForbidden());
        patchUser(instructor, admin, "{\"status\":\"BLOCKED\"}").andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.recentSubmissions").isArray())
                .andExpect(jsonPath("$.recentUsers[0].username").value("au_admin"))
                .andExpect(jsonPath("$.daily.length()").value(14))
                .andExpect(jsonPath("$.acRate").isNumber())
                .andExpect(jsonPath("$.activeUsers7d").isNumber())
                .andExpect(jsonPath("$.byDifficulty.EASY").isNumber())
                .andExpect(jsonPath("$.lowestAc").isArray())
                .andExpect(jsonPath("$.upcomingContests").isArray());
    }

    @Test
    @DisplayName("Search by username or email; block, unblock and change role")
    void searchAndUpdate() throws Exception {
        User admin = user("au_boss", Role.ADMIN);
        User student = user("au_student", Role.USER);
        user("someone_else", Role.USER);

        mockMvc.perform(get("/api/v1/admin/users").param("q", "AU_STU").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("au_student"));
        mockMvc.perform(get("/api/v1/admin/users").param("q", "au_student@example").header("Authorization", token(admin)))
                .andExpect(jsonPath("$.total").value(1));

        patchUser(admin, student, "{\"status\":\"BLOCKED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        // A blocked account's token stops working on the next request.
        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", token(student))).andExpect(status().isUnauthorized());
        patchUser(admin, student, "{\"status\":\"ACTIVE\"}").andExpect(jsonPath("$.status").value("ACTIVE"));
        patchUser(admin, student, "{\"role\":\"INSTRUCTOR\"}").andExpect(jsonPath("$.role").value("INSTRUCTOR"));
        assertThat(userRepository.findById(student.getId()).orElseThrow().getRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    @DisplayName("An admin cannot block or demote themselves")
    void noSelfLockout() throws Exception {
        User admin = user("au_self", Role.ADMIN);
        patchUser(admin, admin, "{\"status\":\"BLOCKED\"}").andExpect(status().isForbidden());
        patchUser(admin, admin, "{\"role\":\"USER\"}").andExpect(status().isForbidden());
        // Sending the current values is a no-op, not an error.
        patchUser(admin, admin, "{\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        User fresh = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(fresh.getRole()).isEqualTo(Role.ADMIN);
        assertThat(fresh.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
