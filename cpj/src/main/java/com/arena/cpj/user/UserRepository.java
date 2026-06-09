package com.arena.cpj.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by Phase-1 roll-number based auth */
    Optional<User> findByRollNo(String rollNo);
}
