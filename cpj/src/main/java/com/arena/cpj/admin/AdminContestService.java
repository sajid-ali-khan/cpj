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

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminContestService {

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ProblemRepository problemRepository;
    private final ContestService contestService;
    private final com.arena.cpj.submission.SubmissionRepository submissionRepository;

    @Transactional
    public ContestResponse create(CreateContestRequest request) {
        validate(request);

        int problemCount = request.getProblems().size();
        int maxScore = request.getProblems().stream()
                .mapToInt(p -> p.getPoints() != null ? p.getPoints() : 0)
                .sum();

        Contest contest = Contest.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .startTime(request.getStartTime())
                .durationMins(request.getDurationMins())
                .problemCount(problemCount)
                .maxScore(maxScore)
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
        throw new BadRequestException("Manual starting is disabled. Contests start automatically at their scheduled time.");
    }

    @Transactional
    public ContestResponse end(Long id) {
        Contest contest = findContest(id);
        Instant now = Instant.now();
        
        if (contest.getPhase(now) != ContestPhase.LIVE) {
            throw new BadRequestException("Can only end a LIVE contest");
        }
        
        int elapsedMins = (int) java.time.Duration.between(contest.getStartTime(), now).toMinutes();
        contest.setDurationMins(Math.max(0, elapsedMins));
        contestRepository.save(contest);
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
                .phase(contest.getPhase(Instant.now()))
                .problems(problems)
                .build();
    }

    @Transactional
    public ContestResponse update(Long id, CreateContestRequest request) {
        validate(request);
        Contest contest = findContest(id);

        Instant now = Instant.now();
        ContestPhase currentPhase = contest.getPhase(now);
        if (currentPhase == ContestPhase.FINISHED) {
            throw new BadRequestException("Cannot edit a finished contest");
        }

        if (currentPhase == ContestPhase.LIVE) {
            if (contest.getStartTime().compareTo(request.getStartTime()) != 0) {
                throw new BadRequestException("Cannot change start time of a live contest");
            }
            List<ContestProblem> existingProblems = contestProblemRepository.findByIdContestIdOrderByDisplayOrderAsc(id);
            if (existingProblems.size() != request.getProblems().size()) {
                throw new BadRequestException("Cannot add or remove questions from a live contest");
            }
            for (ContestProblemRequest reqProb : request.getProblems()) {
                ContestProblem existing = existingProblems.stream()
                        .filter(ep -> ep.getProblem().getId().equals(reqProb.getProblemId()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Cannot change questions of a live contest"));
                if (!existing.getPoints().equals(reqProb.getPoints())) {
                    throw new BadRequestException("Cannot modify question points while a contest is live");
                }
            }
        }

        int problemCount = request.getProblems().size();
        int maxScore = request.getProblems().stream()
                .mapToInt(p -> p.getPoints() != null ? p.getPoints() : 0)
                .sum();

        contest.setTitle(request.getTitle().trim());
        contest.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        contest.setStartTime(request.getStartTime());
        contest.setDurationMins(request.getDurationMins());
        contest.setProblemCount(problemCount);
        contest.setMaxScore(maxScore);
        contest = contestRepository.save(contest);

        contestProblemRepository.deleteByIdContestId(id);
        contestProblemRepository.flush();

        for (ContestProblemRequest problemRequest : request.getProblems()) {
            addProblemToContest(contest, problemRequest);
        }

        return toResponse(contest);
    }

    @Transactional(readOnly = true)
    public List<AdminSubmissionResponse> getContestSubmissions(Long contestId) {
        if (!contestRepository.existsById(contestId)) {
            throw new NotFoundException("Contest not found: " + contestId);
        }

        return submissionRepository.findByContestIdOrderBySubmittedAtDesc(contestId).stream()
                .map(sub -> {
                    String languageName = "Java";
                    if (sub.getLanguageId() != null) {
                        switch (sub.getLanguageId()) {
                            case 54: languageName = "C++"; break;
                            case 71: languageName = "Python"; break;
                            default: languageName = "Java"; break;
                        }
                    }

                    return AdminSubmissionResponse.builder()
                            .id(sub.getId())
                            .studentName(sub.getUser().getName())
                            .studentRoll(sub.getUser().getRollNo())
                            .questionTitle(sub.getProblem().getTitle())
                            .language(languageName)
                            .verdict(sub.getVerdict())
                            .timeMs(sub.getTimeMs())
                            .memoryKb(sub.getMemoryKb())
                            .submittedAt(sub.getSubmittedAt())
                            .build();
                })
                .toList();
    }
}
