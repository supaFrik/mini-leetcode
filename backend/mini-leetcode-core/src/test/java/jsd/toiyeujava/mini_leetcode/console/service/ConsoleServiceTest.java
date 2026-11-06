package jsd.toiyeujava.mini_leetcode.console.service;

import jsd.toiyeujava.mini_leetcode.console.config.ConsoleGatewayProperties;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.entity.ContestStatus;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleDisabledException;
import jsd.toiyeujava.mini_leetcode.exception.ConsoleRateLimitExceededException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleServiceTest {

    private final ProblemRepository problems = mock(ProblemRepository.class);
    private final ContestProblemRepository contests = mock(ContestProblemRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final ConsoleGatewayProperties props = new ConsoleGatewayProperties();
    private ConsoleService service;

    private static final List<ConsoleService.SourceFile> JAVA =
            List.of(new ConsoleService.SourceFile("Solution.java", "public class Solution {}"));

    @BeforeEach
    void setUp() {
        when(problems.existsById(1L)).thenReturn(true);
        when(contests.findContestsByProblemId(1L)).thenReturn(List.of());
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
        service = new ConsoleService(problems, contests, redis, props);
    }

    @Test
    void acceptsJavaRun() {
        assertThat(service.prepareRun(7L, 1L, "JAVA", JAVA)).hasSize(1);
        verify(ops).increment("console:runs:7");
    }

    @Test
    void rejectsNonJavaLanguage() {
        assertThatThrownBy(() -> service.prepareRun(7L, 1L, "PYTHON", JAVA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Java only");
    }

    @Test
    void rejectsUnknownProblem() {
        assertThatThrownBy(() -> service.prepareRun(7L, 99L, "JAVA", JAVA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsDuringRunningContest() {
        Contest contest = mock(Contest.class);
        when(contest.resolveStatus()).thenReturn(ContestStatus.RUNNING);
        when(contests.findContestsByProblemId(1L)).thenReturn(List.of(contest));
        assertThatThrownBy(() -> service.prepareRun(7L, 1L, "JAVA", JAVA))
                .isInstanceOf(ConsoleDisabledException.class);
    }

    @Test
    void rejectsShellCharactersInPathWithoutChargingQuota() {
        var bad = List.of(new ConsoleService.SourceFile("a;rm.java", "x"));
        assertThatThrownBy(() -> service.prepareRun(7L, 1L, "JAVA", bad))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ops, never()).increment(anyString());
    }

    @Test
    void enforcesRunQuota() {
        when(ops.increment(anyString())).thenReturn(31L);
        assertThatThrownBy(() -> service.prepareRun(7L, 1L, "JAVA", JAVA))
                .isInstanceOf(ConsoleRateLimitExceededException.class);
    }
}
