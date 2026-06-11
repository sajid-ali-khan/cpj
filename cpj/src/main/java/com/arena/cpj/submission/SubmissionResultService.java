package com.arena.cpj.submission;
 
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.event.SseService;
import com.arena.cpj.judge0.JudgeSessionTracker;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.submission.dto.VerdictEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
 
import java.time.LocalDateTime;
 
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionResultService {
 
    private final SubmissionRepository submissionRepository;
    private final JudgeSessionTracker sessionTracker;
    private final LeaderboardService leaderboardService;
    private final SseService sseService;
 
    @Transactional
    public void finalize(Long submissionId, Verdict verdict, Integer timeMs,
                         Integer memoryKb, boolean checkLeaderboard) {
        log.info("Finalizing submission result - ID: {}, Verdict: {}, Time: {}ms, Memory: {}KB, CheckLeaderboard: {}", 
                 submissionId, verdict, timeMs, memoryKb, checkLeaderboard);
 
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> {
                    log.error("Failed to finalize submission: Submission ID {} not found", submissionId);
                    return new NotFoundException("Submission not found: " + submissionId);
                });
 
        boolean isFirstAc = checkLeaderboard && verdict == Verdict.ACCEPTED
                && !submissionRepository.existsByUserIdAndProblemIdAndContestIdAndVerdict(
                        submission.getUser().getId(),
                        submission.getProblem().getId(),
                        submission.getContest().getId(),
                        Verdict.ACCEPTED);
 
        log.info("Submission details - User ID: {}, Problem ID: {}, Contest ID: {}, isFirstAc: {}", 
                 submission.getUser().getId(), submission.getProblem().getId(), submission.getContest().getId(), isFirstAc);
 
        submission.setVerdict(verdict);
        submission.setTimeMs(timeMs);
        submission.setMemoryKb(memoryKb);
        submissionRepository.save(submission);
        log.info("Saved final verdict to submission ID: {}", submissionId);
 
        Long userId = submission.getUser().getId();
        Long contestId = submission.getContest().getId();
        Long problemId = submission.getProblem().getId();
 
        if (isFirstAc) {
            log.info("Recording first AC for user ID: {} on problem ID: {} for contest ID: {}", userId, problemId, contestId);
            leaderboardService.recordFirstAcceptedSubmission(
                    submission.getUser(),
                    submission.getContest(),
                    problemId,
                    LocalDateTime.now());
        }
 
        Runnable afterCommit = () -> {
            log.info("Removing submission ID {} from JudgeSessionTracker", submissionId);
            sessionTracker.remove(submissionId);
 
            log.info("Sending SSE verdict event for user ID: {}, submission ID: {}, verdict: {}", userId, submissionId, verdict);
            sseService.sendVerdict(userId, VerdictEventDto.builder()
                    .submissionId(submission.getId())
                    .problemId(problemId)
                    .verdict(verdict)
                    .timeMs(timeMs)
                    .memoryKb(memoryKb)
                    .build());
        };
 
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else {
            afterCommit.run();
        }
    }
}
