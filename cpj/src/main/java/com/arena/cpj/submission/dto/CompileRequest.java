package com.arena.cpj.submission.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompileRequest {
    private Long contestId;
    private Long questionId;
    private String language;
    private String code;
    private String customInput;
}
