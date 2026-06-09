package com.arena.cpj.submission;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.config.Judge0Properties;
import com.arena.cpj.judge0.*;
import com.arena.cpj.problem.TestCase;
import com.arena.cpj.problem.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeDispatchService {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final Judge0Client judge0Client;
    private final Judge0Properties judge0Properties;
    private final JudgeSessionTracker sessionTracker;
    private final SubmissionResultService submissionResultService;

    @Async
    public void dispatch(Long submissionId) {
        try {
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

            List<TestCase> testCases = loadJudgeTestCases(submission.getProblem().getId());
            if (testCases.isEmpty()) {
                submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
                return;
            }

            sessionTracker.start(submissionId, testCases);
            dispatchCurrentTestCase(submission);
        } catch (Exception ex) {
            log.error("Failed to dispatch submission {} to Judge0", submissionId, ex);
            submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
        }
    }

    public void dispatchCurrentTestCase(Submission submission) {
        JudgeSessionTracker.JudgeSession session = sessionTracker.get(submission.getId());
        if (session == null) {
            throw new IllegalStateException("No judge session for submission " + submission.getId());
        }

        TestCase testCase = session.currentTestCase();
        String callbackUrl = judge0Client.buildCallbackUrl(submission.getId(), session.currentIndex());

        Judge0SubmissionRequest request = Judge0SubmissionRequest.builder()
                .sourceCode(submission.getCode())
                .languageId(submission.getLanguageId())
                .stdin(testCase.getStdin())
                .expectedOutput(testCase.getExpectedOutput())
                .callbackUrl(callbackUrl)
                .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                .build();

        String token = judge0Client.submit(request);
        submission.setJudge0Token(token);
        submissionRepository.save(submission);
    }

    private List<TestCase> loadJudgeTestCases(Long problemId) {
        List<TestCase> hidden = testCaseRepository.findByProblemId(problemId).stream()
                .filter(tc -> !tc.isSample())
                .toList();
        if (!hidden.isEmpty()) {
            return hidden;
        }
        return testCaseRepository.findByProblemId(problemId);
    }
}
