package jsd.toiyeujava.mini_leetcode.testcase.repository;

import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);
    List<TestCase> findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(Long problemId);
    long countByProblemId(Long problemId);
    long countByProblemIdAndIsHiddenFalse(Long problemId);

    /**
     * Đếm số testcase theo từng bài trong một lần truy vấn (GROUP BY), để
     * trang danh sách không phải chạy N+1 câu COUNT.
     */
    @Query("select tc.problem.id, count(tc) from TestCase tc where tc.problem.id in :problemIds group by tc.problem.id")
    List<Object[]> countByProblemIds(@Param("problemIds") Collection<Long> problemIds);
}
