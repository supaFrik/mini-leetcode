package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.problem.dto.CreateProblemRequest;
import jsd.toiyeujava.mini_leetcode.problem.dto.ProblemResponse;
import jsd.toiyeujava.mini_leetcode.problem.dto.UpdateProblemRequest;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemTagRepository;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemService;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private ProblemService problemService;

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(problemRepository, testCaseRepository, storageService,
                problemTagRepository, submissionRepository);
    }

    @Test
    @DisplayName("createProblem: generates unique slug and sets defaults")
    void createProblem_generatesUniqueSlug() {
        User creator = User.builder().id(1L).username("admin").build();
        CreateProblemRequest request = new CreateProblemRequest("Valid Palindrome", "A phrase...", Difficulty.EASY, null, null);

        when(problemRepository.existsBySlug("valid-palindrome")).thenReturn(false);
        when(problemRepository.save(any(Problem.class))).thenAnswer(i -> {
            Problem p = i.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProblemResponse response = problemService.createProblem(request, creator);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.slug()).isEqualTo("valid-palindrome");
        assertThat(response.timeLimitMs()).isEqualTo(1000);
        assertThat(response.memoryLimitMb()).isEqualTo(256);
        assertThat(response.status()).isEqualTo(ProblemStatus.DRAFT);
    }

    @Test
    @DisplayName("updateProblem: updates fields and status")
    void updateProblem_updatesFields() {
        Problem existing = Problem.builder()
                .id(1L)
                .title("Old Title")
                .slug("old-title")
                .description("Old Desc")
                .difficulty(Difficulty.EASY)
                .status(ProblemStatus.DRAFT)
                .build();

        when(problemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(problemRepository.existsBySlug("new-title")).thenReturn(false);
        when(problemRepository.save(any(Problem.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProblemRequest updateReq = new UpdateProblemRequest(
                "New Title",
                "New Desc",
                Difficulty.HARD,
                2000,
                512,
                ProblemStatus.PUBLISHED
        );

        ProblemResponse updated = problemService.updateProblem(1L, updateReq);

        assertThat(updated.title()).isEqualTo("New Title");
        assertThat(updated.slug()).isEqualTo("new-title");
        assertThat(updated.description()).isEqualTo("New Desc");
        assertThat(updated.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(updated.status()).isEqualTo(ProblemStatus.PUBLISHED);
        assertThat(updated.timeLimitMs()).isEqualTo(2000);
        assertThat(updated.memoryLimitMb()).isEqualTo(512);
    }

    @Test
    @DisplayName("updateProblem: throws when problem not found")
    void updateProblem_notFound_throwsException() {
        when(problemRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateProblemRequest updateReq = new UpdateProblemRequest("T", null, null, null, null, null);

        assertThatThrownBy(() -> problemService.updateProblem(99L, updateReq))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Problem not found with id: 99");
    }

    @Test
    @DisplayName("getProblemById: throws when problem not found")
    void getProblemById_notFound_throwsException() {
        when(problemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> problemService.getProblemById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProblemBySlug: throws when slug not found")
    void getProblemBySlug_notFound_throwsException() {
        when(problemRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> problemService.getProblemBySlug("unknown-slug"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteProblem: deletes problem and cleans testcases from storage when exists, throws when missing")
    void deleteProblem_behaviour() {
        Problem problem = Problem.builder().id(1L).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        TestCase tc = TestCase.builder()
                .problem(problem)
                .inputFileUri("minio://testcases/in.txt")
                .expectedOutputUri("minio://testcases/out.txt")
                .build();
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(tc));
        doNothing().when(storageService).deleteFileByUri(anyString());
        doNothing().when(testCaseRepository).deleteAll(anyList());
        doNothing().when(problemRepository).delete(problem);

        problemService.deleteProblem(1L);
        verify(storageService).deleteFileByUri("minio://testcases/in.txt");
        verify(storageService).deleteFileByUri("minio://testcases/out.txt");
        verify(testCaseRepository).deleteAll(List.of(tc));
        verify(problemRepository).delete(problem);

        when(problemRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> problemService.deleteProblem(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateProblem: blank title throws IllegalArgumentException")
    void updateProblem_blankTitle_throwsException() {
        Problem existing = Problem.builder().id(1L).title("Old").build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateProblemRequest updateReq = new UpdateProblemRequest("   ", null, null, null, null, null);

        assertThatThrownBy(() -> problemService.updateProblem(1L, updateReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Problem title cannot be blank");
    }

    @Test
    @DisplayName("generateUniqueSlug: handles multiple collisions by incrementing counter")
    void generateUniqueSlug_multipleCollisions() {
        when(problemRepository.existsBySlug("two-sum")).thenReturn(true);
        when(problemRepository.existsBySlug("two-sum-1")).thenReturn(true);
        when(problemRepository.existsBySlug("two-sum-2")).thenReturn(false);

        String slug = problemService.generateUniqueSlug("Two Sum");
        assertThat(slug).isEqualTo("two-sum-2");
    }

    @Test
    @DisplayName("updateProblem: same title does not regenerate slug")
    void updateProblem_sameTitle_keepsSlug() {
        Problem existing = Problem.builder()
                .id(1L)
                .title("Two Sum")
                .slug("two-sum")
                .description("Old Desc")
                .difficulty(Difficulty.EASY)
                .status(ProblemStatus.DRAFT)
                .build();

        when(problemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(problemRepository.save(any(Problem.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProblemRequest updateReq = new UpdateProblemRequest(
                "Two Sum", // same title
                null,
                null,
                null,
                null,
                null
        );

        ProblemResponse updated = problemService.updateProblem(1L, updateReq);
        assertThat(updated.slug()).isEqualTo("two-sum");
        verify(problemRepository, never()).existsBySlug(anyString());
    }

    @Test
    @DisplayName("toSlug: handles null, empty, symbols, Vietnamese characters")
    void toSlug_edgeCases() {
        assertThat(ProblemService.toSlug(null)).isEqualTo("problem");
        assertThat(ProblemService.toSlug("   ")).isEqualTo("problem");
        assertThat(ProblemService.toSlug("Hello World!")).isEqualTo("hello-world");
        assertThat(ProblemService.toSlug("Two Sum II - Input Array Is Sorted")).isEqualTo("two-sum-ii-input-array-is-sorted");
        assertThat(ProblemService.toSlug("---Leading and Trailing---")).isEqualTo("leading-and-trailing");
    }

    @Test
    @DisplayName("getProblems: returns paged result")
    void getProblems_returnsPagedResult() {
        Problem p = Problem.builder().id(1L).title("Two Sum").slug("two-sum").difficulty(Difficulty.EASY).build();
        Page<Problem> page = new PageImpl<>(List.of(p));
        when(problemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<ProblemResponse> result = problemService.getProblems(Difficulty.EASY, ProblemStatus.PUBLISHED, "two", List.of(), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Two Sum");
    }
}
