package com.arena.cpj.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    List<Contest> findAllByOrderByStartTimeDesc();

    List<Contest> findAllByDeletedFalseOrderByStartTimeDesc();

    org.springframework.data.domain.Page<Contest> findAllByDeletedFalseOrderByStartTimeDesc(
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Contest> findByIdInAndDeletedFalseOrderByStartTimeDesc(
            java.util.Collection<Long> ids,
            org.springframework.data.domain.Pageable pageable);
}
