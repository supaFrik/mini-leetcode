package jsd.toiyeujava.mini_leetcode.contest.repository;

import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContestProblemRepository extends JpaRepository<ContestProblem, Long> {

    List<ContestProblem> findByContestIdOrderByOrderIndexAsc(Long contestId);

    Optional<ContestProblem> findByContestIdAndProblemId(Long contestId, Long problemId);
}
