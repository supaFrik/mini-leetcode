package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.ProctoringEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProctoringEventRepository extends JpaRepository<ProctoringEvent, Long> {

    /** Toàn bộ sự kiện của kỳ thi, mới nhất trước. */
    List<ProctoringEvent> findByContestIdOrderByCreatedAtDesc(Long contestId);

    @Query("SELECT e FROM ProctoringEvent e WHERE e.contest.id = :contestId AND e.user.id = :userId ORDER BY e.createdAt DESC")
    List<ProctoringEvent> findByContestIdAndUserId(@Param("contestId") Long contestId, @Param("userId") Long userId, Pageable pageable);
}
