package com.arena.cpj.leaderboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {

    /**
     * Full leaderboard for a contest, sorted ICPC-style:
     * highest score first, earliest last-AC time breaks ties.
     * NULLs (students with 0 AC) sort last due to NULLS LAST on the index.
     */
    List<Leaderboard> findByContestIdOrderByScoreDescLastAcTimeAsc(Long contestId);

    /**
     * Targeted row fetch used by the callback handler to increment
     * a student's score after a confirmed first-time AC.
     */
    Optional<Leaderboard> findByContestIdAndUserId(Long contestId, Long userId);

    /**
     * Existence check used at contest-join time to prevent
     * duplicate leaderboard entries for the same (contest, user).
     */
    boolean existsByContestIdAndUserId(Long contestId, Long userId);
}
