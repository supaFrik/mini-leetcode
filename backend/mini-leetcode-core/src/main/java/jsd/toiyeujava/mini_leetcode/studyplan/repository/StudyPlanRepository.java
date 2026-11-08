package jsd.toiyeujava.mini_leetcode.studyplan.repository;

import jsd.toiyeujava.mini_leetcode.entity.StudyPlanStatus;
import jsd.toiyeujava.mini_leetcode.studyplan.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    Optional<StudyPlan> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<StudyPlan> findByStatusOrderByCreatedAtDesc(StudyPlanStatus status);

    List<StudyPlan> findAllByOrderByCreatedAtDesc();

    /**
     * Vị trí của đề trong các mục lộ trình: {@code [itemId, stageNo]} (stage_no 0-based).
     * Có tối đa một dòng vì {@code UNIQUE(problem_id)}.
     */
    @Query(value = "select item_id, stage_no from study_plan_item_stages where problem_id = :problemId",
            nativeQuery = true)
    List<Object[]> findStagePosition(@Param("problemId") Long problemId);

    @Query(value = "select problem_id from study_plan_item_stages where item_id = :itemId and stage_no = :stageNo",
            nativeQuery = true)
    List<Number> findStageProblemId(@Param("itemId") Long itemId, @Param("stageNo") int stageNo);
}
