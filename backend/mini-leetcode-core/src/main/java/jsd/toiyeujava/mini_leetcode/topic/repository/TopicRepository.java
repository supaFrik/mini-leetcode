package jsd.toiyeujava.mini_leetcode.topic.repository;

import jsd.toiyeujava.mini_leetcode.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select t from Topic t where :q is null or lower(t.title) like lower(concat('%', :q, '%')) "
            + "or lower(t.slug) like lower(concat('%', :q, '%')) order by t.updatedAt desc")
    List<Topic> search(@Param("q") String query);

    /** Có plan nào đang dùng chủ đề này không (chặn xoá). */
    @Query(value = "select count(*) from study_plan_stage_topics where topic_id = :topicId", nativeQuery = true)
    long countPlanUsages(@Param("topicId") Long topicId);
}
