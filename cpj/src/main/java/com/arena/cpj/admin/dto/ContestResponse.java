package com.arena.cpj.admin.dto;

import com.arena.cpj.contest.ContestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ContestResponse {

    private final Long id;
    private final String title;
    private final LocalDateTime startTime;
    private final Integer durationMins;
    private final ContestStatus status;
    private final List<ContestProblemResponse> problems;
}
