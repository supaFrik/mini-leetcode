package jsd.toiyeujava.mini_leetcode.problem.repository;

import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long>, JpaSpecificationExecutor<Problem> {
    /** Bài đang gắn chủ đề {@code tag} (không phân biệt hoa thường). */
    @Query("select distinct p from Problem p join p.tags t where lower(t) = lower(:tag)")
    List<Problem> findByTag(@Param("tag") String tag);

    Optional<Problem> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
