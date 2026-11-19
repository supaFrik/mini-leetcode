package jsd.toiyeujava.mini_leetcode.problem.service;

import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import jsd.toiyeujava.mini_leetcode.problem.entity.TagCategory;
import jsd.toiyeujava.mini_leetcode.problem.repository.TagCategoryRepository;
import jsd.toiyeujava.mini_leetcode.problem.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** CRUD nhóm chủ đề (ADMIN). Đổi tên lan sang tags nhờ FK ON UPDATE CASCADE; xoá thì tags về "Other". */
@Service
public class TagCategoryService {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final TagCategoryRepository repository;
    private final TagRepository tags;

    public TagCategoryService(TagCategoryRepository repository, TagRepository tags) {
        this.repository = repository;
        this.tags = tags;
    }

    public record CategoryView(Long id, String name, String color, int sortOrder) {
        static CategoryView of(TagCategory c) {
            return new CategoryView(c.getId(), c.getName(), c.getColor(), c.getSortOrder());
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(CategoryView::of).toList();
    }

    /** Tên nhóm đúng như đã lưu (không phân biệt hoa thường); không có thì 400. */
    @Transactional(readOnly = true)
    public String canonicalName(String name) {
        if (name == null || name.isBlank()) {
            return TagCategory.OTHER;
        }
        return repository.findByNameIgnoreCase(name.trim()).map(TagCategory::getName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + name.trim()));
    }

    @Transactional
    public CategoryView create(String rawName, String color, Integer sortOrder) {
        String name = validName(rawName);
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw new StateConflictException("Category already exists: " + name);
        }
        TagCategory c = TagCategory.builder().name(name).color(validColor(color))
                .sortOrder(sortOrder == null ? nextOrder() : sortOrder).build();
        return CategoryView.of(repository.save(c));
    }

    @Transactional
    public CategoryView update(Long id, String rawName, String color, Integer sortOrder) {
        TagCategory c = find(id);
        if (rawName != null) {
            String name = validName(rawName);
            if (!name.equals(c.getName())) {
                if (TagCategory.OTHER.equals(c.getName())) {
                    throw new StateConflictException("The Other category cannot be renamed");
                }
                repository.findByNameIgnoreCase(name).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
                    throw new StateConflictException("Category already exists: " + name);
                });
                String old = c.getName();
                c.setName(name);
                repository.saveAndFlush(c);
                // FK ON UPDATE CASCADE đã đổi trong DB; gọi lại để không phụ thuộc FK và xoá cache entity cũ.
                tags.moveCategory(old, name);
                c = find(id);
            }
        }
        if (color != null) {
            c.setColor(validColor(color));
        }
        if (sortOrder != null) {
            c.setSortOrder(sortOrder);
        }
        return CategoryView.of(repository.saveAndFlush(c));
    }

    @Transactional
    public void delete(Long id) {
        TagCategory c = find(id);
        if (TagCategory.OTHER.equals(c.getName())) {
            throw new StateConflictException("The Other category cannot be deleted");
        }
        tags.moveCategory(c.getName(), null);
        repository.delete(c);
    }

    private TagCategory find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private int nextOrder() {
        return repository.findAll().stream().filter(c -> !TagCategory.OTHER.equals(c.getName()))
                .mapToInt(TagCategory::getSortOrder).max().orElse(0) + 10;
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 50) {
            throw new IllegalArgumentException("Category name must be 1-50 characters");
        }
        return name;
    }

    private static String validColor(String color) {
        if (color == null) {
            return "#64748b";
        }
        if (!COLOR.matcher(color).matches()) {
            throw new IllegalArgumentException("Color must look like #1a2b3c");
        }
        return color.toLowerCase();
    }
}
