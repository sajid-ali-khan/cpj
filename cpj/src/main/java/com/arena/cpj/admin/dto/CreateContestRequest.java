package com.arena.cpj.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateContestRequest {

    private String title;
    private LocalDateTime startTime;
    private Integer durationMins;
    private List<ContestProblemRequest> problems;
}
