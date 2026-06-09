package com.arena.cpj.problem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    // Standard CRUD is sufficient for Phase 1.
    // Phase 3 (admin panel) may add search/filter queries.
}
