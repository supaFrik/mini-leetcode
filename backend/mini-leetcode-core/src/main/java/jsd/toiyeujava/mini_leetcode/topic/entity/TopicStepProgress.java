package jsd.toiyeujava.mini_leetcode.topic.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Tiến độ của một học viên ở một step (không dùng cho CODE: xong khi đề được AC). */
@Entity
@Table(name = "topic_step_progress")
@IdClass(TopicStepProgress.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicStepProgress {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "step_id")
    private Long stepId;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long userId;
        private Long stepId;
    }
}
