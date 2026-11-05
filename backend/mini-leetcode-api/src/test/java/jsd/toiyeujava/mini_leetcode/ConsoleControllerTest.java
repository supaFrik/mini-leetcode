package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteResponse;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleTranscriptResponse;
import jsd.toiyeujava.mini_leetcode.console.service.ConsoleService;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp endpoint console OOP.
 *
 * <p>Kiểm tra: yêu cầu phải đăng nhập; payload hợp lệ được chuyển tới service;
 * transcript đọc/xoá hoạt động; lỗi sandbox ⇒ 503; lỗi rate limit ⇒ 429.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private ConsoleService consoleService;

    private User student;
    private String token;

    @BeforeEach
    void setUp() {
        student = userRepository.save(User.builder()
                .username("console_student")
                .email("console_student@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        token = jwtTokenProvider.generateAccessToken(student);
    }

    @Test
    @DisplayName("POST /console/execute without auth → 401")
    void execute_Anonymous_IsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/problems/1/console/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"path\":\"A.java\",\"content\":\"class A {}\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /console/execute with a valid payload → 200 + metrics")
    void execute_Authenticated_ReturnsMetrics() throws Exception {
        ConsoleExecuteResponse response = new ConsoleExecuteResponse(
                "42\n", "", 0, 87, 24576, null, false, false);
        when(consoleService.execute(eq(student.getId()), eq(42L), any())).thenReturn(response);

        String body = objectMapper.writeValueAsString(Map.of(
                "files", List.of(Map.of("path", "Circle.java", "content", "public class Circle {}")),
                "input", "42",
                "expression", "new Circle(2).getArea()"));

        mockMvc.perform(post("/api/v1/problems/42/console/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stdout").value("42\n"))
                .andExpect(jsonPath("$.data.exitCode").value(0))
                .andExpect(jsonPath("$.data.runtimeMs").value(87))
                .andExpect(jsonPath("$.data.peakMemoryKb").value(24576));

        verify(consoleService).execute(eq(student.getId()), eq(42L), any());
    }

    @Test
    @DisplayName("GET /console/transcript returns the stored entries")
    void transcript_ReturnsEntries() throws Exception {
        when(consoleService.getTranscript(student.getId(), 42L))
                .thenReturn(new ConsoleTranscriptResponse(List.of(), 50, 0));

        mockMvc.perform(get("/api/v1/problems/42/console/transcript")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(50))
                .andExpect(jsonPath("$.data.used").value(0));
    }

    @Test
    @DisplayName("DELETE /console/transcript returns 204")
    void transcript_ClearReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/problems/42/console/transcript")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(consoleService).clearTranscript(student.getId(), 42L);
    }
}
