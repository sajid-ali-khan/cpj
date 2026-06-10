package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateTestCaseRequest;
import com.arena.cpj.admin.dto.TestCaseResponse;
import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import com.arena.cpj.problem.TestCase;
import com.arena.cpj.problem.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public TestCaseResponse create(Long problemId, CreateTestCaseRequest request) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + problemId));

        if (request.getExpectedOutput() == null || request.getExpectedOutput().isBlank()) {
            throw new BadRequestException("expectedOutput is required");
        }

        TestCase testCase = TestCase.builder()
                .problem(problem)
                .stdin(request.getStdin())
                .expectedOutput(request.getExpectedOutput())
                .isSample(request.isSample())
                .build();

        return toResponse(testCaseRepository.save(testCase));
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> list(Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            throw new NotFoundException("Problem not found: " + problemId);
        }
        return testCaseRepository.findByProblemId(problemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long testCaseId) {
        if (!testCaseRepository.existsById(testCaseId)) {
            throw new NotFoundException("Test case not found: " + testCaseId);
        }
        testCaseRepository.deleteById(testCaseId);
    }

    private TestCaseResponse toResponse(TestCase testCase) {
        return TestCaseResponse.builder()
                .id(testCase.getId())
                .problemId(testCase.getProblem().getId())
                .stdin(testCase.getStdin())
                .expectedOutput(testCase.getExpectedOutput())
                .isSample(testCase.isSample())
                .build();
    }
}
