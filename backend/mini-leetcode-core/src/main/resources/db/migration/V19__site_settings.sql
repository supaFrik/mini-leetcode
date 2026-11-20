-- ============================================================
-- V19: Cài đặt hệ thống dạng key/value, ADMIN sửa ở /admin/settings.
-- ============================================================
CREATE TABLE site_settings (
    setting_key VARCHAR(64) NOT NULL PRIMARY KEY,
    setting_value TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO site_settings (setting_key, setting_value) VALUES
    ('siteName', 'Mini LeetCode'),
    ('tagline', 'Practice Java and OOP, one problem at a time.'),
    ('maintenanceMode', 'false'),
    ('bannerMessage', ''),
    ('registrationOpen', 'true'),
    ('allowedLanguages', 'JAVA,PYTHON'),
    ('defaultTimeLimitMs', '2000'),
    ('defaultMemoryLimitMb', '256'),
    ('defaultContestMinutes', '120'),
    ('defaultMaxViolations', '3');
