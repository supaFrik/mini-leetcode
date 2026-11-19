package jsd.toiyeujava.mini_leetcode.announcement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.announcement.service.AnnouncementService;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.announcement.service.AnnouncementService.AnnouncementView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Đọc: mọi người đã đăng nhập. Tạo/xoá: /api/v1/admin/** (giảng viên, ADMIN). */
@RestController
@Tag(name = "Announcements", description = "Site-wide announcements shown in the notification bell")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    public record CreateAnnouncementRequest(@NotBlank @Size(max = 200) String title, @NotBlank String body) {
    }

    @GetMapping("/api/v1/announcements")
    @Operation(summary = "Latest announcements, newest first")
    public List<AnnouncementView> list() {
        return service.list();
    }

    @PostMapping("/api/v1/admin/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post an announcement (instructor or admin)")
    public AnnouncementView create(@Valid @RequestBody CreateAnnouncementRequest request,
                                   @AuthenticationPrincipal UserPrincipal user) {
        return service.create(request.title(), request.body(), user.getId());
    }

    @DeleteMapping("/api/v1/admin/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an announcement (admin: any; instructor: own only)")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) {
        service.delete(id, user.getId(), user.getRole());
    }
}
