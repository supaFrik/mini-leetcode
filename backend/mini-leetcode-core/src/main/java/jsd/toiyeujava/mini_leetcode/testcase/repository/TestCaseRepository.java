package jsd.toiyeujava.mini_leetcode.testcase.repository;

import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);
    List<TestCase> findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(Long problemId);
    long countByProblemId(Long problemId);
    long countByProblemIdAndIsHiddenFalse(Long problemId);
}
