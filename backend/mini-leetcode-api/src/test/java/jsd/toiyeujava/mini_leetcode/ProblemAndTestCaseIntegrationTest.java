package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.problem.dto.CreateProblemRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.LoginRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.RegisterRequest;
import jsd.toiyeujava.mini_leetcode.problem.dto.UpdateProblemRequest;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemAndTestCaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private StorageService storageService;

    private String instructorToken;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), any()))
                .thenAnswer(invocation -> "minio://testcases/" + invocation.getArgument(0));

        // Create Instructor in DB & obtain token via login
        userRepository.save(User.builder()
                .username("instructor1")
                .email("inst1@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());
        MvcResult instResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("instructor1", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        instructorToken = objectMapper.readTree(instResult.getResponse().getContentAsString()).get("accessToken").asText();

        // Register Normal User via public registration
        RegisterRequest userReq = new RegisterRequest("student1", "student1@example.com", "Password123!");
        MvcResult userResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated())
                .andReturn();
        userToken = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("accessToken").asText();

        // Create Admin in DB & obtain token via login
        userRepository.save(User.builder()
                .username("adminuser")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("adminuser", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(adminResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("Verification Gate 4: Problem CRUD, Unique Slug Generation, Pagination, and TestCase Filtering")
    void problemAndTestCaseFullFlow() throws Exception {
        // 1. Instructor creates first problem "Two Sum"
        CreateProblemRequest createP1 = new CreateProblemRequest("Two Sum", "Given array...", Difficulty.EASY, 1000, 256, null);
        MvcResult p1Result = mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("two-sum"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andReturn();

        JsonNode p1Json = objectMapper.readTree(p1Result.getResponse().getContentAsString());
        long p1Id = p1Json.get("id").asLong();

        // 2. Instructor creates second problem with same title -> automatically creates unique slug "two-sum-1"
        CreateProblemRequest createP2 = new CreateProblemRequest("Two Sum", "Second version...", Difficulty.MEDIUM, 2000, 512, null);
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("two-sum-1"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"));

        // 3. Normal user cannot create problem -> 403 Forbidden
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 4. Lifecycle: DRAFT -> REVIEWING (allowed edge, no publish gate yet)
        mockMvc.perform(post("/api/v1/problems/" + p1Id + "/transitions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWING"));

        // 4b. A problem that is not PUBLISHED is invisible to anonymous callers.
        mockMvc.perform(get("/api/v1/problems/two-sum"))
                .andExpect(status().isNotFound());

        // 5. Search & Pagination (staff view - sees unpublished problems)
        mockMvc.perform(get("/api/v1/problems")
                        .param("difficulty", "EASY")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].slug").value("two-sum"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        // 6. Get Problem by Slug (staff can read an unpublished problem)
        mockMvc.perform(get("/api/v1/problems/two-sum")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p1Id))
                .andExpect(jsonPath("$.title").value("Two Sum"));

        // 7. Get Problem by ID (staff)
        mockMvc.perform(get("/api/v1/problems/" + p1Id)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("two-sum"));

        // 7b. Publish gate: PUBLISHED without any public test case -> 409 Conflict
        mockMvc.perform(post("/api/v1/problems/" + p1Id + "/transitions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Resource Conflict"));

        // 8. Upload public TestCase (isHidden = false, orderIndex = 1)
        MockMultipartFile inputFile1 = new MockMultipartFile(
                "inputFile", "input_1.txt", "text/plain", "2 7 11 15\n9".getBytes());
        MockMultipartFile outputFile1 = new MockMultipartFile(
                "outputFile", "output_1.txt", "text/plain", "0 1".getBytes());

        mockMvc.perform(multipart("/api/v1/problems/" + p1Id + "/test-cases")
                        .file(inputFile1)
                        .file(outputFile1)
                        .param("isHidden", "false")
                        .param("orderIndex", "1")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isHidden").value(false))
                .andExpect(jsonPath("$.orderIndex").value(1))
                .andExpect(jsonPath("$.inputFileUri").isNotEmpty())
                .andExpect(jsonPath("$.expectedOutputUri").isNotEmpty());

        // 9. Upload hidden TestCase (isHidden = true, orderIndex = 2)
        MockMultipartFile inputFile2 = new MockMultipartFile(
                "inputFile", "input_2.txt", "text/plain", "3 2 4\n6".getBytes());
        MockMultipartFile outputFile2 = new MockMultipartFile(
                "outputFile", "output_2.txt", "text/plain", "1 2".getBytes());

        mockMvc.perform(multipart("/api/v1/problems/" + p1Id + "/test-cases")
                        .file(inputFile2)
                        .file(outputFile2)
                        .param("isHidden", "true")
                        .param("orderIndex", "2")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isHidden").value(true))
                .andExpect(jsonPath("$.orderIndex").value(2));

        // 9b. Now that a public test case exists, publishing succeeds.
        mockMvc.perform(post("/api/v1/problems/" + p1Id + "/transitions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 9c. Illegal edge: PUBLISHED -> REVIEWING is not allowed -> 409 Conflict
        mockMvc.perform(post("/api/v1/problems/" + p1Id + "/transitions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // 9d. Now that the problem is PUBLISHED, anonymous callers can see it again.
        mockMvc.perform(get("/api/v1/problems/two-sum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("two-sum"));

        mockMvc.perform(get("/api/v1/problems")
                        .param("difficulty", "EASY")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("two-sum"));

        // 9e. Test cases of a PUBLISHED problem are frozen: cannot add more.
        MockMultipartFile frozenIn = new MockMultipartFile(
                "inputFile", "input_3.txt", "text/plain", "1".getBytes());
        MockMultipartFile frozenOut = new MockMultipartFile(
                "outputFile", "output_3.txt", "text/plain", "2".getBytes());

        mockMvc.perform(multipart("/api/v1/problems/" + p1Id + "/test-cases")
                        .file(frozenIn)
                        .file(frozenOut)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // 10. Normal User gets testcases -> ONLY sees public testcases (count = 1)
        MvcResult userTcResult = mockMvc.perform(get("/api/v1/problems/" + p1Id + "/test-cases")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isHidden").value(false))
                .andReturn();

        // 11. Unauthenticated user gets testcases -> ONLY sees public testcases (count = 1)
        mockMvc.perform(get("/api/v1/problems/" + p1Id + "/test-cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isHidden").value(false));

        // 12. Instructor gets testcases -> SEES ALL testcases (count = 2)
        mockMvc.perform(get("/api/v1/problems/" + p1Id + "/test-cases")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 13. Explicit Slug Route: /api/v1/problems/slug/{slug}
        mockMvc.perform(get("/api/v1/problems/slug/two-sum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("two-sum"));
    }

    @Test
    @DisplayName("getProblem: non-existent ID or slug returns 404 ProblemDetail")
    void getProblem_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/problems/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"));

        mockMvc.perform(get("/api/v1/problems/non-existent-problem-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Visibility: non-staff only sees PUBLISHED problems; staff sees unpublished too")
    void visibility_publishedOnlyForNonStaff() throws Exception {
        CreateProblemRequest createP = new CreateProblemRequest("Hidden Draft", "Desc", Difficulty.EASY, 1000, 256, null);
        MvcResult pResult = mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        long pId = objectMapper.readTree(pResult.getResponse().getContentAsString()).get("id").asLong();

        // 1. Anonymous -> collection excludes the draft
        mockMvc.perform(get("/api/v1/problems").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + pId + ")]").isEmpty());

        // 2. Normal user -> collection excludes the draft
        mockMvc.perform(get("/api/v1/problems").param("size", "50")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + pId + ")]").isEmpty());

        // 3. Normal user -> single resource is hidden as 404 (not 403, no existence leak)
        mockMvc.perform(get("/api/v1/problems/" + pId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/problems/hidden-draft"))
                .andExpect(status().isNotFound());

        // 4. Instructor -> sees the unpublished draft
        mockMvc.perform(get("/api/v1/problems/" + pId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // 5. Admin -> sees the unpublished draft too
        mockMvc.perform(get("/api/v1/problems/hidden-draft")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // 6. A non-staff cannot bypass visibility by asking for another status.
        mockMvc.perform(get("/api/v1/problems").param("status", "DRAFT").param("size", "50")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + pId + ")]").isEmpty());
    }

    @Test
    @DisplayName("createProblem: invalid payload returns 400 ProblemDetail with invalid_params")
    void createProblem_validationFailure_returns400() throws Exception {
        CreateProblemRequest invalidReq = new CreateProblemRequest("", "", null, -1, -1, null);
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.invalid_params.title").isNotEmpty())
                .andExpect(jsonPath("$.invalid_params.description").isNotEmpty())
                .andExpect(jsonPath("$.invalid_params.difficulty").isNotEmpty());
    }

    @Test
    @DisplayName("deleteProblem: role authorization and deletion flow")
    void deleteProblem_authorizationAndLifecycle() throws Exception {
        CreateProblemRequest createP = new CreateProblemRequest("To Delete", "Desc", Difficulty.EASY, 1000, 256, null);
        MvcResult pResult = mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP)))
                .andExpect(status().isCreated())
                .andReturn();
        long pId = objectMapper.readTree(pResult.getResponse().getContentAsString()).get("id").asLong();

        // 1. Normal user tries to delete -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/problems/" + pId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. Instructor tries to delete -> 403 Forbidden (Only ADMIN allowed)
        mockMvc.perform(delete("/api/v1/problems/" + pId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());

        // 3. Admin deletes existing problem -> 204 No Content
        mockMvc.perform(delete("/api/v1/problems/" + pId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // 4. Admin deletes non-existent problem -> 404 ProblemDetail
        mockMvc.perform(delete("/api/v1/problems/" + pId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("deleteTestCase: role authorization and deletion flow")
    void deleteTestCase_authorizationAndLifecycle() throws Exception {
        CreateProblemRequest createP = new CreateProblemRequest("TC Problem", "Desc", Difficulty.EASY, 1000, 256, null);
        MvcResult pResult = mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP)))
                .andExpect(status().isCreated())
                .andReturn();
        long pId = objectMapper.readTree(pResult.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile inFile = new MockMultipartFile("inputFile", "in.txt", "text/plain", "1".getBytes());
        MockMultipartFile outFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "2".getBytes());

        MvcResult tcResult = mockMvc.perform(multipart("/api/v1/problems/" + pId + "/test-cases")
                        .file(inFile)
                        .file(outFile)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isCreated())
                .andReturn();
        long tcId = objectMapper.readTree(tcResult.getResponse().getContentAsString()).get("id").asLong();

        // 1. User tries to delete test case -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/problems/" + pId + "/test-cases/" + tcId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. Instructor deletes non-existent test case -> 404 Not Found
        mockMvc.perform(delete("/api/v1/problems/" + pId + "/test-cases/99999")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 3. Instructor deletes testcase with mismatched problem ID -> 400 Bad Request
        mockMvc.perform(delete("/api/v1/problems/99999/test-cases/" + tcId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 4. Instructor deletes existing test case -> 204 No Content
        mockMvc.perform(delete("/api/v1/problems/" + pId + "/test-cases/" + tcId)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("uploadTestCase: empty file returns 400 ProblemDetail")
    void uploadTestCase_emptyFile_returns400() throws Exception {
        CreateProblemRequest createP = new CreateProblemRequest("Empty TC Problem", "Desc", Difficulty.EASY, 1000, 256, null);
        MvcResult pResult = mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createP)))
                .andExpect(status().isCreated())
                .andReturn();
        long pId = objectMapper.readTree(pResult.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile emptyIn = new MockMultipartFile("inputFile", "empty.txt", "text/plain", new byte[0]);
        MockMultipartFile validOut = new MockMultipartFile("outputFile", "out.txt", "text/plain", "res".getBytes());

        mockMvc.perform(multipart("/api/v1/problems/" + pId + "/test-cases")
                        .file(emptyIn)
                        .file(validOut)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }
}
