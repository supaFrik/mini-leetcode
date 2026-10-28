package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.submission.controller.SubmissionController;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.producer.SubmissionProducer;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.transaction.annotation.Transactional;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private SubmissionService submissionService;

    @MockBean
    private SubmissionProducer submissionProducer;

    private User student1;
    private User student2;
    private User admin;
    private String tokenStudent1;
    private String tokenStudent2;
    private String tokenAdmin;

    private Problem testProblem;
    private Submission submission1;

    @BeforeEach
    void setUp() {
        student1 = userRepository.save(User.builder()
                .username("sub_student1")
                .email("student1@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        student2 = userRepository.save(User.builder()
                .username("sub_student2")
                .email("student2@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        admin = userRepository.save(User.builder()
                .username("sub_admin")
                .email("admin@example.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        tokenStudent1 = "Bearer " + jwtTokenProvider.generateAccessToken(student1);
        tokenStudent2 = "Bearer " + jwtTokenProvider.generateAccessToken(student2);
        tokenAdmin = "Bearer " + jwtTokenProvider.generateAccessToken(admin);

        testProblem = Problem.builder()
                .id(10L)
                .title("Two Sum")
                .slug("two-sum")
                .description("Two Sum Problem")
                .difficulty(Difficulty.EASY)
                .build();

        submission1 = Submission.builder()
                .id(501L)
                .problem(testProblem)
                .user(student1)
                .language(SubmissionLanguage.JAVA)
                .codeStorageUri("submissions/10/1/uuid.java")
                .status(SubmissionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Nộp bài thành công trả về 202 ACCEPTED và đẩy vào hàng đợi Redis")
    void submitCode_Success_Returns202() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }"
        );

        when(submissionService.createSubmission(eq(10L), eq(student1.getId()), any(SubmitCodeRequest.class)))
                .thenReturn(submission1);

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.language").value("JAVA"));

        verify(submissionProducer).enqueueSubmission(argThat(msg ->
                msg.submissionId().equals(501L) &&
                msg.problemId().equals(10L) &&
                msg.userId().equals(student1.getId()) &&
                msg.language() == SubmissionLanguage.JAVA
        ));
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Redis enqueue thất bại cập nhật trạng thái FAILED và trả về lỗi 500")
    void submitCode_RedisEnqueueFailure_MarksSubmissionFailed_Returns500() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }"
        );

        when(submissionService.createSubmission(eq(10L), eq(student1.getId()), any(SubmitCodeRequest.class)))
                .thenReturn(submission1);
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis connection timed out"))
                .when(submissionProducer).enqueueSubmission(any());

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(submissionService).failSubmission(eq(501L), contains("Redis connection timed out"));
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Redis enqueue thất bại và failSubmission gặp lỗi DB, vẫn log và trả về lỗi 500")
    void submitCode_RedisEnqueueFailure_WhenFailSubmissionThrows_StillLogsAndReturns500() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }"
        );

        when(submissionService.createSubmission(eq(10L), eq(student1.getId()), any(SubmitCodeRequest.class)))
                .thenReturn(submission1);
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis connection timed out"))
                .when(submissionProducer).enqueueSubmission(any());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Database network partition"))
                .when(submissionService).failSubmission(anyLong(), anyString());

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(submissionService).failSubmission(eq(501L), contains("Redis connection timed out"));
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Redis enqueue thất bại với message null vẫn cập nhật FAILED với class name và trả về lỗi 500")
    void submitCode_RedisEnqueueFailure_NullMessage_MarksSubmissionFailedWithClassName() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }"
        );

        when(submissionService.createSubmission(eq(10L), eq(student1.getId()), any(SubmitCodeRequest.class)))
                .thenReturn(submission1);
        doThrow(new RuntimeException((String) null))
                .when(submissionProducer).enqueueSubmission(any());

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(submissionService).failSubmission(eq(501L), contains("RuntimeException"));
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Redis enqueue thất bại với message quá dài thì tự động cắt ngắn an toàn")
    void submitCode_RedisEnqueueFailure_LongMessage_TruncatesReasonSafely() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }"
        );

        String hugeMessage = "A".repeat(1000);

        when(submissionService.createSubmission(eq(10L), eq(student1.getId()), any(SubmitCodeRequest.class)))
                .thenReturn(submission1);
        doThrow(new RuntimeException(hugeMessage))
                .when(submissionProducer).enqueueSubmission(any());

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(submissionService).failSubmission(eq(501L), argThat(msg ->
                msg.contains("A".repeat(100)) && msg.endsWith("...") && msg.length() <= 600
        ));
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Lỗi 400 Bad Request khi code để trống")
    void submitCode_BlankCode_Returns400() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(SubmissionLanguage.JAVA, "   ");

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionProducer);
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Bị từ chối 403 Forbidden khi thí sinh đã bị DISQUALIFIED trong contest")
    void submitCode_ExamMode_Disqualified_Returns403() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution {}",
                100L
        );

        when(submissionService.createSubmission(eq(10L), eq(student1.getId()), any(SubmitCodeRequest.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("User has been disqualified from this contest due to proctoring violations"));

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .header("Authorization", tokenStudent1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(submissionProducer);
    }

    @Test
    @DisplayName("POST /problems/{id}/submit: Lỗi 401 Unauthorized khi không có JWT token")
    void submitCode_Unauthenticated_Returns401() throws Exception {
        SubmitCodeRequest request = new SubmitCodeRequest(SubmissionLanguage.JAVA, "System.out.println(1);");

        mockMvc.perform(post("/api/v1/problems/10/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /submissions/{id}: Chủ sở hữu bài nộp được phép xem kết quả (200 OK)")
    void getSubmission_Owner_Returns200() throws Exception {
        when(submissionService.getSubmissionById(501L)).thenReturn(submission1);

        mockMvc.perform(get("/api/v1/submissions/501")
                        .header("Authorization", tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.username").value("sub_student1"));
    }

    @Test
    @DisplayName("GET /submissions/{id}: Sinh viên khác không được xem bài nộp của người khác (403 Forbidden)")
    void getSubmission_OtherStudent_Forbidden() throws Exception {
        when(submissionService.getSubmissionById(501L)).thenReturn(submission1);

        mockMvc.perform(get("/api/v1/submissions/501")
                        .header("Authorization", tokenStudent2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /submissions/{id}: Admin được phép xem bài nộp của bất kỳ thí sinh nào (200 OK)")
    void getSubmission_Admin_Allowed() throws Exception {
        when(submissionService.getSubmissionById(501L)).thenReturn(submission1);

        mockMvc.perform(get("/api/v1/submissions/501")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(501));
    }

    @Test
    @DisplayName("GET /submissions/my: Lấy danh sách bài nộp của user hiện tại (200 OK)")
    void getMySubmissions_Returns200() throws Exception {
        SubmissionResponse response = SubmissionResponse.fromEntity(submission1);
        PageRequest pageable = PageRequest.of(0, 20);
        when(submissionService.getUserSubmissions(eq(student1.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/v1/submissions/my")
                        .header("Authorization", tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(501));
    }

    @Test
    @DisplayName("GET /submissions/{id}/code: Chủ sở hữu bài nộp được phép xem mã nguồn (200 OK)")
    void getSubmissionCode_Owner_Returns200() throws Exception {
        when(submissionService.getSubmissionById(501L)).thenReturn(submission1);
        when(submissionService.getSubmissionCode(501L)).thenReturn("public class Solution {}");

        mockMvc.perform(get("/api/v1/submissions/501/code")
                        .header("Authorization", tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("public class Solution {}"));
    }

    @Test
    @DisplayName("GET /submissions/{id}/code: Sinh viên khác không được xem mã nguồn bài nộp (403 Forbidden)")
    void getSubmissionCode_OtherStudent_Forbidden() throws Exception {
        when(submissionService.getSubmissionById(501L)).thenReturn(submission1);

        mockMvc.perform(get("/api/v1/submissions/501/code")
                        .header("Authorization", tokenStudent2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /submissions/{id}/code: Admin được phép xem mã nguồn của bất kỳ ai (200 OK)")
    void getSubmissionCode_Admin_Allowed() throws Exception {
        when(submissionService.getSubmissionById(501L)).thenReturn(submission1);
        when(submissionService.getSubmissionCode(501L)).thenReturn("public class Solution {}");

        mockMvc.perform(get("/api/v1/submissions/501/code")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("public class Solution {}"));
    }

    @Test
    @DisplayName("GET /problems/{id}/submissions: Lấy danh sách nộp bài theo problem (200 OK)")
    void getProblemSubmissions_Returns200() throws Exception {
        SubmissionResponse response = SubmissionResponse.fromEntity(submission1);
        PageRequest pageable = PageRequest.of(0, 20);
        when(submissionService.getProblemSubmissions(eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        mockMvc.perform(get("/api/v1/problems/10/submissions")
                        .header("Authorization", tokenStudent1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(501));
    }
}
