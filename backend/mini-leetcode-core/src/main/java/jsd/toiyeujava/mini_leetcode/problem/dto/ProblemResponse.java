package jsd.toiyeujava.mini_leetcode.problem.dto;

import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.entity.UserProgress;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemResponse(
        Long id,
        String title,
        String slug,
        String description,
        Difficulty difficulty,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        ProblemStatus status,
        Long createdById,
        String createdByUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Chủ đề của bài (Array, String, Hash Table...). Rỗng nếu bài chưa gán chủ đề. */
        List<String> tags,
        /**
         * Số testcase của bài (một truy vấn COUNT, không N+1).
         * Bằng 0 khi bài chưa có testcase nào.
         */
        long testCaseCount,
        /**
         * Tiến độ của người dùng hiện tại với bài này.
         * {@code null} khi khách chưa đăng nhập — không ai để so sánh.
         */
        UserProgress progress,
        String starterTemplates,
        ProblemType type
) {
    public ProblemResponse(
            Long id,
            String title,
            String slug,
            String description,
            Difficulty difficulty,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            ProblemStatus status,
            Long createdById,
            String createdByUsername,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<String> tags,
            long testCaseCount,
            UserProgress progress,
            String starterTemplates
    ) {
        this(id, title, slug, description, difficulty, timeLimitMs, memoryLimitMb, status,
                createdById, createdByUsername, createdAt, updatedAt, tags, testCaseCount, progress, starterTemplates, ProblemType.ALGORITHMIC);
    }

    public ProblemResponse(
            Long id,
            String title,
            String slug,
            String description,
            Difficulty difficulty,
            Integer timeLimitMs,
            Integer memoryLimitMb,
            ProblemStatus status,
            Long createdById,
            String createdByUsername,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<String> tags,
            long testCaseCount,
            UserProgress progress
    ) {
        this(id, title, slug, description, difficulty, timeLimitMs, memoryLimitMb, status,
                createdById, createdByUsername, createdAt, updatedAt, tags, testCaseCount, progress, null, ProblemType.ALGORITHMIC);
    }

    public static ProblemResponse fromEntity(Problem p) {
        return fromEntity(p, 0L, null);
    }

    public static ProblemResponse fromEntity(Problem p, UserProgress progress) {
        return fromEntity(p, 0L, progress);
    }

    public static ProblemResponse fromEntity(Problem p, long testCaseCount, UserProgress progress) {
        return new ProblemResponse(
                p.getId(),
                p.getTitle(),
                p.getSlug(),
                p.getDescription(),
                p.getDifficulty(),
                p.getTimeLimitMs(),
                p.getMemoryLimitMb(),
                p.getStatus(),
                p.getCreatedBy() != null ? p.getCreatedBy().getId() : null,
                p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : null,
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getTags() != null ? p.getTags().stream().filter(java.util.Objects::nonNull).toList() : List.of(),
                testCaseCount,
                progress,
                p.getStarterTemplates(),
                p.getProblemType() != null ? p.getProblemType() : ProblemType.ALGORITHMIC
        );
    }
}
