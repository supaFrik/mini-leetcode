package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.entity.*;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.entity.Tag;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.repository.TagRepository;
import jsd.toiyeujava.mini_leetcode.ranking.service.RankingService;
import jsd.toiyeujava.mini_leetcode.ranking.service.RankingService.RankingRow;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1c nhóm chủ đề, P1b bảng xếp hạng và trang chính sách. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TagsRankingPagesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private RankingService rankingService;
    @Autowired private TagRepository tagRepository;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private String token(User u) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(u);
    }

    private Problem problem(String slug, User author, List<String> tags) {
        Problem p = Problem.builder().title(slug).slug(slug).description("d")
                .difficulty(Difficulty.EASY).status(ProblemStatus.PUBLISHED).createdBy(author).build();
        p.setTags(new ArrayList<>(tags));
        return problemRepository.save(p);
    }

    private void submit(User u, Problem p, SubmissionVerdict verdict, boolean quickRun) {
        submissionRepository.save(Submission.builder().user(u).problem(p).language(SubmissionLanguage.JAVA)
                .codeStorageUri("minio://x").status(SubmissionStatus.COMPLETED).verdict(verdict).quickRun(quickRun).build());
    }

    @Test
    @DisplayName("V17 pre-registers seed tags with their group, so a fresh DB does not show them as OTHER")
    void seedTagCatalog() {
        // Not in any migration's problem_tags, only in backend/tools seed data.
        assertThat(tagRepository.findByNameIgnoreCase("Socket")).get().extracting(Tag::getCategory).isEqualTo("Java Core");
        assertThat(tagRepository.findByNameIgnoreCase("Networking")).get().extracting(Tag::getCategory).isEqualTo("Java Core");
        assertThat(tagRepository.findByNameIgnoreCase("Design Patterns")).get().extracting(Tag::getCategory).isEqualTo("OOP");
        assertThat(tagRepository.findByNameIgnoreCase("Sliding Window")).get().extracting(Tag::getCategory).isEqualTo("DSA");
        // Already inserted by V15 from V3's problem_tags: V17 must not duplicate it.
        assertThat(tagRepository.findAll().stream().filter(t -> t.getName().equals("Array")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Admin groups tags; public tag list carries the category; only ADMIN may manage tags")
    void tagCategories() throws Exception {
        User admin = user("tag_admin", Role.ADMIN);
        User instructor = user("tag_instructor", Role.INSTRUCTOR);
        Problem p1 = problem("tag-p1", admin, List.of("Zeta Topic"));

        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(instructor)))
                .andExpect(status().isForbidden());

        // Tag only used by a problem: listed as OTHER, can be regrouped.
        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Zeta Topic')].category").value("Other"))
                .andExpect(jsonPath("$[?(@.name == 'Zeta Topic')].problemCount").value(1));
        mockMvc.perform(put("/api/v1/admin/tags/{name}", "Zeta Topic").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"spring boot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Spring Boot"));
        mockMvc.perform(get("/api/v1/problems/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tag == 'Zeta Topic')].category").value("Spring Boot"));

        // Create, duplicate, delete.
        mockMvc.perform(post("/api/v1/admin/tags").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Fresh Tag \",\"category\":\"DSA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fresh Tag"))
                .andExpect(jsonPath("$.problemCount").value(0));
        mockMvc.perform(post("/api/v1/admin/tags").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"fresh tag\",\"category\":\"OOP\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/tags").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Odd\",\"category\":\"Nope\"}"))
                .andExpect(status().isBadRequest());
        // Rename follows the problem; bulk remove / add; delete also untags the problem.
        mockMvc.perform(put("/api/v1/admin/tags/{name}", "Zeta Topic").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Zeta Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zeta Renamed"))
                .andExpect(jsonPath("$.category").value("Spring Boot"))
                .andExpect(jsonPath("$.problemCount").value(1));
        // Own color, bad hex rejected, empty string falls back to the category color.
        mockMvc.perform(put("/api/v1/admin/tags/{name}", "Zeta Renamed").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"#AA00CC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#aa00cc"));
        mockMvc.perform(put("/api/v1/admin/tags/{name}", "Zeta Renamed").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"red\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/tags/{name}", "Zeta Renamed").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").doesNotExist());
        mockMvc.perform(post("/api/v1/admin/tags/{name}/problems", "Zeta Renamed").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemIds\":[" + p1.getId() + "],\"remove\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(1));
        mockMvc.perform(post("/api/v1/admin/tags/{name}/problems", "Zeta Renamed").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemIds\":[" + p1.getId() + "],\"remove\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(1));
        mockMvc.perform(delete("/api/v1/admin/tags/{name}", "Zeta Renamed").header("Authorization", token(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(admin)))
                .andExpect(jsonPath("$[?(@.name == 'Zeta Renamed')]").isEmpty());
        mockMvc.perform(delete("/api/v1/admin/tags/{name}", "Fresh Tag").header("Authorization", token(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Tag categories: public list, ADMIN CRUD, rename cascades to tags, delete falls back to Other")
    void categoryCrud() throws Exception {
        User admin = user("cat_admin", Role.ADMIN);
        User instructor = user("cat_instructor", Role.INSTRUCTOR);
        problem("cat-p1", admin, List.of("Cat Topic"));

        mockMvc.perform(get("/api/v1/tag-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Testing')].color").value("#db2777"))
                .andExpect(jsonPath("$[-1].name").value("Other"));
        mockMvc.perform(post("/api/v1/admin/tag-categories").header("Authorization", token(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cloud\"}"))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/v1/admin/tag-categories").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Cloud \",\"color\":\"#00AAFF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cloud"))
                .andExpect(jsonPath("$.color").value("#00aaff"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(post("/api/v1/admin/tag-categories").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cloud\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/tag-categories").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bad\",\"color\":\"red\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/tags/{name}", "Cat Topic").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Cloud\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/tag-categories/{id}", id).header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cloud Native\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(admin)))
                .andExpect(jsonPath("$[?(@.name == 'Cat Topic')].category").value("Cloud Native"));

        mockMvc.perform(delete("/api/v1/admin/tag-categories/{id}", id).header("Authorization", token(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/tags").header("Authorization", token(admin)))
                .andExpect(jsonPath("$[?(@.name == 'Cat Topic')].category").value("Other"));

        List<Integer> otherIds = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(get("/api/v1/tag-categories"))
                .andReturn().getResponse().getContentAsString(), "$[?(@.name == 'Other')].id");
        long otherId = otherIds.get(0);
        mockMvc.perform(delete("/api/v1/admin/tag-categories/{id}", otherId).header("Authorization", token(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Ranking: distinct accepted problems, ties go to fewer submissions; runs and staff are ignored")
    void ranking() throws Exception {
        User author = user("rk_author", Role.INSTRUCTOR);
        User alice = user("rk_alice", Role.USER);
        User bob = user("rk_bob", Role.USER);
        User carol = user("rk_carol", Role.USER);
        Problem p1 = problem("rk-p1", author, List.of());
        Problem p2 = problem("rk-p2", author, List.of());

        submit(alice, p1, SubmissionVerdict.AC, false);
        submit(alice, p1, SubmissionVerdict.AC, false); // same problem twice counts once
        submit(bob, p1, SubmissionVerdict.WA, false);
        submit(bob, p1, SubmissionVerdict.AC, false);
        submit(bob, p2, SubmissionVerdict.AC, false);
        submit(carol, p1, SubmissionVerdict.AC, false);
        submit(carol, p2, SubmissionVerdict.AC, true); // quick run: not counted
        submit(author, p1, SubmissionVerdict.AC, false); // staff: not ranked

        List<String> order = rankingService.top().stream().map(RankingRow::username)
                .filter(n -> n.startsWith("rk_")).toList();
        assertThat(order).containsExactly("rk_bob", "rk_carol", "rk_alice");

        mockMvc.perform(get("/api/v1/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'rk_bob')].solved").value(2))
                .andExpect(jsonPath("$[?(@.username == 'rk_alice')].submissions").value(2));
    }

    @Test
    @DisplayName("Site pages: seeded, public read, ADMIN-only update")
    void sitePages() throws Exception {
        User admin = user("pg_admin", Role.ADMIN);
        User instructor = user("pg_instructor", Role.INSTRUCTOR);

        mockMvc.perform(get("/api/v1/pages/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Terms of use"));
        mockMvc.perform(get("/api/v1/pages/rules")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pages/missing")).andExpect(status().isNotFound());

        String body = "{\"title\":\"Terms\",\"contentMd\":\"# Hello\"}";
        mockMvc.perform(put("/api/v1/admin/pages/terms").header("Authorization", token(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/pages/terms").header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentMd").value("# Hello"));
        mockMvc.perform(get("/api/v1/pages/terms")).andExpect(jsonPath("$.title").value("Terms"));
    }
}
