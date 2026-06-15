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

    /**
     * We call this method asynchronously after the submission is saved to the
     * database. It will load the submission and related data, submit it to Judge0,
     * and process the results.
     * The method is annotated with @Async, so it will run in a separate thread and
     * not block the main request thread. This is important because the judging
     * process can take a long time, especially if there are many test cases or if
     * the code takes a long time to run.
     */
    @Async
    public void dispatch(Long submissionId) {
        log.info("=== Dispatching Judge Request ===");
        log.info("Submission ID: {}", submissionId);
        try {
            // Step 1: Load submission and related data from DB
            // short-lived transaction/connection (Spring Data default). ──
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

            String code = submission.getCode();
            Integer languageId = submission.getLanguageId();
            Long problemId = submission.getProblem().getId();

            List<TestCase> testCases = loadJudgeTestCases(submission.getProblem().getId());
            log.info("Loaded {} test case(s) for problem ID: {}", testCases.size(), submission.getProblem().getId());

            if (testCases.isEmpty()) {
                log.warn("No testcases found for problem ID: {}, marking RUNTIME_ERROR",
                        submission.getProblem().getId());
                submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false, 0, 0);
                return;
            }

            // Build one request per testcase
            List<Judge0SubmissionRequest> requests = testCases.stream()
                    .map(tc -> Judge0SubmissionRequest.builder()
                            .sourceCode(code)
                            .languageId(languageId)
                            .stdin(tc.getStdin())
                            .expectedOutput(tc.getExpectedOutput())
                            .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                            .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                            .build())
                    .toList();

            // ── Step 2: NO transaction held here. Up to ~60s, but no DB
            // connection is pinned during this call. ──
            // Submit all testcases in one batch call, then poll until all are resolved
            log.info("Dispatching batch of {} testcase(s) for submission ID: {}", requests.size(), submissionId);
            List<Judge0CallbackPayload> results = judge0Client.submitBatchAndWait(requests);
            log.info("Batch execution completed. Received {} result(s).", results.size());

            // Step 3: Short transaction(s) to persist results.
            persistJudge0Token(submissionId, results.isEmpty() ? null : results.get(0).getToken());

            // Time and memory are taken from the worst-case (last) testcase that ran.
            Verdict finalVerdict = Verdict.ACCEPTED;
            Integer timeMs = null;
            Integer memoryKb = null;
            int passedCount = 0;

            for (int i = 0; i < results.size(); i++) {
                Judge0CallbackPayload result = results.get(i);
                int statusId = result.getStatus() != null ? result.getStatus().getId() : 0;
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);

                log.debug("TestCase #{} -> token={}, status={} ({}), verdict={}, time={}s, memory={}KB",
                        i + 1, result.getToken(),
                        result.getStatus() != null ? result.getStatus().getDescription() : "UNKNOWN",
                        statusId, verdict, result.getTime(), result.getMemory());
                log.trace("TestCase #{} stdout: {}", i + 1, result.getStdout());
                log.trace("TestCase #{} stderr: {}", i + 1, result.getStderr());
                log.trace("TestCase #{} compileOutput: {}", i + 1, result.getCompileOutput());

                timeMs = parseTimeMs(result.getTime());
                memoryKb = result.getMemory();

                if (verdict != Verdict.ACCEPTED) {
                    finalVerdict = verdict;
                    log.info("TestCase #{} failed with verdict: {}. Stopping evaluation.", i + 1, verdict);
                    break;
                }
                passedCount++;
            }

            int totalCount = results.size();

            log.info(
                    "Finalizing judge result for submission ID {}: finalVerdict={}, timeMs={}, memoryKb={}, passed={}/{}",
                    submissionId, finalVerdict, timeMs, memoryKb, passedCount, totalCount);
            submissionResultService.finalize(
                    submissionId,
                    finalVerdict,
                    timeMs,
                    memoryKb,
                    finalVerdict == Verdict.ACCEPTED,
                    passedCount,
                    totalCount);

        } catch (Exception ex) {
            log.error("Failed to judge submission ID: {}", submissionId, ex);
            submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false, 0, 0);
        }
        log.info("=== Dispatch Processing Finished ===");
    }

    private List<TestCase> loadJudgeTestCases(Long problemId) {
        return testCaseRepository.findByProblemId(problemId);
    }

    private void persistJudge0Token(Long submissionId, String token) {
        if (token == null) {
            return;
        }
        submissionRepository.findById(submissionId).ifPresent(s -> {
            s.setJudge0Token(token);
            submissionRepository.save(s);
        });
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
