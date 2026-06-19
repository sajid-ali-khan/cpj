package com.arena.cpj.contest;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.dto.ContestProblemSummaryResponse;
import com.arena.cpj.contest.dto.ContestSummaryResponse;
import com.arena.cpj.event.SseService;
import com.arena.cpj.event.dto.ContestEventDto;
import com.arena.cpj.leaderboard.Leaderboard;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.problem.TestCaseRepository;
import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.user.UserRole;
import com.arena.cpj.auth.UserContext;
import com.arena.cpj.auth.ForbiddenException;
import com.arena.cpj.leaderboard.ParticipantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final SseService sseService;
    private final LeaderboardRepository leaderboardRepository;
    private final LeaderboardService leaderboardService;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ContestSummaryResponse> getCurrentContest() {
        Instant now = Instant.now();
        return contestRepository.findAllByDeletedFalseOrderByStartTimeDesc().stream()
                .filter(c -> c.getPhase(now) == ContestPhase.LIVE)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContestSummaryResponse> getAllContests() {
        return contestRepository.findAllByDeletedFalseOrderByStartTimeDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public List<ContestProblemSummaryResponse> getContestProblems(Long contestId) {
        Contest contest = findContest(contestId);

        User currentUser = UserContext.get();
        if (currentUser != null && currentUser.getRole() == UserRole.STUDENT) {
            if (contest.getPhase(Instant.now()) != ContestPhase.LIVE) {
                throw new ForbiddenException("This contest is not live.");
            }
            Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contestId, currentUser.getId())
                    .orElseThrow(() -> new ForbiddenException("You are not registered for this contest"));
            if (entry.getStatus() == ParticipantStatus.NOT_REGISTERED) {
                throw new ForbiddenException("You must register for the contest first.");
            }
            if (entry.getStatus() == ParticipantStatus.FINISHED) {
                throw new ForbiddenException("You have already submitted this contest");
            }
            if (entry.getStatus() == ParticipantStatus.REGISTERED) {
                entry.setStatus(ParticipantStatus.WRITING);
                leaderboardRepository.save(entry);
                sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
            }
        }

        return contestProblemRepository.findByIdContestIdOrderByDisplayOrderAsc(contestId)
                .stream()
                .map(cp -> {
                    List<ContestProblemSummaryResponse.SampleTestCaseDto> sampleTestCases = testCaseRepository
                            .findByProblemIdAndIsSampleTrue(cp.getProblem().getId())
                            .stream()
                            .map(tc -> ContestProblemSummaryResponse.SampleTestCaseDto.builder()
                                     .input(tc.getStdin())
                                     .output(tc.getExpectedOutput())
                                     .isHidden(false)
                                     .build())
                            .toList();

                    return ContestProblemSummaryResponse.builder()
                            .problemId(cp.getProblem().getId())
                            .title(cp.getProblem().getTitle())
                            .description(cp.getProblem().getDescription())
                            .constraints(cp.getProblem().getConstraints())
                            .difficulty(cp.getProblem().getDifficulty())
                            .points(cp.getPoints())
                            .displayOrder(cp.getDisplayOrder())
                            .inputStructure(cp.getProblem().getInputStructure())
                            .outputStructure(cp.getProblem().getOutputStructure())
                            .testCases(sampleTestCases)
                            .build();
                })
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
                .phase(contest.getPhase(Instant.now()))
                .problemIds(problemIds)
                .build();
    }

    @Transactional
    public void registerUserForContest(User user, Long contestId) {
        Contest contest = findContest(contestId);

        if (contest.getPhase(Instant.now()) == ContestPhase.FINISHED) {
            throw new com.arena.cpj.common.BadRequestException("Cannot register for a finished contest");
        }

        Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contestId, user.getId())
                .orElse(null);

        if (entry == null) {
            entry = Leaderboard.builder()
                    .contest(contest)
                    .user(user)
                    .score(0)
                    .lastAcTime(null)
                    .status(ParticipantStatus.REGISTERED)
                    .build();
            leaderboardRepository.save(entry);
            sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
        } else if (entry.getStatus() == ParticipantStatus.NOT_REGISTERED) {
            entry.setStatus(ParticipantStatus.REGISTERED);
            leaderboardRepository.save(entry);
            sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
        }
    }

    @Transactional
    public void registerStudentsBulk(Long contestId, List<String> rollNumbers) {
        Contest contest = findContest(contestId);

        List<Leaderboard> newEntries = new ArrayList<>();

        for (String rollNo : rollNumbers) {
            if (rollNo == null || rollNo.trim().isEmpty()) {
                continue;
            }
            User user = userRepository.findByRollNo(rollNo.trim())
                    .orElseThrow(() -> new NotFoundException("User not found for roll number: " + rollNo));

            boolean exists = leaderboardRepository.findByContestIdAndUserId(contestId, user.getId()).isPresent();
            if (!exists) {
                Leaderboard entry = Leaderboard.builder()
                        .contest(contest)
                        .user(user)
                        .score(0)
                        .status(ParticipantStatus.NOT_REGISTERED)
                        .build();
                newEntries.add(entry);
            }
        }
        if (!newEntries.isEmpty()) {
            leaderboardRepository.saveAll(newEntries);
        }

        // Broadcast the new leaderboard list via SSE
        sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
    }

    @Transactional
    public void submitContest(Long contestId, User user) {
        
        Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contestId, user.getId())
                .orElseThrow(() -> new NotFoundException("User is not registered for this contest"));

        if (entry.getStatus() != ParticipantStatus.FINISHED) {
            entry.setStatus(ParticipantStatus.FINISHED);
            leaderboardRepository.save(entry);

            // Broadcast the new leaderboard list via SSE
            sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
        }
    }

    public ContestSummaryResponse getContest(Long contestId) {
        Contest contest = findContest(contestId);
        return toSummary(contest);
    }

    private Contest findContest(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new NotFoundException("Contest not found: " + contestId));
        if (contest.isDeleted()) {
            throw new NotFoundException("Contest not found: " + contestId);
        }
        return contest;
    }

    @Transactional
    public void deleteStudentRegistration(Long contestId, Long userId) {
        Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new NotFoundException("Registration not found for contest: " + contestId + " and user: " + userId));
        leaderboardRepository.delete(entry);
        sseService.broadcastLeaderboard(leaderboardService.getLeaderboard(contestId));
    }
}
