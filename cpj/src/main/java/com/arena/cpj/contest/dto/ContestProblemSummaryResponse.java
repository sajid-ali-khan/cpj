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
    private final String inputStructure;
    private final String outputStructure;
    private final java.util.List<SampleTestCaseDto> testCases;

    @Getter
    @Builder
    public static class SampleTestCaseDto {
        private final String input;
        private final String output;
        private final boolean isHidden;
    }
}

