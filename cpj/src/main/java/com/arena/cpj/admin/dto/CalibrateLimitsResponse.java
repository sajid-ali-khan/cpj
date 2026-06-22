package com.arena.cpj.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CalibrateLimitsResponse {
    private double maxTime;
    private int maxMemory;
    private double computedTimeLimit;
    private int computedMemoryLimit;
}
