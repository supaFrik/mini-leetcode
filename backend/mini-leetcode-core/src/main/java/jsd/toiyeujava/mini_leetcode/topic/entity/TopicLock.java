package jsd.toiyeujava.mini_leetcode.topic.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Khoá phần luyện tập của một chủ đề sau 3 lần sai liên tiếp ở một câu: mở lại khi đã qua {@code lockedUntil}
 * và đã đọc lại hết lý thuyết ({@code mustReread = false}).
 */
@Entity
@Table(name = "topic_locks")
@IdClass(TopicLock.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopicLock {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "locked_until", nullable = false)
    private LocalDateTime lockedUntil;

    @Column(name = "must_reread", nullable = false)
    private boolean mustReread;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long userId;
        private Long topicId;
    }
}
