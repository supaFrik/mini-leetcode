package jsd.toiyeujava.mini_leetcode.submission.service;

import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.StudyPlanEntryType;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.exception.StageLockedException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.studyplan.repository.StudyPlanRepository;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Khoá tuần tự (specs/study-plan.spec.md, FR-SP-003) trong lộ trình PUBLISHED: đề của một session chỉ mở khi
 * mọi đề của các session trước đã AC; một project stage còn cần các stage đứng trước trong cùng session đã AC.
 * Lesson không khoá. Người gọi tự bỏ qua cho INSTRUCTOR / ADMIN.
 */
@Service
@RequiredArgsConstructor
public class StageGate {

    private final StudyPlanRepository studyPlanRepository;
    private final SubmissionRepository submissionRepository;

    /** @param userId {@code null} = khách (chưa AC gì) ⇒ mọi đề có điều kiện trước đều khoá. */
    public void assertUnlocked(Problem problem, Long userId) {
        List<Object[]> position = studyPlanRepository.findStagePosition(problem.getId());
        if (position.isEmpty()) {
            return;
        }
        // Đề đang được giảng viên sửa (Unlock trong Course Builder, hoặc mới thêm chưa publish).
        if (problem.getStatus() != ProblemStatus.PUBLISHED) {
            throw new StageLockedException("This stage is being updated. Try again later.");
        }
        Object[] row = position.get(0);
        boolean stage = StudyPlanEntryType.STAGE.name().equals(String.valueOf(row[4]));
        List<Number> required = studyPlanRepository.findPrerequisiteProblemIds(((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(), ((Number) row[2]).intValue(), stage ? ((Number) row[3]).intValue() : -1);
        if (required.isEmpty()) {
            return;
        }
        Set<Long> solved = userId == null ? Set.of()
                : new HashSet<>(submissionRepository.findSolvedProblemIdsByUserId(userId, SubmissionVerdict.AC));
        if (!required.stream().allMatch(id -> solved.contains(id.longValue()))) {
            throw new StageLockedException("Complete the previous sessions and stages before opening this one");
        }
    }
}
