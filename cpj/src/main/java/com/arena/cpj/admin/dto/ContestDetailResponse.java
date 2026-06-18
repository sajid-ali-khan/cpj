package com.arena.cpj.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ContestDetailResponse {
    private final ContestResponse contest;
    private final List<ContestProblemResponse> problems;
}
