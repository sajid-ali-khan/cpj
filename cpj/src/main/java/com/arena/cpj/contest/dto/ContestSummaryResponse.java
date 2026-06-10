package com.arena.cpj.contest.dto;

import com.arena.cpj.contest.ContestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ContestSummaryResponse {

    private final Long id;
    private final String title;
    private final LocalDateTime startTime;
    private final Integer durationMins;
    private final ContestStatus status;
}
