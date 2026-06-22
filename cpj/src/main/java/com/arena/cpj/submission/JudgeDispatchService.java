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
            Submission submission = submissionRepository.findByIdWithProblem(submissionId)
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

            double defaultCpuTimeLimit = judge0Properties.getCpuTimeLimit();
            int defaultMemoryLimitKb = judge0Properties.getMemoryLimitKb();

            Double calibratedCpuTimeLimit = null;
            Integer calibratedMemoryLimitKb = null;

            com.arena.cpj.problem.Problem problem = submission.getProblem();
            if (languageId != null) {
                if (languageId == 54) { // C++
                    calibratedCpuTimeLimit = problem.getCppTimeLimit();
                    calibratedMemoryLimitKb = problem.getCppMemoryLimit();
                } else if (languageId == 71) { // Python
                    calibratedCpuTimeLimit = problem.getPythonTimeLimit();
                    calibratedMemoryLimitKb = problem.getPythonMemoryLimit();
                } else if (languageId == 62) { // Java
                    calibratedCpuTimeLimit = problem.getJavaTimeLimit();
                    calibratedMemoryLimitKb = problem.getJavaMemoryLimit();
                }
            }

            final double finalCpuTimeLimit = calibratedCpuTimeLimit != null ? calibratedCpuTimeLimit : defaultCpuTimeLimit;
            final int finalMemoryLimitKb = calibratedMemoryLimitKb != null ? calibratedMemoryLimitKb : defaultMemoryLimitKb;

            // Build one request per testcase (using generous default limits to prevent sandbox crashes)
            List<Judge0SubmissionRequest> requests = testCases.stream()
                    .map(tc -> Judge0SubmissionRequest.builder()
                            .sourceCode(code)
                            .languageId(languageId)
                            .stdin(tc.getStdin())
                            .expectedOutput(tc.getExpectedOutput())
                            .cpuTimeLimit(defaultCpuTimeLimit)
                            .memoryLimitKb(defaultMemoryLimitKb)
                            .build())
                    .toList();


            // ── Step 2: NO transaction held here. Up to ~60s, but no DB
            // connection is pinned during this call. ──
            // Submit all testcases in one batch call, then poll status until all are resolved or any failure is encountered
            log.info("Dispatching batch of {} testcase(s) for submission ID: {}", requests.size(), submissionId);
            List<String> tokens = judge0Client.submitBatch(requests);
            log.info("Batch submitted {} testcase(s). Tokens: {}", tokens.size(), tokens);

            // Poll only the tokens still pending, until all resolved or any failure is encountered
            java.util.Map<String, Judge0CallbackPayload> resolved = new java.util.LinkedHashMap<>();
            java.util.Set<String> pending = new java.util.LinkedHashSet<>(tokens);

            int pollIntervalMs = 1500;
            int maxPolls = 40;
            boolean earlyTermination = false;
            String failingToken = null;

            for (int attempt = 0; attempt < maxPolls && !pending.isEmpty(); attempt++) {
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while polling Judge0", e);
                }

                String tokenParam = String.join(",", pending);
                List<Judge0CallbackPayload> pollResults = judge0Client.getBatch(tokenParam);

                for (Judge0CallbackPayload r : pollResults) {
                    int statusId = r.getStatus() != null ? r.getStatus().getId() : 0;
                    boolean stillRunning = statusId == 1 || statusId == 2; // In Queue or Processing

                    if (!stillRunning) {
                        resolved.put(r.getToken(), r);
                        pending.remove(r.getToken());

                        // Decode and evaluate the verdict of this resolved test case
                        Verdict verdict = Judge0StatusMapper.toVerdict(statusId);
                        Integer actualTimeMs = parseTimeMs(r.getTime());
                        Integer actualMemoryKb = r.getMemory();

                        // Enforce calibrated limits locally if Judge0 returned ACCEPTED
                        if (verdict == Verdict.ACCEPTED) {
                            if (actualTimeMs != null && actualTimeMs > (finalCpuTimeLimit * 1000)) {
                                verdict = Verdict.TIME_LIMIT_EXCEEDED;
                            } else if (actualMemoryKb != null && actualMemoryKb > finalMemoryLimitKb) {
                                verdict = Verdict.MEMORY_LIMIT_EXCEEDED;
                            }
                        }

                        if (verdict != Verdict.ACCEPTED) {
                            earlyTermination = true;
                            failingToken = r.getToken();
                            break;
                        }
                    }
                }

                if (earlyTermination) {
                    log.info("Early termination triggered by failing token: {}", failingToken);
                    break;
                }

                if (pending.isEmpty()) {
                    log.info("Batch resolved after {} poll(s)", attempt + 1);
                    break;
                }

                log.info("Batch poll {}/{}: {}/{} token(s) still pending",
                        attempt + 1, maxPolls, pending.size(), tokens.size());
            }

            // Re-assemble/finalize results in original submission order
            persistJudge0Token(submissionId, tokens.isEmpty() ? null : tokens.get(0));

            // Time and memory are taken from the maximum (worst-case) resource usage across all test cases.
            Verdict finalVerdict = Verdict.ACCEPTED;
            Integer timeMs = null;
            Integer memoryKb = null;
            int passedCount = 0;

            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                Judge0CallbackPayload result = resolved.get(token);

                if (result == null) {
                    if (!earlyTermination) {
                        if (finalVerdict == Verdict.ACCEPTED) {
                            finalVerdict = Verdict.TIME_LIMIT_EXCEEDED;
                            log.info("TestCase #{} did not resolve in time. Treating as TIME_LIMIT_EXCEEDED.", i + 1);
                        }
                    }
                    continue;
                }

                int statusId = result.getStatus() != null ? result.getStatus().getId() : 0;
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);

                Integer actualTimeMs = parseTimeMs(result.getTime());
                Integer actualMemoryKb = result.getMemory();

                if (verdict == Verdict.ACCEPTED) {
                    if (actualTimeMs != null && actualTimeMs > (finalCpuTimeLimit * 1000)) {
                        verdict = Verdict.TIME_LIMIT_EXCEEDED;
                    } else if (actualMemoryKb != null && actualMemoryKb > finalMemoryLimitKb) {
                        verdict = Verdict.MEMORY_LIMIT_EXCEEDED;
                    }
                }

                log.debug("TestCase #{} -> token={}, status={} ({}), verdict={}, time={}ms, memory={}KB",
                        i + 1, token,
                        result.getStatus() != null ? result.getStatus().getDescription() : "UNKNOWN",
                        statusId, verdict, actualTimeMs, actualMemoryKb);

                if (actualTimeMs != null) {
                    if (timeMs == null || actualTimeMs > timeMs) {
                        timeMs = actualTimeMs;
                    }
                }
                if (actualMemoryKb != null) {
                    if (memoryKb == null || actualMemoryKb > memoryKb) {
                        memoryKb = actualMemoryKb;
                    }
                }

                if (verdict != Verdict.ACCEPTED) {
                    if (finalVerdict == Verdict.ACCEPTED) {
                        finalVerdict = verdict;
                        log.info("TestCase #{} failed with verdict: {}.", i + 1, verdict);
                    }
                } else {
                    passedCount++;
                }
            }

            int totalCount = tokens.size();

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
