package com.arena.cpj.contest;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.dto.ContestProblemSummaryResponse;
import com.arena.cpj.contest.dto.ContestSummaryResponse;
import com.arena.cpj.event.SseService;
import com.arena.cpj.event.dto.ContestEventDto;
import com.arena.cpj.leaderboard.Leaderboard;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final SseService sseService;
    private final LeaderboardRepository leaderboardRepository;
    private final LeaderboardService leaderboardService;

    @Transactional(readOnly = true)
    public ContestSummaryResponse getCurrentContest() {
        Contest contest = contestRepository.findByStatus(ContestStatus.ONGOING)
                .orElseThrow(() -> new NotFoundException("No ongoing contest"));
        return toSummary(contest);
    }

    @Transactional(readOnly = true)
    public List<ContestSummaryResponse> getAllContests() {
        return contestRepository.findAllByOrderByStartTimeDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContestProblemSummaryResponse> getContestProblems(Long contestId) {
        if (!contestRepository.existsById(contestId)) {
            throw new NotFoundException("Contest not found: " + contestId);
        }
        return contestProblemRepository.findByIdContestIdOrderByDisplayOrderAsc(contestId)
                .stream()
                .map(cp -> ContestProblemSummaryResponse.builder()
                        .problemId(cp.getProblem().getId())
                        .title(cp.getProblem().getTitle())
                        .description(cp.getProblem().getDescription())
                        .constraints(cp.getProblem().getConstraints())
                        .difficulty(cp.getProblem().getDifficulty())
                        .points(cp.getPoints())
                        .displayOrder(cp.getDisplayOrder())
                        .build())
                .toList();
    }

    private ContestSummaryResponse toSummary(Contest contest) {
        List<Long> problemIds = contestProblemRepository.findByIdContestIdOrderByDisplayOrderAsc(contest.getId())
                .stream()
                .map(cp -> cp.getProblem().getId())
                .toList();

        return ContestSummaryResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .description(contest.getDescription())
                .startTime(contest.getStartTime())
                .durationMins(contest.getDurationMins())
                .status(contest.getStatus())
                .problemIds(problemIds)
                .build();
    }

    /**
     * Shared method for all status transitions (automatic and manual).
     * Persists the new status and broadcasts contest event to all SSE emitters.
     *
     * @param contest The contest to transition
     * @param newStatus The target status
     */
    @Transactional
    public void transitionStatus(Contest contest, ContestStatus newStatus) {
        contest.setStatus(newStatus);
        contestRepository.save(contest);

        // Broadcast contest status change to all connected students
        sseService.broadcastContestEvent(ContestEventDto.builder()
                .contestId(contest.getId())
                .status(newStatus)
                .build());
    }

    @Transactional
    public void registerUserForContest(User user, Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new NotFoundException("Contest not found: " + contestId));

        if (contest.getStatus() == ContestStatus.ENDED) {
            throw new com.arena.cpj.common.BadRequestException("Cannot register for an ended contest");
        }

        boolean exists = leaderboardRepository.findByContestIdAndUserId(contestId, user.getId()).isPresent();
        if (!exists) {
            Leaderboard entry = Leaderboard.builder()
                    .contest(contest)
                    .user(user)
                    .score(0)
                    .lastAcTime(null)
                    .build();
            leaderboardRepository.save(entry);

            // Broadcast the new leaderboard list via SSE
            sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
        }
    }
}
