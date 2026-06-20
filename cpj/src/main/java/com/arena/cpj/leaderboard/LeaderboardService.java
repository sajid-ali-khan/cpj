package com.arena.cpj.leaderboard;

import com.arena.cpj.contest.Contest;
import com.arena.cpj.contest.ContestProblem;
import com.arena.cpj.contest.ContestProblemRepository;
import com.arena.cpj.event.SseService;
import com.arena.cpj.leaderboard.dto.LeaderboardEntryDto;
import com.arena.cpj.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final LeaderboardRepository leaderboardRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final SseService sseService;
    private final com.arena.cpj.submission.SubmissionRepository submissionRepository;

    /**
     * Sole entry point for leaderboard writes. Called from the submission callback
     * handler after a confirmed first-time AC.
     */
    @Transactional
    public void recordFirstAcceptedSubmission(User user, Contest contest, Long problemId,
                                              LocalDateTime acTime) {
        ContestProblem contestProblem = contestProblemRepository
                .findByIdContestIdAndIdProblemId(contest.getId(), problemId)
                .orElseThrow(() -> new IllegalStateException(
                        "Problem " + problemId + " is not part of contest " + contest.getId()));

        Leaderboard entry = leaderboardRepository
                .findByContestIdAndUserId(contest.getId(), user.getId())
                .orElseGet(() -> Leaderboard.builder()
                        .contest(contest)
                        .user(user)
                        .score(0)
                        .solvedCount(0)
                        .build());

        entry.setScore(entry.getScore() + contestProblem.getPoints());
        entry.setSolvedCount(entry.getSolvedCount() + 1);
        entry.setLastAcTime(acTime);
        leaderboardRepository.save(entry);

                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                         sseService.broadcastLeaderboard(getLeaderboard(contest.getId()));
                                }
                        });
                } else {
                        sseService.broadcastLeaderboard(getLeaderboard(contest.getId()));
                }
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboard(Long contestId) {
        List<Leaderboard> rows = leaderboardRepository
                .findByContestIdOrderByScoreDescLastAcTimeAsc(contestId);

        List<ContestProblem> contestProblems = contestProblemRepository
                .findByIdContestIdOrderByDisplayOrderAsc(contestId);
        java.util.Map<Long, Integer> problemPoints = contestProblems.stream()
                .collect(java.util.stream.Collectors.toMap(
                        cp -> cp.getProblem().getId(),
                        ContestProblem::getPoints
                ));

        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(i -> {
                    Leaderboard row = rows.get(i);
                    Contest contest = row.getContest();
                    User user = row.getUser();

                    List<com.arena.cpj.submission.Submission> acSubs = submissionRepository
                            .findByUserIdAndContestIdAndVerdictOrderBySubmittedAtAsc(
                                    user.getId(), contestId, com.arena.cpj.submission.Verdict.ACCEPTED);

                    java.util.Map<Long, com.arena.cpj.submission.Submission> firstAcPerProblem = new java.util.LinkedHashMap<>();
                    for (com.arena.cpj.submission.Submission sub : acSubs) {
                        firstAcPerProblem.putIfAbsent(sub.getProblem().getId(), sub);
                    }

                    List<LeaderboardEntryDto.SolvedProblemDto> solvedProblems = firstAcPerProblem.values().stream()
                            .map(sub -> {
                                int pts = problemPoints.getOrDefault(sub.getProblem().getId(), 100);
                                String timeStr = sub.getTimeMs() != null ? sub.getTimeMs() + "ms" : "0ms";
                                return LeaderboardEntryDto.SolvedProblemDto.builder()
                                        .title(sub.getProblem().getTitle())
                                        .verdict("Accepted")
                                        .score(pts)
                                        .maxScore(pts)
                                        .time(timeStr)
                                        .build();
                            })
                            .toList();

                    return LeaderboardEntryDto.builder()
                            .rank(i + 1)
                            .userId(user.getId())
                            .name(user.getName())
                            .rollNo(user.getRollNo())
                            .score(row.getScore())
                            .lastAcTime(row.getLastAcTime())
                            .status(contest.getPhase(java.time.Instant.now()) == com.arena.cpj.contest.ContestPhase.FINISHED ? ParticipantStatus.SUBMITTED.name() : row.getStatus().name())
                            .solvedCount(row.getSolvedCount())
                            .totalQuestions(contest.getProblemCount())
                            .maxScore(contest.getMaxScore())
                            .violations(row.getViolations())
                            .deleted(user.isDeleted())
                            .problems(solvedProblems)
                            .build();
                })
                .toList();
    }
}
