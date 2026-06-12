package com.arena.cpj.submission.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitRequest {
    private Long contestId;
    private Long questionId;
    private String rollNumber;
    private String language;
    private String code;
}
