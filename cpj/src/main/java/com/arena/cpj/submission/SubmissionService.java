package com.arena.cpj.submission;

import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.Contest;
import com.arena.cpj.contest.ContestProblemRepository;
import com.arena.cpj.contest.ContestRepository;
import com.arena.cpj.contest.ContestStatus;
import com.arena.cpj.judge0.Judge0CallbackPayload;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import com.arena.cpj.submission.dto.SubmissionResponse;
import com.arena.cpj.submission.dto.SubmitResponse;
import com.arena.cpj.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ProblemRepository problemRepository;
    private final JudgeDispatchService judgeDispatchService;

    @Transactional
    public SubmitResponse submit(User user, Long contestId, Long problemId, String code, Integer languageId) {
        validateSubmitRequest(contestId, problemId, code, languageId);

        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new NotFoundException("Contest not found: " + contestId));
        
        // Guard 1: Check contest status is ONGOING
        if (contest.getStatus() != ContestStatus.ONGOING) {
            throw new BadRequestException("Contest is not active");
        }
        
        // Guard 2: Check contest hasn't expired (closes the scheduler gap)
        if (contest.isExpired()) {
            throw new BadRequestException("Contest has ended");
        }

        contestProblemRepository.findByIdContestIdAndIdProblemId(contestId, problemId)
                .orElseThrow(() -> new BadRequestException("Problem is not part of this contest"));

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + problemId));

        Submission submission = Submission.builder()
                .user(user)
                .contest(contest)
                .problem(problem)
                .code(code)
                .languageId(languageId)
                .verdict(Verdict.PENDING)
                .build();
        submission = submissionRepository.save(submission);

        Long submissionId = submission.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    judgeDispatchService.dispatch(submissionId);
                }
            });
        } else {
            judgeDispatchService.dispatch(submissionId);
        }
        return SubmitResponse.builder().submissionId(submission.getId()).build();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getSubmissions(Long userId, Long contestId) {
        return submissionRepository.findByUserIdAndContestIdOrderBySubmittedAtDesc(userId, contestId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void handleCallback(Long submissionId, int testCaseIndex, Judge0CallbackPayload payload) {
        log.warn("Ignoring Judge0 callback for submission {} — judging uses synchronous wait mode", submissionId);
    }

    private void validateSubmitRequest(Long contestId, Long problemId, String code, Integer languageId) {
        if (contestId == null) {
            throw new BadRequestException("contestId is required");
        }
        if (problemId == null) {
            throw new BadRequestException("problemId is required");
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("code is required");
        }
        if (languageId == null) {
            throw new BadRequestException("languageId is required");
        }
    }

    private SubmissionResponse toResponse(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblem().getId())
                .languageId(submission.getLanguageId())
                .code(submission.getCode())
                .verdict(submission.getVerdict())
                .timeMs(submission.getTimeMs())
                .memoryKb(submission.getMemoryKb())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
}
