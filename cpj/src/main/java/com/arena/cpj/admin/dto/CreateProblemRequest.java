package com.arena.cpj.admin.dto;

import com.arena.cpj.problem.Difficulty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateProblemRequest {

    private String title;
    private String description;
    private String constraints;
    private Difficulty difficulty;
    private String mediaLink;
}
