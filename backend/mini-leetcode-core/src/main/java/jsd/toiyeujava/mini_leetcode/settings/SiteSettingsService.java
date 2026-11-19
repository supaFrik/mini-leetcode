package jsd.toiyeujava.mini_leetcode.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cài đặt hệ thống. Có hiệu lực thật: maintenanceMode/bannerMessage (banner toàn site) và registrationOpen
 * (chặn đăng ký). Các giá trị còn lại chỉ được lưu, làm mặc định cho form tạo bài/kỳ thi.
 */
@Service
public class SiteSettingsService {

    /** Khoá hợp lệ và giá trị mặc định (trùng V19), giữ thứ tự hiển thị. */
    static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("siteName", "Mini LeetCode");
        DEFAULTS.put("tagline", "Practice Java and OOP, one problem at a time.");
        DEFAULTS.put("maintenanceMode", "false");
        DEFAULTS.put("bannerMessage", "");
        DEFAULTS.put("registrationOpen", "true");
        DEFAULTS.put("allowedLanguages", "JAVA,PYTHON");
        DEFAULTS.put("defaultTimeLimitMs", "2000");
        DEFAULTS.put("defaultMemoryLimitMb", "256");
        DEFAULTS.put("defaultContestMinutes", "120");
        DEFAULTS.put("defaultMaxViolations", "3");
    }

    private static final Set<String> BOOLEANS = Set.of("maintenanceMode", "registrationOpen");
    private static final Set<String> NUMBERS = Set.of(
            "defaultTimeLimitMs", "defaultMemoryLimitMb", "defaultContestMinutes", "defaultMaxViolations");
    private static final Set<String> LANGUAGES = Set.of("JAVA", "PYTHON");

    private final SiteSettingRepository repository;

    public SiteSettingsService(SiteSettingRepository repository) {
        this.repository = repository;
    }

    public record PublicSettings(String siteName, String tagline, boolean maintenanceMode, String bannerMessage,
                                 boolean registrationOpen) {
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>(DEFAULTS);
        repository.findAll().forEach(s -> {
            if (out.containsKey(s.getKey())) out.put(s.getKey(), s.getValue());
        });
        return out;
    }

    @Transactional(readOnly = true)
    public PublicSettings publicSettings() {
        Map<String, String> s = all();
        return new PublicSettings(s.get("siteName"), s.get("tagline"), Boolean.parseBoolean(s.get("maintenanceMode")),
                s.get("bannerMessage"), Boolean.parseBoolean(s.get("registrationOpen")));
    }

    @Transactional(readOnly = true)
    public boolean registrationOpen() {
        return repository.findById("registrationOpen").map(s -> Boolean.parseBoolean(s.getValue())).orElse(true);
    }

    /** Lưu các khoá gửi lên; khoá lạ hoặc giá trị sai kiểu ném IllegalArgumentException (400). */
    @Transactional
    public Map<String, String> update(Map<String, String> changes) {
        changes.forEach((k, v) -> {
            if (!DEFAULTS.containsKey(k)) throw new IllegalArgumentException("Unknown setting: " + k);
            String value = v == null ? "" : v.trim();
            validate(k, value);
            repository.save(new SiteSetting(k, value));
        });
        return all();
    }

    private static void validate(String key, String value) {
        if (value.length() > 500) throw new IllegalArgumentException(key + " is too long (max 500 characters)");
        if (BOOLEANS.contains(key) && !value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        if (NUMBERS.contains(key)) {
            int n;
            try {
                n = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " must be a whole number");
            }
            if (n < 1) throw new IllegalArgumentException(key + " must be at least 1");
        }
        if (key.equals("allowedLanguages")) {
            for (String lang : value.split(",")) {
                if (!LANGUAGES.contains(lang.trim())) throw new IllegalArgumentException("Unsupported language: " + lang);
            }
        }
        if (key.equals("siteName") && value.isEmpty()) throw new IllegalArgumentException("siteName must not be blank");
    }
}
