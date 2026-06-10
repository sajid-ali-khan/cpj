package com.arena.cpj.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ContestProblemRequest {

    private Long problemId;
    private Integer points;
    private Integer displayOrder;
}
