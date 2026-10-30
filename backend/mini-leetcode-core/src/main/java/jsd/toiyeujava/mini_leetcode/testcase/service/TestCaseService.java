package jsd.toiyeujava.mini_leetcode.testcase.service;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;

import jsd.toiyeujava.mini_leetcode.testcase.dto.TestCaseResponse;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemLifecycle;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

        // Testcase của bài đang PUBLISHED bị khoá: không thêm được testcase mới.
        if (ProblemLifecycle.isTestCaseFrozen(problem.getStatus())) {
            throw new StateConflictException("Test cases of a PUBLISHED problem are frozen and cannot be added");
        }

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

    @Transactional
    public TestCaseResponse updateTestCase(
            Long problemId,
            Long testCaseId,
            MultipartFile inputFile,
            MultipartFile outputFile,
            Boolean isHidden,
            Integer orderIndex) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found with id: " + testCaseId));

        if (!testCase.getProblem().getId().equals(problemId)) {
            throw new IllegalArgumentException("Test case does not belong to problem: " + problemId);
        }

        // Testcase của bài đang PUBLISHED bị khoá: bài đã được chấm, không được
        // đổi bộ testcase giữa chừng.
        if (ProblemLifecycle.isTestCaseFrozen(testCase.getProblem().getStatus())) {
            throw new StateConflictException("Test cases of a PUBLISHED problem are frozen and cannot be modified");
        }

        boolean hasInput = inputFile != null && !inputFile.isEmpty();
        boolean hasOutput = outputFile != null && !outputFile.isEmpty();

        if (!hasInput && !hasOutput && isHidden == null && orderIndex == null) {
            throw new IllegalArgumentException("Nothing to update: provide at least one file, isHidden or orderIndex");
        }
        if (orderIndex != null && orderIndex < 0) {
            throw new IllegalArgumentException("Order index must not be negative");
        }

        // Khoá object là bất biến: file mới luôn nằm ở khoá MỚI, không ghi đè
        // khoá cũ. Nhờ vậy nếu giao dịch rollback thì file cũ vẫn nguyên vẹn.
        String oldInputUri = testCase.getInputFileUri();
        String oldOutputUri = testCase.getExpectedOutputUri();

        String newInputUri = oldInputUri;
        String newOutputUri = oldOutputUri;
        String uploadedInputUri = null;
        String uploadedOutputUri = null;

        try {
            if (hasInput) {
                String objectName = String.format("problems/%d/testcases/%d_input_%s.txt",
                        problemId, testCase.getOrderIndex(), UUID.randomUUID());
                try (InputStream inStream = inputFile.getInputStream()) {
                    uploadedInputUri = storageService.uploadFile(
                            objectName, inStream, inputFile.getSize(), inputFile.getContentType());
                } catch (IOException e) {
                    throw new StorageException("Failed to stream testcase input file", e);
                }
                newInputUri = uploadedInputUri;
            }
            if (hasOutput) {
                String objectName = String.format("problems/%d/testcases/%d_output_%s.txt",
                        problemId, testCase.getOrderIndex(), UUID.randomUUID());
                try (InputStream outStream = outputFile.getInputStream()) {
                    uploadedOutputUri = storageService.uploadFile(
                            objectName, outStream, outputFile.getSize(), outputFile.getContentType());
                } catch (IOException e) {
                    throw new StorageException("Failed to stream testcase output file", e);
                }
                newOutputUri = uploadedOutputUri;
            }

            testCase.setInputFileUri(newInputUri);
            testCase.setExpectedOutputUri(newOutputUri);
            if (isHidden != null) {
                testCase.setIsHidden(isHidden);
            }
            if (orderIndex != null) {
                testCase.setOrderIndex(orderIndex);
            }
            if (hasInput || hasOutput) {
                testCase.setFileSizeBytes((hasInput ? inputFile.getSize() : 0)
                        + (hasOutput ? outputFile.getSize() : 0));
            }

            TestCase saved = testCaseRepository.save(testCase);

            // Dọn file cũ SAU khi giao dịch commit. Nếu commit thất bại, file
            // cũ vẫn được giữ nguyên và file mới bị xoá.
            scheduleCleanupAfterCommit(oldInputUri, newInputUri, oldOutputUri, newOutputUri);

            return TestCaseResponse.fromEntity(saved);
        } catch (Exception e) {
            if (uploadedInputUri != null) {
                storageService.deleteFileByUri(uploadedInputUri);
            }
            if (uploadedOutputUri != null) {
                storageService.deleteFileByUri(uploadedOutputUri);
            }
            throw e;
        }
    }

    /**
     * Đăng ký dọn file cũ sau khi giao dịch commit thành công. Nếu không có
     * giao dịch đang chạy (ví dụ gọi ngoài @Transactional), dọn ngay.
     */
    private void scheduleCleanupAfterCommit(String oldInputUri, String newInputUri,
                                            String oldOutputUri, String newOutputUri) {
        Runnable cleanup = () -> {
            if (oldInputUri != null && !oldInputUri.equals(newInputUri)) {
                storageService.deleteFileByUri(oldInputUri);
            }
            if (oldOutputUri != null && !oldOutputUri.equals(newOutputUri)) {
                storageService.deleteFileByUri(oldOutputUri);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
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

        // Testcase của bài đang PUBLISHED bị khoá: không được xoá giữa kỳ chấm.
        if (ProblemLifecycle.isTestCaseFrozen(testCase.getProblem().getStatus())) {
            throw new StateConflictException("Test cases of a PUBLISHED problem are frozen and cannot be deleted");
        }

        storageService.deleteFileByUri(testCase.getInputFileUri());
        storageService.deleteFileByUri(testCase.getExpectedOutputUri());

        testCaseRepository.delete(testCase);
    }
}
