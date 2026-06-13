package com.arena.cpj.submission.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class CompileResponse {
    private final boolean success;
    private final String status;
    private final String output;
    private final String consoleOutput;
    private final List<TestCaseResult> testCaseResults;

    @Getter
    @Builder
    public static class TestCaseResult {
        private final String stdin;
        private final String expectedOutput;
        private final String actualOutput;
        private final String verdict;
        private final String stderr;
        private final boolean success;
    }
}
