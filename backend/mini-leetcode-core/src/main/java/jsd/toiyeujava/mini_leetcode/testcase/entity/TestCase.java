package jsd.toiyeujava.mini_leetcode.testcase.entity;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "input_file_uri", nullable = false, length = 500)
    private String inputFileUri;

    @Column(name = "expected_output_uri", nullable = false, length = 500)
    private String expectedOutputUri;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private Boolean isHidden = true;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "file_size_bytes", nullable = false)
    @Builder.Default
    private Long fileSizeBytes = 0L;

    /** Nhãn hiện trong thông báo lỗi của judge (vd. "stage 1 · two-accounts"); null = không nhãn. */
    @Column(length = 100)
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
