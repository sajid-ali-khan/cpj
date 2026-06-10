package com.arena.cpj.admin.dto;

import com.arena.cpj.problem.Difficulty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContestProblemResponse {

    private final Long problemId;
    private final String title;
    private final Difficulty difficulty;
    private final Integer points;
    private final Integer displayOrder;
}
