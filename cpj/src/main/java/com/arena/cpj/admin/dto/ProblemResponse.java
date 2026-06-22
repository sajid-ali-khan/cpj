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
    private final String inputStructure;
    private final String outputStructure;
    private final int testCaseCount;
    private final java.util.List<TestCaseResponse> testCases;

    private final Double javaTimeLimit;
    private final Integer javaMemoryLimit;
    private final Double cppTimeLimit;
    private final Integer cppMemoryLimit;
    private final Double pythonTimeLimit;
    private final Integer pythonMemoryLimit;
}

