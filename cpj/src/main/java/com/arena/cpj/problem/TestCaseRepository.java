package com.arena.cpj.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    /**
     * Fetches all test cases (hidden + sample) for a problem, sorted so that sample cases
     * are evaluated first, followed by remaining hidden test cases sorted by ID.
     */
    @Query("SELECT t FROM TestCase t WHERE t.problem.id = :problemId ORDER BY t.isSample DESC, t.id ASC")
    List<TestCase> findByProblemId(@Param("problemId") Long problemId);

    /**
     * Fetches only sample test cases — useful for displaying
     * examples to the student in the problem view (Phase 3).
     */
    List<TestCase> findByProblemIdAndIsSampleTrue(Long problemId);

    @Query("SELECT t.problem.id, COUNT(t) FROM TestCase t GROUP BY t.problem.id")
    List<Object[]> countByProblemIdGroupByProblemId();
}

