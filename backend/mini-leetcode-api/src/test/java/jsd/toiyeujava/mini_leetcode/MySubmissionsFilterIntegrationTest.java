package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.entity.*;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /submissions/my lọc theo verdict/ngôn ngữ ở server, phân trang, chỉ trả bài của user; /summary đếm toàn bộ. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MySubmissionsFilterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;

    private User alice;
    private String aliceToken;
    private Problem problem;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private Submission submit(User u, SubmissionLanguage lang, SubmissionStatus st, SubmissionVerdict v) {
        return submissionRepository.save(Submission.builder().user(u).problem(problem).language(lang)
                .codeStorageUri("minio://test/" + u.getUsername()).status(st).verdict(v).build());
    }

    private ResultActions my(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/submissions/my" + query).header("Authorization", aliceToken))
                .andExpect(status().isOk());
    }

    @BeforeEach
    void setUp() {
        User author = user("filter_author", Role.INSTRUCTOR);
        alice = user("filter_alice", Role.USER);
        User bob = user("filter_bob", Role.USER);
        aliceToken = "Bearer " + jwtTokenProvider.generateAccessToken(alice);
        problem = problemRepository.save(Problem.builder().title("Filter P").slug("filter-p").description("d")
                .difficulty(Difficulty.EASY).createdBy(author).build());

        submit(alice, SubmissionLanguage.JAVA, SubmissionStatus.COMPLETED, SubmissionVerdict.AC);
        submit(alice, SubmissionLanguage.JAVA, SubmissionStatus.COMPLETED, SubmissionVerdict.AC);
        submit(alice, SubmissionLanguage.PYTHON, SubmissionStatus.COMPLETED, SubmissionVerdict.AC);
        submit(alice, SubmissionLanguage.PYTHON, SubmissionStatus.COMPLETED, SubmissionVerdict.WA);
        submit(alice, SubmissionLanguage.JAVA, SubmissionStatus.COMPLETED, SubmissionVerdict.RE);
        submit(alice, SubmissionLanguage.JAVA, SubmissionStatus.FAILED, null);   // không có verdict nhưng FAILED -> RE
        submit(alice, SubmissionLanguage.PYTHON, SubmissionStatus.PENDING, null); // -> PENDING
        submit(bob, SubmissionLanguage.JAVA, SubmissionStatus.COMPLETED, SubmissionVerdict.AC);
    }

    @Test
    @DisplayName("Filters by verdict and language on the server and pages the result")
    void filtersAndPages() throws Exception {
        my("?verdict=AC&size=2").andExpect(jsonPath("$.data.page.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(2));
        my("?verdict=AC&language=PYTHON").andExpect(jsonPath("$.data.page.totalElements").value(1));
        my("?language=JAVA").andExpect(jsonPath("$.data.page.totalElements").value(4));
        my("?verdict=RE").andExpect(jsonPath("$.data.page.totalElements").value(2));
        my("?verdict=PENDING").andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
        // Không lọc: vẫn là toàn bộ bài của alice (không lẫn bài của bob).
        my("").andExpect(jsonPath("$.data.page.totalElements").value(7))
                .andExpect(jsonPath("$.data.content[*].username", containsInAnyOrder(
                        "filter_alice", "filter_alice", "filter_alice", "filter_alice",
                        "filter_alice", "filter_alice", "filter_alice")));
    }

    @Test
    @DisplayName("Rejects an unknown verdict with 400")
    void rejectsUnknownVerdict() throws Exception {
        mockMvc.perform(get("/api/v1/submissions/my?verdict=NOPE").header("Authorization", aliceToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Summary counts every submission of the user, not one page")
    void summaryCountsAll() throws Exception {
        mockMvc.perform(get("/api/v1/submissions/my/summary").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.AC").value(3))
                .andExpect(jsonPath("$.data.WA").value(1))
                .andExpect(jsonPath("$.data.RE").value(2))
                .andExpect(jsonPath("$.data.PENDING").value(1));
    }
}
