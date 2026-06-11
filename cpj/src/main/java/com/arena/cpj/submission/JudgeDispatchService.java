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
                log.warn("No testcases found for problem {}, marking RUNTIME_ERROR",
                        submission.getProblem().getId());
                submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
                return;
            }

            // Build one request per testcase
            List<Judge0SubmissionRequest> requests = testCases.stream()
                    .map(tc -> Judge0SubmissionRequest.builder()
                            .sourceCode(submission.getCode())
                            .languageId(submission.getLanguageId())
                            .stdin(tc.getStdin())
                            .expectedOutput(tc.getExpectedOutput())
                            .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                            .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                            .build())
                    .toList();

            // Submit all testcases in one batch call, then poll until all are resolved
            log.debug("Dispatching batch of {} testcase(s) for submission {}",
                    requests.size(), submissionId);
            List<Judge0CallbackPayload> results = judge0Client.submitBatchAndWait(requests);

            // Store the token of the first result for traceability
            if (!results.isEmpty() && results.get(0).getToken() != null) {
                submission.setJudge0Token(results.get(0).getToken());
                submissionRepository.save(submission);
            }

            // Walk results in testcase order — first non-AC determines the final verdict.
            // Time and memory are taken from the worst-case (last) testcase that ran.
            Verdict finalVerdict = Verdict.ACCEPTED;
            Integer timeMs = null;
            Integer memoryKb = null;

            for (Judge0CallbackPayload result : results) {
                int statusId = result.getStatus() != null ? result.getStatus().getId() : 0;
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);

                // Always update time/memory so we report the last measured testcase
                timeMs   = parseTimeMs(result.getTime());
                memoryKb = result.getMemory();

                if (verdict != Verdict.ACCEPTED) {
                    finalVerdict = verdict;
                    break; // no need to check remaining testcases
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
