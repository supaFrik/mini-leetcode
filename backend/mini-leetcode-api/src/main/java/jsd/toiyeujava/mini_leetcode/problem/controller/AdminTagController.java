package jsd.toiyeujava.mini_leetcode.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jsd.toiyeujava.mini_leetcode.problem.service.TagService;
import jsd.toiyeujava.mini_leetcode.problem.service.TagService.AdminTag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Nhóm chủ đề. Chỉ ADMIN (SecurityConfig). Chủ đề được nhận diện bằng tên. */
@RestController
@RequestMapping("/api/v1/admin/tags")
@Tag(name = "Admin tags", description = "Group topic tags into categories (ADMIN only)")
public class AdminTagController {

    private final TagService tagService;

    public AdminTagController(TagService tagService) {
        this.tagService = tagService;
    }

    public record CreateTagRequest(@NotBlank String name, @NotNull jsd.toiyeujava.mini_leetcode.problem.entity.Tag.Category category) {
    }

    public record UpdateTagRequest(@NotNull jsd.toiyeujava.mini_leetcode.problem.entity.Tag.Category category) {
    }

    @GetMapping
    @Operation(summary = "List tags with category and problem count")
    public List<AdminTag> list() {
        return tagService.list();
    }

    @PostMapping
    @Operation(summary = "Create a tag")
    public ResponseEntity<AdminTag> create(@Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.create(request.name(), request.category()));
    }

    @PutMapping("/{name}")
    @Operation(summary = "Change a tag's category")
    public AdminTag update(@PathVariable String name, @Valid @RequestBody UpdateTagRequest request) {
        return tagService.updateCategory(name, request.category());
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete a tag that no problem uses")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        tagService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
