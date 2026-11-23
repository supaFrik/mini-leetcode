package jsd.toiyeujava.mini_leetcode.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public record CreateTagRequest(@NotBlank String name, @NotBlank String category) {
    }

    /** Trường null thì giữ nguyên; {@code color} rỗng = dùng màu category. */
    public record UpdateTagRequest(String name, String category, String color) {
    }

    public record ApplyRequest(List<Long> problemIds, boolean remove) {
    }

    public record ApplyResult(int changed) {
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
    @Operation(summary = "Rename a tag and/or change its category")
    public AdminTag update(@PathVariable String name, @RequestBody UpdateTagRequest request) {
        String current = request.name() == null ? name : tagService.rename(name, request.name()).name();
        if (request.category() != null) {
            tagService.updateCategory(current, request.category());
        }
        return request.color() == null ? tagService.find(current) : tagService.updateColor(current, request.color());
    }

    @PostMapping("/{name}/problems")
    @Operation(summary = "Add the tag to, or remove it from, several problems")
    public ApplyResult apply(@PathVariable String name, @RequestBody ApplyRequest request) {
        return new ApplyResult(tagService.applyToProblems(name, request.problemIds(), request.remove()));
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete a tag and remove it from its problems")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        tagService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
