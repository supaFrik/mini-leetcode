package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.dto.LoginRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.RegisterRequest;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.authoring.JudgeAuthoringClient;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemAuthoringIntegrationTest {

    private static final String RULES = "{\"rules\":[{\"targetClassName\":\"Account\",\"expectedType\":\"CLASS\"}]}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ProblemRepository problemRepository;

    @MockBean
    private StorageService storageService;
    @MockBean
    private JudgeAuthoringClient judge;

    /** Bộ nhớ giả của MinIO: uri → nội dung. */
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private String instructorToken;
    private String studentToken;
    private long problemId;

    @BeforeEach
    void setUp() throws Exception {
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), any())).thenAnswer(inv -> {
            String uri = "minio://testcases/" + inv.getArgument(0);
            objects.put(uri, ((InputStream) inv.getArgument(1)).readAllBytes());
            return uri;
        });
        when(storageService.downloadFileByUri(anyString())).thenAnswer(inv -> {
            byte[] bytes = objects.get(inv.<String>getArgument(0));
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        });

        userRepository.save(User.builder().username("author1").email("author1@example.com")
                .passwordHash(passwordEncoder.encode("Password123!")).role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE).build());
        instructorToken = token(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("author1", "Password123!"))), 200);
        studentToken = token(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("learner1", "learner1@example.com", "Password123!"))), 201);

        MvcResult created = mockMvc.perform(post("/api/v1/problems").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mini Bank\",\"description\":\"d\",\"difficulty\":\"EASY\","
                                + "\"type\":\"OOP_GRINDING\",\"starterTemplates\":\"[]\"}"))
                .andExpect(status().isCreated()).andReturn();
        problemId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void authoringEndpointsAreStaffOnly() throws Exception {
        mockMvc.perform(get(base() + "/authoring")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base() + "/authoring").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base() + "/reference-run").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base() + "/authoring").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradingConfig").doesNotExist())
                .andExpect(jsonPath("$.referenceSolution.length()").value(0))
                .andExpect(jsonPath("$.gradingFrozen").value(false))
                .andExpect(jsonPath("$.problemType").value("OOP_GRINDING"));
    }

    /** Hồi quy: editor admin luôn gửi tags (có thể rỗng); TagNormalizer từng trả List.of() ⇒ 500 khi lưu. */
    @Test
    void savingDetailsWithEmptyTagsSucceeds() throws Exception {
        mockMvc.perform(put("/api/v1/problems/" + problemId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mini Bank v2\",\"description\":\"d\",\"difficulty\":\"EASY\","
                                + "\"timeLimitMs\":1000,\"memoryLimitMb\":256,\"tags\":[],\"type\":\"OOP_GRINDING\","
                                + "\"starterTemplates\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mini Bank v2"));
        problemRepository.flush();
        mockMvc.perform(put("/api/v1/problems/" + problemId).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[]}"))
                .andExpect(status().isOk());
        problemRepository.flush();
    }

    @Test
    void gradingConfigIsValidatedByJudgeAndHiddenFromPublicApi() throws Exception {
        when(judge.validateGradingConfig("{\"rules\":[{}]}")).thenReturn(new JudgeAuthoringClient.ValidationResult(
                false, List.of("rules[0].targetClassName: required"), 0, 0, 0));
        mockMvc.perform(put(base() + "/grading-config").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("gradingConfig", "{\"rules\":[{}]}"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("rules[0].targetClassName: required"));

        when(judge.validateGradingConfig(RULES)).thenReturn(
                new JudgeAuthoringClient.ValidationResult(true, List.of(), 1, 0, 1.0));
        mockMvc.perform(put(base() + "/grading-config").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("gradingConfig", RULES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradingConfig").isNotEmpty());

        // DTO bài tập thông thường (kể cả staff xem) không bao giờ mang rule chấm / lời giải mẫu.
        String publicJson = mockMvc.perform(get("/api/v1/problems/" + problemId).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicJson).doesNotContain("gradingConfig").doesNotContain("Account")
                .doesNotContain("referenceSolution");
        assertThat(objectMapper.readTree(publicJson).get("type").asText()).isEqualTo("OOP_GRINDING");

        // Bài PUBLISHED: rule bị khoá.
        var problem = problemRepository.findById(problemId).orElseThrow();
        problem.setStatus(ProblemStatus.PUBLISHED);
        problemRepository.save(problem);
        mockMvc.perform(put(base() + "/grading-config").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("gradingConfig", RULES))))
                .andExpect(status().isConflict());
    }

    @Test
    void referenceRunComparesAndAppliesExpectedOutputs() throws Exception {
        mockMvc.perform(post(base() + "/reference-run").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(base() + "/reference-solution").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("files", List.of(Map.of("path", "Main.java", "content", "class Main{}"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceSolution[0].path").value("Main.java"));

        long first = addCase("1\n", "10\n");
        long second = addCase("2\n", "old\n");

        when(judge.runReference(any(), any(), anyLong(), anyInt())).thenReturn(new JudgeAuthoringClient.RunResult(
                true, null, "Main", null, List.of(
                        new JudgeAuthoringClient.CaseRun("10\n", "", 0, 120, false, false, false, true),
                        new JudgeAuthoringClient.CaseRun("20\n", "", 0, 130, false, false, false, false))));

        mockMvc.perform(post(base() + "/reference-run").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].status").value("MATCH"))
                .andExpect(jsonPath("$.cases[1].status").value("DIFF"))
                .andExpect(jsonPath("$.appliedCount").value(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JudgeAuthoringClient.CaseInput>> inputs = ArgumentCaptor.forClass(List.class);
        verify(judge).runReference(any(), inputs.capture(), eq(1000L), eq(256));
        assertThat(inputs.getValue()).containsExactly(
                new JudgeAuthoringClient.CaseInput("1\n", "10\n"), new JudgeAuthoringClient.CaseInput("2\n", "old\n"));

        mockMvc.perform(post(base() + "/reference-run").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"apply\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1))
                .andExpect(jsonPath("$.cases[0].applied").value(false))
                .andExpect(jsonPath("$.cases[1].applied").value(true));

        JsonNode cases = objectMapper.readTree(mockMvc.perform(get("/api/v1/problems/" + problemId + "/test-cases")
                .header("Authorization", bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cases.get(0).get("id").asLong()).isEqualTo(first);
        assertThat(cases.get(0).get("expectedOutput").asText()).isEqualTo("10\n");
        assertThat(cases.get(1).get("id").asLong()).isEqualTo(second);
        assertThat(cases.get(1).get("expectedOutput").asText()).isEqualTo("20\n");
        assertThat(cases.get(1).get("expectedOutputTruncated").asBoolean()).isFalse();

        // stdin tuỳ ý không được kèm apply.
        mockMvc.perform(post(base() + "/reference-run").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputs\":[\"5\\n\"],\"apply\":true}"))
                .andExpect(status().isBadRequest());
    }

    private long addCase(String input, String output) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/v1/problems/" + problemId + "/test-cases")
                        .file(new MockMultipartFile("inputFile", "in.txt", "text/plain", input.getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("outputFile", "out.txt", "text/plain", output.getBytes(StandardCharsets.UTF_8)))
                        .param("isHidden", "false")
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private String token(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult r = mockMvc.perform(request).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(expectedStatus);
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String base() {
        return "/api/v1/admin/problems/" + problemId;
    }

    private String bearer() {
        return "Bearer " + instructorToken;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
