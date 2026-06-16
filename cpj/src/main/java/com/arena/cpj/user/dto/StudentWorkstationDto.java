package com.arena.cpj.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentWorkstationDto {
    private final String name;
    private final String rollNumber;
    private final Long contestId;
    private final int violations;
    private final String status;
}
