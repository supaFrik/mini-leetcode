package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Long> {

    Optional<ContestParticipant> findByContestIdAndUserId(Long contestId, Long userId);

    List<ContestParticipant> findByContestId(Long contestId);

    /** Mọi kỳ thi user đã đăng ký, trong một truy vấn. */
    List<ContestParticipant> findByUserId(Long userId);

    /** Thí sinh của nhiều kỳ thi (nạp sẵn User để lấy username khi xếp hạng). */
    @Query("select p from ContestParticipant p join fetch p.user where p.contest.id in :contestIds")
    List<ContestParticipant> findWithUserByContestIdIn(@Param("contestIds") Collection<Long> contestIds);
}
