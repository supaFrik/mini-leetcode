package jsd.toiyeujava.mini_leetcode.testcase.service;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;

import jsd.toiyeujava.mini_leetcode.testcase.dto.TestCaseResponse;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class TestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;

    public TestCaseService(
            ProblemRepository problemRepository,
            TestCaseRepository testCaseRepository,
            StorageService storageService) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.storageService = storageService;
    }

    @Transactional
    public TestCaseResponse addTestCase(
            Long problemId,
            MultipartFile inputFile,
            MultipartFile outputFile,
            Boolean isHidden,
            Integer orderIndex) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));

        if (inputFile == null || inputFile.isEmpty()) {
            throw new IllegalArgumentException("Input testcase file is required and cannot be empty");
        }
        if (outputFile == null || outputFile.isEmpty()) {
            throw new IllegalArgumentException("Expected output testcase file is required and cannot be empty");
        }

        if (orderIndex != null && orderIndex < 0) {
            throw new IllegalArgumentException("Order index must not be negative");
        }

        int index = orderIndex != null
                ? orderIndex
                : (int) testCaseRepository.countByProblemId(problemId);

        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String inputObjectName = String.format("problems/%d/testcases/%d_input_%s.txt", problemId, index, uuid);
        String outputObjectName = String.format("problems/%d/testcases/%d_output_%s.txt", problemId, index, uuid);

        String inputUri = null;
        String outputUri = null;
        try {
            try (InputStream inStream = inputFile.getInputStream();
                 InputStream outStream = outputFile.getInputStream()) {
                inputUri = storageService.uploadFile(
                        inputObjectName,
                        inStream,
                        inputFile.getSize(),
                        inputFile.getContentType()
                );
                outputUri = storageService.uploadFile(
                        outputObjectName,
                        outStream,
                        outputFile.getSize(),
                        outputFile.getContentType()
                );
            } catch (IOException e) {
                throw new StorageException("Failed to stream testcase files", e);
            }

            long totalSize = inputFile.getSize() + outputFile.getSize();

            TestCase testCase = TestCase.builder()
                    .problem(problem)
                    .inputFileUri(inputUri)
                    .expectedOutputUri(outputUri)
                    .isHidden(isHidden != null ? isHidden : true)
                    .orderIndex(index)
                    .fileSizeBytes(totalSize)
                    .build();

            testCase = testCaseRepository.save(testCase);
            return TestCaseResponse.fromEntity(testCase);
        } catch (Exception e) {
            if (inputUri != null) {
                storageService.deleteFileByUri(inputUri);
            }
            if (outputUri != null) {
                storageService.deleteFileByUri(outputUri);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> getTestCases(Long problemId, boolean includeHidden) {
        if (!problemRepository.existsById(problemId)) {
            throw new ResourceNotFoundException("Problem not found with id: " + problemId);
        }

        List<TestCase> testCases = includeHidden
                ? testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId)
                : testCaseRepository.findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(problemId);

        return testCases.stream().map(tc -> {
            String inContent = null;
            String outContent = null;
            if (!tc.getIsHidden() || includeHidden) {
                inContent = readContentQuietly(tc.getInputFileUri());
                outContent = readContentQuietly(tc.getExpectedOutputUri());
            }
            return TestCaseResponse.fromEntity(tc, inContent, outContent);
        }).toList();
    }

    private String readContentQuietly(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try (InputStream is = storageService.downloadFileByUri(uri)) {
            if (is == null) {
                return null;
            }
            byte[] bytes = is.readNBytes(8192);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void deleteTestCase(Long problemId, Long testCaseId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found with id: " + testCaseId));

        if (!testCase.getProblem().getId().equals(problemId)) {
            throw new IllegalArgumentException("Test case does not belong to problem: " + problemId);
        }

        storageService.deleteFileByUri(testCase.getInputFileUri());
        storageService.deleteFileByUri(testCase.getExpectedOutputUri());

        testCaseRepository.delete(testCase);
    }
}
