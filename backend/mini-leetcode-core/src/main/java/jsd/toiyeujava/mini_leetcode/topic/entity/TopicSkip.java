package jsd.toiyeujava.mini_leetcode.topic.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/** Học viên bấm "Skip" một chủ đề (đảo ngược được). */
@Entity
@Table(name = "topic_skips")
@IdClass(TopicSkip.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopicSkip {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "topic_id")
    private Long topicId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long userId;
        private Long topicId;
    }
}
