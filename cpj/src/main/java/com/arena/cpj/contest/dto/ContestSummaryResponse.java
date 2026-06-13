package com.arena.cpj.contest.dto;

import com.arena.cpj.contest.ContestPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ContestSummaryResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final Instant startTime;
    private final Integer durationMins;
    private final ContestPhase phase;
    private final java.util.List<Long> problemIds;
}
