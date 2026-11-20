package jsd.toiyeujava.mini_leetcode.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jsd.toiyeujava.mini_leetcode.problem.service.TagCategoryService;
import jsd.toiyeujava.mini_leetcode.problem.service.TagCategoryService.CategoryView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Nhóm chủ đề: GET công khai (trang /problems cần), sửa/xoá chỉ ADMIN (SecurityConfig). */
@RestController
@Tag(name = "Tag categories", description = "Categories that group topic tags")
public class TagCategoryController {

    private final TagCategoryService service;

    public TagCategoryController(TagCategoryService service) {
        this.service = service;
    }

    public record CreateCategoryRequest(@NotBlank String name, String color, Integer sortOrder) {
    }

    public record UpdateCategoryRequest(String name, String color, Integer sortOrder) {
    }

    @GetMapping({"/api/v1/tag-categories", "/api/v1/admin/tag-categories"})
    @Operation(summary = "List categories in display order")
    public List<CategoryView> list() {
        return service.list();
    }

    @PostMapping("/api/v1/admin/tag-categories")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryView> create(@Valid @RequestBody CreateCategoryRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r.name(), r.color(), r.sortOrder()));
    }

    @PutMapping("/api/v1/admin/tag-categories/{id}")
    @Operation(summary = "Rename, recolour or reorder a category")
    public CategoryView update(@PathVariable Long id, @RequestBody UpdateCategoryRequest r) {
        return service.update(id, r.name(), r.color(), r.sortOrder());
    }

    @DeleteMapping("/api/v1/admin/tag-categories/{id}")
    @Operation(summary = "Delete a category; its tags fall back to Other")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
