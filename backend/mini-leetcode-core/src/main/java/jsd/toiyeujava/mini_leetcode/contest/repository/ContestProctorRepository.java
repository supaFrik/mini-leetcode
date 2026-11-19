package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContestProctorRepository extends JpaRepository<ContestProctor, Long> {

    boolean existsByContestIdAndUserId(Long contestId, Long userId);

    Optional<ContestProctor> findByContestIdAndUserId(Long contestId, Long userId);

    @Query("select p from ContestProctor p join fetch p.user where p.contestId = :contestId order by p.user.username")
    List<ContestProctor> findWithUserByContestId(Long contestId);

    /** [contestId, userId] của mọi giám thị, để gắn vào danh sách kỳ thi. */
    @Query("select p.contestId, p.user.id from ContestProctor p")
    List<Object[]> findAllPairs();
}
