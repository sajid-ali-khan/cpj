package com.arena.cpj.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Callback handler lookup: Judge0 returns a token, we find the submission to update.
     */
    Optional<Submission> findByJudge0Token(String judge0Token);

    /**
     * First-AC guard: returns true if the user already has an accepted submission
     * for this (problem, contest) pair — leaderboard must NOT be updated again.
     */
    boolean existsByUserIdAndProblemIdAndContestIdAndVerdict(
            Long userId, Long problemId, Long contestId, Verdict verdict);

    /**
     * Student submission history for a contest (shown after SSE reconnect).
     * Ordered newest-first so the UI can display the latest verdict at the top.
     */
    List<Submission> findByUserIdAndContestIdOrderBySubmittedAtDesc(Long userId, Long contestId);

    /**
     * Get all submissions for a specific contest ordered by time descending.
     */
    List<Submission> findByContestIdOrderBySubmittedAtDesc(Long contestId);
}
