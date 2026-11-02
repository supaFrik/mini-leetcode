package jsd.toiyeujava.mini_leetcode.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.producer.SubmissionProducer;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Empirical Challenger Test: Testing POST /api/v1/submissions with REAL SubmissionService.
 * (No @MockBean SubmissionService!)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RealServiceMultipartZipChallengerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubmissionProducer submissionProducer;

    @MockBean
    private StorageService storageService;

    private User student;
    private String studentToken;
    private Problem oopProblem;

    @BeforeEach
    void setUp() {
        student = userRepository.save(User.builder()
                .username("real_student_" + System.currentTimeMillis())
                .email("real_student_" + System.currentTimeMillis() + "@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        studentToken = "Bearer " + jwtTokenProvider.generateAccessToken(student);

        oopProblem = problemRepository.save(Problem.builder()
                .title("OOP Real Service Test Problem")
                .slug("oop-real-" + System.currentTimeMillis())
                .description("Testing real service integration")
                .difficulty(Difficulty.MEDIUM)
                .timeLimitMs(2000)
                .memoryLimitMb(256)
                .problemType(ProblemType.OOP_GRINDING)
                .status(ProblemStatus.PUBLISHED)
                .createdBy(student)
                .build());

        when(storageService.generateSubmissionZipKey(anyLong(), anyLong()))
                .thenReturn("submissions/" + oopProblem.getId() + "/" + student.getId() + "/test.zip");

        when(storageService.getSubmissionsBucketName())
                .thenReturn("submissions");

        when(storageService.uploadFile(anyString(), anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(inv -> "minio://" + inv.getArgument(0) + "/" + inv.getArgument(1));
    }

    @Test
    @DisplayName("Remediation Proof: Real POST /api/v1/submissions with Multipart ZIP returns 201 and stores Submission")
    void testMultipartZip_WithRealSubmissionService_RevealsArchitectureBug() throws Exception {
        byte[] zipData = createZip(Map.of(
                "Solution.java", "public class Solution {}"
        ));

        MockMultipartFile zipFile = new MockMultipartFile(
                "file",
                "solution.zip",
                "application/zip",
                zipData
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/submissions")
                        .file(zipFile)
                        .param("problemId", oopProblem.getId().toString())
                        .param("language", "JAVA")
                        .header("Authorization", studentToken))
                .andReturn();

        int status = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();

        System.out.println("=== EMPIRICAL TEST RESULT FOR MULTIPART ZIP WITH REAL SERVICE ===");
        System.out.println("HTTP Status: " + status);
        System.out.println("Response Body: " + responseBody);

        assertThat(status).isEqualTo(201);
        assertThat(responseBody).contains("Submission accepted and queued for judging");

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseBody);
        assertThat(root.get("success").asBoolean()).isTrue();
        Long submissionId = root.get("data").get("id").asLong();

        jsd.toiyeujava.mini_leetcode.submission.entity.Submission submission = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(submission.getCodeStorageUri())
                .isNotNull()
                .startsWith("minio://submissions/")
                .endsWith(".zip");

        org.mockito.Mockito.verify(storageService, org.mockito.Mockito.times(1))
                .uploadFile(org.mockito.ArgumentMatchers.eq("submissions"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(InputStream.class),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.eq("application/zip"));
        org.mockito.Mockito.verify(submissionProducer, org.mockito.Mockito.times(1))
                .enqueueSubmission(org.mockito.ArgumentMatchers.any());
    }

    private byte[] createZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
