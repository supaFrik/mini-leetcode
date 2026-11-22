package jsd.toiyeujava.mini_leetcode.problem.service;

import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.entity.Tag;
import jsd.toiyeujava.mini_leetcode.problem.entity.TagCategory;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
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
    private final ProblemRepository problems;

    public TagService(TagRepository tagRepository, TagCategoryService categories, ProblemRepository problems) {
        this.tagRepository = tagRepository;
        this.categories = categories;
        this.problems = problems;
    }

    /** {@code color} null = dùng màu của category. */
    public record AdminTag(String name, String category, String color, int problemCount) {
    }

    private static final java.util.regex.Pattern COLOR = java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");

    @Transactional(readOnly = true)
    public List<AdminTag> list() {
        Map<String, Integer> counts = usageCounts();
        Map<String, AdminTag> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Tag tag : tagRepository.findAll()) {
            byName.put(tag.getName(), new AdminTag(tag.getName(), tag.getCategory() == null ? TagCategory.OTHER : tag.getCategory(), tag.getColor(), counts.getOrDefault(tag.getName(), 0)));
        }
        counts.forEach((name, count) -> byName.putIfAbsent(name, new AdminTag(name, TagCategory.OTHER, null, count)));
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

    /** Màu riêng của chủ đề; chuỗi rỗng = bỏ màu riêng, dùng lại màu category. */
    @Transactional
    public AdminTag updateColor(String name, String color) {
        String value = color.isBlank() ? null : color.trim();
        if (value != null && !COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("Color must look like #1a2b3c");
        }
        Tag tag = tagRepository.findByNameIgnoreCase(name).orElse(null);
        if (tag == null) {
            if (!usageCounts().containsKey(name)) {
                throw new ResourceNotFoundException("Tag not found: " + name);
            }
            tag = Tag.builder().name(name).category(TagCategory.OTHER).build();
        }
        tag.setColor(value == null ? null : value.toLowerCase());
        tagRepository.save(tag);
        return find(tag.getName());
    }

    /** Đổi tên chủ đề, cả trên mọi bài đang gắn nó. Sửa qua entity để giữ sort_order liền mạch. */
    @Transactional
    public AdminTag rename(String name, String rawNewName) {
        String next = normalizeName(rawNewName);
        Tag tag = tagRepository.findByNameIgnoreCase(name).orElse(null);
        List<Problem> using = problems.findByTag(name);
        if (tag == null && using.isEmpty()) {
            throw new ResourceNotFoundException("Tag not found: " + name);
        }
        if (!next.equalsIgnoreCase(name)
                && (tagRepository.findByNameIgnoreCase(next).isPresent() || usageCounts().containsKey(next))) {
            throw new StateConflictException("Tag already exists: " + next);
        }
        for (Problem p : using) {
            p.getTags().replaceAll(t -> t.equalsIgnoreCase(name) ? next : t);
        }
        problems.saveAll(using);
        if (tag == null) {
            tag = Tag.builder().category(TagCategory.OTHER).build();
        }
        tag.setName(next);
        tagRepository.save(tag);
        return find(next);
    }

    /** Xoá chủ đề và gỡ nó khỏi mọi bài đang gắn (giao diện đã hỏi xác nhận kèm số bài). */
    @Transactional
    public void delete(String name) {
        Optional<Tag> tag = tagRepository.findByNameIgnoreCase(name);
        List<Problem> using = problems.findByTag(name);
        if (tag.isEmpty() && using.isEmpty()) {
            throw new ResourceNotFoundException("Tag not found: " + name);
        }
        for (Problem p : using) {
            p.getTags().removeIf(t -> t.equalsIgnoreCase(name));
        }
        problems.saveAll(using);
        tag.ifPresent(tagRepository::delete);
    }

    /** Gắn ({@code remove = false}) hoặc gỡ chủ đề cho nhiều bài; trả số bài thực sự đổi. */
    @Transactional
    public int applyToProblems(String rawName, List<Long> problemIds, boolean remove) {
        String name = normalizeName(rawName);
        int changed = 0;
        for (Problem p : problems.findAllById(problemIds == null ? List.of() : problemIds)) {
            boolean has = p.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(name));
            if (remove && has) {
                p.getTags().removeIf(t -> t.equalsIgnoreCase(name));
            } else if (!remove && !has) {
                p.getTags().add(name);
            } else {
                continue;
            }
            problems.save(p);
            changed++;
        }
        return changed;
    }

    private Map<String, Integer> usageCounts() {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Object[] row : tagRepository.countProblemsByTag()) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public AdminTag find(String name) {
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
