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
     * Vị trí của đề trong một lộ trình đang PUBLISHED: {@code [planId, sessionId, sessionOrder, entryOrder, type]}.
     * Có tối đa một dòng vì {@code UNIQUE(problem_id)}. Plan DRAFT / ARCHIVED không khoá gì.
     */
    @Query(value = "select i.plan_id, i.id, i.sort_order, e.sort_order, e.type from study_plan_entries e "
            + "join study_plan_items i on i.id = e.session_id join study_plans p on p.id = i.plan_id "
            + "where e.problem_id = :problemId and p.status = 'PUBLISHED'",
            nativeQuery = true)
    List<Object[]> findStagePosition(@Param("problemId") Long problemId);

    /** Các đề trong {@code ids} đã thuộc một session nào đó (mọi trạng thái plan). */
    @Query(value = "select problem_id from study_plan_entries where problem_id in (:ids)", nativeQuery = true)
    List<Number> findProblemIdsAlreadyInPlans(@Param("ids") List<Long> ids);

    /**
     * Đề phải AC trước khi mở một đề: mọi đề của các session trước, và các stage đứng trước {@code entryOrder}
     * trong cùng session (truyền -1 nếu đề không phải stage).
     */
    @Query(value = "select e.problem_id from study_plan_entries e join study_plan_items i on i.id = e.session_id "
            + "where i.plan_id = :planId and e.problem_id is not null and (i.sort_order < :sessionOrder "
            + "or (i.id = :sessionId and e.type = 'STAGE' and e.sort_order < :entryOrder))",
            nativeQuery = true)
    List<Number> findPrerequisiteProblemIds(@Param("planId") Long planId, @Param("sessionId") Long sessionId,
                                            @Param("sessionOrder") int sessionOrder, @Param("entryOrder") int entryOrder);
}
