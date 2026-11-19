package jsd.toiyeujava.mini_leetcode.sitepage.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Trang tĩnh (Terms of use, Contest rules...) do ADMIN sửa bằng Markdown (bảng {@code site_pages}, V16). */
@Entity
@Table(name = "site_pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SitePage {

    @Id
    @Column(length = 50)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "content_md", nullable = false, columnDefinition = "TEXT")
    private String contentMd;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
