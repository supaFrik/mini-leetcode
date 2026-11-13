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
     * Vị trí của đề trong một lộ trình đang PUBLISHED: {@code [itemId, stageNo]} (stage_no 0-based).
     * Có tối đa một dòng vì {@code UNIQUE(problem_id)}. Plan DRAFT / ARCHIVED không khoá gì.
     */
    @Query(value = "select s.item_id, s.stage_no from study_plan_item_stages s "
            + "join study_plan_items i on i.id = s.item_id join study_plans p on p.id = i.plan_id "
            + "where s.problem_id = :problemId and p.status = 'PUBLISHED'",
            nativeQuery = true)
    List<Object[]> findStagePosition(@Param("problemId") Long problemId);

    /** Các đề trong {@code ids} đã thuộc một mục lộ trình nào đó (mọi trạng thái plan). */
    @Query(value = "select problem_id from study_plan_item_stages where problem_id in (:ids)", nativeQuery = true)
    List<Number> findProblemIdsAlreadyInPlans(@Param("ids") List<Long> ids);

    @Query(value = "select problem_id from study_plan_item_stages where item_id = :itemId and stage_no = :stageNo",
            nativeQuery = true)
    List<Number> findStageProblemId(@Param("itemId") Long itemId, @Param("stageNo") int stageNo);
}
