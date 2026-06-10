package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateProblemRequest;
import com.arena.cpj.admin.dto.ProblemResponse;
import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
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
        int testCaseCount = testCaseRepository.findByProblemId(problem.getId()).size();
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .constraints(problem.getConstraints())
                .difficulty(problem.getDifficulty())
                .mediaLink(problem.getMediaLink())
                .testCaseCount(testCaseCount)
                .build();
    }
}
