package com.arena.cpj.submission.dto;

import com.arena.cpj.submission.Verdict;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerdictEventDto {

    private final Long submissionId;
    private final Long problemId;
    private final Verdict verdict;
    private final Integer timeMs;
    private final Integer memoryKb;
}
