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

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final LeaderboardRepository leaderboardRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final SseService sseService;

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
                        .build());

        entry.setScore(entry.getScore() + contestProblem.getPoints());
        entry.setLastAcTime(acTime);
        leaderboardRepository.save(entry);

        sseService.broadcastLeaderboard(getLeaderboard(contest.getId()));
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboard(Long contestId) {
        List<Leaderboard> rows = leaderboardRepository
                .findByContestIdOrderByScoreDescLastAcTimeAsc(contestId);

        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(i -> {
                    Leaderboard row = rows.get(i);
                    return LeaderboardEntryDto.builder()
                            .rank(i + 1)
                            .userId(row.getUser().getId())
                            .name(row.getUser().getName())
                            .rollNo(row.getUser().getRollNo())
                            .score(row.getScore())
                            .lastAcTime(row.getLastAcTime())
                            .build();
                })
                .toList();
    }
}
