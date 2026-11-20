package jsd.toiyeujava.mini_leetcode.sitepage.service;

import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.sitepage.entity.SitePage;
import jsd.toiyeujava.mini_leetcode.sitepage.repository.SitePageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Đọc công khai, chỉ ADMIN sửa. Không tạo trang mới qua API: các trang được seed bằng migration. */
@Service
public class SitePageService {

    public static final int MAX_CONTENT_LENGTH = 15_000; // TEXT = 65535 bytes, utf8mb4 up to 4 bytes per char

    private final SitePageRepository repository;

    public SitePageService(SitePageRepository repository) {
        this.repository = repository;
    }

    public record PageView(String slug, String title, String contentMd, LocalDateTime updatedAt) {
        static PageView of(SitePage p) {
            return new PageView(p.getSlug(), p.getTitle(), p.getContentMd(), p.getUpdatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<PageView> list() {
        return repository.findAll(Sort.by("slug")).stream().map(PageView::of).toList();
    }

    @Transactional(readOnly = true)
    public PageView get(String slug) {
        return PageView.of(find(slug));
    }

    @Transactional
    public PageView update(String slug, String title, String contentMd) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (title.trim().length() > 200) {
            throw new IllegalArgumentException("Title must not exceed 200 characters");
        }
        if (contentMd == null || contentMd.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Content is required and must not exceed " + MAX_CONTENT_LENGTH + " characters");
        }
        SitePage page = find(slug);
        page.setTitle(title.trim());
        page.setContentMd(contentMd);
        return PageView.of(repository.saveAndFlush(page));
    }

    private SitePage find(String slug) {
        return repository.findById(slug).orElseThrow(() -> new ResourceNotFoundException("Page not found: " + slug));
    }
}
