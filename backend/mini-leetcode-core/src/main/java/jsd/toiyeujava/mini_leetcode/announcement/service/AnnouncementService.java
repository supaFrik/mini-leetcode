package jsd.toiyeujava.mini_leetcode.announcement.service;

import jsd.toiyeujava.mini_leetcode.announcement.entity.Announcement;
import jsd.toiyeujava.mini_leetcode.announcement.repository.AnnouncementRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.exception.ForbiddenOperationException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Thông báo chung: mọi người đã đăng nhập đọc được, chỉ giảng viên/ADMIN tạo và xoá (SecurityConfig). */
@Service
public class AnnouncementService {

    private final AnnouncementRepository repository;
    private final UserRepository userRepository;

    public AnnouncementService(AnnouncementRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public record AnnouncementView(Long id, String title, String body, Long createdById, String createdByUsername,
                                   LocalDateTime createdAt) {
        static AnnouncementView of(Announcement a) {
            return new AnnouncementView(a.getId(), a.getTitle(), a.getBody(), a.getCreatedBy().getId(),
                    a.getCreatedBy().getUsername(), a.getCreatedAt());
        }
    }

    /** 50 thông báo mới nhất. */
    @Transactional(readOnly = true)
    public List<AnnouncementView> list() {
        return repository.findTop50ByOrderByIdDesc().stream().map(AnnouncementView::of).toList();
    }

    @Transactional
    public AnnouncementView create(String title, String body, Long authorId) {
        return AnnouncementView.of(repository.saveAndFlush(Announcement.builder()
                .title(title.trim()).body(body.trim()).createdBy(userRepository.getReferenceById(authorId)).build()));
    }

    /** ADMIN xoá mọi thông báo; giảng viên chỉ xoá thông báo của chính mình. */
    @Transactional
    public void delete(Long id, Long userId, Role role) {
        Announcement a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        if (role != Role.ADMIN && !a.getCreatedBy().getId().equals(userId)) {
            throw new ForbiddenOperationException("You can only delete your own announcements");
        }
        repository.delete(a);
    }
}
