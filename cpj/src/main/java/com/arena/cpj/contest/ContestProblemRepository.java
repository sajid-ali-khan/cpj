package com.arena.cpj.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, ContestProblemId> {

    /**
     * Returns all problems for a contest in display order.
     * Used to render the problem list on the contest page.
     */
    List<ContestProblem> findByIdContestIdOrderByDisplayOrderAsc(Long contestId);

    /**
     * Single lookup by composite key parts — useful in the callback handler
     * when resolving points for a newly accepted submission.
     */
    java.util.Optional<ContestProblem> findByIdContestIdAndIdProblemId(Long contestId, Long problemId);
}
