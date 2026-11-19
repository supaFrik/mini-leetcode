package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Giám thị kỳ thi: ai được bổ nhiệm, ai xem được báo cáo vi phạm / đạo văn. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContestProctorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ContestRepository contestRepository;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private Contest contest(User owner) {
        LocalDateTime now = LocalDateTime.now();
        return contestRepository.saveAndFlush(Contest.builder().title("Proctored").slug("proctored-" + owner.getId())
                .startTime(now.minusHours(1)).endTime(now.plusHours(1)).status(ContestStatus.RUNNING)
                .registrationMode(RegistrationMode.OPEN).createdBy(owner).build());
    }

    private String body(String username) {
        return "{\"username\":\"" + username + "\"}";
    }

    @Test
    @DisplayName("Owner appoints an instructor proctor, who can then read the reports; others get 403")
    void proctorAccess() throws Exception {
        User owner = user("pr_owner", Role.INSTRUCTOR);
        User proctor = user("pr_proctor", Role.INSTRUCTOR);
        User stranger = user("pr_stranger", Role.INSTRUCTOR);
        User student = user("pr_student", Role.USER);
        User admin = user("pr_admin", Role.ADMIN);
        Long id = contest(owner).getId();
        String report = "/api/v1/contests/{id}/proctoring/report";

        // Trước khi bổ nhiệm: giảng viên khác không xem được báo cáo.
        mockMvc.perform(get(report, id).header("Authorization", token(proctor))).andExpect(status().isForbidden());
        mockMvc.perform(get(report, id).header("Authorization", token(owner))).andExpect(status().isOk());
        mockMvc.perform(get(report, id).header("Authorization", token(admin))).andExpect(status().isOk());

        // Chỉ chủ kỳ thi / ADMIN được bổ nhiệm; chỉ INSTRUCTOR/ADMIN được làm giám thị.
        mockMvc.perform(post("/api/v1/admin/contests/{id}/proctors", id).header("Authorization", token(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(body("pr_stranger")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/contests/{id}/proctors", id).header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body("pr_student")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/contests/{id}/proctors", id).header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body("pr_proctor")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("pr_proctor"));
        mockMvc.perform(post("/api/v1/admin/contests/{id}/proctors", id).header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body("pr_proctor")))
                .andExpect(status().isConflict());

        // Giám thị xem được báo cáo vi phạm và đạo văn; người lạ và học viên thì không.
        mockMvc.perform(get(report, id).header("Authorization", token(proctor))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contests/{id}/plagiarism", id).header("Authorization", token(proctor)))
                .andExpect(status().isOk());
        mockMvc.perform(get(report, id).header("Authorization", token(stranger))).andExpect(status().isForbidden());
        mockMvc.perform(get(report, id).header("Authorization", token(student))).andExpect(status().isForbidden());

        // Danh sách kỳ thi mang proctorIds để giao diện biết ai là giám thị.
        mockMvc.perform(get("/api/v1/admin/contests").header("Authorization", token(proctor)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].proctorIds[0]").value(proctor.getId().intValue()));

        // Giám thị không tự sửa được kỳ thi hay gỡ mình; chủ kỳ thi gỡ được.
        mockMvc.perform(delete("/api/v1/admin/contests/{id}/proctors/{uid}", id, proctor.getId())
                        .header("Authorization", token(proctor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/contests/{id}/proctors", id).header("Authorization", token(owner)))
                .andExpect(jsonPath("$[0].username").value("pr_proctor"));
        mockMvc.perform(delete("/api/v1/admin/contests/{id}/proctors/{uid}", id, proctor.getId())
                        .header("Authorization", token(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(report, id).header("Authorization", token(proctor))).andExpect(status().isForbidden());
    }
}
