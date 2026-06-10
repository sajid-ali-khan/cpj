package com.arena.cpj.contest;

import com.arena.cpj.submission.SubmissionRepository;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ContestSchedulerTest {

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestProblemRepository contestProblemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private LeaderboardRepository leaderboardRepository;

    @Autowired
    private ContestScheduler contestScheduler;

    @BeforeEach
    public void setup() {
        submissionRepository.deleteAll();
        leaderboardRepository.deleteAll();
        contestProblemRepository.deleteAll();
        contestRepository.deleteAll();
    }

    @Test
    public void testUpcomingToOngoingTransition() {
        // Create an upcoming contest whose start time is in the past (should start)
        Contest upcomingToStart = Contest.builder()
                .title("Upcoming to Start")
                .startTime(LocalDateTime.now().minusMinutes(5))
                .durationMins(60)
                .status(ContestStatus.UPCOMING)
                .build();

        // Create an upcoming contest whose start time is in the future (should NOT start)
        Contest upcomingFuture = Contest.builder()
                .title("Upcoming Future")
                .startTime(LocalDateTime.now().plusMinutes(5))
                .durationMins(60)
                .status(ContestStatus.UPCOMING)
                .build();

        contestRepository.saveAll(List.of(upcomingToStart, upcomingFuture));

        // Run scheduler
        contestScheduler.checkAndTransitionContests();

        // Refresh from DB
        Contest updatedToStart = contestRepository.findById(upcomingToStart.getId()).orElseThrow();
        Contest updatedFuture = contestRepository.findById(upcomingFuture.getId()).orElseThrow();

        assertEquals(ContestStatus.ONGOING, updatedToStart.getStatus());
        assertEquals(ContestStatus.UPCOMING, updatedFuture.getStatus());
    }

    @Test
    public void testOngoingToEndedTransition() {
        // Create an ongoing contest that is expired
        Contest ongoingExpired = Contest.builder()
                .title("Ongoing Expired")
                .startTime(LocalDateTime.now().minusMinutes(70))
                .durationMins(60)
                .status(ContestStatus.ONGOING)
                .build();

        // Create an ongoing contest that is NOT expired
        Contest ongoingActive = Contest.builder()
                .title("Ongoing Active")
                .startTime(LocalDateTime.now().minusMinutes(10))
                .durationMins(60)
                .status(ContestStatus.ONGOING)
                .build();

        contestRepository.saveAll(List.of(ongoingExpired, ongoingActive));

        // Run scheduler
        contestScheduler.checkAndTransitionContests();

        // Refresh from DB
        Contest updatedExpired = contestRepository.findById(ongoingExpired.getId()).orElseThrow();
        Contest updatedActive = contestRepository.findById(ongoingActive.getId()).orElseThrow();

        assertEquals(ContestStatus.ENDED, updatedExpired.getStatus());
        assertEquals(ContestStatus.ONGOING, updatedActive.getStatus());
    }
}
