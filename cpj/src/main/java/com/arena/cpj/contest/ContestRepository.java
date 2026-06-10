package com.arena.cpj.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findByStatus(ContestStatus status);

    List<Contest> findAllByStatus(ContestStatus status);

    List<Contest> findAllByOrderByStartTimeDesc();
}
