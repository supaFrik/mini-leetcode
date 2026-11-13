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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Study Plan + Personal Access Token (specs/study-plan.spec.md, slice 1). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudyPlanIntegrationTest {

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
    private String studentJwt;
    private Problem stage1;
    private Problem stage2;
    private Problem single;

    @BeforeEach
    void setUp() throws Exception {
        when(storageService.uploadFile(any(), any(), any(), anyLong(), any())).thenReturn("minio://submissions/test");
        instructor = user("sp_teacher", Role.INSTRUCTOR);
        student = user("sp_student", Role.USER);
        instructorJwt = "Bearer " + jwtTokenProvider.generateAccessToken(instructor);
        studentJwt = "Bearer " + jwtTokenProvider.generateAccessToken(student);
        stage1 = problem("sp-bank-1", ProblemStatus.PUBLISHED);
        stage2 = problem("sp-bank-2", ProblemStatus.PUBLISHED);
        single = problem("sp-two-sum", ProblemStatus.PUBLISHED);

        createPlan("sp-oop", List.of(
                item("PROJECT", "Mini Bank", stage1.getId(), stage2.getId()),
                item("PROBLEM", "Two Sum", single.getId())))
                .andExpect(status().isCreated());
        transition("sp-oop", "PUBLISHED").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- stage lock

    @Test
    @DisplayName("AC-003: a locked stage is rejected on every submit path and no submission is stored")
    void lockedStageRejectedOnAllPaths() throws Exception {
        String body = codeBody(stage2.getId(), null);
        expectLocked(mockMvc.perform(post("/api/v1/problems/" + stage2.getId() + "/submit")
                .header("Authorization", studentJwt).contentType(MediaType.APPLICATION_JSON).content(body)));
        expectLocked(mockMvc.perform(post("/api/v1/problems/" + stage2.getId() + "/run")
                .header("Authorization", studentJwt).contentType(MediaType.APPLICATION_JSON).content(body)));
        expectLocked(mockMvc.perform(post("/api/v1/submissions")
                .header("Authorization", studentJwt).contentType(MediaType.APPLICATION_JSON).content(body)));
        expectLocked(mockMvc.perform(multipart("/api/v1/problems/" + stage2.getId() + "/submit-multipart")
                .file(new MockMultipartFile("files", "Main.java", "text/plain", "public class Main {}".getBytes()))
                .param("language", "JAVA")
                .header("Authorization", studentJwt)));

        assertThat(submissionRepository.findAll()).noneMatch(s -> s.getProblem().getId().equals(stage2.getId()));
    }

    @Test
    @DisplayName("Stage 2 opens after an official AC on stage 1; a quick-run AC does not open it")
    void stageOpensAfterAc() throws Exception {
        save(stage1, SubmissionVerdict.AC, true); // Run AC: không tính
        expectLocked(submitJson(stage2, studentJwt));
        plan(studentJwt).andExpect(jsonPath("$.items[0].stages[0].status").value("NEVER"))
                .andExpect(jsonPath("$.items[0].stages[1].status").value("LOCKED"));

        save(stage1, SubmissionVerdict.AC, false);
        submitJson(stage2, studentJwt).andExpect(status().isAccepted());
        plan(studentJwt).andExpect(jsonPath("$.items[0].stages[0].status").value("DONE"))
                .andExpect(jsonPath("$.items[0].stages[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.doneStages").value(1))
                .andExpect(jsonPath("$.stageCount").value(3));
    }

    @Test
    @DisplayName("Review fix: a contestId for a contest that does not contain the problem does not bypass the lock")
    void contestIdDoesNotBypassLock() throws Exception {
        expectLocked(mockMvc.perform(post("/api/v1/problems/" + stage2.getId() + "/submit")
                .header("Authorization", studentJwt).contentType(MediaType.APPLICATION_JSON)
                .content(codeBody(stage2.getId(), 999L))));
    }

    @Test
    @DisplayName("Review fix: draft / archived plans neither lock stages nor hide problems")
    void unpublishedPlanDoesNotLock() throws Exception {
        transition("sp-oop", "ARCHIVED").andExpect(status().isOk());
        submitJson(stage2, studentJwt).andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/problems?size=100").header("Authorization", studentJwt))
                .andExpect(jsonPath("$.content[*].slug", hasItem("sp-bank-2")));
    }

    @Test
    @DisplayName("Review fix: a published plan cannot be emptied; an archived stage can stay while editing")
    void updateRules() throws Exception {
        mockMvc.perform(put("/api/v1/study-plans/sp-oop").header("Authorization", instructorJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(planBody("sp-oop", List.of())))
                .andExpect(status().isConflict());

        stage1.setStatus(ProblemStatus.ARCHIVED); // đang bảo trì
        problemRepository.save(stage1);
        mockMvc.perform(put("/api/v1/study-plans/sp-oop").header("Authorization", instructorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("sp-oop", List.of(item("PROJECT", "Mini Bank v2", stage1.getId(), stage2.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Mini Bank v2"))
                .andExpect(jsonPath("$.items[0].stages[0].published").value(false));
    }

    @Test
    @DisplayName("Instructors can submit any stage of their plan to try it")
    void instructorBypassesLock() throws Exception {
        submitJson(stage2, instructorJwt).andExpect(status().isAccepted());
    }

    // ---------------------------------------------------------------- visibility

    @Test
    @DisplayName("AC-010: project stages are hidden from /problems for students, single problems stay")
    void projectProblemsHiddenFromCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/problems?size=100").header("Authorization", studentJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("sp-bank-1"))))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("sp-bank-2"))))
                .andExpect(jsonPath("$.content[*].slug", hasItem("sp-two-sum")));
        mockMvc.perform(get("/api/v1/problems?size=100").header("Authorization", instructorJwt))
                .andExpect(jsonPath("$.content[*].slug", hasItem("sp-bank-2")));
        // Vẫn mở trực tiếp được (plan / plugin cần đề bài).
        mockMvc.perform(get("/api/v1/problems/sp-bank-2").header("Authorization", studentJwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Plans expose their author so the authoring UI can decide who may edit")
    void plansExposeAuthor() throws Exception {
        mockMvc.perform(get("/api/v1/study-plans").header("Authorization", studentJwt))
                .andExpect(jsonPath("$[0].createdByUsername").value("sp_teacher"))
                .andExpect(jsonPath("$[0].createdById").value(instructor.getId()));
        plan(studentJwt).andExpect(jsonPath("$.createdByUsername").value("sp_teacher"));
    }

    @Test
    @DisplayName("A draft plan is invisible to students (404) but visible to staff")
    void draftPlanHidden() throws Exception {
        transition("sp-oop", "DRAFT").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/study-plans/sp-oop").header("Authorization", studentJwt))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/study-plans").header("Authorization", studentJwt))
                .andExpect(jsonPath("$[*].slug", not(hasItem("sp-oop"))));
        mockMvc.perform(get("/api/v1/study-plans/sp-oop").header("Authorization", instructorJwt))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- authoring

    @Test
    @DisplayName("Authoring rules: role, ownership, draft problems allowed, one plan per problem, no empty publish")
    void authoringRules() throws Exception {
        Problem draft = problem("sp-draft", ProblemStatus.DRAFT);
        Problem free = problem("sp-free", ProblemStatus.PUBLISHED);

        mockMvc.perform(post("/api/v1/study-plans").header("Authorization", studentJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(planBody("sp-x", List.of(item("PROBLEM", "x", free.getId())))))
                .andExpect(status().isForbidden());
        // Course Builder: đề DRAFT gắn được vào plan (publish cùng plan sau).
        createPlan("sp-with-draft", List.of(item("PROBLEM", "x", draft.getId()))).andExpect(status().isCreated());
        createPlan("sp-x", List.of(item("PROBLEM", "x", stage1.getId()))).andExpect(status().isConflict());
        Problem free2 = problem("sp-free-2", ProblemStatus.PUBLISHED);
        createPlan("sp-x", List.of(item("PROBLEM", "x", free.getId(), free2.getId()))).andExpect(status().isBadRequest());
        createPlan("sp-empty", List.of()).andExpect(status().isCreated());
        transition("sp-empty", "PUBLISHED").andExpect(status().isConflict());

        String otherJwt = "Bearer " + jwtTokenProvider.generateAccessToken(user("sp_other_teacher", Role.INSTRUCTOR));
        mockMvc.perform(put("/api/v1/study-plans/sp-oop").header("Authorization", otherJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(planBody("sp-oop", List.of())))
                .andExpect(status().isForbidden());

        // Tác giả sắp xếp lại: đề cũ được dùng lại trong chính plan đó.
        mockMvc.perform(put("/api/v1/study-plans/sp-oop").header("Authorization", instructorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody("sp-oop", List.of(item("PROJECT", "Mini Bank", stage2.getId(), stage1.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stages[0].slug").value("sp-bank-2"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    // ---------------------------------------------------------------- personal access tokens

    @Test
    @DisplayName("PAT: created once, works on the allowlist, blocked elsewhere and for contests, 401 after revoke")
    void personalAccessTokenFlow() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v1/me/tokens").header("Authorization", studentJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"IntelliJ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", startsWith("mlc_pat_")))
                .andExpect(jsonPath("$.info.name").value("IntelliJ")));
        String pat = "Bearer " + created.get("token").asText();
        long tokenId = created.get("info").get("id").asLong();

        mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", studentJwt))
                .andExpect(jsonPath("$[0].name").value("IntelliJ"))
                .andExpect(jsonPath("$[0].token").doesNotExist());

        plan(pat).andExpect(jsonPath("$.slug").value("sp-oop"));
        Submission mine = save(stage1, SubmissionVerdict.WA, false);
        Submission theirs = submissionRepository.save(Submission.builder().user(instructor).problem(stage1)
                .language(SubmissionLanguage.JAVA).codeStorageUri("minio://x").status(SubmissionStatus.COMPLETED).build());
        mockMvc.perform(get("/api/v1/submissions/" + mine.getId()).header("Authorization", pat)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/submissions/" + theirs.getId()).header("Authorization", pat)).andExpect(status().isForbidden());
        submitJson(stage1, pat).andExpect(status().isAccepted());

        // Ngoài allowlist: quản lý token, danh sách đề, kỳ thi.
        expectPatForbidden(mockMvc.perform(post("/api/v1/me/tokens").header("Authorization", pat)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")));
        expectPatForbidden(mockMvc.perform(get("/api/v1/problems").header("Authorization", pat)));
        expectPatForbidden(mockMvc.perform(get("/api/v1/submissions/my").header("Authorization", pat)));
        expectPatForbidden(mockMvc.perform(post("/api/v1/problems/" + single.getId() + "/submit").header("Authorization", pat)
                .contentType(MediaType.APPLICATION_JSON).content(codeBody(single.getId(), 999L))));

        mockMvc.perform(delete("/api/v1/me/tokens/" + tokenId).header("Authorization", studentJwt))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/study-plans/sp-oop").header("Authorization", pat))
                .andExpect(status().isUnauthorized());
        // Kể cả endpoint công khai: token thu hồi phải báo 401 để plugin yêu cầu token mới.
        mockMvc.perform(get("/api/v1/problems/sp-bank-1").header("Authorization", pat))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAT_INVALID"));
    }

    @Test
    @DisplayName("PAT: at most 5 active tokens, other users cannot revoke mine")
    void tokenLimits() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/me/tokens").header("Authorization", studentJwt)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"t" + i + "\"}")).andExpect(status().isCreated());
        }
        JsonNode list = json(mockMvc.perform(post("/api/v1/me/tokens").header("Authorization", studentJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"t6\"}")).andExpect(status().isConflict()));
        assertThat(list).isNotNull();
        long id = json(mockMvc.perform(get("/api/v1/me/tokens").header("Authorization", studentJwt))).get(0).get("id").asLong();
        mockMvc.perform(delete("/api/v1/me/tokens/" + id).header("Authorization", instructorJwt))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private Problem problem(String slug, ProblemStatus status) {
        return problemRepository.save(Problem.builder().title(slug).slug(slug).description("d")
                .difficulty(Difficulty.EASY).status(status).createdBy(instructor).build());
    }

    private Submission save(Problem p, SubmissionVerdict verdict, boolean quickRun) {
        return submissionRepository.save(Submission.builder().user(student).problem(p).language(SubmissionLanguage.JAVA)
                .codeStorageUri("minio://test").status(SubmissionStatus.COMPLETED).verdict(verdict).quickRun(quickRun).build());
    }

    private Map<String, Object> item(String kind, String title, Long... problemIds) {
        return Map.of("kind", kind, "title", title, "problemIds", List.of(problemIds));
    }

    private String planBody(String slug, List<Map<String, Object>> items) throws Exception {
        return objectMapper.writeValueAsString(Map.of("slug", slug, "title", slug, "items", items));
    }

    private ResultActions createPlan(String slug, List<Map<String, Object>> items) throws Exception {
        return mockMvc.perform(post("/api/v1/study-plans").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content(planBody(slug, items)));
    }

    private ResultActions transition(String slug, String status) throws Exception {
        return mockMvc.perform(post("/api/v1/study-plans/" + slug + "/transitions").header("Authorization", instructorJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions plan(String auth) throws Exception {
        return mockMvc.perform(get("/api/v1/study-plans/sp-oop").header("Authorization", auth)).andExpect(status().isOk());
    }

    private String codeBody(Long problemId, Long contestId) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("problemId", problemId, "language", "JAVA",
                "code", "public class Main { public static void main(String[] a) {} }"));
        if (contestId != null) {
            body.put("contestId", contestId);
        }
        return objectMapper.writeValueAsString(body);
    }

    private ResultActions submitJson(Problem p, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/problems/" + p.getId() + "/submit").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(codeBody(p.getId(), null)));
    }

    private void expectLocked(ResultActions result) throws Exception {
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STAGE_LOCKED"));
    }

    private void expectPatForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PAT_FORBIDDEN"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
