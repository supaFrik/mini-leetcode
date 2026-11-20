package jsd.toiyeujava.mini_leetcode.problem.service;

import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Tag;
import jsd.toiyeujava.mini_leetcode.problem.entity.TagCategory;
import jsd.toiyeujava.mini_leetcode.problem.repository.TagRepository;
import jsd.toiyeujava.mini_leetcode.problem.util.TagNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Quản lý nhóm chủ đề (chỉ ADMIN). Chủ đề được nhận diện bằng tên, giống {@code problem_tags}.
 * Chủ đề có trên bài nhưng chưa có dòng trong {@code tags} được xem là "Other".
 */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final TagCategoryService categories;

    public TagService(TagRepository tagRepository, TagCategoryService categories) {
        this.tagRepository = tagRepository;
        this.categories = categories;
    }

    public record AdminTag(String name, String category, int problemCount) {
    }

    @Transactional(readOnly = true)
    public List<AdminTag> list() {
        Map<String, Integer> counts = usageCounts();
        Map<String, AdminTag> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Tag tag : tagRepository.findAll()) {
            byName.put(tag.getName(), new AdminTag(tag.getName(), tag.getCategory() == null ? TagCategory.OTHER : tag.getCategory(), counts.getOrDefault(tag.getName(), 0)));
        }
        counts.forEach((name, count) -> byName.putIfAbsent(name, new AdminTag(name, TagCategory.OTHER, count)));
        return List.copyOf(byName.values());
    }

    @Transactional
    public AdminTag create(String rawName, String category) {
        String name = normalizeName(rawName);
        if (tagRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new StateConflictException("Tag already exists: " + name);
        }
        tagRepository.save(Tag.builder().name(name).category(categories.canonicalName(category)).build());
        return find(name);
    }

    /** Đổi nhóm. Chủ đề chỉ có trên bài (chưa có dòng trong tags) thì tạo dòng mới. */
    @Transactional
    public AdminTag updateCategory(String name, String category) {
        Tag tag = tagRepository.findByNameIgnoreCase(name).orElse(null);
        if (tag == null) {
            if (!usageCounts().containsKey(name)) {
                throw new ResourceNotFoundException("Tag not found: " + name);
            }
            tag = Tag.builder().name(name).build();
        }
        tag.setCategory(categories.canonicalName(category));
        tagRepository.save(tag);
        return find(tag.getName());
    }

    /** Chỉ xoá được chủ đề chưa gắn bài nào, để không lặng lẽ sửa bài (kể cả bài trong contest). */
    @Transactional
    public void delete(String name) {
        Tag tag = tagRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + name));
        int used = usageCounts().getOrDefault(tag.getName(), 0);
        if (used > 0) {
            throw new StateConflictException("Tag \"" + tag.getName() + "\" is used by " + used
                    + " problem(s). Remove it from those problems first.");
        }
        tagRepository.delete(tag);
    }

    private Map<String, Integer> usageCounts() {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Object[] row : tagRepository.countProblemsByTag()) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
        }
        return counts;
    }

    private AdminTag find(String name) {
        return list().stream().filter(t -> t.name().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }

    private static String normalizeName(String rawName) {
        List<String> normalized = TagNormalizer.normalize(rawName == null ? List.of() : List.of(rawName));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }
        return normalized.get(0);
    }
}
