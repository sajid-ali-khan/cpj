package com.arena.cpj.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TestCaseResponse {

    private final Long id;
    private final Long problemId;
    private final String stdin;
    private final String expectedOutput;
    private final boolean isSample;
}
