package com.arena.cpj.leaderboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LeaderboardEntryDto {

    private final int rank;
    private final Long userId;
    private final String name;
    private final String rollNo;
    private final int score;
    private final LocalDateTime lastAcTime;
}
