package com.arena.cpj.submission.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentSubmitResponse {
    private final boolean success;
    private final String verdict;
    private final int passed;
    private final int total;
}
