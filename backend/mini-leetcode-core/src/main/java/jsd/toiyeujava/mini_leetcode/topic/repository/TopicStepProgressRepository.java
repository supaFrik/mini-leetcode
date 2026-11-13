package jsd.toiyeujava.mini_leetcode.topic.repository;

import jsd.toiyeujava.mini_leetcode.topic.entity.TopicStepProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TopicStepProgressRepository extends JpaRepository<TopicStepProgress, TopicStepProgress.Key> {

    List<TopicStepProgress> findByUserIdAndStepIdIn(Long userId, Collection<Long> stepIds);
}
