package jsd.toiyeujava.mini_leetcode.sitepage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jsd.toiyeujava.mini_leetcode.sitepage.service.SitePageService;
import jsd.toiyeujava.mini_leetcode.sitepage.service.SitePageService.PageView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Trang chính sách: đọc công khai, sửa chỉ ADMIN (SecurityConfig). */
@RestController
@Tag(name = "Site pages", description = "Policy pages such as Terms of use and Contest rules")
public class SitePageController {

    private final SitePageService service;

    public SitePageController(SitePageService service) {
        this.service = service;
    }

    public record UpdatePageRequest(@NotBlank String title, @NotNull String contentMd) {
    }

    @GetMapping("/api/v1/pages")
    @Operation(summary = "List site pages")
    public List<PageView> list() {
        return service.list();
    }

    @GetMapping("/api/v1/pages/{slug}")
    @Operation(summary = "Get a site page")
    public PageView get(@PathVariable String slug) {
        return service.get(slug);
    }

    @PutMapping("/api/v1/admin/pages/{slug}")
    @Operation(summary = "Update a site page (ADMIN only)")
    public PageView update(@PathVariable String slug, @Valid @RequestBody UpdatePageRequest request) {
        return service.update(slug, request.title(), request.contentMd());
    }
}
