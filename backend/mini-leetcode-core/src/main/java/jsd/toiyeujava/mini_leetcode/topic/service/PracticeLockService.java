package jsd.toiyeujava.mini_leetcode.topic.service;

import jsd.toiyeujava.mini_leetcode.entity.TopicStepType;
import jsd.toiyeujava.mini_leetcode.exception.TopicLockedException;
import jsd.toiyeujava.mini_leetcode.topic.entity.Topic;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicLock;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicStep;
import jsd.toiyeujava.mini_leetcode.topic.entity.TopicStepProgress;
import jsd.toiyeujava.mini_leetcode.topic.repository.TopicLockRepository;
import jsd.toiyeujava.mini_leetcode.topic.repository.TopicStepProgressRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Luật khoá luyện tập của chủ đề (specs/topics.spec.md): sai {@link #LOCK_AFTER} lần liên tiếp ở một câu ⇒ khoá
 * {@link #LOCK_FOR} và đặt mọi step lý thuyết về chưa đọc. Mở lại khi đã hết giờ VÀ đã đọc lại hết lý thuyết.
 * Chạy trong transaction của người gọi ({@link TopicService#attempt}).
 */
@Service
public class PracticeLockService {

    public static final int LOCK_AFTER = 3;
    public static final Duration LOCK_FOR = Duration.ofMinutes(5);

    /** Trạng thái khoá trả cho client; {@code lockedUntil} null khi đã hết giờ. */
    public record LockState(LocalDateTime lockedUntil, boolean mustReread) {
        static final LockState NONE = new LockState(null, false);
    }

    private final TopicLockRepository lockRepository;
    private final TopicStepProgressRepository progressRepository;

    public PracticeLockService(TopicLockRepository lockRepository, TopicStepProgressRepository progressRepository) {
        this.lockRepository = lockRepository;
        this.progressRepository = progressRepository;
    }

    public LockState state(Long userId, Long topicId) {
        TopicLock lock = activeLock(userId, topicId);
        if (lock == null) {
            return LockState.NONE;
        }
        LocalDateTime until = lock.getLockedUntil().isAfter(LocalDateTime.now()) ? lock.getLockedUntil() : null;
        return new LockState(until, lock.isMustReread());
    }

    /** Từ chối bài luyện tập khi còn đang đếm giờ hoặc chưa đọc lại lý thuyết. */
    public void ensureCanPractice(Long userId, Long topicId) {
        TopicLock lock = activeLock(userId, topicId);
        if (lock == null) {
            return;
        }
        if (lock.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new TopicLockedException("Too many wrong answers. Practice reopens at "
                    + lock.getLockedUntil().toLocalTime().withNano(0) + "; review the theory meanwhile.");
        }
        throw new TopicLockedException("Read the theory again before you continue practicing.");
    }

    /**
     * Ghi nhận một câu trả lời luyện tập vào {@code progress.wrongStreak}.
     *
     * @return {@code true} nếu câu này làm chủ đề bị khoá
     */
    public boolean recordPracticeAnswer(Long userId, Topic topic, TopicStepProgress progress, boolean correct) {
        int streak = correct ? 0 : progress.getWrongStreak() + 1;
        if (streak < LOCK_AFTER) {
            progress.setWrongStreak(streak);
            return false;
        }
        progress.setWrongStreak(0);
        lock(userId, topic);
        return true;
    }

    /** Gọi sau khi đã lưu một step lý thuyết: đọc đủ lại thì bỏ điều kiện "phải đọc lại". */
    public void recordTheoryRead(Long userId, Topic topic) {
        TopicLock lock = activeLock(userId, topic.getId());
        if (lock != null && lock.isMustReread() && allTheoryRead(userId, topic)) {
            lock.setMustReread(false);
            lockRepository.save(lock);
        }
    }

    public static String lockedMessage() {
        return "Wrong " + LOCK_AFTER + " times in a row. Practice is locked for " + LOCK_FOR.toMinutes()
                + " minutes; read the theory again.";
    }

    private void lock(Long userId, Topic topic) {
        List<Long> theoryIds = theoryIds(topic);
        if (!theoryIds.isEmpty()) {
            progressRepository.findByUserIdAndStepIdIn(userId, theoryIds).forEach(p -> p.setPassed(false));
        }
        LocalDateTime until = LocalDateTime.now().plus(LOCK_FOR);
        TopicLock lock = lockRepository.findById(new TopicLock.Key(userId, topic.getId()))
                .orElseGet(() -> new TopicLock(userId, topic.getId(), until, true));
        lock.setLockedUntil(until);
        lock.setMustReread(!theoryIds.isEmpty());
        lockRepository.save(lock);
    }

    private boolean allTheoryRead(Long userId, Topic topic) {
        List<Long> ids = theoryIds(topic);
        long read = progressRepository.findByUserIdAndStepIdIn(userId, ids).stream().filter(TopicStepProgress::isPassed).count();
        return read == ids.size();
    }

    /** Khoá còn hiệu lực: đang đếm giờ hoặc chưa đọc lại lý thuyết. */
    private TopicLock activeLock(Long userId, Long topicId) {
        return lockRepository.findById(new TopicLock.Key(userId, topicId))
                .filter(l -> l.isMustReread() || l.getLockedUntil().isAfter(LocalDateTime.now()))
                .orElse(null);
    }

    private static List<Long> theoryIds(Topic topic) {
        return topic.getSteps().stream().filter(s -> s.getType() == TopicStepType.THEORY).map(TopicStep::getId).toList();
    }
}
