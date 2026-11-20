package jsd.toiyeujava.mini_leetcode.announcement.repository;

import jsd.toiyeujava.mini_leetcode.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @EntityGraph(attributePaths = "createdBy")
    List<Announcement> findTop50ByOrderByIdDesc();
}
