package com.arena.cpj.contest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContestScheduler {

    private final ContestRepository contestRepository;
    private final ContestService contestService;

    /**
     * Scheduled task running every 30 seconds to handle automatic contest status transitions.
     * 
     * Transitions:
     * - UPCOMING → ONGOING if start_time <= now()
     * - ONGOING → ENDED if start_time + duration_mins <= now()
     */
    @Scheduled(fixedRate = 30000) // 30 seconds in milliseconds
    @Transactional
    public void checkAndTransitionContests() {
        LocalDateTime now = LocalDateTime.now();

        // Check for UPCOMING contests that should start
        List<Contest> upcomingContests = contestRepository.findAllByStatus(ContestStatus.UPCOMING);
        for (Contest contest : upcomingContests) {
            if (now.isAfter(contest.getStartTime()) || now.equals(contest.getStartTime())) {
                log.info("Transitioning contest {} from UPCOMING to ONGOING", contest.getId());
                contestService.transitionStatus(contest, ContestStatus.ONGOING);
            }
        }

        // Check for ONGOING contests that should end
        List<Contest> ongoingContests = contestRepository.findAllByStatus(ContestStatus.ONGOING);
        for (Contest contest : ongoingContests) {
            if (contest.isExpired()) {
                log.info("Transitioning contest {} from ONGOING to ENDED", contest.getId());
                contestService.transitionStatus(contest, ContestStatus.ENDED);
            }
        }
    }
}
