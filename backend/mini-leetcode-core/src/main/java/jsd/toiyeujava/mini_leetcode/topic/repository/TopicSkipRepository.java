package jsd.toiyeujava.mini_leetcode.topic.repository;

import jsd.toiyeujava.mini_leetcode.topic.entity.TopicSkip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TopicSkipRepository extends JpaRepository<TopicSkip, TopicSkip.Key> {

    List<TopicSkip> findByUserIdAndTopicIdIn(Long userId, Collection<Long> topicIds);
}
