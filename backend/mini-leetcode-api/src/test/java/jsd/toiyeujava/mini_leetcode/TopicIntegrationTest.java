package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.entity.*;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.producer.SubmissionProducer;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chủ đề học gắn vào stage project (specs/topics.spec.md). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TopicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;

    @MockBean private SubmissionProducer submissionProducer;
    @MockBean private StorageService storageService;

    private User instructor;
    private User student;
    private String instructorJwt;
    private String otherInstructorJwt;
    private String studentJwt;
    private Problem stage1;
    private Problem exercise;

    @BeforeEach
    void setUp() {
        instructor = user("tp_teacher", Role.INSTRUCTOR);
        student = user("tp_student", Role.USER);
        instructorJwt = "Bearer " + jwtTokenProvider.generateAccessToken(instructor);
        otherInstructorJwt = "Bearer " + jwtTokenProvider.generateAccessToken(user("tp_other", Role.INSTRUCTOR));
        studentJwt = "Bearer " + jwtTokenProvider.generateAccessToken(student);
        stage1 = problem("tp-bank-1");
        exercise = problem("tp-wallet");
    }

    private Map<String, Object> topicBody(String slug) {
        return Map.of("slug", slug, "title", "Đóng gói", "summary", "private + method có kiểm tra", "estMinutes", 15,
                "steps", List.of(
                        Map.of("type", "THEORY", "title", "Lý thuyết",
                                "content", Map.of("markdown", "## Vì sao\nGiấu trạng thái.\n## Modifier\nprivate...")),
                        Map.of("type", "CHOICE", "title", "Field nào nên private?",
                                "content", Map.of("prompt", "Chọn tất cả", "multiple", true, "options", List.of(
                                        Map.of("id", "a", "text", "balance"), Map.of("id", "b", "text", "owner"),
                                        Map.of("id", "c", "text", "toString()"))),
                                "answer", Map.of("correct", List.of("a", "b"), "explanation", "SECRET-EXPLANATION")),
                        Map.of("type", "ORDERING", "title", "Sắp xếp",
                                "content", Map.of("prompt", "Thứ tự đúng", "items", List.of(
                                        Map.of("id", "x", "text", "kiểm tra"), Map.of("id", "y", "text", "đổi số dư"))),
                                "answer", Map.of("order", List.of("x", "y"))),
                        Map.of("type", "CODE", "title", "Ví điện tử", "content", Map.of("intro", "Viết Wallet"),
                                "problemId", exercise.getId())));
    }

    @Test
    @DisplayName("Author → attach to stage → learner reads, answers, finishes the code step; progress shows on the plan")
    void fullLearnerFlow() throws Exception {
        JsonNode created = json(createTopic("tp-encapsulation").andExpect(status().isCreated()));
        long topicId = created.get("id").asLong();
        transitionTopic("tp-encapsulation", "PUBLISHED").andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/study-plans").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "slug", "tp-plan", "title", "Plan", "items", List.of(Map.of("kind", "PROJECT", "title", "Bank",
                                "problemIds", List.of(stage1.getId()), "stageTopics", List.of(List.of(topicId))))))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/study-plans/tp-plan/transitions").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());

        plan().andExpect(jsonPath("$.items[0].stages[0].topics", hasSize(1)))
                .andExpect(jsonPath("$.items[0].stages[0].topics[0].slug").value("tp-encapsulation"))
                .andExpect(jsonPath("$.items[0].stages[0].topics[0].progress").value("NEW"))
                .andExpect(jsonPath("$.items[0].stages[0].topics[0].stepCount").value(4));

        // Học viên không bao giờ thấy đáp án
        JsonNode topic = json(mockMvc.perform(get("/api/v1/topics/tp-encapsulation").header("Authorization", studentJwt))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("SECRET-EXPLANATION"))))
                .andExpect(content().string(not(containsString("\"correct\""))))
                .andExpect(jsonPath("$.steps[3].content.problemSlug").value("tp-wallet")));
        long theory = topic.at("/steps/0/id").asLong();
        long choice = topic.at("/steps/1/id").asLong();
        long ordering = topic.at("/steps/2/id").asLong();

        attempt(theory, Map.of()).andExpect(jsonPath("$.correct").value(true)).andExpect(jsonPath("$.progress").value("IN_PROGRESS"));
        attempt(choice, Map.of("selected", List.of("a")))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.attempts").value(1));
        attempt(choice, Map.of("selected", List.of("a", "b")))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.explanation").value("SECRET-EXPLANATION"))
                .andExpect(jsonPath("$.attempts").value(2));
        attempt(ordering, Map.of("order", List.of("x", "y")))
                .andExpect(jsonPath("$.doneSteps").value(3)).andExpect(jsonPath("$.progress").value("IN_PROGRESS"));

        // Bước code xong khi đề được AC (bài nộp chính thức)
        submissionRepository.save(Submission.builder().user(student).problem(exercise).language(SubmissionLanguage.JAVA)
                .codeStorageUri("minio://t").status(SubmissionStatus.COMPLETED).verdict(SubmissionVerdict.AC).quickRun(false).build());
        plan().andExpect(jsonPath("$.items[0].stages[0].topics[0].progress").value("DONE"))
                .andExpect(jsonPath("$.items[0].stages[0].topics[0].doneSteps").value(4))
                .andExpect(jsonPath("$.items[0].stages[0].status").value("NEVER")); // chủ đề không đổi trạng thái stage
    }

    @Test
    @DisplayName("Skip is reversible; draft topics stay hidden from learners, also on the plan")
    void skipAndVisibility() throws Exception {
        long topicId = json(createTopic("tp-skip").andExpect(status().isCreated())).get("id").asLong();
        mockMvc.perform(get("/api/v1/topics/tp-skip").header("Authorization", studentJwt)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/study-plans").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                        "slug", "tp-plan2", "title", "Plan", "items", List.of(Map.of("kind", "PROJECT", "title", "Bank",
                                "problemIds", List.of(stage1.getId()), "stageTopics", List.of(List.of(topicId))))))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/study-plans/tp-plan2/transitions").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/study-plans/tp-plan2").header("Authorization", studentJwt))
                .andExpect(jsonPath("$.items[0].stages[0].topics", hasSize(0)));

        transitionTopic("tp-skip", "PUBLISHED").andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/topics/tp-skip/skip").header("Authorization", studentJwt)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/topics/tp-skip").header("Authorization", studentJwt))
                .andExpect(jsonPath("$.progress").value("SKIPPED"));
        mockMvc.perform(delete("/api/v1/topics/tp-skip/skip").header("Authorization", studentJwt)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/topics/tp-skip").header("Authorization", studentJwt))
                .andExpect(jsonPath("$.progress").value("NEW"));

        // Đang được plan dùng ⇒ không xoá được
        mockMvc.perform(delete("/api/v1/topics/tp-skip").header("Authorization", instructorJwt)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Editing keeps the progress of steps sent back with their id; authoring is author/admin only")
    void editingKeepsProgressAndChecksOwnership() throws Exception {
        createTopic("tp-edit").andExpect(status().isCreated());
        transitionTopic("tp-edit", "PUBLISHED").andExpect(status().isOk());
        JsonNode topic = json(mockMvc.perform(get("/api/v1/topics/tp-edit").header("Authorization", studentJwt)));
        long theory = topic.at("/steps/0/id").asLong();
        attempt("tp-edit", theory, Map.of());

        JsonNode authoring = json(mockMvc.perform(get("/api/v1/topics/tp-edit/authoring").header("Authorization", instructorJwt))
                .andExpect(jsonPath("$.steps[1].answer.correct[0]").value("a")));
        mockMvc.perform(get("/api/v1/topics/tp-edit/authoring").header("Authorization", studentJwt)).andExpect(status().isForbidden());

        // Đảo thứ tự: step lý thuyết (giữ id) xuống cuối, bỏ step code
        List<Object> steps = new java.util.ArrayList<>();
        steps.add(objectMapper.convertValue(authoring.at("/steps/1"), Map.class));
        steps.add(objectMapper.convertValue(authoring.at("/steps/0"), Map.class));
        Map<String, Object> body = new java.util.HashMap<>(topicBody("tp-edit"));
        body.put("steps", steps);
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(put("/api/v1/topics/tp-edit").header("Authorization", otherInstructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/topics/tp-edit").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[1].id").value(theory));

        mockMvc.perform(get("/api/v1/topics/tp-edit").header("Authorization", studentJwt))
                .andExpect(jsonPath("$.steps[1].passed").value(true))
                .andExpect(jsonPath("$.doneSteps").value(1));
    }

    @Test
    @DisplayName("Invalid step content is rejected with the step position; learners cannot author")
    void validation() throws Exception {
        Map<String, Object> bad = Map.of("slug", "tp-bad", "title", "Bad", "estMinutes", 5, "steps", List.of(
                Map.of("type", "MATCHING", "title", "Ghép", "content", Map.of("prompt", "p",
                        "left", List.of(Map.of("id", "1", "text", "a"), Map.of("id", "2", "text", "b")),
                        "right", List.of(Map.of("id", "x", "text", "c"), Map.of("id", "y", "text", "d"))),
                        "answer", Map.of("pairs", Map.of("1", "x")))));
        mockMvc.perform(post("/api/v1/topics").header("Authorization", instructorJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("steps[0]")));
        mockMvc.perform(post("/api/v1/topics").header("Authorization", studentJwt)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(topicBody("tp-x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/topics").header("Authorization", studentJwt)).andExpect(status().isForbidden());
        assertThat(json(mockMvc.perform(get("/api/v1/topics?search=bad").header("Authorization", instructorJwt)))).isEmpty();
    }

    // ------------------------------------------------------------------

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private Problem problem(String slug) {
        return problemRepository.save(Problem.builder().title(slug).slug(slug).description("d")
                .difficulty(Difficulty.EASY).status(ProblemStatus.PUBLISHED).createdBy(instructor).build());
    }

    private ResultActions createTopic(String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/topics").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(topicBody(slug))));
    }

    private ResultActions transitionTopic(String slug, String status) throws Exception {
        return mockMvc.perform(post("/api/v1/topics/" + slug + "/transitions").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions attempt(long stepId, Map<String, Object> answer) throws Exception {
        return attempt("tp-encapsulation", stepId, answer);
    }

    private ResultActions attempt(String slug, long stepId, Map<String, Object> answer) throws Exception {
        return mockMvc.perform(post("/api/v1/topics/" + slug + "/steps/" + stepId + "/attempts").header("Authorization", studentJwt)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("answer", answer))))
                .andExpect(status().isOk());
    }

    private ResultActions plan() throws Exception {
        return mockMvc.perform(get("/api/v1/study-plans/tp-plan").header("Authorization", studentJwt)).andExpect(status().isOk());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
