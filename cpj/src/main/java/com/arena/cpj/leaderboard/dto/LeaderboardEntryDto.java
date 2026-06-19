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
    private final String status;
    private final int solvedCount;
    private final int totalQuestions;
    private final int maxScore;
    private final int violations;
    private final boolean deleted;
    private final java.util.List<SolvedProblemDto> problems;

    @lombok.Getter
    @lombok.Builder
    public static class SolvedProblemDto {
        private final String title;
        private final String verdict;
        private final int score;
        private final int maxScore;
        private final String time;
    }
}
