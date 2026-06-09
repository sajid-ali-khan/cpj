package com.arena.cpj.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    /**
     * Fetches all test cases (hidden + sample) for a problem.
     * Used when building the batch payload for Judge0.
     */
    List<TestCase> findByProblemId(Long problemId);

    /**
     * Fetches only sample test cases — useful for displaying
     * examples to the student in the problem view (Phase 3).
     */
    List<TestCase> findByProblemIdAndIsSampleTrue(Long problemId);
}
