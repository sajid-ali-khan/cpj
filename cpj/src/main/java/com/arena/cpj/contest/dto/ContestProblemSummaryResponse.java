package com.arena.cpj.contest.dto;

import com.arena.cpj.problem.Difficulty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContestProblemSummaryResponse {

    private final Long problemId;
    private final String title;
    private final String description;
    private final String constraints;
    private final Difficulty difficulty;
    private final Integer points;
    private final Integer displayOrder;
}
