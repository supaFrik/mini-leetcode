package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.dto.LoginRequest;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin có thể sửa mọi nội dung qua API, không chỉ qua seed script. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminEditingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockBean StorageService storageService;

    String admin;
    String instructor;
    String student;
    long adminId;
    long studentId;

    @BeforeEach
    void setUp() throws Exception {
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), any()))
                .thenAnswer(inv -> "minio://testcases/" + inv.getArgument(0));
        adminId = save("ad", Role.ADMIN);
        save("ins", Role.INSTRUCTOR);
        studentId = save("stu", Role.USER);
        admin = login("ad");
        instructor = login("ins");
        student = login("stu");
    }

    private long save(String name, Role role) {
        return users.save(User.builder().username(name).email(name + "@x.io")
                .passwordHash(encoder.encode("Password123!")).role(role).status(UserStatus.ACTIVE).build()).getId();
    }

    private String login(String name) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new LoginRequest(name, "Password123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(body).get("accessToken").asText();
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    @Test
    void problemTypeTemplatesAndGradingConfigArePersisted() throws Exception {
        String templates = "[{\"path\":\"Shape.java\",\"content\":\"class Shape {}\"}]";
        String grading = "{\"rules\":[]}";
        String created = mvc.perform(post("/api/v1/problems").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Shapes", "description", "d", "difficulty", "EASY",
                                "problemType", "OOP_GRINDING", "starterTemplates", templates, "gradingConfig", grading))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OOP_GRINDING"))
                .andExpect(jsonPath("$.starterTemplates").value(containsString("Shape.java")))
                .andReturn().getResponse().getContentAsString();
        long id = om.readTree(created).get("id").asLong();

        mvc.perform(get("/api/v1/problems/" + id + "/grading-config").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradingConfig").value(containsString("rules")));
        mvc.perform(get("/api/v1/problems/" + id + "/grading-config").header("Authorization", student))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/problems/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("problemType", "ALGORITHMIC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ALGORITHMIC"));

        mvc.perform(put("/api/v1/problems/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gradingConfig", "{not json"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminEditsTestCasesOfPublishedProblemButInstructorCannot() throws Exception {
        String created = mvc.perform(post("/api/v1/problems").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Sum", "description", "d", "difficulty", "EASY"))))
                .andReturn().getResponse().getContentAsString();
        long id = om.readTree(created).get("id").asLong();
        String tc = mvc.perform(multipart("/api/v1/problems/" + id + "/test-cases")
                        .file(new MockMultipartFile("inputFile", "in.txt", "text/plain", "1 2".getBytes()))
                        .file(new MockMultipartFile("outputFile", "out.txt", "text/plain", "3".getBytes()))
                        .param("isHidden", "false").header("Authorization", admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long tcId = om.readTree(tc).get("id").asLong();
        for (String s : new String[]{"REVIEWING", "PUBLISHED"}) {
            mvc.perform(post("/api/v1/problems/" + id + "/transitions").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", s))))
                    .andExpect(status().isOk());
        }

        String url = "/api/v1/problems/" + id + "/test-cases/" + tcId;
        mvc.perform(multipart(url).with(r -> { r.setMethod("PUT"); return r; })
                        .param("isHidden", "true").header("Authorization", instructor))
                .andExpect(status().isConflict());
        mvc.perform(multipart(url).with(r -> { r.setMethod("PUT"); return r; })
                        .param("isHidden", "true").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHidden").value(true));
    }

    @Test
    void contestCrud() throws Exception {
        long p1 = om.readTree(mvc.perform(post("/api/v1/problems").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "P1", "description", "d", "difficulty", "EASY"))))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "title", "Midterm", "startTime", "2026-12-01T08:00:00", "endTime", "2026-12-01T10:00:00",
                "status", "SCHEDULED", "maxViolationsAllowed", 5, "problemIds", java.util.List.of(p1)));
        String created = mvc.perform(post("/api/v1/contests").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problems[0].label").value("A"))
                .andExpect(jsonPath("$.maxViolationsAllowed").value(5))
                .andReturn().getResponse().getContentAsString();
        long cid = om.readTree(created).get("id").asLong();

        mvc.perform(post("/api/v1/contests").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isForbidden());

        body.put("title", "Midterm v2");
        body.put("status", "RUNNING");
        body.put("problemIds", java.util.List.of());
        mvc.perform(put("/api/v1/contests/" + cid).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Midterm v2"))
                .andExpect(jsonPath("$.problems.length()").value(0));

        body.put("endTime", "2026-12-01T07:00:00");
        mvc.perform(put("/api/v1/contests/" + cid).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());

        // Có bài + thí sinh đăng ký -> vẫn xoá được (chưa có bài nộp).
        body.put("endTime", "2026-12-01T10:00:00");
        body.put("problemIds", java.util.List.of(p1));
        mvc.perform(put("/api/v1/contests/" + cid).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/contests/" + cid + "/register").header("Authorization", student))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/contests/" + cid).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/contests/" + cid).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminManagesUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("search", "stu").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("stu"));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", instructor))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/admin/users/" + studentId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "INSTRUCTOR", "email", "new@x.io"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.email").value("new@x.io"));

        mvc.perform(put("/api/v1/admin/users/" + adminId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "BLOCKED"))))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/admin/users/" + studentId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "BLOCKED"))))
                .andExpect(status().isOk());
        // Bị khoá -> token cũ mất hiệu lực ngay.
        mvc.perform(get("/api/v1/contests").header("Authorization", student))
                .andExpect(status().isUnauthorized());
    }
}
