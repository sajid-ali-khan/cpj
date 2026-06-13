package com.arena.cpj.admin.dto;

import com.arena.cpj.contest.ContestPhase;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ContestResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final Instant startTime;
    private final Integer durationMins;
    private final ContestPhase phase;
    private final List<ContestProblemResponse> problems;
}
