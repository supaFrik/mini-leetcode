package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.RegistrationMode;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContestAdminIntegrationTest {

    private static final String BASE = "/api/v1/admin/contests";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private ContestRepository contestRepository;
    @Autowired private ContestProblemRepository contestProblemRepository;
    @Autowired private ContestParticipantRepository participantRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @MockBean private StorageService storageService;

    private User instructor;
    private User sv01;
    private String adminToken;
    private String instructorToken;
    private String studentToken;
    private final List<Long> published = new ArrayList<>();
    private Long draftProblem;

    @BeforeEach
    void setUp() {
        User admin = user("boss", Role.ADMIN, UserStatus.ACTIVE);
        instructor = user("teacher", Role.INSTRUCTOR, UserStatus.ACTIVE);
        sv01 = user("sv01", Role.USER, UserStatus.ACTIVE);
        user("sv02", Role.USER, UserStatus.ACTIVE);
        user("sv03", Role.USER, UserStatus.ACTIVE);
        user("svblock", Role.USER, UserStatus.BLOCKED);
        adminToken = "Bearer " + jwt.generateAccessToken(admin);
        instructorToken = "Bearer " + jwt.generateAccessToken(instructor);
        studentToken = "Bearer " + jwt.generateAccessToken(sv01);
        for (int i = 1; i <= 3; i++) {
            published.add(problem("ct-p" + i, ProblemStatus.PUBLISHED));
        }
        draftProblem = problem("ct-draft", ProblemStatus.DRAFT);
    }

    // ---------------- Quyền ----------------

    @Test
    void permissionMatrix() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE).header("Authorization", studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).header("Authorization", studentToken).contentType(MediaType.APPLICATION_JSON)
                .content(json(body("X", tomorrow(8), tomorrow(10), List.of())))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE).header("Authorization", instructorToken)).andExpect(status().isOk());

        long id = createId(instructorToken, body("Perm", tomorrow(8), tomorrow(10), List.of()));
        // Xoá kỳ thi: chỉ ADMIN.
        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", instructorToken)).andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", adminToken)).andExpect(status().isNoContent());
        assertThat(contestRepository.findById(id)).isEmpty();
    }

    // ---------------- AC-CT-001 / 003 ----------------

    @Test
    void instructorCreatesScheduledContestWithAutoLabels() throws Exception {
        Map<String, Object> body = body("Giữa kỳ OOP", tomorrow(8), tomorrow(10),
                published.stream().map(id -> Map.<String, Object>of("problemId", id)).toList());
        body.put("maxViolationsAllowed", 3);

        JsonNode created = readJson(perform(post(BASE), instructorToken, body).andExpect(status().isCreated()));
        assertThat(created.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(created.get("slug").asText()).isEqualTo("giua-ky-oop");
        assertThat(created.get("createdBy").asText()).isEqualTo("teacher");
        assertThat(created.get("problems")).extracting(n -> n.get("label").asText()).containsExactly("A", "B", "C");

        // Hiện trong danh sách kỳ thi của sinh viên.
        mockMvc.perform(get("/api/v1/contests").header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Giữa kỳ OOP')].problems.length()").value(3));

        // Danh sách admin có số bài, lọc theo trạng thái và tìm theo tên.
        mockMvc.perform(get(BASE).param("status", "SCHEDULED").param("search", "giữa").header("Authorization", instructorToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].problemCount").value(3))
                .andExpect(jsonPath("$[0].participantCount").value(0));
        mockMvc.perform(get(BASE).param("status", "RUNNING").param("search", "giữa").header("Authorization", instructorToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createValidation() throws Exception {
        perform(post(BASE), instructorToken, body("Bad", tomorrow(10), tomorrow(10), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("End time must be after start time"));
        perform(post(BASE), instructorToken, body("Past", Instant.now().minus(1, ChronoUnit.HOURS), tomorrow(10), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Start time must be in the future"));
        perform(post(BASE), instructorToken, body("Draft", tomorrow(8), tomorrow(10),
                List.of(Map.of("problemId", draftProblem)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Problem 'ct-draft' is not published"));
        perform(post(BASE), instructorToken, body("Label", tomorrow(8), tomorrow(10),
                List.of(Map.of("problemId", published.get(0), "label", "TOOLONG")))).andExpect(status().isBadRequest());
        perform(post(BASE), instructorToken, body("Dup", tomorrow(8), tomorrow(10),
                List.of(Map.of("problemId", published.get(0), "label", "A"),
                        Map.of("problemId", published.get(1), "label", "a")))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Label 'A' is used more than once"));
    }

    @Test
    void scheduledContestEditsEverythingIncludingProblemOrder() throws Exception {
        long id = createId(instructorToken, body("Sched", tomorrow(8), tomorrow(10),
                List.of(Map.of("problemId", published.get(0)), Map.of("problemId", published.get(1)))));
        JsonNode current = readJson(mockMvc.perform(get(BASE + "/" + id).header("Authorization", instructorToken)));

        Map<String, Object> edit = body("Sched v2", tomorrow(9), tomorrow(11), List.of(
                Map.of("problemId", published.get(1), "label", "X1", "orderIndex", 0),
                Map.of("problemId", published.get(0), "label", "X2", "orderIndex", 1),
                Map.of("problemId", published.get(2), "orderIndex", 2)));
        edit.put("registrationMode", "INVITE_ONLY");
        edit.put("version", current.get("version").asLong());

        JsonNode saved = readJson(perform(put(BASE + "/" + id), instructorToken, edit).andExpect(status().isOk()));
        assertThat(saved.get("title").asText()).isEqualTo("Sched v2");
        assertThat(saved.get("registrationMode").asText()).isEqualTo("INVITE_ONLY");
        assertThat(saved.get("problems")).extracting(n -> n.get("problemId").asLong() + ":" + n.get("label").asText())
                .containsExactly(published.get(1) + ":X1", published.get(0) + ":X2", published.get(2) + ":C");
        assertThat(saved.get("version").asLong()).isGreaterThan(current.get("version").asLong());

        // Phiên bản cũ → 409 "tải lại".
        perform(put(BASE + "/" + id), instructorToken, edit)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Contest was changed by someone else, reload"));
    }

    // ---------------- AC-CT-002 ----------------

    @Test
    void runningContestOnlyAcceptsALaterEndTime() throws Exception {
        Contest running = contest("Running", now().minusMinutes(30), now().plusMinutes(60), ContestStatus.SCHEDULED);
        addProblems(running, published.get(0), published.get(1));

        JsonNode detail = readJson(mockMvc.perform(get(BASE + "/" + running.getId()).header("Authorization", instructorToken)));
        assertThat(detail.get("status").asText()).isEqualTo("RUNNING");

        Map<String, Object> extend = sameAs(detail);
        Instant newEnd = running.getEndTime().plusMinutes(30).toInstant(ZoneOffset.UTC);
        extend.put("endTime", newEnd);
        JsonNode saved = readJson(perform(put(BASE + "/" + running.getId()), instructorToken, extend).andExpect(status().isOk()));
        assertThat(saved.get("endTime").asText()).startsWith(LocalDateTime.ofInstant(newEnd, ZoneOffset.UTC).toString().substring(0, 16));

        // Bỏ bài B khi đang thi → 409.
        Map<String, Object> removeB = sameAs(saved);
        removeB.put("problems", List.of(Map.of("problemId", published.get(0), "label", "A")));
        perform(put(BASE + "/" + running.getId()), instructorToken, removeB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only the end time can be changed while the contest is running"));

        // Rút ngắn giờ kết thúc cũng bị từ chối.
        Map<String, Object> shorten = sameAs(saved);
        shorten.put("endTime", now().plusMinutes(5).toInstant(ZoneOffset.UTC));
        perform(put(BASE + "/" + running.getId()), instructorToken, shorten)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The end time can only be extended while the contest is running"));

        // Đang thi không xoá được.
        mockMvc.perform(delete(BASE + "/" + running.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only scheduled contests without submissions can be deleted"));
    }

    @Test
    void completedContestIsReadOnly() throws Exception {
        Contest done = contest("Done", now().minusDays(1), now().minusDays(1).plusHours(2), ContestStatus.COMPLETED);
        Map<String, Object> edit = body("Done", now().minusDays(1).toInstant(ZoneOffset.UTC),
                now().plusDays(1).toInstant(ZoneOffset.UTC), null);
        perform(put(BASE + "/" + done.getId()), instructorToken, edit)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Contest has ended"));
        perform(post(BASE + "/" + done.getId() + "/participants"), instructorToken, Map.of("entries", "sv02"))
                .andExpect(status().isConflict());
    }

    // ---------------- AC-CT-004 ----------------

    @Test
    void inviteOnlyContestAcceptsOnlyListedUsers() throws Exception {
        Map<String, Object> body = body("Invite", tomorrow(8), tomorrow(10), List.of(Map.of("problemId", published.get(0))));
        body.put("registrationMode", "INVITE_ONLY");
        long id = createId(instructorToken, body);

        perform(post(BASE + "/" + id + "/participants"), instructorToken,
                Map.of("entries", "sv01, sv02, khongtontai\nsvblock\nsv01@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.lines[0].outcome").value("ADDED"))
                .andExpect(jsonPath("$.lines[1].outcome").value("ADDED"))
                .andExpect(jsonPath("$.lines[2].outcome").value("NOT_FOUND"))
                .andExpect(jsonPath("$.lines[3].outcome").value("BLOCKED"))
                .andExpect(jsonPath("$.lines[4].outcome").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.lines[4].username").value("sv01"));

        String sv03 = "Bearer " + jwt.generateAccessToken(userRepository.findByUsername("sv03").orElseThrow());
        mockMvc.perform(post("/api/v1/contests/" + id + "/register").header("Authorization", sv03))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This contest is invite-only"));
        // Người đã có trong danh sách bấm đăng ký lại vẫn OK.
        mockMvc.perform(post("/api/v1/contests/" + id + "/register").header("Authorization", studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/" + id + "/participants").header("Authorization", instructorToken))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("sv01"))
                .andExpect(jsonPath("$[0].status").value("REGISTERED"));
    }

    @Test
    void bulkAddRejectsMoreThan500Entries() throws Exception {
        long id = createId(instructorToken, body("Many", tomorrow(8), tomorrow(10), List.of()));
        String entries = String.join("\n", java.util.stream.IntStream.range(0, 501).mapToObj(i -> "u" + i).toList());
        perform(post(BASE + "/" + id + "/participants"), instructorToken, Map.of("entries", entries))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeParticipantOnlyWithoutSubmissions() throws Exception {
        Contest running = contest("Remove", now().minusMinutes(10), now().plusHours(1), ContestStatus.SCHEDULED);
        addProblems(running, published.get(0));
        User sv02 = userRepository.findByUsername("sv02").orElseThrow();
        for (User u : List.of(sv01, sv02)) {
            participantRepository.save(ContestParticipant.builder().contest(running).user(u)
                    .status(ParticipantStatus.REGISTERED).violationCount(0).score(0).penaltyTimeMinutes(0).build());
        }
        submissionRepository.save(Submission.builder().user(sv01)
                .problem(problemRepository.findById(published.get(0)).orElseThrow())
                .contestId(running.getId()).language(SubmissionLanguage.JAVA).codeStorageUri("minio://x")
                .status(SubmissionStatus.PENDING).build());

        mockMvc.perform(delete(BASE + "/" + running.getId() + "/participants/" + sv01.getId()).header("Authorization", instructorToken))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(BASE + "/" + running.getId() + "/participants/" + sv02.getId()).header("Authorization", instructorToken))
                .andExpect(status().isNoContent());
        assertThat(participantRepository.findByContestId(running.getId())).hasSize(1);
    }

    // ---------------- AC-CT-005 ----------------

    @Test
    void problemUsedByActiveContestCannotLeavePublished() throws Exception {
        long id = createId(instructorToken, body("Guard", tomorrow(8), tomorrow(10), List.of(Map.of("problemId", published.get(0)))));
        mockMvc.perform(post("/api/v1/problems/" + published.get(0) + "/transitions").header("Authorization", instructorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Problem is used by contest 'Guard'"));
        mockMvc.perform(delete("/api/v1/problems/" + published.get(0)).header("Authorization", adminToken))
                .andExpect(status().isConflict());

        // Kỳ thi đã kết thúc thì không giữ bài nữa.
        Contest c = contestRepository.findById(id).orElseThrow();
        c.setStatus(ContestStatus.COMPLETED);
        contestRepository.saveAndFlush(c);
        mockMvc.perform(post("/api/v1/problems/" + published.get(0) + "/transitions").header("Authorization", instructorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());
    }

    // ---------------- AC-CT-006 ----------------

    @Test
    void deleteRemovesProblemsAndParticipantsButNotWhenThereAreSubmissions() throws Exception {
        long id = createId(instructorToken, body("Del", tomorrow(8), tomorrow(10), List.of(Map.of("problemId", published.get(0)))));
        perform(post(BASE + "/" + id + "/participants"), instructorToken, Map.of("entries", "sv01")).andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", adminToken)).andExpect(status().isNoContent());
        assertThat(contestRepository.findById(id)).isEmpty();
        assertThat(contestProblemRepository.findByContestIdOrderByOrderIndexAsc(id)).isEmpty();
        assertThat(participantRepository.findByContestId(id)).isEmpty();

        long id2 = createId(instructorToken, body("Del2", tomorrow(8), tomorrow(10), List.of(Map.of("problemId", published.get(0)))));
        submissionRepository.save(Submission.builder().user(sv01)
                .problem(problemRepository.findById(published.get(0)).orElseThrow())
                .contestId(id2).language(SubmissionLanguage.JAVA).codeStorageUri("minio://y")
                .status(SubmissionStatus.PENDING).build());
        mockMvc.perform(delete(BASE + "/" + id2).header("Authorization", adminToken)).andExpect(status().isConflict());
        mockMvc.perform(delete(BASE + "/999999").header("Authorization", adminToken)).andExpect(status().isNotFound());
    }

    // ---------------- helpers ----------------

    private User user(String name, Role role, UserStatus status) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(status).build());
    }

    private Long problem(String title, ProblemStatus status) {
        return problemRepository.save(Problem.builder().title(title).slug(title).description("d")
                .difficulty(Difficulty.EASY).status(status).createdBy(instructor).build()).getId();
    }

    private Contest contest(String title, LocalDateTime start, LocalDateTime end, ContestStatus status) {
        return contestRepository.saveAndFlush(Contest.builder().title(title).slug("slug-" + title.toLowerCase())
                .startTime(start).endTime(end).status(status).registrationMode(RegistrationMode.OPEN)
                .createdBy(instructor).build());
    }

    private void addProblems(Contest contest, Long... problemIds) {
        for (int i = 0; i < problemIds.length; i++) {
            contestProblemRepository.save(ContestProblem.builder().contest(contest)
                    .problem(problemRepository.findById(problemIds[i]).orElseThrow())
                    .label(String.valueOf((char) ('A' + i))).orderIndex(i).build());
        }
    }

    /** Body PUT giữ nguyên mọi trường như detail hiện tại. */
    private Map<String, Object> sameAs(JsonNode detail) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("title", detail.get("title").asText());
        b.put("startTime", detail.get("startTime").asText());
        b.put("endTime", detail.get("endTime").asText());
        b.put("scoringPolicy", detail.get("scoringPolicy").asText());
        b.put("maxViolationsAllowed", detail.get("maxViolationsAllowed").asInt());
        b.put("registrationMode", detail.get("registrationMode").asText());
        List<Map<String, Object>> problems = new ArrayList<>();
        detail.get("problems").forEach(p -> problems.add(Map.of("problemId", p.get("problemId").asLong(),
                "label", p.get("label").asText(), "orderIndex", p.get("orderIndex").asInt())));
        b.put("problems", problems);
        b.put("version", detail.get("version").asLong());
        return b;
    }

    private Map<String, Object> body(String title, Instant start, Instant end, List<?> problems) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("title", title);
        b.put("startTime", start);
        b.put("endTime", end);
        b.put("scoringPolicy", "ACM_ICPC");
        if (problems != null) {
            b.put("problems", problems);
        }
        return b;
    }

    private long createId(String token, Map<String, Object> body) throws Exception {
        return readJson(perform(post(BASE), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                  String token, Object body) throws Exception {
        return mockMvc.perform(builder.header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private JsonNode readJson(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static Instant tomorrow(int hourUtc) {
        return LocalDateTime.now(ZoneOffset.UTC).plusDays(1).withHour(hourUtc).withMinute(0).withSecond(0).withNano(0)
                .toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
