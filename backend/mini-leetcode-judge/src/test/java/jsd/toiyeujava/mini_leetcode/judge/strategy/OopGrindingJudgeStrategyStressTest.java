package jsd.toiyeujava.mini_leetcode.judge.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.judge.CompileResult;
import jsd.toiyeujava.mini_leetcode.judge.DockerExecutor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.MultiFileSourceExtractor;
import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.compilation.OopGrindingCompiler;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OopGrindingJudgeStrategyStressTest {

    @Mock
    private SubmissionService submissionService;

    @Mock
    private StorageService storageService;

    @Mock
    private MultiFileSourceExtractor sourceExtractor;

    @Mock
    private OopGrindingCompiler compiler;

    @Mock
    private DockerExecutor dockerExecutor;

    @Mock
    private SubmissionRepository submissionRepository;

    private ObjectMapper objectMapper;
    private OopGrindingJudgeStrategy strategy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        strategy = new OopGrindingJudgeStrategy(
                submissionService,
                storageService,
                sourceExtractor,
                compiler,
                objectMapper,
                dockerExecutor,
                submissionRepository
        );
    }

    @Test
    @DisplayName("Short-Circuit Stress 1: When compilation fails, DockerExecutor is NEVER invoked, verdict is CE, score 0.0, COMPILATION_FAILED")
    void executeJudge_CompilationFailure_StrictDockerIsolationAndMetadataIntegrity(@TempDir Path workDir) throws Exception {
        Long submissionId = 5001L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("public class SyntaxFail {");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        Path src = workDir.resolve("SyntaxFail.java");
        when(sourceExtractor.extract(anyString(), eq(workDir))).thenReturn(List.of(src));
        when(compiler.compile(eq(workDir), anyList())).thenReturn(
                new CompileResult(false, "SyntaxFail.java:1: error: reached end of file while parsing")
        );

        strategy.executeJudge(message, problem, workDir);

        // 1. ABSOLUTE CERTAINTY: DockerExecutor is never touched
        verifyNoInteractions(dockerExecutor);

        // 2. SubmissionService receives completed CE verdict
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("reached end of file while parsing")
        );

        // 3. SubmissionRepository records score 0.0, COMPILATION_FAILED, and score_breakdown JSON
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        Submission saved = captor.getValue();
        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");

        // Verify JSON parseability and contract
        JsonNode json = objectMapper.readTree(saved.getScoreBreakdown());
        assertThat(json.get("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(json.get("executionPhase").asText()).isEqualTo("COMPILATION_FAILED");
        assertThat(json.get("totalScore").asDouble()).isEqualTo(0.0);
        assertThat(json.get("verdict").asText()).isEqualTo("CE");
        assertThat(json.get("phases").get("compilation").get("passed").asBoolean()).isFalse();
        assertThat(json.get("phases").get("compilation").get("errors").asText()).contains("reached end of file while parsing");
    }

    @Test
    @DisplayName("Short-Circuit Stress 2: When compilation times out, DockerExecutor is NEVER invoked, verdict is CE")
    void executeJudge_CompilationTimeout_StrictDockerIsolationAndMetadataIntegrity(@TempDir Path workDir) throws Exception {
        Long submissionId = 5002L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("public class HeavyCode {}");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        Path src = workDir.resolve("HeavyCode.java");
        when(sourceExtractor.extract(anyString(), eq(workDir))).thenReturn(List.of(src));
        when(compiler.compile(eq(workDir), anyList())).thenReturn(
                new CompileResult(false, "Compilation timed out after 10000ms")
        );

        strategy.executeJudge(message, problem, workDir);

        verifyNoInteractions(dockerExecutor);
        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("timed out")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        Submission saved = captor.getValue();
        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");
    }

    @Test
    @DisplayName("Short-Circuit Stress 3: Storage download returns null stream -> fails fast without Docker")
    void executeJudge_StorageDownloadReturnsNullStream_FailsFastWithoutDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 5003L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/10/20/code.zip")
                .build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/10/20/code.zip")).thenReturn(null);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("Storage returned null stream")
        );

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        Submission saved = captor.getValue();
        assertThat(saved.getScore()).isEqualTo(0.0);
        assertThat(saved.getExecutionPhase()).isEqualTo("COMPILATION_FAILED");
    }

    @Test
    @DisplayName("Short-Circuit Stress 4: Storage download throws IOException -> fails fast without Docker")
    void executeJudge_StorageDownloadThrowsIOException_FailsFastWithoutDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 5004L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/10/20/code.zip")
                .build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/10/20/code.zip"))
                .thenReturn(new ByteArrayInputStream(new byte[]{0x50, 0x4B, 0x03, 0x04}));
        when(sourceExtractor.extract(any(InputStream.class), eq(workDir)))
                .thenThrow(new IOException("S3 MinIO stream read error"));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        strategy.executeJudge(message, problem, workDir);

        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("S3 MinIO stream read error")
        );
    }

    @Test
    @DisplayName("Short-Circuit Stress 5: Zip Slip SecurityException in stream extraction -> fails fast without Docker")
    void executeJudge_ZipSlipInZipStream_FailsFastWithoutDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 5005L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder()
                .id(submissionId)
                .codeStorageUri("minio://submissions/10/20/evil.zip")
                .build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(storageService.downloadFileByUri("minio://submissions/10/20/evil.zip"))
                .thenReturn(new ByteArrayInputStream(new byte[]{0x50, 0x4B, 0x03, 0x04}));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(sourceExtractor.extract(any(ByteArrayInputStream.class), eq(workDir)))
                .thenThrow(new SecurityException("Zip Slip detected! Path traversal outside target"));

        strategy.executeJudge(message, problem, workDir);

        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("Zip Slip detected")
        );
    }

    @Test
    @DisplayName("Short-Circuit Stress 6: Extracted archive contains 0 Java files -> fails fast without Docker")
    void executeJudge_ZeroJavaFilesExtracted_FailsFastWithoutDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 5006L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.JAVA);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();
        Submission submission = Submission.builder().id(submissionId).build();

        when(submissionService.getSubmissionById(submissionId)).thenReturn(submission);
        when(submissionService.getSubmissionCode(submissionId)).thenReturn("data only");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(sourceExtractor.extract(anyString(), eq(workDir)))
                .thenReturn(List.of(workDir.resolve("README.md"), workDir.resolve("diagram.png")));

        strategy.executeJudge(message, problem, workDir);

        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);

        verify(submissionService).updateSubmissionVerdict(
                eq(submissionId),
                eq(SubmissionStatus.COMPLETED),
                eq(SubmissionVerdict.CE),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                contains("No Java source files (.java) found")
        );
    }

    @Test
    @DisplayName("Short-Circuit Stress 7: Unsupported non-Java language -> fails submission immediately without Docker")
    void executeJudge_UnsupportedLanguage_FailsImmediatelyWithoutDocker(@TempDir Path workDir) throws Exception {
        Long submissionId = 5007L;
        SubmissionTaskMessage message = new SubmissionTaskMessage(submissionId, 10L, 20L, SubmissionLanguage.CPP);
        Problem problem = Problem.builder().id(10L).problemType(ProblemType.OOP_GRINDING).build();

        strategy.executeJudge(message, problem, workDir);

        verify(submissionService).failSubmission(eq(submissionId), contains("OOP Grinding only supports Java"));
        verifyNoInteractions(sourceExtractor);
        verifyNoInteractions(compiler);
        verifyNoInteractions(dockerExecutor);
    }
}
