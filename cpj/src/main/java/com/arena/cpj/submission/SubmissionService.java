package com.arena.cpj.submission;

import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.contest.Contest;
import com.arena.cpj.contest.ContestProblemRepository;
import com.arena.cpj.contest.ContestRepository;
import com.arena.cpj.contest.ContestStatus;
import com.arena.cpj.judge0.Judge0CallbackPayload;
import com.arena.cpj.judge0.Judge0StatusMapper;
import com.arena.cpj.judge0.JudgeSessionTracker;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import com.arena.cpj.submission.dto.SubmissionResponse;
import com.arena.cpj.submission.dto.SubmitResponse;
import com.arena.cpj.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final JudgeSessionTracker sessionTracker;
    private final SubmissionResultService submissionResultService;

    @Transactional
    public SubmitResponse submit(User user, Long contestId, Long problemId, String code, Integer languageId) {
        validateSubmitRequest(contestId, problemId, code, languageId);

        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new NotFoundException("Contest not found: " + contestId));
        if (contest.getStatus() != ContestStatus.ONGOING) {
            throw new BadRequestException("Contest is not ongoing");
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

        judgeDispatchService.dispatch(submission.getId());
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
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

        if (submission.getVerdict() != Verdict.PENDING) {
            log.warn("Ignoring duplicate callback for submission {}", submissionId);
            return;
        }

        JudgeSessionTracker.JudgeSession session = sessionTracker.get(submissionId);
        if (session == null || session.currentIndex() != testCaseIndex) {
            log.warn("Stale or unknown callback for submission {} test case {}", submissionId, testCaseIndex);
            return;
        }

        int statusId = payload.getStatus() != null ? payload.getStatus().getId() : 0;
        Verdict verdict = Judge0StatusMapper.toVerdict(statusId);
        Integer timeMs = parseTimeMs(payload.getTime());
        Integer memoryKb = payload.getMemory();

        if (verdict != Verdict.ACCEPTED) {
            submissionResultService.finalize(submissionId, verdict, timeMs, memoryKb, false);
            return;
        }

        if (session.hasMoreAfterCurrent()) {
            sessionTracker.advance(submissionId);
            judgeDispatchService.dispatchCurrentTestCase(submission);
            return;
        }

        submissionResultService.finalize(submissionId, Verdict.ACCEPTED, timeMs, memoryKb, true);
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

    private Integer parseTimeMs(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(time);
            return (int) Math.round(seconds * 1000);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private SubmissionResponse toResponse(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .problemId(submission.getProblem().getId())
                .languageId(submission.getLanguageId())
                .verdict(submission.getVerdict())
                .timeMs(submission.getTimeMs())
                .memoryKb(submission.getMemoryKb())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
}
