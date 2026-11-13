package jsd.toiyeujava.mini_leetcode.studyplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một chủ đề gắn vào stage {@code stageNo} (0-based) của một mục plan (specs/topics.spec.md). */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StageTopic {

    @Column(name = "stage_no", nullable = false)
    private int stageNo;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;
}
