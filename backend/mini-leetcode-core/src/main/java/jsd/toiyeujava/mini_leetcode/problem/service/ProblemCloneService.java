package jsd.toiyeujava.mini_leetcode.problem.service;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.exception.StorageException;
import jsd.toiyeujava.mini_leetcode.problem.dto.ProblemResponse;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thao tác của Course Builder trên đề (specs/course-builder.spec.md): nhân bản stage, mở khoá stage đang chạy.
 */
@Service
public class ProblemCloneService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;
    private final ProblemService problemService;

    public ProblemCloneService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                               StorageService storageService, ProblemService problemService) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.storageService = storageService;
        this.problemService = problemService;
    }

    /**
     * Đề DRAFT mới chép từ {@code sourceId}: đề bài, giới hạn, loại, tags, grading config, starter, lời giải mẫu
     * và toàn bộ testcase (giữ nhãn, ẩn / công khai, thứ tự) — đúng quy ước testcase cộng dồn giữa các stage.
     * Slug sinh từ {@code title}, tự thêm hậu tố khi trùng.
     */
    @Transactional
    public ProblemResponse cloneProblem(Long sourceId, String title, User creator) {
        Problem source = problemRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + sourceId));
        String newTitle = title == null || title.isBlank() ? source.getTitle() + " (copy)" : title.trim();
        Problem copy = problemRepository.save(Problem.builder()
                .title(newTitle)
                .slug(problemService.generateUniqueSlug(newTitle))
                .description(source.getDescription())
                .difficulty(source.getDifficulty())
                .timeLimitMs(source.getTimeLimitMs())
                .memoryLimitMb(source.getMemoryLimitMb())
                .problemType(source.getProblemType())
                .starterTemplates(source.getStarterTemplates())
                .gradingConfig(source.getGradingConfig())
                .referenceSolution(source.getReferenceSolution())
                .tags(new ArrayList<>(source.getTags()))
                .status(ProblemStatus.DRAFT)
                .createdBy(creator)
                .build());

        List<String> uploaded = new ArrayList<>();
        // Transaction rollback ⇒ xoá file đã tải lên để không để lại rác trong storage.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        uploaded.forEach(storageService::deleteFileByUri);
                    }
                }
            });
        }
        for (TestCase tc : testCaseRepository.findByProblemIdOrderByOrderIndexAsc(sourceId)) {
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            String prefix = String.format("problems/%d/testcases/%d_", copy.getId(), tc.getOrderIndex());
            String input = copyFile(tc.getInputFileUri(), prefix + "input_" + uuid + ".txt", uploaded);
            String output = copyFile(tc.getExpectedOutputUri(), prefix + "output_" + uuid + ".txt", uploaded);
            testCaseRepository.save(TestCase.builder()
                    .problem(copy)
                    .inputFileUri(input)
                    .expectedOutputUri(output)
                    .isHidden(tc.getIsHidden())
                    .orderIndex(tc.getOrderIndex())
                    .fileSizeBytes(tc.getFileSizeBytes())
                    .label(tc.getLabel())
                    .build());
        }
        return ProblemResponse.fromEntity(copy, testCaseRepository.countByProblemId(copy.getId()), null);
    }

    /**
     * Mở khoá testcase / grading của đề đang PUBLISHED: PUBLISHED → ARCHIVED → DRAFT. Trong lúc này học viên không
     * nộp được stage đó (đã chốt C1); publish lại qua Course Builder.
     */
    @Transactional
    public ProblemResponse unlock(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));
        if (problem.getStatus() != ProblemStatus.PUBLISHED) {
            throw new StateConflictException("Only a PUBLISHED problem needs unlocking; this one is " + problem.getStatus());
        }
        problemService.transitionStatus(problemId, ProblemStatus.ARCHIVED);
        return problemService.transitionStatus(problemId, ProblemStatus.DRAFT);
    }

    private String copyFile(String uri, String objectName, List<String> uploaded) {
        byte[] bytes;
        try (InputStream in = storageService.downloadFileByUri(uri)) {
            if (in == null) {
                throw new StorageException("Test case file not found: " + uri);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new StorageException("Failed to copy test case file", e);
        }
        String target = storageService.uploadFile(objectName, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        uploaded.add(target);
        return target;
    }
}
