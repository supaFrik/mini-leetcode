package jsd.toiyeujava.mini_leetcode.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Cài đặt hệ thống: /admin/settings chỉ ADMIN (SecurityConfig), /settings/public công khai. */
@RestController
@Tag(name = "Site settings", description = "Site-wide settings (ADMIN) and their public subset")
public class SiteSettingsController {

    private final SiteSettingsService service;

    public SiteSettingsController(SiteSettingsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/settings/public")
    @Operation(summary = "Public settings: site name, tagline, maintenance banner, registration toggle")
    public SiteSettingsService.PublicSettings publicSettings() {
        return service.publicSettings();
    }

    @GetMapping("/api/v1/admin/settings")
    @Operation(summary = "All settings (ADMIN only)")
    public Map<String, String> all() {
        return service.all();
    }

    @PutMapping("/api/v1/admin/settings")
    @Operation(summary = "Update settings (ADMIN only); unknown keys or invalid values return 400")
    public Map<String, String> update(@RequestBody Map<String, String> changes) {
        return service.update(changes);
    }
}
