package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.testcase.dto.TestCaseResponse;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.testcase.service.TestCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private StorageService storageService;

    private TestCaseService testCaseService;

    @BeforeEach
    void setUp() {
        testCaseService = new TestCaseService(problemRepository, testCaseRepository, storageService);
    }

    @Test
    @DisplayName("addTestCase: validates empty or null input files")
    void addTestCase_validatesInput() {
        Problem problem = Problem.builder().id(1L).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        MockMultipartFile emptyFile = new MockMultipartFile("inputFile", "", "text/plain", new byte[0]);
        MockMultipartFile validFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "abc".getBytes());

        assertThatThrownBy(() -> testCaseService.addTestCase(1L, null, validFile, false, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> testCaseService.addTestCase(1L, emptyFile, validFile, false, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> testCaseService.addTestCase(1L, validFile, null, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addTestCase: problem not found throws ResourceNotFoundException")
    void addTestCase_problemNotFound() {
        when(problemRepository.findById(99L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "f.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() -> testCaseService.addTestCase(99L, file, file, false, 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("addTestCase: streams files and saves metadata")
    void addTestCase_success() {
        Problem problem = Problem.builder().id(1L).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), any()))
                .thenReturn("minio://testcases/path.txt");

        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> {
            TestCase tc = i.getArgument(0);
            tc.setId(100L);
            return tc;
        });

        MockMultipartFile inFile = new MockMultipartFile("inputFile", "in.txt", "text/plain", "input".getBytes());
        MockMultipartFile outFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "output".getBytes());

        TestCaseResponse response = testCaseService.addTestCase(1L, inFile, outFile, false, 1);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.problemId()).isEqualTo(1L);
        assertThat(response.isHidden()).isFalse();
        assertThat(response.orderIndex()).isEqualTo(1);
        assertThat(response.fileSizeBytes()).isEqualTo(inFile.getSize() + outFile.getSize());

        verify(storageService, times(2)).uploadFile(anyString(), any(InputStream.class), anyLong(), any());
    }

    @Test
    @DisplayName("addTestCase: negative orderIndex throws IllegalArgumentException")
    void addTestCase_negativeOrderIndex_throwsException() {
        Problem problem = Problem.builder().id(1L).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        MockMultipartFile inFile = new MockMultipartFile("inputFile", "in.txt", "text/plain", "input".getBytes());
        MockMultipartFile outFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "output".getBytes());

        assertThatThrownBy(() -> testCaseService.addTestCase(1L, inFile, outFile, false, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order index must not be negative");
    }

    @Test
    @DisplayName("addTestCase: null orderIndex auto-increments based on existing count")
    void addTestCase_nullOrderIndex_autoAssignsNextSequentialIndex() {
        Problem problem = Problem.builder().id(1L).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.countByProblemId(1L)).thenReturn(3L);
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), any()))
                .thenReturn("minio://testcases/path.txt");

        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> {
            TestCase tc = i.getArgument(0);
            tc.setId(200L);
            return tc;
        });

        MockMultipartFile inFile = new MockMultipartFile("inputFile", "in.txt", "text/plain", "input".getBytes());
        MockMultipartFile outFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "output".getBytes());

        TestCaseResponse response = testCaseService.addTestCase(1L, inFile, outFile, false, null);
        assertThat(response.orderIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("addTestCase: cleans up uploaded inputFile when outputFile upload fails")
    void addTestCase_cleansUpUploadedFileOnFailure() {
        Problem problem = Problem.builder().id(1L).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        when(storageService.uploadFile(contains("input"), any(InputStream.class), anyLong(), any()))
                .thenReturn("minio://testcases/problems/1/testcases/0_input_abc.txt");
        when(storageService.uploadFile(contains("output"), any(InputStream.class), anyLong(), any()))
                .thenThrow(new jsd.toiyeujava.mini_leetcode.exception.StorageException("MinIO network failure"));

        MockMultipartFile inFile = new MockMultipartFile("inputFile", "in.txt", "text/plain", "input".getBytes());
        MockMultipartFile outFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "output".getBytes());

        assertThatThrownBy(() -> testCaseService.addTestCase(1L, inFile, outFile, false, 0))
                .isInstanceOf(jsd.toiyeujava.mini_leetcode.exception.StorageException.class);

        verify(storageService).deleteFileByUri("minio://testcases/problems/1/testcases/0_input_abc.txt");
    }

    @Test
    @DisplayName("getTestCases: filters hidden based on flag")
    void getTestCases_visibilityFilter() {
        when(problemRepository.existsById(1L)).thenReturn(true);
        TestCase publicTc = TestCase.builder().id(1L).isHidden(false).build();
        TestCase hiddenTc = TestCase.builder().id(2L).isHidden(true).build();

        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(publicTc, hiddenTc));
        when(testCaseRepository.findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(1L)).thenReturn(List.of(publicTc));

        List<TestCaseResponse> allCases = testCaseService.getTestCases(1L, true);
        assertThat(allCases).hasSize(2);

        List<TestCaseResponse> publicOnly = testCaseService.getTestCases(1L, false);
        assertThat(publicOnly).hasSize(1);
    }

    @Test
    @DisplayName("deleteTestCase: validates problem ID match and deletes")
    void deleteTestCase_validation() {
        Problem problem1 = Problem.builder().id(1L).build();
        Problem problem2 = Problem.builder().id(2L).build();
        TestCase tc = TestCase.builder().id(10L).problem(problem1).build();

        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc));

        // Wrong problem ID
        assertThatThrownBy(() -> testCaseService.deleteTestCase(2L, 10L))
                .isInstanceOf(IllegalArgumentException.class);

        // Correct problem ID
        testCaseService.deleteTestCase(1L, 10L);
        verify(testCaseRepository).delete(tc);
    }

    @Test
    @DisplayName("updateTestCase: writes new object keys and keeps the old URI until commit")
    void updateTestCase_usesImmutableKeys() {
        Problem problem = Problem.builder().id(1L).status(ProblemStatus.DRAFT).build();
        TestCase tc = TestCase.builder()
                .id(10L)
                .problem(problem)
                .inputFileUri("minio://testcases/old_input.txt")
                .expectedOutputUri("minio://testcases/old_output.txt")
                .isHidden(true)
                .orderIndex(2)
                .build();

        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc));
        when(storageService.uploadFile(anyString(), any(InputStream.class), anyLong(), any()))
                .thenAnswer(i -> "minio://testcases/" + i.getArgument(0));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile inFile = new MockMultipartFile("inputFile", "in.txt", "text/plain", "new input".getBytes());
        MockMultipartFile outFile = new MockMultipartFile("outputFile", "out.txt", "text/plain", "new output".getBytes());

        TestCaseResponse response = testCaseService.updateTestCase(1L, 10L, inFile, outFile, false, 5);

        // New URIs point at new object keys, never overwriting the old ones.
        assertThat(response.inputFileUri()).contains("input_").doesNotContain("old_input");
        assertThat(response.expectedOutputUri()).contains("output_").doesNotContain("old_output");
        assertThat(response.isHidden()).isFalse();
        assertThat(response.orderIndex()).isEqualTo(5);
        assertThat(response.fileSizeBytes()).isEqualTo(inFile.getSize() + outFile.getSize());
    }

    @Test
    @DisplayName("updateTestCase: changing only metadata keeps the existing files")
    void updateTestCase_metadataOnly_keepsFiles() {
        Problem problem = Problem.builder().id(1L).status(ProblemStatus.DRAFT).build();
        TestCase tc = TestCase.builder()
                .id(10L)
                .problem(problem)
                .inputFileUri("minio://testcases/old_input.txt")
                .expectedOutputUri("minio://testcases/old_output.txt")
                .isHidden(true)
                .orderIndex(2)
                .build();

        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        TestCaseResponse response = testCaseService.updateTestCase(1L, 10L, null, null, false, null);

        assertThat(response.inputFileUri()).isEqualTo("minio://testcases/old_input.txt");
        assertThat(response.expectedOutputUri()).isEqualTo("minio://testcases/old_output.txt");
        assertThat(response.isHidden()).isFalse();
        verify(storageService, never()).uploadFile(anyString(), any(InputStream.class), anyLong(), any());
    }

    @Test
    @DisplayName("updateTestCase: rejects updates on a PUBLISHED problem (frozen test cases)")
    void updateTestCase_publishedProblem_throwsConflict() {
        Problem problem = Problem.builder().id(1L).status(ProblemStatus.PUBLISHED).build();
        TestCase tc = TestCase.builder().id(10L).problem(problem).build();

        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc));

        MockMultipartFile file = new MockMultipartFile("inputFile", "in.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() -> testCaseService.updateTestCase(1L, 10L, file, file, null, null))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    @DisplayName("addTestCase: rejects new test cases on a PUBLISHED problem (frozen)")
    void addTestCase_publishedProblem_throwsConflict() {
        Problem problem = Problem.builder().id(1L).status(ProblemStatus.PUBLISHED).build();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));

        MockMultipartFile file = new MockMultipartFile("inputFile", "in.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() -> testCaseService.addTestCase(1L, file, file, false, 0))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    @DisplayName("deleteTestCase: rejects deletion on a PUBLISHED problem (frozen)")
    void deleteTestCase_publishedProblem_throwsConflict() {
        Problem problem = Problem.builder().id(1L).status(ProblemStatus.PUBLISHED).build();
        TestCase tc = TestCase.builder().id(10L).problem(problem).build();
        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc));

        assertThatThrownBy(() -> testCaseService.deleteTestCase(1L, 10L))
                .isInstanceOf(StateConflictException.class);

        verify(testCaseRepository, never()).delete(any(TestCase.class));
    }
}
