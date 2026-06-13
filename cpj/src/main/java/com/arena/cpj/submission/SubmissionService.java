package com.arena.cpj.submission;

import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.config.Judge0Properties;
import com.arena.cpj.contest.Contest;
import com.arena.cpj.contest.ContestProblemRepository;
import com.arena.cpj.contest.ContestRepository;
import com.arena.cpj.contest.ContestPhase;
import com.arena.cpj.judge0.*;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import com.arena.cpj.problem.TestCase;
import com.arena.cpj.problem.TestCaseRepository;
import com.arena.cpj.submission.dto.*;
import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.auth.ForbiddenException;
import com.arena.cpj.auth.UserContext;
import com.arena.cpj.leaderboard.Leaderboard;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import com.arena.cpj.leaderboard.ParticipantStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final TestCaseRepository testCaseRepository;
    private final Judge0Client judge0Client;
    private final Judge0Properties judge0Properties;
    private final SubmissionResultService submissionResultService;
    private final UserRepository userRepository;
    private final LeaderboardRepository leaderboardRepository;

    @Transactional
    public SubmitResponse submit(User user, Long contestId, Long problemId, String code, Integer languageId) {
        log.info("=== Submission Flow Started ===");
        log.info("Request Details - User: {}, Contest ID: {}, Problem ID: {}, Language ID: {}", 
                 user != null ? user.getName() + " (RollNo: " + user.getRollNo() + ", ID: " + user.getId() + ")" : "Anonymous", 
                 contestId, problemId, languageId);
        log.info("Submitted Source Code:\n{}", code);
 
        try {
            validateSubmitRequest(contestId, problemId, code, languageId);
        } catch (BadRequestException e) {
            log.warn("Validation failed for submission request: {}", e.getMessage());
            throw e;
        }
 
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> {
                    log.warn("Contest validation failed: Contest ID {} not found", contestId);
                    return new NotFoundException("Contest not found: " + contestId);
                });
        
        // Guard 1: Check contest phase is LIVE
        if (contest.getPhase(Instant.now()) != ContestPhase.LIVE) {
            log.warn("Contest validation failed: Contest ID {} phase is not LIVE", contestId);
            throw new BadRequestException("Contest is not active");
        }
        
        // Guard 2: Check contest hasn't expired (closes the scheduler gap)
        if (contest.isExpired()) {
            log.warn("Contest validation failed: Contest ID {} has expired", contestId);
            throw new BadRequestException("Contest has ended");
        }
 
        contestProblemRepository.findByIdContestIdAndIdProblemId(contestId, problemId)
                .orElseThrow(() -> {
                    log.warn("Problem validation failed: Problem ID {} is not part of Contest ID {}", problemId, contestId);
                    return new BadRequestException("Problem is not part of this contest");
                });
 
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> {
                    log.warn("Problem validation failed: Problem ID {} not found", problemId);
                    return new NotFoundException("Problem not found: " + problemId);
                });
 
        Submission submission = Submission.builder()
                .user(user)
                .contest(contest)
                .problem(problem)
                .code(code)
                .languageId(languageId)
                .verdict(Verdict.PENDING)
                .build();
        submission = submissionRepository.save(submission);
        log.info("Submission saved to DB. Generated Submission ID: {}", submission.getId());
 
        Long submissionId = submission.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            log.info("Transaction synchronization active. Registering afterCommit synchronization to dispatch submission ID: {}", submissionId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Transaction committed. Dispatching submission ID: {}", submissionId);
                    judgeDispatchService.dispatch(submissionId);
                }
            });
        } else {
            log.info("Transaction synchronization not active. Dispatching submission ID immediately: {}", submissionId);
            judgeDispatchService.dispatch(submissionId);
        }
        log.info("=== Submission Flow Completed (ID: {}) ===", submission.getId());
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

    @Transactional
    public CompileResponse compileAndRun(CompileRequest request) {
        log.info("Compile and run started - Problem ID: {}, Language: {}", request.getQuestionId(), request.getLanguage());

        User currentUser = UserContext.get();
        if (currentUser != null && currentUser.getRole() == com.arena.cpj.user.UserRole.STUDENT) {
            Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(request.getContestId(), currentUser.getId())
                    .orElseThrow(() -> new ForbiddenException("You are not registered for this contest"));
            if (entry.getStatus() == ParticipantStatus.FINISHED) {
                throw new ForbiddenException("You have already submitted this contest");
            }
        }

        int judge0LangId = getLanguageId(request.getLanguage());

        List<TestCase> sampleCases = testCaseRepository.findByProblemIdAndIsSampleTrue(request.getQuestionId());

        if (request.getCustomInput() != null && !request.getCustomInput().isBlank()) {
            TestCase customCase = TestCase.builder()
                    .stdin(request.getCustomInput())
                    .expectedOutput("")
                    .isSample(true)
                    .build();
            sampleCases = new java.util.ArrayList<>(sampleCases);
            sampleCases.add(customCase);
        }

        if (sampleCases.isEmpty()) {
            return CompileResponse.builder()
                    .success(false)
                    .status("Compilation Error")
                    .output("No sample testcases found for this problem")
                    .consoleOutput("")
                    .build();
        }

        List<Judge0SubmissionRequest> judgeRequests = sampleCases.stream()
                .map(tc -> Judge0SubmissionRequest.builder()
                        .sourceCode(request.getCode())
                        .languageId(judge0LangId)
                        .stdin(tc.getStdin())
                        .expectedOutput(tc.getExpectedOutput())
                        .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                        .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                        .build())
                .toList();

        try {
            List<Judge0CallbackPayload> results = judge0Client.submitBatchAndWait(judgeRequests);

            boolean allPassed = true;
            StringBuilder consoleBuilder = new StringBuilder();
            List<CompileResponse.TestCaseResult> tcResults = new java.util.ArrayList<>();

            for (int i = 0; i < results.size(); i++) {
                Judge0CallbackPayload res = results.get(i);
                TestCase tc = sampleCases.get(i);
                int statusId = res.getStatus() != null ? res.getStatus().getId() : 0;
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);

                if (statusId == 6) { // Compilation Error
                    return CompileResponse.builder()
                            .success(false)
                            .status("Compilation Error")
                            .output(res.getCompileOutput())
                            .consoleOutput("")
                            .build();
                }

                if (verdict != Verdict.ACCEPTED && !(tc.getExpectedOutput() == null || tc.getExpectedOutput().isBlank())) {
                    allPassed = false;
                }

                CompileResponse.TestCaseResult tcRes = CompileResponse.TestCaseResult.builder()
                        .stdin(tc.getStdin())
                        .expectedOutput(tc.getExpectedOutput())
                        .actualOutput(res.getStdout() != null ? res.getStdout() : "")
                        .verdict(verdict.name())
                        .stderr(res.getStderr() != null ? res.getStderr() : "")
                        .success(verdict == Verdict.ACCEPTED)
                        .build();
                tcResults.add(tcRes);

                consoleBuilder.append("Case #").append(i + 1).append(":\n");
                consoleBuilder.append("Input:\n").append(tc.getStdin()).append("\n");
                consoleBuilder.append("Expected Output:\n").append(tc.getExpectedOutput()).append("\n");
                consoleBuilder.append("Actual Output:\n").append(res.getStdout() != null ? res.getStdout() : "").append("\n");
                if (res.getStderr() != null && !res.getStderr().isBlank()) {
                    consoleBuilder.append("Stderr:\n").append(res.getStderr()).append("\n");
                }
                consoleBuilder.append("Verdict: ").append(verdict).append("\n");
                consoleBuilder.append("Time: ").append(res.getTime()).append("s, Memory: ").append(res.getMemory()).append(" KB\n\n");
            }

            String finalStatus = allPassed ? "Accepted" : "Wrong Answer";

            String outputStr = "Execution finished successfully.";
            for (Judge0CallbackPayload res : results) {
                if (res.getStderr() != null && !res.getStderr().isBlank()) {
                    outputStr = res.getStderr();
                    break;
                }
            }

            return CompileResponse.builder()
                    .success(allPassed)
                    .status(finalStatus)
                    .output(outputStr)
                    .consoleOutput(consoleBuilder.toString())
                    .testCaseResults(tcResults)
                    .build();

        } catch (Exception e) {
            log.error("Compile and run failed", e);
            return CompileResponse.builder()
                    .success(false)
                    .status("Runtime Error")
                    .output(e.getMessage())
                    .consoleOutput("")
                    .build();
        }
    }

    @Transactional
    public StudentSubmitResponse submitSynchronous(SubmitRequest request) {
        User user = com.arena.cpj.auth.UserContext.get();
        if (user == null) {
            throw new com.arena.cpj.auth.UnauthorizedException("Session invalid or expired. Please log in again.");
        }

        Contest contest = contestRepository.findById(request.getContestId())
                .orElseThrow(() -> new NotFoundException("Contest not found: " + request.getContestId()));

        Leaderboard entry = leaderboardRepository.findByContestIdAndUserId(contest.getId(), user.getId())
                .orElseThrow(() -> new ForbiddenException("You are not registered for this contest"));
        if (entry.getStatus() == ParticipantStatus.FINISHED) {
            throw new ForbiddenException("You have already submitted this contest");
        }

        if (contest.getPhase(Instant.now()) != ContestPhase.LIVE) {
            throw new BadRequestException("Contest is not active");
        }
        if (contest.isExpired()) {
            throw new BadRequestException("Contest has ended");
        }

        Problem problem = problemRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new NotFoundException("Problem not found: " + request.getQuestionId()));

        int judge0LangId = getLanguageId(request.getLanguage());

        Submission submission = Submission.builder()
                .user(user)
                .contest(contest)
                .problem(problem)
                .code(request.getCode())
                .languageId(judge0LangId)
                .verdict(Verdict.PENDING)
                .build();
        submission = submissionRepository.save(submission);
        Long submissionId = submission.getId();

        List<TestCase> allCases = testCaseRepository.findByProblemId(problem.getId());

        if (allCases.isEmpty()) {
            submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
            return StudentSubmitResponse.builder()
                    .success(false)
                    .verdict("Runtime Error")
                    .passed(0)
                    .total(0)
                    .build();
        }

        List<Judge0SubmissionRequest> judgeRequests = allCases.stream()
                .map(tc -> Judge0SubmissionRequest.builder()
                        .sourceCode(request.getCode())
                        .languageId(judge0LangId)
                        .stdin(tc.getStdin())
                        .expectedOutput(tc.getExpectedOutput())
                        .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                        .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                        .build())
                .toList();

        try {
            List<Judge0CallbackPayload> results = judge0Client.submitBatchAndWait(judgeRequests);

            if (!results.isEmpty() && results.get(0).getToken() != null) {
                submission.setJudge0Token(results.get(0).getToken());
                submissionRepository.save(submission);
            }

            Verdict finalVerdict = Verdict.ACCEPTED;
            Integer timeMs = null;
            Integer memoryKb = null;
            int passedCount = 0;

            for (int i = 0; i < results.size(); i++) {
                Judge0CallbackPayload result = results.get(i);
                int statusId = result.getStatus() != null ? result.getStatus().getId() : 0;
                Verdict verdict = Judge0StatusMapper.toVerdict(statusId);

                Integer currentMs = parseTimeMs(result.getTime());
                if (currentMs != null && (timeMs == null || currentMs > timeMs)) {
                    timeMs = currentMs;
                }
                if (result.getMemory() != null && (memoryKb == null || result.getMemory() > memoryKb)) {
                    memoryKb = result.getMemory();
                }

                if (verdict == Verdict.ACCEPTED) {
                    passedCount++;
                } else if (finalVerdict == Verdict.ACCEPTED) {
                    finalVerdict = verdict;
                }
            }

            submissionResultService.finalize(submissionId, finalVerdict, timeMs, memoryKb, finalVerdict == Verdict.ACCEPTED);

            return StudentSubmitResponse.builder()
                    .success(finalVerdict == Verdict.ACCEPTED)
                    .verdict(finalVerdict.name().replace("_", " "))
                    .passed(passedCount)
                    .total(allCases.size())
                    .build();

        } catch (Exception e) {
            log.error("Synchronous submission judge failed", e);
            submissionResultService.finalize(submissionId, Verdict.RUNTIME_ERROR, null, null, false);
            return StudentSubmitResponse.builder()
                    .success(false)
                    .verdict("Runtime Error")
                    .passed(0)
                    .total(allCases.size())
                    .build();
        }
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

    private static int getLanguageId(String language) {
        if (language == null) return 62;
        switch (language.toLowerCase()) {
            case "java": return 62;
            case "cpp": case "c++": return 54;
            case "python": return 71;
            default: return 62;
        }
    }
}
