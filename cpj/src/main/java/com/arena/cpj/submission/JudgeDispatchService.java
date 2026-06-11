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
        log.info("=== Dispatching Judge Request ===");
        log.info("Submission ID: {}", submissionId);
        try {
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));
 
            List<TestCase> testCases = loadJudgeTestCases(submission.getProblem().getId());
            log.info("Loaded {} test case(s) for problem ID: {}", testCases.size(), submission.getProblem().getId());
            for (int i = 0; i < testCases.size(); i++) {
                TestCase tc = testCases.get(i);
                log.info("TestCase #{} -> ID: {}, isSample: {}, stdinLength: {}, expectedOutputLength: {}", 
                         i + 1, tc.getId(), tc.isSample(), 
                         tc.getStdin() != null ? tc.getStdin().length() : 0, 
                         tc.getExpectedOutput() != null ? tc.getExpectedOutput().length() : 0);
            }
 
            if (testCases.isEmpty()) {
                log.warn("No testcases found for problem ID: {}, marking RUNTIME_ERROR",
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
            log.info("Dispatching batch of {} testcase(s) for submission ID: {}", requests.size(), submissionId);
            List<Judge0CallbackPayload> results = judge0Client.submitBatchAndWait(requests);
            log.info("Batch execution completed. Received {} result(s).", results.size());
 
            // Store the token of the first result for traceability
            if (!results.isEmpty() && results.get(0).getToken() != null) {
                log.info("Storing first Judge0 token for traceability: {}", results.get(0).getToken());
                submission.setJudge0Token(results.get(0).getToken());
                submissionRepository.save(submission);
            }
 
            // Walk results in testcase order — first non-AC determines the final verdict.
            // Time and memory are taken from the worst-case (last) testcase that ran.
            Verdict finalVerdict = Verdict.ACCEPTED;
            Integer timeMs = null;
            Integer memoryKb = null;
 
            for (int i = 0; i < results.size(); i++) {
                Judge0CallbackPayload result = results.get(i);
                int statusId = result.getStatus() != null ? result.getStatus().getId() : 0;
                String statusDescription = result.getStatus() != null ? result.getStatus().getDescription() : "UNKNOWN";
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);
 
                log.info("--- TestCase #{} Result ---", i + 1);
                log.info("Token: {}", result.getToken());
                log.info("Status: {} (ID: {})", statusDescription, statusId);
                log.info("Mapped Verdict: {}", verdict);
                log.info("Time: {}s, Memory: {} KB", result.getTime(), result.getMemory());
                if (result.getStdout() != null && !result.getStdout().isBlank()) {
                    log.info("Stdout:\n{}", result.getStdout());
                }
                if (result.getStderr() != null && !result.getStderr().isBlank()) {
                    log.info("Stderr:\n{}", result.getStderr());
                }
                if (result.getCompileOutput() != null && !result.getCompileOutput().isBlank()) {
                    log.info("Compile Output:\n{}", result.getCompileOutput());
                }
                if (result.getMessage() != null && !result.getMessage().isBlank()) {
                    log.info("Message: {}", result.getMessage());
                }
                log.info("----------------------------");
 
                // Always update time/memory so we report the last measured testcase
                timeMs   = parseTimeMs(result.getTime());
                memoryKb = result.getMemory();
 
                if (verdict != Verdict.ACCEPTED) {
                    finalVerdict = verdict;
                    log.info("TestCase #{} failed with verdict: {}. Stopping evaluation.", i + 1, verdict);
                    break; // no need to check remaining testcases
                }
            }
 
            log.info("Finalizing judge result for submission ID {}: finalVerdict={}, timeMs={}, memoryKb={}", 
                     submissionId, finalVerdict, timeMs, memoryKb);
            submissionResultService.finalize(
                    submissionId,
                    finalVerdict,
                    timeMs,
                    memoryKb,
                    finalVerdict == Verdict.ACCEPTED);
 
        } catch (Exception ex) {
            log.error("Failed to judge submission ID: {}", submissionId, ex);
            submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
        }
        log.info("=== Dispatch Processing Finished ===");
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
