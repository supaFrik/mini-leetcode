package jsd.toiyeujava.mini_leetcode.problem.repository;

import jsd.toiyeujava.mini_leetcode.problem.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    /** Số bài (mọi trạng thái) gắn từng chủ đề: [tag, count]. */
    @Query(value = "select pt.tag, count(pt.problem_id) from problem_tags pt group by pt.tag", nativeQuery = true)
    List<Object[]> countProblemsByTag();

    /** Chuyển mọi chủ đề từ nhóm {@code from} sang {@code to} (null = Other); xoá cache để đọc lại đúng. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Tag t set t.category = :to where t.category = :from")
    int moveCategory(@Param("from") String from, @Param("to") String to);
}
