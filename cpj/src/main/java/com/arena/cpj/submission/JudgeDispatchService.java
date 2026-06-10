package com.arena.cpj.submission;

import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.config.Judge0Properties;
import com.arena.cpj.judge0.Judge0CallbackPayload;
import com.arena.cpj.judge0.Judge0StatusMapper;
import com.arena.cpj.judge0.Judge0Client;
import com.arena.cpj.judge0.Judge0SubmissionRequest;
import com.arena.cpj.problem.TestCase;
import com.arena.cpj.problem.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeDispatchService {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final Judge0Client judge0Client;
    private final Judge0Properties judge0Properties;
    private final SubmissionResultService submissionResultService;

    @Async
    @Transactional
    public void dispatch(Long submissionId) {
        try {
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

            List<TestCase> testCases = loadJudgeTestCases(submission.getProblem().getId());
            if (testCases.isEmpty()) {
                submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
                return;
            }

            Verdict finalVerdict = Verdict.ACCEPTED;
            Integer timeMs = null;
            Integer memoryKb = null;

            for (TestCase testCase : testCases) {
                Judge0SubmissionRequest request = Judge0SubmissionRequest.builder()
                        .sourceCode(submission.getCode())
                        .languageId(submission.getLanguageId())
                        .stdin(testCase.getStdin())
                        .expectedOutput(testCase.getExpectedOutput())
                        .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                        .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                        .build();

                Judge0CallbackPayload result = judge0Client.submitAndWait(request);
                if (result.getToken() != null) {
                    submission.setJudge0Token(result.getToken());
                    submissionRepository.save(submission);
                }

                int statusId = result.getStatus() != null ? result.getStatus().getId() : 0;
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);
                timeMs = parseTimeMs(result.getTime());
                memoryKb = result.getMemory();

                if (verdict != Verdict.ACCEPTED) {
                    finalVerdict = verdict;
                    break;
                }
            }

            submissionResultService.finalize(
                    submissionId,
                    finalVerdict,
                    timeMs,
                    memoryKb,
                    finalVerdict == Verdict.ACCEPTED);
        } catch (Exception ex) {
            log.error("Failed to judge submission {}", submissionId, ex);
            submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
        }
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

    private Integer parseTimeMs(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(time) * 1000);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
