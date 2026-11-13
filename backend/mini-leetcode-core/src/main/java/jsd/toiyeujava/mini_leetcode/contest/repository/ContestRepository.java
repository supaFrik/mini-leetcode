package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContestRepository extends JpaRepository<Contest, Long> {
    Optional<Contest> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Danh sách kỳ thi cho trang admin trong MỘT truy vấn: mỗi dòng là
     * {@code [Contest, số bài, số thí sinh]} (tránh N+1 khi đếm).
     */
    @Query("select c,"
            + " (select count(cp) from ContestProblem cp where cp.contest = c),"
            + " (select count(p) from ContestParticipant p where p.contest = c)"
            + " from Contest c order by c.startTime desc")
    List<Object[]> findAllWithCounts();
}
