package com.arena.cpj.submission.dto;

import com.arena.cpj.submission.Verdict;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SubmissionResponse {

    private final Long id;
    private final Long problemId;
    private final Integer languageId;
    private final Verdict verdict;
    private final Integer timeMs;
    private final Integer memoryKb;
    private final LocalDateTime submittedAt;
}
