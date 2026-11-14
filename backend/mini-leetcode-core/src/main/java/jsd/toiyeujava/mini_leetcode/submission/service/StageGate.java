package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.StageLockedException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.studyplan.repository.StudyPlanRepository;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Khoá stage tuần tự (specs/study-plan.spec.md, FR-SP-003): đề là stage N > 1 của một mục lộ trình
 * PUBLISHED thì người học phải AC stage N-1 trước — cả khi nộp bài lẫn khi mở đề.
 * Người gọi tự bỏ qua cho INSTRUCTOR / ADMIN.
 */
@Service
@RequiredArgsConstructor
public class StageGate {

    private final StudyPlanRepository studyPlanRepository;
    private final SubmissionRepository submissionRepository;

    /** @param userId {@code null} = khách (chưa AC gì) ⇒ mọi stage sau stage 1 đều khoá. */
    public void assertUnlocked(Problem problem, Long userId) {
        List<Object[]> position = studyPlanRepository.findStagePosition(problem.getId());
        if (position.isEmpty()) {
            return;
        }
        // Stage đang được giảng viên sửa (Unlock trong Course Builder, hoặc mới thêm chưa publish).
        if (problem.getStatus() != ProblemStatus.PUBLISHED) {
            throw new StageLockedException("This stage is being updated. Try again later.");
        }
        long itemId = ((Number) position.get(0)[0]).longValue();
        int stageNo = ((Number) position.get(0)[1]).intValue();
        if (stageNo == 0) {
            return;
        }
        List<Number> previous = studyPlanRepository.findStageProblemId(itemId, stageNo - 1);
        if (!previous.isEmpty() && (userId == null || !submissionRepository
                .existsByUser_IdAndProblem_IdAndVerdictAndQuickRunFalse(userId, previous.get(0).longValue(), SubmissionVerdict.AC))) {
            throw new StageLockedException("Complete stage " + stageNo + " before opening stage " + (stageNo + 1));
        }
    }
}
