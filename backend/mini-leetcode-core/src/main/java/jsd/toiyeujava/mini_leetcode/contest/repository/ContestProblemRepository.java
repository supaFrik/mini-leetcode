package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContestProblemRepository extends JpaRepository<ContestProblem, Long> {

    List<ContestProblem> findByContestIdOrderByOrderIndexAsc(Long contestId);

    Optional<ContestProblem> findByContestIdAndProblemId(Long contestId, Long problemId);

    /** Bài của nhiều kỳ thi trong một truy vấn (nạp sẵn Problem), tránh N+1 khi liệt kê kỳ thi. */
    @Query("select cp from ContestProblem cp join fetch cp.problem where cp.contest.id in :contestIds order by cp.orderIndex asc")
    List<ContestProblem> findWithProblemByContestIdIn(@Param("contestIds") Collection<Long> contestIds);

    /**
     * Các kỳ thi chứa bài này — dùng để chặn console OOP khi đang diễn ra kỳ thi.
     */
    @Query("select cp.contest from ContestProblem cp where cp.problem.id = :problemId")
    List<Contest> findContestsByProblemId(@Param("problemId") Long problemId);

    /** Bài của một kỳ thi kèm Problem (cho trình soạn kỳ thi). */
    @Query("select cp from ContestProblem cp join fetch cp.problem where cp.contest.id = :contestId order by cp.orderIndex asc")
    List<ContestProblem> findWithProblemByContestId(@Param("contestId") Long contestId);

    /**
     * Xoá ngay (không chờ flush) để có thể chèn lại cùng cặp (contest, problem) trong cùng giao dịch
     * mà không vướng ràng buộc unique — Hibernate mặc định chạy INSERT trước DELETE khi flush.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ContestProblem cp where cp.contest.id = :contestId")
    int deleteByContestId(@Param("contestId") Long contestId);
}
