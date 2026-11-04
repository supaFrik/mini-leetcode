package jsd.toiyeujava.mini_leetcode;

import jakarta.persistence.EntityManager;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.contest.dto.ContestResponse;
import jsd.toiyeujava.mini_leetcode.contest.dto.LeaderboardRowResponse;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestProblem;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestProblemRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.contest.service.ContestService;
import jsd.toiyeujava.mini_leetcode.entity.*;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.submission.entity.Submission;
import jsd.toiyeujava.mini_leetcode.submission.repository.SubmissionRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** myRank/mySolved của danh sách kỳ thi phải khớp bảng xếp hạng và không tốn thêm truy vấn theo số kỳ thi. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContestListRankingTest {

    @Autowired private ContestService contestService;
    @Autowired private UserRepository userRepository;
    @Autowired private ContestRepository contestRepository;
    @Autowired private ContestParticipantRepository participantRepository;
    @Autowired private ContestProblemRepository contestProblemRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private EntityManager entityManager;

    private User author;
    private Problem problem;
    private final LocalDateTime now = LocalDateTime.now();

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private Contest completedContest(String slug) {
        Contest c = contestRepository.save(Contest.builder().title(slug).slug(slug)
                .startTime(now.minusDays(2)).endTime(now.minusDays(2).plusHours(2))
                .maxViolationsAllowed(3).status(ContestStatus.COMPLETED).createdBy(author).build());
        contestProblemRepository.save(ContestProblem.builder().contest(c).problem(problem).label("A").build());
        return c;
    }

    private void join(Contest c, User u, ParticipantStatus status) {
        participantRepository.save(ContestParticipant.builder().contest(c).user(u)
                .status(status).violationCount(0).score(0).penaltyTimeMinutes(0).build());
    }

    private void accepted(Contest c, User u) {
        submissionRepository.save(Submission.builder().user(u).problem(problem).contestId(c.getId())
                .language(SubmissionLanguage.JAVA).codeStorageUri("minio://test/" + u.getUsername())
                .status(SubmissionStatus.COMPLETED).verdict(SubmissionVerdict.AC).build());
    }

    private ContestResponse listed(Long contestId, User u) {
        return contestService.getContests(u.getId()).stream()
                .filter(c -> c.id().equals(contestId)).findFirst().orElseThrow();
    }

    private void resetSession() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("myRank/mySolved match the leaderboard, including ties, disqualification and non-participants")
    void listMatchesLeaderboard() {
        author = user("rank_author", Role.INSTRUCTOR);
        problem = problemRepository.save(Problem.builder().title("Rank P").slug("rank-p").description("d")
                .difficulty(Difficulty.EASY).createdBy(author).build());
        User carol = user("rank_carol", Role.USER);
        User alice = user("rank_alice", Role.USER);
        User bob = user("rank_bob", Role.USER);
        User dave = user("rank_dave", Role.USER);
        User outsider = user("rank_outsider", Role.USER);

        Contest past = completedContest("rank-past");
        join(past, carol, ParticipantStatus.SUBMITTED);
        join(past, alice, ParticipantStatus.SUBMITTED);
        join(past, bob, ParticipantStatus.SUBMITTED);
        join(past, dave, ParticipantStatus.DISQUALIFIED);
        accepted(past, carol);
        accepted(past, dave);
        resetSession();

        // Đồng hạng (alice, bob cùng 0 bài, 0 penalty) vẫn tách theo username như bảng xếp hạng.
        assertThat(listed(past.getId(), carol)).extracting(ContestResponse::myRank, ContestResponse::mySolved).containsExactly(1, 1);
        assertThat(listed(past.getId(), alice)).extracting(ContestResponse::myRank, ContestResponse::mySolved).containsExactly(2, 0);
        assertThat(listed(past.getId(), bob)).extracting(ContestResponse::myRank, ContestResponse::mySolved).containsExactly(3, 0);
        assertThat(listed(past.getId(), dave)).extracting(ContestResponse::myRank, ContestResponse::mySolved).containsExactly(null, 1);
        assertThat(listed(past.getId(), outsider)).extracting(ContestResponse::myRank, ContestResponse::mySolved).containsExactly(null, null);

        for (LeaderboardRowResponse row : contestService.getLeaderboard(past.getId())) {
            User u = userRepository.findById(row.userId()).orElseThrow();
            assertThat(listed(past.getId(), u).myRank()).isEqualTo(row.rank());
            assertThat(listed(past.getId(), u).mySolved()).isEqualTo(row.solved());
        }
    }

    @Test
    @DisplayName("Listing contests runs the same number of queries for one or many completed contests")
    void listQueryCountDoesNotGrowWithContests() {
        author = user("count_author", Role.INSTRUCTOR);
        problem = problemRepository.save(Problem.builder().title("Count P").slug("count-p").description("d")
                .difficulty(Difficulty.EASY).createdBy(author).build());
        User alice = user("count_alice", Role.USER);
        User bob = user("count_bob", Role.USER);

        Contest first = completedContest("count-0");
        join(first, alice, ParticipantStatus.SUBMITTED);
        join(first, bob, ParticipantStatus.SUBMITTED);
        accepted(first, bob);
        long one = countQueries(alice);

        for (int i = 1; i <= 4; i++) {
            Contest c = completedContest("count-" + i);
            join(c, alice, ParticipantStatus.SUBMITTED);
            join(c, bob, ParticipantStatus.SUBMITTED);
            accepted(c, bob);
        }
        long five = countQueries(alice);

        assertThat(five).isEqualTo(one);
    }

    private long countQueries(User u) {
        resetSession();
        Statistics stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        contestService.getContests(u.getId());
        return stats.getPrepareStatementCount();
    }
}
