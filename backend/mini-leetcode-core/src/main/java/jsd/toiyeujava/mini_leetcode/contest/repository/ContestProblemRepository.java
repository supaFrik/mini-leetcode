package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
