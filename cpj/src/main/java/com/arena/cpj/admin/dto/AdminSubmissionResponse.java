package com.arena.cpj.admin.dto;

import com.arena.cpj.submission.Verdict;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminSubmissionResponse {
    private final Long id;
    private final String studentName;
    private final String studentRoll;
    private final String questionTitle;
    private final String language;
    private final Verdict verdict;
    private final Integer timeMs;
    private final Integer memoryKb;
    private final LocalDateTime submittedAt;
}
