package jsd.toiyeujava.mini_leetcode.contest.admin;

import jsd.toiyeujava.mini_leetcode.contest.admin.ContestAdminDtos.ContestProblemInput;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContestAdminRulesTest {

    @Test
    void defaultLabelsGoAtoZThenAA() {
        assertThat(ContestAdminService.defaultLabel(0)).isEqualTo("A");
        assertThat(ContestAdminService.defaultLabel(25)).isEqualTo("Z");
        assertThat(ContestAdminService.defaultLabel(26)).isEqualTo("AA");
        assertThat(ContestAdminService.defaultLabel(27)).isEqualTo("AB");
    }

    @Test
    void normalizeSortsByOrderFillsLabelsAndUppercases() {
        List<ContestProblemInput> out = ContestAdminService.normalizeProblems(List.of(
                new ContestProblemInput(7L, null, 2),
                new ContestProblemInput(5L, "b1", 0),
                new ContestProblemInput(6L, " ", 1)));
        assertThat(out).extracting(ContestProblemInput::problemId).containsExactly(5L, 6L, 7L);
        assertThat(out).extracting(ContestProblemInput::label).containsExactly("B1", "B", "C");
        assertThat(out).extracting(ContestProblemInput::orderIndex).containsExactly(0, 1, 2);
    }

    @Test
    void normalizeRejectsBadLabelsAndDuplicates() {
        assertThatThrownBy(() -> ContestAdminService.normalizeProblems(List.of(new ContestProblemInput(1L, "ABCD", 0))))
                .hasMessageContaining("1-3 letters or digits");
        assertThatThrownBy(() -> ContestAdminService.normalizeProblems(List.of(new ContestProblemInput(1L, "A-", 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContestAdminService.normalizeProblems(List.of(
                new ContestProblemInput(1L, "A", 0), new ContestProblemInput(1L, "B", 1))))
                .hasMessageContaining("more than once");
        assertThatThrownBy(() -> ContestAdminService.normalizeProblems(Arrays.asList(new ContestProblemInput(null, "A", 0))))
                .hasMessageContaining("problemId");
    }

    @Test
    void parseEntriesSplitsLinesCommasAndSemicolons() {
        assertThat(ContestAdminService.parseEntries("sv01, sv02\r\n\n sv03;sv04 ,"))
                .containsExactly("sv01", "sv02", "sv03", "sv04");
        assertThat(ContestAdminService.parseEntries(null)).isEmpty();
    }

    @Test
    void resolveStatusFollowsTheClock() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(contest(ContestStatus.SCHEDULED, now.plusHours(1), now.plusHours(2)).resolveStatus())
                .isEqualTo(ContestStatus.SCHEDULED);
        // Tạo trên admin (cột còn SCHEDULED) → tự RUNNING khi tới giờ, COMPLETED khi hết giờ.
        assertThat(contest(ContestStatus.SCHEDULED, now.minusMinutes(1), now.plusHours(1)).resolveStatus())
                .isEqualTo(ContestStatus.RUNNING);
        assertThat(contest(ContestStatus.SCHEDULED, now.minusHours(2), now.minusHours(1)).resolveStatus())
                .isEqualTo(ContestStatus.COMPLETED);
        assertThat(contest(ContestStatus.RUNNING, now.minusHours(2), now.minusSeconds(1)).resolveStatus())
                .isEqualTo(ContestStatus.COMPLETED);
        assertThat(contest(ContestStatus.COMPLETED, now.minusHours(1), now.plusHours(1)).resolveStatus())
                .isEqualTo(ContestStatus.COMPLETED);
        assertThat(contest(ContestStatus.SCHEDULED, null, null).resolveStatus()).isEqualTo(ContestStatus.SCHEDULED);
    }

    private static Contest contest(ContestStatus status, LocalDateTime start, LocalDateTime end) {
        return Contest.builder().status(status).startTime(start).endTime(end).build();
    }
}
