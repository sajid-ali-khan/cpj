package com.arena.cpj.contest;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.dto.ContestProblemSummaryResponse;
import com.arena.cpj.contest.dto.ContestSummaryResponse;
import com.arena.cpj.event.SseService;
import com.arena.cpj.event.dto.ContestEventDto;
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
        return ContestSummaryResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .startTime(contest.getStartTime())
                .durationMins(contest.getDurationMins())
                .status(contest.getStatus())
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
}
