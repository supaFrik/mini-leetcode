package jsd.toiyeujava.mini_leetcode.contest.admin;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProctor;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProctorRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.exception.ForbiddenOperationException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Giám thị kỳ thi. Bổ nhiệm/gỡ: chủ kỳ thi hoặc ADMIN (OwnershipInterceptor chặn giảng viên khác).
 * Xem live view và báo cáo: ADMIN, chủ kỳ thi hoặc giám thị ({@link #requireMonitor}).
 */
@Service
@RequiredArgsConstructor
public class ContestProctorService {

    private final ContestProctorRepository proctors;
    private final ContestRepository contests;
    private final UserRepository users;

    public record ProctorView(Long userId, String username, Role role) {
    }

    @Transactional(readOnly = true)
    public List<ProctorView> list(Long contestId) {
        contest(contestId);
        return proctors.findWithUserByContestId(contestId).stream()
                .map(p -> new ProctorView(p.getUser().getId(), p.getUser().getUsername(), p.getUser().getRole()))
                .toList();
    }

    @Transactional
    public ProctorView add(Long contestId, String username) {
        Contest c = contest(contestId);
        User u = users.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (u.getRole() != Role.INSTRUCTOR && u.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Only instructors and admins can proctor a contest");
        }
        if (c.getCreatedBy() != null && c.getCreatedBy().getId().equals(u.getId())) {
            throw new StateConflictException(u.getUsername() + " owns this contest");
        }
        if (proctors.existsByContestIdAndUserId(contestId, u.getId())) {
            throw new StateConflictException(u.getUsername() + " is already a proctor");
        }
        proctors.save(ContestProctor.builder().contestId(contestId).user(u).build());
        return new ProctorView(u.getId(), u.getUsername(), u.getRole());
    }

    @Transactional
    public void remove(Long contestId, Long userId) {
        ContestProctor p = proctors.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Proctor not found"));
        proctors.delete(p);
    }

    /** ADMIN, chủ kỳ thi hoặc giám thị; người khác 403, kỳ thi không có 404. */
    @Transactional(readOnly = true)
    public void requireMonitor(Long contestId, Long userId, Role role) {
        Contest c = contest(contestId);
        if (role == Role.ADMIN) return;
        boolean owner = c.getCreatedBy() != null && c.getCreatedBy().getId().equals(userId);
        if (!owner && !proctors.existsByContestIdAndUserId(contestId, userId)) {
            throw new ForbiddenOperationException("Only the contest owner, its proctors and admins can view this");
        }
    }

    /** contestId → id các giám thị. */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> proctorIdsByContest() {
        Map<Long, List<Long>> map = new HashMap<>();
        for (Object[] row : proctors.findAllPairs()) {
            map.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Long) row[1]);
        }
        return map;
    }

    private Contest contest(Long id) {
        return contests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contest not found with id: " + id));
    }
}
