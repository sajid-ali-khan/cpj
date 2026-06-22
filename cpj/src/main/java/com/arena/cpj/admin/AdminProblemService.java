package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateProblemRequest;
import com.arena.cpj.admin.dto.ProblemResponse;
import com.arena.cpj.admin.dto.TestCaseResponse;
import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import com.arena.cpj.problem.TestCase;
import com.arena.cpj.problem.TestCaseRepository;
import com.arena.cpj.contest.Contest;
import com.arena.cpj.contest.ContestPhase;
import com.arena.cpj.contest.ContestProblem;
import com.arena.cpj.contest.ContestProblemRepository;
import com.arena.cpj.contest.ContestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestRepository contestRepository;
    private final com.arena.cpj.judge0.Judge0Client judge0Client;
    private final com.arena.cpj.config.Judge0Properties judge0Properties;


    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        validate(request);

        Problem problem = Problem.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .constraints(request.getConstraints())
                .difficulty(request.getDifficulty())
                .mediaLink(request.getMediaLink())
                .inputStructure(request.getInputStructure())
                .outputStructure(request.getOutputStructure())
                .build();

        return toResponse(problemRepository.save(problem));
    }

    @Transactional
    public void delete(Long id, boolean force) {
        Problem problem = findProblem(id);

        List<ContestProblem> associations = contestProblemRepository.findByIdProblemId(id);
        List<ContestProblem> upcomingAssociations = associations.stream()
                .filter(cp -> cp.getContest().getPhase(Instant.now()) == ContestPhase.UPCOMING)
                .toList();

        if (!upcomingAssociations.isEmpty()) {
            if (!force) {
                String contestTitles = upcomingAssociations.stream()
                        .map(cp -> cp.getContest().getTitle())
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new BadRequestException("This problem is part of upcoming contests: " + contestTitles + 
                        ". Deleting it will automatically remove it from these contests.");
            } else {
                for (ContestProblem cp : upcomingAssociations) {
                    Contest contest = cp.getContest();
                    contest.setProblemCount(Math.max(0, contest.getProblemCount() - 1));
                    contest.setMaxScore(Math.max(0, contest.getMaxScore() - cp.getPoints()));
                    contestRepository.save(contest);
                    contestProblemRepository.delete(cp);
                }
            }
        }

        problem.setDeleted(true);
        problemRepository.save(problem);
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> list() {
        return problemRepository.findAllByDeletedFalseOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(Long id) {
        return toResponse(findProblem(id));
    }

    private Problem findProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + id));
        if (problem.isDeleted()) {
            throw new NotFoundException("Problem not found: " + id);
        }
        return problem;
    }

    private void validate(CreateProblemRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BadRequestException("description is required");
        }
    }

    private ProblemResponse toResponse(Problem problem) {
        List<TestCase> tcs = testCaseRepository.findByProblemId(problem.getId());
        List<TestCaseResponse> testCaseResponses = tcs.stream()
                .map(tc -> TestCaseResponse.builder()
                        .id(tc.getId())
                        .problemId(tc.getProblem().getId())
                        .stdin(tc.getStdin())
                        .expectedOutput(tc.getExpectedOutput())
                        .isSample(tc.isSample())
                        .build())
                .toList();

        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .constraints(problem.getConstraints())
                .difficulty(problem.getDifficulty())
                .mediaLink(problem.getMediaLink())
                .inputStructure(problem.getInputStructure())
                .outputStructure(problem.getOutputStructure())
                .testCaseCount(tcs.size())
                .testCases(testCaseResponses)
                .javaTimeLimit(problem.getJavaTimeLimit())
                .javaMemoryLimit(problem.getJavaMemoryLimit())
                .cppTimeLimit(problem.getCppTimeLimit())
                .cppMemoryLimit(problem.getCppMemoryLimit())
                .pythonTimeLimit(problem.getPythonTimeLimit())
                .pythonMemoryLimit(problem.getPythonMemoryLimit())
                .build();
    }

    @Transactional
    public ProblemResponse update(Long id, CreateProblemRequest request) {
        validate(request);
        Problem problem = findProblem(id);
        problem.setTitle(request.getTitle().trim());
        problem.setDescription(request.getDescription().trim());
        problem.setConstraints(request.getConstraints());
        problem.setDifficulty(request.getDifficulty());
        problem.setMediaLink(request.getMediaLink());
        problem.setInputStructure(request.getInputStructure());
        problem.setOutputStructure(request.getOutputStructure());
        return toResponse(problemRepository.save(problem));
    }

    @Transactional
    public com.arena.cpj.admin.dto.CalibrateLimitsResponse calibrateLimits(Long problemId, com.arena.cpj.admin.dto.CalibrateLimitsRequest request) {
        Problem problem = findProblem(problemId);

        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new BadRequestException("language is required");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BadRequestException("code is required");
        }

        int languageId;
        String lang = request.getLanguage().toLowerCase();
        if (lang.equals("java")) {
            languageId = 62;
        } else if (lang.equals("cpp") || lang.equals("c++")) {
            languageId = 54;
        } else if (lang.equals("python")) {
            languageId = 71;
        } else {
            throw new BadRequestException("Unsupported language for calibration: " + request.getLanguage());
        }

        List<TestCase> testCases = testCaseRepository.findByProblemId(problemId);
        if (testCases.isEmpty()) {
            throw new BadRequestException("Cannot calibrate: problem has no test cases");
        }

        // Build Judge0 requests with default limits for safety during calibration
        List<com.arena.cpj.judge0.Judge0SubmissionRequest> judgeRequests = testCases.stream()
                .map(tc -> com.arena.cpj.judge0.Judge0SubmissionRequest.builder()
                        .sourceCode(request.getCode())
                        .languageId(languageId)
                        .stdin(tc.getStdin())
                        .expectedOutput(tc.getExpectedOutput())
                        .cpuTimeLimit(judge0Properties.getCpuTimeLimit())
                        .memoryLimitKb(judge0Properties.getMemoryLimitKb())
                        .build())
                .toList();

        List<com.arena.cpj.judge0.Judge0CallbackPayload> results;
        try {
            results = judge0Client.submitBatchAndWait(judgeRequests);
        } catch (Exception e) {
            throw new BadRequestException("Failed to run code on Judge0: " + e.getMessage());
        }

        double maxTime = 0.0;
        int maxMemory = 0;

        for (int i = 0; i < results.size(); i++) {
            com.arena.cpj.judge0.Judge0CallbackPayload res = results.get(i);
            int statusId = res.getStatus() != null ? res.getStatus().getId() : 0;
            if (statusId != 3) { // 3 = Accepted
                String desc = res.getStatus() != null ? res.getStatus().getDescription() : "UNKNOWN";
                String errOutput = res.getStderr() != null ? res.getStderr() : "";
                if (res.getCompileOutput() != null && !res.getCompileOutput().isBlank()) {
                    errOutput = res.getCompileOutput();
                }
                throw new BadRequestException("Correct solution failed on testcase #" + (i + 1) + ". Status: " + desc + ". Error: " + errOutput);
            }

            if (res.getTime() != null) {
                try {
                    double t = Double.parseDouble(res.getTime());
                    if (t > maxTime) {
                        maxTime = t;
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (res.getMemory() != null) {
                int m = res.getMemory();
                if (m > maxMemory) {
                    maxMemory = m;
                }
            }
        }

        // Update limits based on language with language-specific safety floors
        int computedMemoryLimit;
        double computedTimeLimit;
        if (languageId == 62) { // Java
            computedTimeLimit = Math.max(2.0, maxTime * 2.0);
            computedMemoryLimit = Math.max(262144, maxMemory * 2); // 256MB floor for JVM
            problem.setJavaTimeLimit(Math.round(computedTimeLimit * 100.0) / 100.0);
            problem.setJavaMemoryLimit(computedMemoryLimit);
        } else if (languageId == 54) { // C++
            computedTimeLimit = Math.max(1.0, maxTime * 2.0);
            computedMemoryLimit = Math.max(65536, maxMemory * 2);  // 64MB floor
            problem.setCppTimeLimit(Math.round(computedTimeLimit * 100.0) / 100.0);
            problem.setCppMemoryLimit(computedMemoryLimit);
        } else if (languageId == 71) { // Python
            computedTimeLimit = Math.max(2.0, maxTime * 2.0);
            computedMemoryLimit = Math.max(131072, maxMemory * 2); // 128MB floor for Python
            problem.setPythonTimeLimit(Math.round(computedTimeLimit * 100.0) / 100.0);
            problem.setPythonMemoryLimit(computedMemoryLimit);
        } else {
            computedTimeLimit = Math.max(1.0, maxTime * 2.0);
            computedMemoryLimit = Math.max(32768, maxMemory * 2);
        }

        problemRepository.save(problem);

        return com.arena.cpj.admin.dto.CalibrateLimitsResponse.builder()
                .maxTime(maxTime)
                .maxMemory(maxMemory)
                .computedTimeLimit(computedTimeLimit)
                .computedMemoryLimit(computedMemoryLimit)
                .build();
    }
}

