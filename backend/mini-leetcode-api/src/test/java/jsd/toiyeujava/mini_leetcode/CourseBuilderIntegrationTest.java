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
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
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

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Course Builder (specs/course-builder.spec.md): clone, unlock, publish-check, publish, learners, optimistic lock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseBuilderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private SubmissionRepository submissionRepository;

    @MockBean private SubmissionProducer submissionProducer;
    @MockBean private StorageService storageService;

    private User instructor;
    private User student;
    private String instructorJwt;
    private String studentJwt;
    private final AtomicInteger uploads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        when(storageService.uploadFile(anyString(), any(), anyLong(), any()))
                .thenAnswer(inv -> "minio://copy/" + uploads.incrementAndGet());
        when(storageService.uploadFile(any(), any(), any(), anyLong(), any())).thenReturn("minio://submissions/test");
        when(storageService.downloadFileByUri(anyString()))
                .thenAnswer(inv -> new ByteArrayInputStream(("content of " + inv.getArgument(0)).getBytes()));
        instructor = user("cb_teacher", Role.INSTRUCTOR);
        student = user("cb_student", Role.USER);
        instructorJwt = bearer(instructor);
        studentJwt = bearer(student);
    }

    @Test
    @DisplayName("AC-CB-01: clone copies statement, grading, reference and every test case with its label, as DRAFT")
    void cloneCopiesEverything() throws Exception {
        Problem source = problem("cb-stage-1", ProblemStatus.PUBLISHED);
        source.setGradingConfig("{\"rules\":[]}");
        source.setReferenceSolution("[{\"path\":\"Main.java\",\"content\":\"class Main{}\"}]");
        source.setTags(new java.util.ArrayList<>(List.of("oop")));
        problemRepository.save(source);
        testCase(source, false, "stage 1: deposit");
        testCase(source, true, "stage 1: balance");

        JsonNode copy = json(mockMvc.perform(post("/api/v1/admin/problems/" + source.getId() + "/clone")
                        .header("Authorization", instructorJwt).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stage 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value("stage-2")));
        Problem cloned = problemRepository.findById(copy.get("id").asLong()).orElseThrow();
        assertThat(cloned.getGradingConfig()).isEqualTo(source.getGradingConfig());
        assertThat(cloned.getReferenceSolution()).isEqualTo(source.getReferenceSolution());
        assertThat(cloned.getDescription()).isEqualTo(source.getDescription());
        List<TestCase> cases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(cloned.getId());
        assertThat(cases).extracting(TestCase::getLabel).containsExactly("stage 1: deposit", "stage 1: balance");
        assertThat(cases).extracting(TestCase::getIsHidden).containsExactly(false, true);
        assertThat(cases).allMatch(tc -> tc.getInputFileUri().startsWith("minio://copy/"));

        // Clone thứ hai cùng tiêu đề vẫn được: slug tự thêm hậu tố.
        mockMvc.perform(post("/api/v1/admin/problems/" + source.getId() + "/clone")
                        .header("Authorization", instructorJwt).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stage 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("stage-2-1"));
        mockMvc.perform(post("/api/v1/admin/problems/" + source.getId() + "/clone").header("Authorization", studentJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A draft plan saves an empty project; publish-check lists errors and publish changes nothing")
    void publishCheckBlocks() throws Exception {
        Problem noTests = problem("cb-no-tests", ProblemStatus.DRAFT);
        createPlan("cb-plan", List.of(item("PROJECT", "Bank", noTests.getId()), item("PROJECT", "Empty")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/study-plans/cb-plan/publish-check").header("Authorization", instructorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Bank · stage 1: add at least one public test case")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("\"Empty\" has no stage")))
                .andExpect(jsonPath("$.problemsToPublish[0]").value(noTests.getId()));
        mockMvc.perform(post("/api/v1/study-plans/cb-plan/publish").header("Authorization", instructorJwt))
                .andExpect(status().isConflict());
        assertThat(problemRepository.findById(noTests.getId()).orElseThrow().getStatus()).isEqualTo(ProblemStatus.DRAFT);
        plan("cb-plan").andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("AC-CB-05: publish moves the plan, its DRAFT / REVIEWING / ARCHIVED problems and draft topics to PUBLISHED")
    void publishEverything() throws Exception {
        Problem draft = problem("cb-p-draft", ProblemStatus.DRAFT);
        Problem reviewing = problem("cb-p-review", ProblemStatus.REVIEWING);
        Problem archived = problem("cb-p-archived", ProblemStatus.ARCHIVED);
        for (Problem p : List.of(draft, reviewing, archived)) {
            testCase(p, false, null);
        }
        long topicId = createTopic("cb-topic");
        Map<String, Object> project = new HashMap<>(item("PROJECT", "Bank", draft.getId(), reviewing.getId(), archived.getId()));
        project.put("stageTopics", List.of(List.of(topicId)));
        createPlan("cb-live", List.of(project)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/study-plans/cb-live/publish-check").header("Authorization", instructorJwt))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.problemsToPublish.length()").value(3))
                .andExpect(jsonPath("$.topicsToPublish[0]").value(topicId));
        mockMvc.perform(post("/api/v1/study-plans/cb-live/publish").header("Authorization", instructorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].stages[2].problemStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].stages[0].topics[0].status").value("PUBLISHED"));
        assertThat(problemRepository.findAllById(List.of(draft.getId(), reviewing.getId(), archived.getId())))
                .allMatch(p -> p.getStatus() == ProblemStatus.PUBLISHED);
    }

    @Test
    @DisplayName("A draft topic owned by another instructor blocks publishing")
    void foreignDraftTopicBlocks() throws Exception {
        Problem p = problem("cb-foreign", ProblemStatus.PUBLISHED);
        testCase(p, false, null);
        String otherJwt = bearer(user("cb_other", Role.INSTRUCTOR));
        long topicId = createTopic("cb-foreign-topic", otherJwt);
        Map<String, Object> project = new HashMap<>(item("PROJECT", "Bank", p.getId()));
        project.put("stageTopics", List.of(List.of(topicId)));
        createPlan("cb-foreign-plan", List.of(project)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/study-plans/cb-foreign-plan/publish-check").header("Authorization", instructorJwt))
                .andExpect(jsonPath("$.errors[0].node").value("topic"))
                .andExpect(jsonPath("$.errors[0].message", containsString("only its author can publish it")));
    }

    @Test
    @DisplayName("AC-CB-07: unlock sends a live stage back to DRAFT, learners cannot submit it, counts are reported")
    void unlockLiveStage() throws Exception {
        Problem s1 = problem("cb-u-1", ProblemStatus.PUBLISHED);
        Problem s2 = problem("cb-u-2", ProblemStatus.PUBLISHED);
        createPlan("cb-unlock", List.of(item("PROJECT", "Bank", s1.getId(), s2.getId()))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/study-plans/cb-unlock/transitions").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());
        submission(s1, SubmissionVerdict.WA);

        plan("cb-unlock").andExpect(jsonPath("$.learnerCount").value(1))
                .andExpect(jsonPath("$.items[0].stages[0].learnersOnStage").value(1))
                .andExpect(jsonPath("$.items[0].stages[1].learnersOnStage").value(0));
        mockMvc.perform(get("/api/v1/study-plans/cb-unlock").header("Authorization", studentJwt))
                .andExpect(jsonPath("$.learnerCount").doesNotExist())
                .andExpect(jsonPath("$.items[0].stages[0].problemStatus").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/problems/" + s1.getId() + "/unlock").header("Authorization", instructorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(post("/api/v1/problems/" + s1.getId() + "/submit").header("Authorization", studentJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(codeBody(s1.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_LOCKED"))
                .andExpect(jsonPath("$.detail", containsString("being updated")));
        mockMvc.perform(post("/api/v1/admin/problems/" + s1.getId() + "/unlock").header("Authorization", instructorJwt))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Optimistic lock: a save based on a stale updatedAt is rejected")
    void staleSaveRejected() throws Exception {
        JsonNode created = json(createPlan("cb-lock", List.of()).andExpect(status().isCreated()));
        String stamp = created.get("updatedAt").asText();
        save("cb-lock", "First", stamp).andExpect(status().isOk());
        // Lưu lần nữa dựa trên dấu thời gian cũ (tab khác) ⇒ 409. Đẩy lùi 1 phút để chắc chắn khác giây.
        String stale = java.time.Instant.parse(stamp).minusSeconds(60).toString();
        save("cb-lock", "Second", stale).andExpect(status().isConflict());
        save("cb-lock", "No check", null).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String bearer(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private Problem problem(String slug, ProblemStatus status) {
        return problemRepository.save(Problem.builder().title(slug).slug(slug).description("Statement of " + slug)
                .difficulty(Difficulty.EASY).status(status).createdBy(instructor).build());
    }

    private void testCase(Problem p, boolean hidden, String label) {
        int index = (int) testCaseRepository.countByProblemId(p.getId());
        testCaseRepository.save(TestCase.builder().problem(p).inputFileUri("minio://in/" + p.getSlug() + index)
                .expectedOutputUri("minio://out/" + p.getSlug() + index).isHidden(hidden).orderIndex(index)
                .fileSizeBytes(10L).label(label).build());
    }

    private void submission(Problem p, SubmissionVerdict verdict) {
        submissionRepository.save(Submission.builder().user(student).problem(p).language(SubmissionLanguage.JAVA)
                .codeStorageUri("minio://test").status(SubmissionStatus.COMPLETED).verdict(verdict).build());
    }

    private long createTopic(String slug) throws Exception {
        return createTopic(slug, instructorJwt);
    }

    private long createTopic(String slug, String auth) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("slug", slug, "title", slug, "estMinutes", 5,
                "steps", List.of(Map.of("type", "THEORY", "title", "Theory", "content", Map.of("markdown", "## A\n\ntext")))));
        return json(mockMvc.perform(post("/api/v1/topics").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> item(String kind, String title, Long... problemIds) {
        return Map.of("kind", kind, "title", title, "problemIds", List.of(problemIds));
    }

    private ResultActions createPlan(String slug, List<Map<String, Object>> items) throws Exception {
        return mockMvc.perform(post("/api/v1/study-plans").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("slug", slug, "title", slug, "items", items))));
    }

    private ResultActions save(String slug, String title, String expectedUpdatedAt) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("slug", slug, "title", title, "items", List.of()));
        if (expectedUpdatedAt != null) {
            body.put("expectedUpdatedAt", expectedUpdatedAt);
        }
        return mockMvc.perform(put("/api/v1/study-plans/" + slug).header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions plan(String slug) throws Exception {
        return mockMvc.perform(get("/api/v1/study-plans/" + slug).header("Authorization", instructorJwt))
                .andExpect(status().isOk());
    }

    private String codeBody(Long problemId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("problemId", problemId, "language", "JAVA",
                "code", "public class Main { public static void main(String[] a) {} }"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
