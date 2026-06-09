package com.arena.cpj.submission.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmissionRequest {

    private Long contestId;
    private Long problemId;
    private String code;
    private Integer languageId;
}
