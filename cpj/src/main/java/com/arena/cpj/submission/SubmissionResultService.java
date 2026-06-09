package com.arena.cpj.submission;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.event.SseService;
import com.arena.cpj.judge0.JudgeSessionTracker;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.submission.dto.VerdictEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

        boolean isFirstAc = checkLeaderboard && verdict == Verdict.ACCEPTED
                && !submissionRepository.existsByUserIdAndProblemIdAndContestIdAndVerdict(
                        submission.getUser().getId(),
                        submission.getProblem().getId(),
                        submission.getContest().getId(),
                        Verdict.ACCEPTED);

        submission.setVerdict(verdict);
        submission.setTimeMs(timeMs);
        submission.setMemoryKb(memoryKb);
        submissionRepository.save(submission);
        sessionTracker.remove(submissionId);

        sseService.sendVerdict(submission.getUser().getId(), VerdictEventDto.builder()
                .submissionId(submission.getId())
                .problemId(submission.getProblem().getId())
                .verdict(verdict)
                .timeMs(timeMs)
                .memoryKb(memoryKb)
                .build());

        if (isFirstAc) {
            leaderboardService.recordFirstAcceptedSubmission(
                    submission.getUser(),
                    submission.getContest(),
                    submission.getProblem().getId(),
                    LocalDateTime.now());
        }
    }
}
