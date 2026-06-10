package com.arena.cpj.event.dto;

import com.arena.cpj.contest.ContestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestEventDto {
    private Long contestId;
    private ContestStatus status;
}
