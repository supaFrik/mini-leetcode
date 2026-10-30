package jsd.toiyeujava.mini_leetcode.submission.repository;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Submission> findByProblemIdOrderByCreatedAtDesc(Long problemId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemIdOrderByCreatedAtDesc(Long userId, Long problemId, Pageable pageable);

    List<Submission> findByStatus(SubmissionStatus status);

    long countByProblemIdAndVerdict(Long problemId, SubmissionVerdict verdict);

    /** Các bài nộp (không phải "run") trong một kỳ thi, dùng để tính bảng xếp hạng. */
    List<Submission> findByContestId(Long contestId);

    /** Id các bài mà user đã có ít nhất một bài nộp được chấm AC → tiến độ DONE. */
    @Query("select distinct s.problem.id from Submission s "
            + "where s.user.id = :userId and s.verdict = :verdict")
    List<Long> findSolvedProblemIdsByUserId(@Param("userId") Long userId,
                                             @Param("verdict") SubmissionVerdict verdict);

    /** Id các bài mà user đã từng nộp (bất kể kết quả) → tiến độ IN_PROGRESS. */
    @Query("select distinct s.problem.id from Submission s where s.user.id = :userId")
    List<Long> findAttemptedProblemIdsByUserId(@Param("userId") Long userId);
}
