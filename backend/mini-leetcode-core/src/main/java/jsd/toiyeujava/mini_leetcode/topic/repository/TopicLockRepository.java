package jsd.toiyeujava.mini_leetcode.topic.repository;

import jsd.toiyeujava.mini_leetcode.topic.entity.TopicLock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicLockRepository extends JpaRepository<TopicLock, TopicLock.Key> {
}
