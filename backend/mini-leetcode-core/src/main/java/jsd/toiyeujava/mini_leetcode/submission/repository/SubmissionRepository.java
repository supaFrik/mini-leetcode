package jsd.toiyeujava.mini_leetcode.submission.repository;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus;
import jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Bài nộp của user, lọc ở server. Khớp cách trang Submissions gọi tên verdict: bài chưa có verdict
     * mà FAILED được tính là RE, còn lại là PENDING.
     */
    @Query("select s from Submission s where s.user.id = :userId"
            + " and (:language is null or s.language = :language)"
            + " and (:verdict is null or s.verdict = :verdict or (:verdict = jsd.toiyeujava.mini_leetcode.entity.SubmissionVerdict.RE"
            + "      and s.verdict is null and s.status = jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus.FAILED))"
            + " and (:pending = false or (s.verdict is null and s.status <> jsd.toiyeujava.mini_leetcode.entity.SubmissionStatus.FAILED))"
            + " order by s.createdAt desc")
    Page<Submission> findMine(@Param("userId") Long userId,
                              @Param("verdict") SubmissionVerdict verdict,
                              @Param("pending") boolean pending,
                              @Param("language") SubmissionLanguage language,
                              Pageable pageable);

    /** Số bài nộp của user theo (verdict, status), để tóm tắt trên toàn bộ dữ liệu. */
    @Query("select s.verdict, s.status, count(s) from Submission s where s.user.id = :userId group by s.verdict, s.status")
    List<Object[]> countByVerdictAndStatus(@Param("userId") Long userId);

    Page<Submission> findByProblemIdOrderByCreatedAtDesc(Long problemId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemIdOrderByCreatedAtDesc(Long userId, Long problemId, Pageable pageable);

    List<Submission> findByStatus(SubmissionStatus status);

    long countByProblemIdAndVerdict(Long problemId, SubmissionVerdict verdict);

    /** Số bài nộp của một bài — dùng để chặn xoá cứng khi bài đã có lịch sử. */
    long countByProblemId(Long problemId);

    /** Các bài nộp (không phải "run") trong một kỳ thi, dùng để tính bảng xếp hạng. */
    List<Submission> findByContestId(Long contestId);

    /** Bài nộp của nhiều kỳ thi (nạp sẵn User), để xếp hạng nhiều kỳ trong một truy vấn. */
    @Query("select s from Submission s join fetch s.user where s.contestId in :contestIds")
    List<Submission> findWithUserByContestIdIn(@Param("contestIds") Collection<Long> contestIds);

    /** Id các bài mà user đã có ít nhất một bài nộp được chấm AC → tiến độ DONE. */
    @Query("select distinct s.problem.id from Submission s "
            + "where s.user.id = :userId and s.verdict = :verdict")
    List<Long> findSolvedProblemIdsByUserId(@Param("userId") Long userId,
                                             @Param("verdict") SubmissionVerdict verdict);

    /** Id các bài mà user đã từng nộp (bất kể kết quả) → tiến độ IN_PROGRESS. */
    @Query("select distinct s.problem.id from Submission s where s.user.id = :userId")
    List<Long> findAttemptedProblemIdsByUserId(@Param("userId") Long userId);
}
