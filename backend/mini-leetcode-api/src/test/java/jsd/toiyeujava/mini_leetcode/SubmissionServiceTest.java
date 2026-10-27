package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionResponse;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmitCodeRequest;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionService;
import jsd.toiyeujava.mini_leetcode.submission.service.SubmissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository contestRepository;

    @Mock
    private jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository contestParticipantRepository;

    private SubmissionService submissionService;

    private User testUser;
    private Problem testProblem;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionServiceImpl(
                submissionRepository,
                problemRepository,
                userRepository,
                storageService,
                contestRepository,
                contestParticipantRepository
        );

        testUser = User.builder()
                .id(1L)
                .username("testcoder")
                .email("testcoder@example.com")
                .passwordHash("hash")
                .build();

        testProblem = Problem.builder()
                .id(100L)
                .title("Two Sum")
                .slug("two-sum")
                .description("Solve two sum")
                .difficulty(Difficulty.EASY)
                .timeLimitMs(1000)
                .memoryLimitMb(256)
                .build();
    }

    @Test
    @DisplayName("createSubmission: thành công lưu bài nộp và upload mã nguồn lên Storage")
    void createSubmission_Success() {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }"
        );

        when(problemRepository.findById(100L)).thenReturn(Optional.of(testProblem));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("submissions/100/1/uuid.java");

        Submission savedEntity = Submission.builder()
                .id(501L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.JAVA)
                .codeStorageUri("submissions/100/1/uuid.java")
                .status(SubmissionStatus.PENDING)
                .build();

        when(submissionRepository.save(any(Submission.class))).thenReturn(savedEntity);

        Submission result = submissionService.createSubmission(100L, 1L, request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(501L);
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(result.getLanguage()).isEqualTo(SubmissionLanguage.JAVA);
        verify(storageService).uploadFile(anyString(), any(InputStream.class), anyLong(), eq("text/plain"));
        verify(submissionRepository).save(any(Submission.class));
    }

    @Test
    @DisplayName("createSubmission: thành công lưu bài nộp trong chế độ EXAM khi contest RUNNING và thí sinh hợp lệ")
    void createSubmission_ExamMode_Success() {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution { public static void main(String[] args) {} }",
                10L
        );

        jsd.toiyeujava.mini_leetcode.contest.entity.Contest contest = jsd.toiyeujava.mini_leetcode.contest.entity.Contest.builder()
                .id(10L)
                .title("Midterm Exam")
                .status(jsd.toiyeujava.mini_leetcode.entity.ContestStatus.RUNNING)
                .build();

        jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant participant = jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant.builder()
                .id(20L)
                .contest(contest)
                .user(testUser)
                .status(jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus.IN_PROGRESS)
                .violationCount(1)
                .build();

        when(problemRepository.findById(100L)).thenReturn(Optional.of(testProblem));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(contestRepository.findById(10L)).thenReturn(Optional.of(contest));
        when(contestParticipantRepository.findByContestIdAndUserId(10L, 1L)).thenReturn(Optional.of(participant));
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("submissions/100/1/uuid.java");

        Submission savedEntity = Submission.builder()
                .id(502L)
                .problem(testProblem)
                .user(testUser)
                .contestId(10L)
                .language(SubmissionLanguage.JAVA)
                .codeStorageUri("submissions/100/1/uuid.java")
                .status(SubmissionStatus.PENDING)
                .build();

        when(submissionRepository.save(any(Submission.class))).thenReturn(savedEntity);

        Submission result = submissionService.createSubmission(100L, 1L, request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(502L);
        assertThat(result.getContestId()).isEqualTo(10L);
        verify(contestRepository).findById(10L);
        verify(contestParticipantRepository).findByContestIdAndUserId(10L, 1L);
    }

    @Test
    @DisplayName("createSubmission: từ chối nộp bài trong EXAM khi thí sinh đã bị DISQUALIFIED")
    void createSubmission_ExamMode_DisqualifiedUser() {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution {}",
                10L
        );

        jsd.toiyeujava.mini_leetcode.contest.entity.Contest contest = jsd.toiyeujava.mini_leetcode.contest.entity.Contest.builder()
                .id(10L)
                .title("Midterm Exam")
                .status(jsd.toiyeujava.mini_leetcode.entity.ContestStatus.RUNNING)
                .build();

        jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant participant = jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant.builder()
                .id(20L)
                .contest(contest)
                .user(testUser)
                .status(jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus.DISQUALIFIED)
                .violationCount(3)
                .build();

        when(problemRepository.findById(100L)).thenReturn(Optional.of(testProblem));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(contestRepository.findById(10L)).thenReturn(Optional.of(contest));
        when(contestParticipantRepository.findByContestIdAndUserId(10L, 1L)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> submissionService.createSubmission(100L, 1L, request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("disqualified");

        verifyNoInteractions(storageService);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSubmission: từ chối nộp bài trong EXAM khi thí sinh đã SUBMITTED bài thi")
    void createSubmission_ExamMode_AlreadySubmittedUser() {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.JAVA,
                "public class Solution {}",
                10L
        );

        jsd.toiyeujava.mini_leetcode.contest.entity.Contest contest = jsd.toiyeujava.mini_leetcode.contest.entity.Contest.builder()
                .id(10L)
                .title("Midterm Exam")
                .status(jsd.toiyeujava.mini_leetcode.entity.ContestStatus.RUNNING)
                .build();

        jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant participant = jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant.builder()
                .id(20L)
                .contest(contest)
                .user(testUser)
                .status(jsd.toiyeujava.mini_leetcode.entity.ParticipantStatus.SUBMITTED)
                .violationCount(0)
                .build();

        when(problemRepository.findById(100L)).thenReturn(Optional.of(testProblem));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(contestRepository.findById(10L)).thenReturn(Optional.of(contest));
        when(contestParticipantRepository.findByContestIdAndUserId(10L, 1L)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> submissionService.createSubmission(100L, 1L, request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("already finalized their contest submission");

        verifyNoInteractions(storageService);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSubmission: từ chối nộp bài khi contest chưa chạy hoặc đã kết thúc")
    void createSubmission_ExamMode_ContestNotRunning() {
        SubmitCodeRequest request = new SubmitCodeRequest(
                SubmissionLanguage.PYTHON,
                "print('test')",
                10L
        );

        jsd.toiyeujava.mini_leetcode.contest.entity.Contest contest = jsd.toiyeujava.mini_leetcode.contest.entity.Contest.builder()
                .id(10L)
                .title("Midterm Exam")
                .status(jsd.toiyeujava.mini_leetcode.entity.ContestStatus.SCHEDULED)
                .build();

        when(problemRepository.findById(100L)).thenReturn(Optional.of(testProblem));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(contestRepository.findById(10L)).thenReturn(Optional.of(contest));

        assertThatThrownBy(() -> submissionService.createSubmission(100L, 1L, request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Contest is not currently active");

        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("createSubmission: ném lỗi ResourceNotFoundException khi không tìm thấy Problem")
    void createSubmission_ProblemNotFound() {
        SubmitCodeRequest request = new SubmitCodeRequest(SubmissionLanguage.PYTHON, "print('hello')");
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.createSubmission(999L, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Problem not found with id: 999");

        verifyNoInteractions(storageService);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSubmission: ném lỗi ResourceNotFoundException khi không tìm thấy User")
    void createSubmission_UserNotFound() {
        SubmitCodeRequest request = new SubmitCodeRequest(SubmissionLanguage.PYTHON, "print('hello')");
        when(problemRepository.findById(100L)).thenReturn(Optional.of(testProblem));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.createSubmission(100L, 999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 999");

        verifyNoInteractions(storageService);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateSubmissionVerdict: cập nhật thành công kết quả chấm bài")
    void updateSubmissionVerdict_Success() {
        Submission existing = Submission.builder()
                .id(501L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.JAVA)
                .status(SubmissionStatus.JUDGING)
                .build();

        when(submissionRepository.findById(501L)).thenReturn(Optional.of(existing));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Submission updated = submissionService.updateSubmissionVerdict(
                501L,
                SubmissionStatus.COMPLETED,
                SubmissionVerdict.AC,
                120,
                32000,
                10,
                10,
                null
        );

        assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(updated.getVerdict()).isEqualTo(SubmissionVerdict.AC);
        assertThat(updated.getExecutionTimeMs()).isEqualTo(120);
        assertThat(updated.getMemoryUsedKb()).isEqualTo(32000);
        assertThat(updated.getPassTestCases()).isEqualTo(10);
        assertThat(updated.getTotalTestCases()).isEqualTo(10);
        assertThat(updated.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("failSubmission: cập nhật trạng thái FAILED kèm thông báo lỗi chi tiết")
    void failSubmission_Success() {
        Submission existing = Submission.builder()
                .id(501L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.JAVA)
                .status(SubmissionStatus.PENDING)
                .build();

        when(submissionRepository.findById(501L)).thenReturn(Optional.of(existing));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Submission failed = submissionService.failSubmission(501L, "Failed to enqueue submission: Redis connection timeout");

        assertThat(failed.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Failed to enqueue submission: Redis connection timeout");
        assertThat(failed.getVerdict()).isNull();
        verify(submissionRepository).save(any(Submission.class));
    }

    @Test
    @DisplayName("failSubmission: ném lỗi ResourceNotFoundException khi không tìm thấy bài nộp")
    void failSubmission_NotFound_ThrowsResourceNotFoundException() {
        when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.failSubmission(999L, "Some error"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found with id: 999");

        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("failSubmission: khi errorMessage để trống hoặc null thì gán thông báo lỗi mặc định")
    void failSubmission_BlankErrorMessage_UsesDefaultMessage() {
        Submission existing = Submission.builder()
                .id(501L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.JAVA)
                .status(SubmissionStatus.PENDING)
                .build();

        when(submissionRepository.findById(501L)).thenReturn(Optional.of(existing));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Submission failed = submissionService.failSubmission(501L, "   ");

        assertThat(failed.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Submission evaluation failed");
    }

    @Test
    @DisplayName("getSubmissionById: ném ResourceNotFoundException khi id truyền vào là null")
    void getSubmissionById_NullId_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> submissionService.getSubmissionById(null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found with id: null");
    }

    @Test
    @DisplayName("getUserSubmissions: lấy danh sách phân trang bài nộp của user")
    void getUserSubmissions_Success() {
        Submission s1 = Submission.builder()
                .id(1L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.PYTHON)
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.AC)
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(submissionRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(s1), pageable, 1));

        Page<SubmissionResponse> responses = submissionService.getUserSubmissions(1L, pageable);

        assertThat(responses).hasSize(1);
        assertThat(responses.getContent().get(0).id()).isEqualTo(1L);
        assertThat(responses.getContent().get(0).verdict()).isEqualTo(SubmissionVerdict.AC);
    }

    @Test
    @DisplayName("getProblemSubmissions: lấy danh sách phân trang bài nộp của problem")
    void getProblemSubmissions_Success() {
        Submission s1 = Submission.builder()
                .id(1L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.JAVA)
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.AC)
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(submissionRepository.findByProblemIdOrderByCreatedAtDesc(100L, pageable))
                .thenReturn(new PageImpl<>(List.of(s1), pageable, 1));

        Page<SubmissionResponse> responses = submissionService.getProblemSubmissions(100L, pageable);

        assertThat(responses).hasSize(1);
        assertThat(responses.getContent().get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getSubmissionCode: đọc mã nguồn thành công từ StorageService")
    void getSubmissionCode_Success() {
        Submission s1 = Submission.builder()
                .id(1L)
                .problem(testProblem)
                .user(testUser)
                .codeStorageUri("submissions/100/1/uuid.java")
                .build();

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(storageService.extractObjectName("submissions/100/1/uuid.java")).thenReturn("submissions/100/1/uuid.java");
        when(storageService.downloadFile("submissions/100/1/uuid.java"))
                .thenReturn(new java.io.ByteArrayInputStream("public class Main {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        String code = submissionService.getSubmissionCode(1L);
        assertThat(code).isEqualTo("public class Main {}");
    }

    @Test
    @DisplayName("getSubmissionCode: xử lý an toàn khi codeStorageUri bị null hoặc blank")
    void getSubmissionCode_BlankStorageUri_ReturnsEmpty() {
        Submission s1 = Submission.builder()
                .id(1L)
                .problem(testProblem)
                .user(testUser)
                .codeStorageUri("   ")
                .build();

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(s1));
        String code = submissionService.getSubmissionCode(1L);
        assertThat(code).isEmpty();
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("createSubmission: ném lỗi khi problemId hoặc userId bị null")
    void createSubmission_NullIds_ThrowsResourceNotFoundException() {
        SubmitCodeRequest request = new SubmitCodeRequest(SubmissionLanguage.JAVA, "code");

        assertThatThrownBy(() -> submissionService.createSubmission(null, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Problem not found with id: null");

        assertThatThrownBy(() -> submissionService.createSubmission(100L, null, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: null");
    }

    @Test
    @DisplayName("createSubmission: ném lỗi khi request, code hoặc language bị thiếu")
    void createSubmission_InvalidRequest_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> submissionService.createSubmission(100L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request must not be null");

        SubmitCodeRequest nullLang = new SubmitCodeRequest(null, "print(1)");
        assertThatThrownBy(() -> submissionService.createSubmission(100L, 1L, nullLang))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Language is required");

        SubmitCodeRequest blankCode = new SubmitCodeRequest(SubmissionLanguage.PYTHON, "   ");
        assertThatThrownBy(() -> submissionService.createSubmission(100L, 1L, blankCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code must not be blank");
    }

    @Test
    @DisplayName("failSubmission: ném ResourceNotFoundException khi submissionId là null")
    void failSubmission_NullSubmissionId_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> submissionService.failSubmission(null, "Error"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Submission not found with id: null");
    }

    @Test
    @DisplayName("failSubmission: tự động cắt ngắn thông báo lỗi nếu vượt quá 2000 ký tự")
    void failSubmission_LongErrorMessage_Truncates() {
        Submission existing = Submission.builder()
                .id(501L)
                .problem(testProblem)
                .user(testUser)
                .language(SubmissionLanguage.JAVA)
                .status(SubmissionStatus.PENDING)
                .build();

        when(submissionRepository.findById(501L)).thenReturn(Optional.of(existing));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String hugeError = "E".repeat(3000);
        Submission result = submissionService.failSubmission(501L, hugeError);

        assertThat(result.getErrorMessage()).hasSize(2003); // 2000 + "..."
        assertThat(result.getErrorMessage()).endsWith("...");
    }

    @Test
    @DisplayName("getUserSubmissions & getProblemSubmissions: ném ResourceNotFoundException khi id bị null")
    void getSubmissions_NullIds_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> submissionService.getUserSubmissions(null, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: null");

        assertThatThrownBy(() -> submissionService.getProblemSubmissions(null, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Problem not found with id: null");
    }
}
