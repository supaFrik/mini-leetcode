package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.contest.entity.Contest;
import jsd.toiyeujava.mini_leetcode.contest.entity.ContestParticipant;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestParticipantRepository;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** myRank / mySolved trong ContestResponse: chỉ có ở kỳ thi đã kết thúc mà user tham gia. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContestRecordIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ContestRepository contestRepository;
    @Autowired private ContestParticipantRepository participantRepository;

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).build());
    }

    private Contest contest(String slug, LocalDateTime start, LocalDateTime end, ContestStatus status, User author) {
        return contestRepository.save(Contest.builder().title(slug).slug(slug).startTime(start).endTime(end)
                .maxViolationsAllowed(3).status(status).createdBy(author).build());
    }

    @Test
    @DisplayName("Completed contest the user joined carries myRank and mySolved; others do not")
    void contestList_exposesOwnResultForCompletedContests() throws Exception {
        User author = user("record_author", Role.INSTRUCTOR);
        User alice = user("record_alice", Role.USER);
        User bob = user("record_bob", Role.USER);
        LocalDateTime now = LocalDateTime.now();

        Contest past = contest("record-past", now.minusDays(2), now.minusDays(2).plusHours(2), ContestStatus.COMPLETED, author);
        Contest live = contest("record-live", now.minusHours(1), now.plusHours(1), ContestStatus.RUNNING, author);
        for (Contest c : new Contest[]{past, live}) {
            participantRepository.save(ContestParticipant.builder().contest(c).user(alice)
                    .status(ParticipantStatus.SUBMITTED).violationCount(0).score(0).penaltyTimeMinutes(0).build());
        }

        String aliceToken = "Bearer " + jwtTokenProvider.generateAccessToken(alice);
        mockMvc.perform(get("/api/v1/contests").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + past.getId() + ")].myRank").value(1))
                .andExpect(jsonPath("$[?(@.id == " + past.getId() + ")].mySolved").value(0))
                .andExpect(jsonPath("$[?(@.id == " + live.getId() + ")].myRank").value((Object) null))
                .andExpect(jsonPath("$[?(@.id == " + live.getId() + ")].mySolved").value((Object) null));

        // Bob never joined: no result for him.
        mockMvc.perform(get("/api/v1/contests").header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + past.getId() + ")].myRank").value((Object) null));
    }
}
