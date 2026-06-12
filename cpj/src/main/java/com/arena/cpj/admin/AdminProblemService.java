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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

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

    @Transactional(readOnly = true)
    public List<ProblemResponse> list() {
        return problemRepository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(Long id) {
        return toResponse(findProblem(id));
    }

    private Problem findProblem(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + id));
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
}
