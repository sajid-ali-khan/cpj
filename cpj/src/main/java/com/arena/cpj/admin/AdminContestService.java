package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.*;
import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.*;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminContestService {

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ProblemRepository problemRepository;
    private final ContestService contestService;

    @Transactional
    public ContestResponse create(CreateContestRequest request) {
        validate(request);

        Contest contest = Contest.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .startTime(request.getStartTime())
                .durationMins(request.getDurationMins())
                .status(ContestStatus.UPCOMING)
                .build();
        contest = contestRepository.save(contest);

        for (ContestProblemRequest problemRequest : request.getProblems()) {
            addProblemToContest(contest, problemRequest);
        }

        return toResponse(contest);
    }

    @Transactional
    public List<ContestResponse> list() {
        return contestRepository.findAllByOrderByStartTimeDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContestResponse get(Long id) {
        return toResponse(findContest(id));
    }

    @Transactional
    public ContestResponse start(Long id) {
        Contest contest = findContest(id);
        
        if (contest.getStatus() == ContestStatus.ENDED) {
            throw new BadRequestException("Cannot start an ended contest");
        }

        // End any other ONGOING contests before starting this one
        contestRepository.findAllByStatus(ContestStatus.ONGOING).forEach(other -> {
            if (!other.getId().equals(contest.getId())) {
                contestService.transitionStatus(other, ContestStatus.ENDED);
            }
        });

        // Start this contest (only if not already ONGOING)
        if (contest.getStatus() != ContestStatus.ONGOING) {
            contestService.transitionStatus(contest, ContestStatus.ONGOING);
        }
        
        return toResponse(contest);
    }

    @Transactional
    public ContestResponse end(Long id) {
        Contest contest = findContest(id);
        
        if (contest.getStatus() != ContestStatus.ONGOING) {
            throw new BadRequestException("Can only end an ONGOING contest");
        }
        
        contestService.transitionStatus(contest, ContestStatus.ENDED);
        return toResponse(contest);
    }

    private void addProblemToContest(Contest contest, ContestProblemRequest request) {
        if (request.getProblemId() == null) {
            throw new BadRequestException("problemId is required");
        }
        if (request.getPoints() == null || request.getPoints() <= 0) {
            throw new BadRequestException("points must be positive");
        }
        if (request.getDisplayOrder() == null) {
            throw new BadRequestException("displayOrder is required");
        }

        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new NotFoundException("Problem not found: " + request.getProblemId()));

        ContestProblemId key = new ContestProblemId(contest.getId(), problem.getId());
        if (contestProblemRepository.existsById(key)) {
            throw new BadRequestException("Problem already in contest: " + problem.getId());
        }

        ContestProblem contestProblem = ContestProblem.builder()
                .id(key)
                .contest(contest)
                .problem(problem)
                .points(request.getPoints())
                .displayOrder(request.getDisplayOrder())
                .build();
        contestProblemRepository.save(contestProblem);
    }

    private Contest findContest(Long id) {
        return contestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Contest not found: " + id));
    }

    private void validate(CreateContestRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }
        if (request.getStartTime() == null) {
            throw new BadRequestException("startTime is required");
        }
        if (request.getDurationMins() == null || request.getDurationMins() <= 0) {
            throw new BadRequestException("durationMins must be positive");
        }
        if (request.getProblems() == null || request.getProblems().isEmpty()) {
            throw new BadRequestException("At least one problem is required");
        }
    }

    private ContestResponse toResponse(Contest contest) {
        List<ContestProblemResponse> problems = contestProblemRepository
                .findByIdContestIdOrderByDisplayOrderAsc(contest.getId())
                .stream()
                .map(cp -> ContestProblemResponse.builder()
                        .problemId(cp.getProblem().getId())
                        .title(cp.getProblem().getTitle())
                        .difficulty(cp.getProblem().getDifficulty())
                        .points(cp.getPoints())
                        .displayOrder(cp.getDisplayOrder())
                        .build())
                .toList();

        return ContestResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .description(contest.getDescription())
                .startTime(contest.getStartTime())
                .durationMins(contest.getDurationMins())
                .status(contest.getStatus())
                .problems(problems)
                .build();
    }

    @Transactional
    public ContestResponse update(Long id, CreateContestRequest request) {
        validate(request);
        Contest contest = findContest(id);

        if (contest.getStatus() == ContestStatus.ENDED) {
            throw new BadRequestException("Cannot edit an ended contest");
        }

        contest.setTitle(request.getTitle().trim());
        contest.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        contest.setStartTime(request.getStartTime());
        contest.setDurationMins(request.getDurationMins());
        contest = contestRepository.save(contest);

        contestProblemRepository.deleteByIdContestId(id);
        contestProblemRepository.flush();

        for (ContestProblemRequest problemRequest : request.getProblems()) {
            addProblemToContest(contest, problemRequest);
        }

        return toResponse(contest);
    }
}
