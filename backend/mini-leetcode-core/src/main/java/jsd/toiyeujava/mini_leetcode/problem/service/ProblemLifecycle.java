package jsd.toiyeujava.mini_leetcode.problem.service;

import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máy trạng thái của vòng đời bài tập.
 *
 * <pre>
 *   DRAFT ──▶ REVIEWING ──▶ PUBLISHED ──▶ ARCHIVED
 *     ▲           │                           │
 *     └───────────┘                           │
 *     └───────────────────────────────────────┘
 * </pre>
 *
 * Quy tắc:
 * <ul>
 *   <li>Chỉ đi theo cạnh hợp lệ; chuyển sang trạng thái không hợp lệ bị từ chối
 *       ở tầng server (không tin phía client).</li>
 *   <li>{@code PUBLISHED} yêu cầu bài đã có ít nhất một testcase công khai.</li>
 *   <li>Testcase của bài đã {@code PUBLISHED} bị khoá (đóng băng): không thêm,
 *       sửa hay xoá được cho tới khi bài rời trạng thái {@code PUBLISHED}.</li>
 * </ul>
 */
public final class ProblemLifecycle {

    private static final Map<ProblemStatus, Set<ProblemStatus>> ALLOWED = new EnumMap<>(ProblemStatus.class);

    static {
        ALLOWED.put(ProblemStatus.DRAFT, EnumSet.of(ProblemStatus.REVIEWING, ProblemStatus.ARCHIVED));
        ALLOWED.put(ProblemStatus.REVIEWING, EnumSet.of(ProblemStatus.DRAFT, ProblemStatus.PUBLISHED));
        ALLOWED.put(ProblemStatus.PUBLISHED, EnumSet.of(ProblemStatus.ARCHIVED));
        ALLOWED.put(ProblemStatus.ARCHIVED, EnumSet.of(ProblemStatus.DRAFT));
    }

    private ProblemLifecycle() {
    }

    /** Các trạng thái hợp lệ có thể chuyển tới từ {@code from}. */
    public static Set<ProblemStatus> allowedTargets(ProblemStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    /** {@code from → to} có hợp lệ không? */
    public static boolean isAllowed(ProblemStatus from, ProblemStatus to) {
        return from != null && to != null && allowedTargets(from).contains(to);
    }

    /**
     * Testcase của bài có bị khoá không? Bài {@code PUBLISHED} là bài đang
     * được chấm, nên không được thay đổi bộ testcase giữa chừng.
     */
    public static boolean isTestCaseFrozen(ProblemStatus status) {
        return status == ProblemStatus.PUBLISHED;
    }
}
