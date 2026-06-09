package com.arena.cpj.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    /**
     * Finds the currently active contest.
     * Per business rule: only one contest runs at a time.
     */
    Optional<Contest> findByStatus(ContestStatus status);
}
