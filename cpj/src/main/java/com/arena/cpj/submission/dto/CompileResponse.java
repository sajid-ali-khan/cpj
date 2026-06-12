package com.arena.cpj.submission.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompileResponse {
    private final boolean success;
    private final String status;
    private final String output;
    private final String consoleOutput;
}
