package jsd.toiyeujava.mini_leetcode.problem.repository;

import jsd.toiyeujava.mini_leetcode.problem.entity.TagCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagCategoryRepository extends JpaRepository<TagCategory, Long> {

    Optional<TagCategory> findByNameIgnoreCase(String name);

    List<TagCategory> findAllByOrderBySortOrderAscNameAsc();
}
