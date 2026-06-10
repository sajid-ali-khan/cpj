package com.arena.cpj.admin.dto;

import com.arena.cpj.problem.Difficulty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProblemResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final String constraints;
    private final Difficulty difficulty;
    private final String mediaLink;
    private final int testCaseCount;
}
